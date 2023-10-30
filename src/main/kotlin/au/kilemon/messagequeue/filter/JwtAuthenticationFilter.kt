package au.kilemon.messagequeue.filter

import au.kilemon.messagequeue.logging.HasLogger
import au.kilemon.messagequeue.authentication.MultiQueueAuthenticationType
import au.kilemon.messagequeue.authentication.authenticator.MultiQueueAuthenticator
import au.kilemon.messagequeue.authentication.exception.MultiQueueAuthenticationException
import au.kilemon.messagequeue.authentication.token.JwtTokenProvider
import org.slf4j.Logger
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.*
import javax.servlet.FilterChain
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

/**
 * A filter responsible for verifying provided Jwt tokens when sub-queues are being accessed.
 *
 * @author github.com/Kilemonn
 */
@Component
@Order(2)
class JwtAuthenticationFilter: OncePerRequestFilter(), HasLogger
{
    companion object
    {
        const val AUTHORIZATION_HEADER = "Authorization"
        const val BEARER_HEADER_VALUE = "Bearer "

        const val SUB_QUEUE = "Sub-Queue"

        /**
         * Gets the stored [SUB_QUEUE] from the [MDC].
         * This can be null if no valid token is provided.
         */
        fun getSubQueue(): String?
        {
            return MDC.get(SUB_QUEUE)
        }
    }

    override val LOG: Logger = this.initialiseLogger()

    @Autowired
    lateinit var authenticator: MultiQueueAuthenticator

    @Autowired
    lateinit var jwtTokenProvider: JwtTokenProvider

    /**
     * Perform appropriate validation of the [AUTHORIZATION_HEADER] if it is provided.
     * Depending on the set [MultiQueueAuthenticationType] will determine how this filter handles a request.
     * - [MultiQueueAuthenticationType.NONE] all requests will be allowed, whether they provide a valid token or not.
     * - [MultiQueueAuthenticationType.HYBRID] all requests will be allowed and the provided token [SUB_QUEUE] parameter
     * will be set if a token is provided. It's up to the lower level controllers to determine how they need to react
     * in accordance with the active [MultiQueueAuthenticator].
     * - [MultiQueueAuthenticationType.RESTRICTED] a token is required and if not valid the request will be rejected
     * here and a [MultiQueueAuthenticationException] will be thrown
     *
     * @throws MultiQueueAuthenticationException if [MultiQueueAuthenticationType] is set to
     * [MultiQueueAuthenticationType.RESTRICTED] and an invalid token OR NO token is provided
     */
    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain)
    {
        try
        {
            val subQueue = getSubQueueInTokenFromHeaders(request)
            setSubQueue(subQueue)

            if (authenticator.isInNoneMode())
            {
                LOG.trace("Allowed access as authentication is set to [{}].", MultiQueueAuthenticationType.NONE)
                filterChain.doFilter(request, response)
            }
            else if (authenticator.isInHybridMode())
            {
                LOG.trace("Allowing request through for lower layer to check as authentication is set to [{}].", MultiQueueAuthenticationType.NONE)
                filterChain.doFilter(request, response)
            }
            else if (authenticator.isInRestrictedMode())
            {
                if (tokenIsPresentAndQueueIsRestricted(subQueue, authenticator))
                {
                    LOG.trace("Accepted request for sub queue [{}].", subQueue.get())
                    filterChain.doFilter(request, response)
                }
                else
                {
                    LOG.error("Failed to manipulate sub queue [{}] with provided token as the authentication level is set to [{}].", subQueue.get(), authenticator.getAuthenticationType())
                    throw MultiQueueAuthenticationException()
                }
            }
        }
        finally
        {
            MDC.remove(SUB_QUEUE)
        }
    }

    /**
     * Check if the token is set and it is restricted sub-queue identifier.
     *
     * @return `true` if the provided [Optional.isPresent] and the call to [MultiQueueAuthenticator.isRestricted] is
     * `true` using the provided [Optional] value. Otherwise, returns `false`
     */
    fun tokenIsPresentAndQueueIsRestricted(subQueue: Optional<String>, multiQueueAuthenticator: MultiQueueAuthenticator): Boolean
    {
        return subQueue.isPresent && multiQueueAuthenticator.isRestricted(subQueue.get())
    }

    /**
     * Set the provided [Optional][String] into the [MDC] as [JwtAuthenticationFilter.SUB_QUEUE] if it is not [Optional.empty].
     *
     * @param subQueue an optional sub queue identifier, if it is not [Optional.empty] it will be placed into the [MDC]
     */
    fun setSubQueue(subQueue: Optional<String>)
    {
        if (subQueue.isPresent)
        {
            LOG.trace("Setting resolved sub queue from token into request context [{}].", subQueue.get())
            MDC.put(SUB_QUEUE, subQueue.get())
        }
    }

    /**
     * Get the value of the provided [request] for the [AUTHORIZATION_HEADER] header.
     *
     * @param request the request to retrieve the [AUTHORIZATION_HEADER] from
     * @return the [AUTHORIZATION_HEADER] header value wrapped as an [Optional], otherwise [Optional.empty]
     */
    fun getSubQueueInTokenFromHeaders(request: HttpServletRequest): Optional<String>
    {
        val authHeader = request.getHeader(AUTHORIZATION_HEADER)
        if (authHeader != null)
        {
            return if (authHeader.startsWith(BEARER_HEADER_VALUE))
            {
                val removeBearer = authHeader.substring(BEARER_HEADER_VALUE.length)
                isValidJwtToken(removeBearer)
            }
            else
            {
                LOG.error("Provided [{}] header did not have prefix [{}].", AUTHORIZATION_HEADER, BEARER_HEADER_VALUE)
                Optional.empty()
            }
        }
        return Optional.empty()
    }

    /**
     * Delegate to the [JwtTokenProvider] to determine if the provided token is valid.
     *
     * @param jwtToken the token to verify
     * @return the [String] for the sub-queue that this token is able to access, otherwise [Optional.empty] if there was
     * a problem with parsing the claim
     * @throws [MultiQueueAuthenticationException] if there is an issue verifying the token
     */
    @Throws(MultiQueueAuthenticationException::class)
    fun isValidJwtToken(jwtToken: String): Optional<String>
    {
        val result = jwtTokenProvider.verifyTokenForSubQueue(jwtToken)
        if (result.isPresent)
        {
            return Optional.ofNullable(result.get().getClaim(JwtTokenProvider.SUB_QUEUE_CLAIM).asString())
        }
        else
        {
            throw MultiQueueAuthenticationException()
        }
    }
}

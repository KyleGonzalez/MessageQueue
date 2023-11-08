package au.kilemon.messagequeue.authentication.authenticator.cache.redis

import au.kilemon.messagequeue.authentication.AuthenticationMatrix
import au.kilemon.messagequeue.authentication.authenticator.MultiQueueAuthenticator
import org.slf4j.Logger
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.RedisTemplate

/**
 * A [MultiQueueAuthenticator] implementation using Redis as the storage mechanism for the restricted
 * sub-queue identifiers.
 *
 * @author github.com/Kilemonn
 */
class RedisAuthenticator: MultiQueueAuthenticator()
{
    companion object
    {
        const val RESTRICTED_KEY = AuthenticationMatrix.TABLE_NAME + "_restricted"
    }

    override val LOG: Logger = this.initialiseLogger()

    @Autowired
    lateinit var redisTemplate: RedisTemplate<String, AuthenticationMatrix>

    /**
     * Overriding to completely remove all access to the [RESTRICTED_KEY].
     */
    override fun getReservedSubQueues(): Set<String>
    {
        return setOf(RESTRICTED_KEY)
    }

    private fun getMembersSet(): Set<AuthenticationMatrix>
    {
        return redisTemplate.opsForSet().members(RESTRICTED_KEY) ?: HashSet()
    }

    override fun isRestrictedInternal(subQueue: String): Boolean
    {
        return getMembersSet().contains(AuthenticationMatrix(subQueue)) ?: false
    }

    override fun addRestrictedEntryInternal(subQueue: String)
    {
        redisTemplate.opsForSet().add(RESTRICTED_KEY, AuthenticationMatrix(subQueue))
    }

    override fun removeRestrictionInternal(subQueue: String): Boolean
    {
        val result = redisTemplate.opsForSet().remove(RESTRICTED_KEY, AuthenticationMatrix(subQueue))
        return result != null && result > 0
    }

    override fun getRestrictedSubQueueIdentifiers(): Set<String>
    {
        return getMembersSet().map { authMatrix -> authMatrix.subQueue }.toList().toSet()
    }

    override fun clearRestrictedSubQueues(): Long
    {
        val members = getMembersSet()
        val existingMembersSize = members.size.toLong()
        redisTemplate.delete(RESTRICTED_KEY)
        return existingMembersSize
    }
}

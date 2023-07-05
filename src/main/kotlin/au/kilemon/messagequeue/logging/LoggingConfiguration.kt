package au.kilemon.messagequeue.logging

import org.slf4j.Logger
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.support.ReloadableResourceBundleMessageSource
import java.util.*

/**
 * A [Configuration] class to logging specific [Bean] initialisation such as for the [ReloadableResourceBundleMessageSource].
 *
 * @author github.com/Kilemonn
 */
@Configuration
class LoggingConfiguration : HasLogger
{
    override val LOG: Logger = this.initialiseLogger()

    companion object
    {
        /**
         * The `resource` path to the `messages.properties` file that holds external source messages.
         */
        const val SOURCE_MESSAGES: String = "classpath:messages"

        const val EXCEPTION_SOURCE_MESSAGES: String = "classpath:exception_messages"
    }

    /**
     * Initialise the [ReloadableResourceBundleMessageSource] [Bean] to read from [SOURCE_MESSAGES].
     */
    @Bean
    open fun getMessageSource(): ReloadableResourceBundleMessageSource
    {
        val messageSource = ReloadableResourceBundleMessageSource()
        messageSource.setBasenames(SOURCE_MESSAGES, EXCEPTION_SOURCE_MESSAGES)
        messageSource.setDefaultLocale(Locale.getDefault())
        messageSource.setFallbackToSystemLocale(false)
        LOG.debug(messageSource.getMessage(Messages.INITIALISING_MESSAGE_SOURCE, arrayOf(SOURCE_MESSAGES), Locale.getDefault()))
        return messageSource
    }
}

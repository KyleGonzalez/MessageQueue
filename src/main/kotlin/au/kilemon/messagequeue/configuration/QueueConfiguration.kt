package au.kilemon.messagequeue.configuration

import au.kilemon.messagequeue.MessageQueueApplication
import au.kilemon.messagequeue.logging.HasLogger
import au.kilemon.messagequeue.logging.Messages
import au.kilemon.messagequeue.queue.MultiQueue
import au.kilemon.messagequeue.queue.cache.redis.RedisMultiQueue
import au.kilemon.messagequeue.queue.inmemory.InMemoryMultiQueue
import au.kilemon.messagequeue.queue.sql.SqlMultiQueue
import au.kilemon.messagequeue.settings.MessageQueueSettings
import au.kilemon.messagequeue.settings.MultiQueueType
import lombok.Generated
import org.slf4j.Logger
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.support.ReloadableResourceBundleMessageSource
import java.util.*

/**
 * A [Configuration] class holding all required [Bean]s for the [MessageQueueApplication] to run.
 * Mainly [Bean]s related to the [MultiQueue].
 *
 * @author github.com/KyleGonzalez
 */
@Configuration
class QueueConfiguration : HasLogger
{
    override val LOG: Logger = initialiseLogger()

    @Autowired
    @get:Generated
    @set:Generated
    lateinit var messageQueueSettings: MessageQueueSettings

    @Autowired
    @get:Generated
    @set:Generated
    lateinit var messageSource: ReloadableResourceBundleMessageSource

    /**
     * Initialise the [MultiQueue] [Bean] based on the [MessageQueueSettings.multiQueueType].
     */
    @Bean
    open fun getMultiQueue(): MultiQueue
    {
        LOG.info(messageSource.getMessage(Messages.VERSION_START_UP, null, Locale.getDefault()), MessageQueueApplication.VERSION)
        val queue: MultiQueue = when (messageQueueSettings.multiQueueType)
        {
            MultiQueueType.IN_MEMORY.toString() ->
            {
                InMemoryMultiQueue()
            }
            MultiQueueType.REDIS.toString() ->
            {
                RedisMultiQueue()
            }
            MultiQueueType.SQL.toString() ->
            {
                SqlMultiQueue()
            }
            else ->
            {
                InMemoryMultiQueue()
            }
        }
        LOG.info("Initialising [{}] queue as the [{}] is set to [{}].", queue::class.java.name, MessageQueueSettings.MULTI_QUEUE_TYPE, messageQueueSettings.multiQueueType)

        return queue
    }
}

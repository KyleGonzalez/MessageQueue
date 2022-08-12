package au.kilemon.messagequeue.queue

import au.kilemon.messagequeue.message.QueueMessage
import au.kilemon.messagequeue.queue.type.QueueType
import au.kilemon.messagequeue.queue.type.QueueTypeProvider
import lombok.extern.slf4j.Slf4j
import org.springframework.stereotype.Component
import java.util.Queue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * The MapQueue which implements the [MultiQueue]. It holds a [ConcurrentHashMap] with [Queue] entries.
 * Using the provided [QueueTypeProvider], specific entries in the queue can be manipulated and changed as needed.
 *
 * @author github.com/KyleGonzalez
 */
@Slf4j
@Component
open class MapQueue: MultiQueue<QueueMessage>
{
    private val messageQueue: ConcurrentHashMap<String, Queue<QueueMessage>> = ConcurrentHashMap()

    override var size: Int = 0

    /**
     * Retrieves or creates a new [Queue] of type [QueueMessage] for the provided [QueueTypeProvider].
     * If the underlying [Queue] does not exist for the provided [QueueTypeProvider] then a new [Queue] will
     * be created and stored in the [ConcurrentHashMap] under the provided [QueueTypeProvider].
     *
     * @param queueTypeProvider the provider used to get the correct underlying [Queue]
     * @return the [Queue] matching the provided [QueueTypeProvider]
     */
    private fun getQueueForType(queueTypeProvider: QueueTypeProvider): Queue<QueueMessage>
    {
        var queueForType: Queue<QueueMessage>? = messageQueue[queueTypeProvider.getIdentifier()]
        if (queueForType == null)
        {
            queueForType = ConcurrentLinkedQueue()
            messageQueue[queueTypeProvider.getIdentifier()] = queueForType
        }
        return queueForType
    }

    override fun add(element: QueueMessage): Boolean
    {
        val queueForType: Queue<QueueMessage> = getQueueForType(element.type)
        val wasAdded = queueForType.add(element)
        if (wasAdded)
        {
            size++
        }
        return wasAdded
    }

    override fun addAll(elements: Collection<QueueMessage>): Boolean
    {
        var wasAdded = false
        for (element: QueueMessage in elements)
        {
            wasAdded = wasAdded || add(element)
        }
        return wasAdded
    }

    override fun clear()
    {
        messageQueue.clear()
        size = 0
    }

    override fun clearForType(queueTypeProvider: QueueTypeProvider)
    {
        val queueForType: Queue<QueueMessage>? = messageQueue[queueTypeProvider.getIdentifier()]
        if (queueForType != null)
        {
            size -= queueForType.size
            queueForType.clear()
        }
    }

    override fun retainAll(elements: Collection<QueueMessage>): Boolean
    {
        var anyWasRemoved = false
        for (key: String in messageQueue.keys())
        {
            val queueForKey: Queue<QueueMessage>? = messageQueue[key]
            if (queueForKey != null)
            {
                for(entry: QueueMessage in queueForKey)
                {
                    if (!elements.contains(entry))
                    {
                        anyWasRemoved = anyWasRemoved || queueForKey.remove(entry)
                    }
                }
            }
        }
        return anyWasRemoved
    }

    override fun removeAll(elements: Collection<QueueMessage>): Boolean
    {
        var wasRemoved = false
        for (element: QueueMessage in elements)
        {
            wasRemoved = wasRemoved || remove(element)
        }
        return wasRemoved
    }

    override fun remove(element: QueueMessage): Boolean
    {
        val queueForType: Queue<QueueMessage> = getQueueForType(element.type)
        val wasRemoved = queueForType.remove(element)
        if (wasRemoved)
        {
            size--
        }
        return wasRemoved
    }

    override fun isEmpty(): Boolean
    {
        return messageQueue.isEmpty() || messageQueue.keys.stream().allMatch{ key -> this.isEmptyForType(QueueType(key)) }
    }

    override fun isEmptyForType(queueTypeProvider: QueueTypeProvider): Boolean
    {
        val queueForType: Queue<QueueMessage> = getQueueForType(queueTypeProvider)
        return queueForType.isEmpty()
    }

    override fun pollForType(queueTypeProvider: QueueTypeProvider): QueueMessage?
    {
        val queueForType: Queue<QueueMessage> = getQueueForType(queueTypeProvider)
        val head = queueForType.poll()
        if (head != null)
        {
            size--
        }
        return head
    }

    override fun peekForType(queueTypeProvider: QueueTypeProvider): QueueMessage?
    {
        val queueForType: Queue<QueueMessage> = getQueueForType(queueTypeProvider)
        return queueForType.peek()
    }

    override fun containsAll(elements: Collection<QueueMessage>): Boolean
    {
        return elements.stream().allMatch{ element -> this.contains(element) }
    }

    override fun contains(element: QueueMessage): Boolean
    {
        val queueForType: Queue<QueueMessage> = getQueueForType(element.type)
        return queueForType.contains(element)
    }
}

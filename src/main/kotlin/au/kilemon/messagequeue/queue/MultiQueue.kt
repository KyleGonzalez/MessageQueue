package au.kilemon.messagequeue.queue

import au.kilemon.messagequeue.queue.exception.DuplicateMessageException
import au.kilemon.messagequeue.logging.HasLogger
import au.kilemon.messagequeue.message.QueueMessage
import au.kilemon.messagequeue.queue.exception.MessageUpdateException
import org.slf4j.Logger
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.jvm.Throws

/**
 * A [MultiQueue] interface, which extends [Queue].
 * It contains various extra methods for interfacing with the [MultiQueue] using the [String]
 * to manipulate the appropriate underlying [Queue]s.
 *
 * @author github.com/KyleGonzalez
 */
interface MultiQueue: Queue<QueueMessage>, HasLogger
{
    companion object
    {
        private const val NOT_IMPLEMENTED_METHOD: String = "Method is not implemented."
    }

    override val LOG: Logger

    /**
     * Get the underlying size of the [MultiQueue].
     * This is done by summing the length of each [getQueueForType] for each key in [keys].
     *
     * This is to allow the underlying storage to be the source of truth instead of any temporary counters, since the underlying storage could
     * change at any timeout without direct interaction from the [MultiQueue].
     */
    override val size: Int
        get() {
            var internalSize = 0
            keys(false).forEach { key ->
                internalSize += getQueueForType(key).size
            }
            return internalSize
        }

    /**
     * New methods for the [MultiQueue] that are required by implementing classes.
     */

    /**
     * Used to persist the updated [QueueMessage] to the storage mechanism.
     * It must match an existing message this should not create a new [QueueMessage].
     *
     * @throws MessageUpdateException if the message to update does not exist or there is an error
     * in the underlying storage mechanism when performing the update.
     *
     * @param message the updated [QueueMessage] to persist
     * @return `true` if the [QueueMessage] was updated successfully, otherwise `false`
     */
    @Throws(MessageUpdateException::class)
    fun persistMessage(message: QueueMessage)

    /**
     * Retrieves or creates a new [Queue] of type [QueueMessage] for the provided [String].
     * If the underlying [Queue] does not exist for the provided [String] then a new [Queue] will
     * be created and stored in the [ConcurrentHashMap] under the provided [String].
     *
     * @param queueType the identifier of the sub-queue [Queue]
     * @return the [Queue] matching the provided [String]
     */
    fun getQueueForType(queueType: String): Queue<QueueMessage>

    /**
     * Retrieves only assigned messages in the sub-queue for the provided [queueType].
     *
     * @param queueType the identifier of the sub-queue [Queue]
     * @return a limited version of the [Queue] containing only assigned messages
     */
    fun getAssignedMessagesForType(queueType: String): Queue<QueueMessage>

    /**
     * Retrieves only unassigned messages in the sub-queue for the provided [queueType].
     *
     * @param queueType the identifier of the sub-queue [Queue]
     * @return a limited version of the [Queue] containing only unassigned messages
     */
    fun getUnassignedMessagesForType(queueType:String): Queue<QueueMessage>

    /**
     * Get a map of assignee identifiers and the sub-queue identifier that they own messages in.
     * If the [queueType] is provided this will iterate over all sub-queues and call [getOwnersAndKeysMapForType] for each of them.
     * Otherwise, it will only call [getOwnersAndKeysMapForType] on the provided [queueType] if it is not null.
     *
     * @param queueType the queue type retrieve the [Map] from
     * @return the [Map] of assignee identifiers mapped to a list of the sub-queue identifiers that they own any messages in
     */
    fun getOwnersAndKeysMap(queueType: String?): Map<String, HashSet<String>>
    {
        val responseMap = HashMap<String, HashSet<String>>()
        if (queueType != null)
        {
            LOG.debug("Getting owners map for sub-queue with identifier [{}].", queueType)
            getOwnersAndKeysMapForType(queueType, responseMap)
        }
        else
        {
            LOG.debug("Getting owners map for all sub-queues.")
            val keys = keys(false)
            keys.forEach { key ->
                getOwnersAndKeysMapForType(key, responseMap)
            }
        }
        return responseMap
    }

    /**
     * Add an entry to the provided [Map] if any of the messages in the sub-queue are assigned.
     * The [QueueMessage.type] is appended to the [Set] under it's [QueueMessage.assignedTo] identifier.
     *
     * @param queueType the sub-queue to iterate and update the map from
     * @param responseMap the map to update
     */
    fun getOwnersAndKeysMapForType(queueType: String, responseMap: HashMap<String, HashSet<String>>)
    {
        val queueForType: Queue<QueueMessage> = getAssignedMessagesForType(queueType)
        queueForType.forEach { message ->
            val type = message.type
            val assigned = message.assignedTo
            if (assigned != null)
            {
                LOG.trace("Appending sub-queue identifier [{}] to map for assignee ID [{}].", type, assigned)
                if (!responseMap.contains(assigned))
                {
                    val set = hashSetOf(type)
                    responseMap[assigned] = set
                }
                else
                {
                    // Set should not be null since we initialise and set it if the key is contained
                    responseMap[assigned]!!.add(type)
                }
            }
        }
    }

    /**
     * Get a [QueueMessage] directly from the [MultiQueue] that matches the provided [uuid].
     *
     * @param uuid of the [QueueMessage] to find within the [MultiQueue]
     * @return the matching [QueueMessage] or [Optional.EMPTY]
     */
    fun getMessageByUUID(uuid: String): Optional<QueueMessage>

    /**
     * Clears the underlying [Queue] for the provided [String]. By calling [Queue.clear].
     *
     * This method should update the [size] property as part of the clearing of the sub-queue.
     *
     * @param queueType the [String] of the [Queue] to clear
     * @return the number of entries removed
     */
    fun clearForType(queueType: String): Int

    /**
     * Indicates whether the underlying [Queue] for the provided [String] is empty. By calling [Queue.isEmpty].
     *
     * @param queueType the [String] of the [Queue] to check whether it is empty
     * @return `true` if the [Queue] for the [String] is empty, otherwise `false`
     */
    fun isEmptyForType(queueType: String): Boolean

    /**
     * Calls [Queue.poll] on the underlying [Queue] for the provided [String].
     * This will retrieve **AND** remove the head element of the [Queue].
     *
     * @param queueType [String] of the [Queue] to poll
     * @return the head element or `null`
     */
    fun pollForType(queueType: String): Optional<QueueMessage>
    {
        val head = performPoll(queueType)
        if (head.isPresent)
        {
            performRemove(head.get())
            LOG.debug("Found and removed head element with UUID [{}] from queue with type [{}].", head.get().uuid, queueType)
        }
        else
        {
            LOG.debug("No head element found when polling queue with type [{}].", queueType)
        }
        return head
    }

    /**
     * The internal poll method to be called.
     * This is not to  be called directly.
     *
     * This method should return the first element in the queue for the provided [queueType].
     * *The caller will remove this element*.
     *
     * @param queueType the sub-queue to poll
     * @return the first message wrapped as an [Optional] otherwise [Optional.empty]
     */
    fun performPoll(queueType: String): Optional<QueueMessage>

    /**
     * Calls [Queue.peek] on the underlying [Queue] for the provided [String].
     * This will retrieve the head element of the [Queue] without removing it.
     *
     * @param queueType [String] of the [Queue] to peek
     * @return the head element or `null`
     */
    fun peekForType(queueType: String): Optional<QueueMessage>
    {
        val queueForType: Queue<QueueMessage> = getQueueForType(queueType)
        val peeked = Optional.ofNullable(queueForType.peek())
        if (peeked.isPresent)
        {
            LOG.debug("Found head element with UUID [{}] from queue with type [{}].", peeked.get().uuid, queueType)
        }
        else
        {
            LOG.debug("No head element found when peeking queue with type [{}].", queueType)
        }
        return peeked
    }

    /**
     * Retrieves the underlying key list as a set.
     *
     * @param includeEmpty *true* to include any empty queues which one had elements in them, otherwise *false* to only include keys from queues which have elements.
     * @return a [Set] of the available `QueueTypes` that have entries in the [MultiQueue].
     */
    fun keys(includeEmpty: Boolean = true): Set<String>

    /**
     * Returns the `queueType` that the [QueueMessage] with the provided [UUID] exists in.
     *
     * @param uuid the [UUID] (as a [String]) to look up
     * @return the `queueType` [String] if a [QueueMessage] exists with the provided [UUID] otherwise [Optional.empty]
     */
    fun containsUUID(uuid: String): Optional<String>

    /**
     * Any overridden methods to update the signature for all implementing [MultiQueue] classes.
     */
    /**
     * Override [add] method to declare [Throws] [DuplicateMessageException] annotation.
     *
     * @throws [DuplicateMessageException] if a message already exists with the same [QueueMessage.uuid] in `any` other queue.
     */
    @Throws(DuplicateMessageException::class)
    override fun add(element: QueueMessage): Boolean
    {
        val elementIsMappedToType = containsUUID(element.uuid)
        if ( !elementIsMappedToType.isPresent)
        {
            val wasAdded = performAdd(element)
            return if (wasAdded)
            {
                LOG.debug("Added new message with uuid [{}] to queue with type [{}].", element.uuid, element.type)
                true
            }
            else
            {
                LOG.error("Failed to add message with uuid [{}] to queue with type [{}].", element.uuid, element.type)
                false
            }
        }
        else
        {
            val existingQueueType = elementIsMappedToType.get()
            LOG.warn("Did not add new message with uuid [{}] to queue with type [{}] as it already exists in queue with type [{}].", element.uuid, element.type, existingQueueType)
            throw DuplicateMessageException(element.uuid.toString(), existingQueueType)
        }
    }

    /**
     * The internal add method to be called.
     * This is not to  be called directly.
     *
     * @param element the element to add
     * @return `true` if the element was added successfully, otherwise `false`.
     */
    fun performAdd(element: QueueMessage): Boolean

    override fun remove(element: QueueMessage): Boolean
    {
        val wasRemoved = performRemove(element)
        if (wasRemoved)
        {
            LOG.debug("Removed element with UUID [{}] from queue with type [{}].", element.uuid, element.type)
        }
        else
        {
            LOG.error("Failed to remove element with UUID [{}] from queue with type [{}].", element.uuid, element.type)
        }
        return wasRemoved
    }

    /**
     * The internal remove method to be called.
     * This is not to be called directly.
     *
     * @param element the element to remove
     * @return `true` if the element was removed successfully, otherwise `false`.
     */
    fun performRemove(element: QueueMessage): Boolean

    override fun contains(element: QueueMessage?): Boolean
    {
        if (element == null)
        {
            return false
        }
        val queueForType: Queue<QueueMessage> = getQueueForType(element.type)
        return queueForType.contains(element)
    }

    override fun containsAll(elements: Collection<QueueMessage>): Boolean
    {
        return elements.stream().allMatch{ element -> this.contains(element) }
    }

    override fun addAll(elements: Collection<QueueMessage>): Boolean
    {
        var allAdded = true
        for (element: QueueMessage in elements)
        {
            allAdded = try {
                val wasAdded = add(element)
                allAdded && wasAdded
            }
            catch (ex: DuplicateMessageException)
            {
                false
            }
        }
        return allAdded
    }

    override fun retainAll(elements: Collection<QueueMessage>): Boolean
    {
        var anyWasRemoved = false
        for (key: String in keys(false))
        {
            // The queue should never be new or created since we passed `false` into `keys()` above.
            val queueForKey: Queue<QueueMessage> = getQueueForType(key)
            for(entry: QueueMessage in queueForKey)
            {
                if ( !elements.contains(entry))
                {
                    LOG.debug("Message with uuid [{}] does not exist in retain list, attempting to remove.", entry.uuid)
                    val wasRemoved = remove(entry)
                    anyWasRemoved = wasRemoved || anyWasRemoved
                    if (wasRemoved)
                    {
                        LOG.debug("Removed message with uuid [{}] as it does not exist in retain list.", entry.uuid)
                    }
                    else
                    {
                        LOG.error("Failed to remove message with uuid [{}].", entry.uuid)
                    }
                }
                else
                {
                    LOG.debug("Retaining element with uuid [{}] as it exists in the retain list.", entry.uuid)
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
            wasRemoved = remove(element) || wasRemoved
        }
        return wasRemoved
    }

    /**
     * @return `true` any of the [keys] returns `false` for [isEmptyForType], otherwise `false`.
     */
    override fun isEmpty(): Boolean
    {
        val anyHasElements = keys(false).stream().anyMatch { key -> !isEmptyForType(key) }
        return !anyHasElements
    }

    override fun clear()
    {
        val keys = keys()
        var removedEntryCount = 0
        for (key in keys)
        {
            val amountRemovedForQueue = clearForType(key)
            removedEntryCount += amountRemovedForQueue
        }
        LOG.debug("Cleared multi-queue, removed [{}] message entries over [{}] queue types.", removedEntryCount, keys)
    }

    /**
     * Any unsupported methods from the [Queue] interface that are not implemented.
     */
    /**
     * Not Implemented.
     */
    override fun offer(e: QueueMessage): Boolean
    {
        throw UnsupportedOperationException(NOT_IMPLEMENTED_METHOD)
    }

    /**
     * Not Implemented.
     */
    override fun poll(): QueueMessage
    {
        throw UnsupportedOperationException(NOT_IMPLEMENTED_METHOD)
    }

    /**
     * Not Implemented.
     */
    override fun element(): QueueMessage
    {
        throw UnsupportedOperationException(NOT_IMPLEMENTED_METHOD)
    }

    /**
     * Not Implemented.
     */
    override fun peek(): QueueMessage
    {
        throw UnsupportedOperationException(NOT_IMPLEMENTED_METHOD)
    }

    /**
     * Not Implemented.
     */
    override fun remove(): QueueMessage
    {
        throw UnsupportedOperationException(NOT_IMPLEMENTED_METHOD)
    }

    /**
     * Not Implemented.
     */
    override fun iterator(): MutableIterator<QueueMessage>
    {
        throw UnsupportedOperationException(NOT_IMPLEMENTED_METHOD)
    }
}

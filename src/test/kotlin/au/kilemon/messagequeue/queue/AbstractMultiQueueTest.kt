package au.kilemon.messagequeue.queue

import au.kilemon.messagequeue.Payload
import au.kilemon.messagequeue.message.QueueMessage
import au.kilemon.messagequeue.queue.type.QueueType
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.io.Serializable
import java.util.stream.Stream

/**
 * An abstract test class for the [MultiQueue] class.
 * This class can be extended, and the [MultiQueue] member overridden to easily ensure that the different
 * [MultiQueue] implementations all operate as expected in the same test cases.
 *
 * @author github.com/KyleGonzalez
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest
abstract class AbstractMultiQueueTest<T: MultiQueue>
{
    @Autowired
    protected lateinit var multiQueue: T

    /**
     * Ensure the [MultiQueue] is cleared before each test.
     */
    @BeforeEach
    fun setup()
    {
        multiQueue.clear()
        duringSetup()
    }

    /**
     * Called in the [BeforeEach] after the parent has done its preparation.
     */
    abstract fun duringSetup()

    /**
     * Ensure that when a new entry is added, that the [MultiQueue] is no longer empty and reports the correct size.
     *
     * @param data the incoming [Serializable] data to store in the [MultiQueue] to test that we can cater for multiple types
     */
    @ParameterizedTest
    @MethodSource("parameters_testAdd")
    fun testAdd(data: Serializable)
    {
        Assertions.assertTrue(multiQueue.isEmpty())
        val message = QueueMessage(data, QueueType("type"))
        Assertions.assertTrue(multiQueue.add(message))
        Assertions.assertFalse(multiQueue.isEmpty())
        Assertions.assertEquals(1, multiQueue.size)

        val retrievedMessage = multiQueue.pollForType(message.type)
        Assertions.assertTrue(multiQueue.isEmpty())
        Assertions.assertEquals(0, multiQueue.size)

        Assertions.assertNotNull(retrievedMessage)
        Assertions.assertEquals(data, retrievedMessage!!.data)
    }

    /**
     * An argument provider for the [AbstractMultiQueueTest.testAdd] method.
     */
    private fun parameters_testAdd(): Stream<Arguments>
    {
        return Stream.of(
            Arguments.of(1234),
            Arguments.of("a string"),
            Arguments.of(listOf("element1", "element2", "element3")),
            Arguments.of(true)
        )
    }

    /**
     * Ensure that when an entry is added and the same entry is removed that the [MultiQueue] is empty.
     */
    @Test
    fun testRemove()
    {
        Assertions.assertTrue(multiQueue.isEmpty())

        val message = QueueMessage("A test value", QueueType("type"))

        Assertions.assertTrue(multiQueue.add(message))
        Assertions.assertFalse(multiQueue.isEmpty())
        Assertions.assertEquals(1, multiQueue.size)

        Assertions.assertTrue(multiQueue.remove(message))
        Assertions.assertTrue(multiQueue.isEmpty())
        Assertions.assertEquals(0, multiQueue.size)
    }

    /**
     * Ensure that if an entry that does not exist is attempting to be removed, then `false` is returned from the [MultiQueue.remove] method.
     */
    @Test
    fun testRemove_whenEntryDoesntExist()
    {
        Assertions.assertTrue(multiQueue.isEmpty())
        val messageThatDoesntExist = QueueMessage(Payload("some Other data"), QueueType("type"))

        Assertions.assertFalse(multiQueue.remove(messageThatDoesntExist))
        Assertions.assertTrue(multiQueue.isEmpty())
    }

    /**
     * Ensure that `false` is returned from [MultiQueue.contains] when the entry does not exist.
     */
    @Test
    fun testContains_whenEntryDoesntExist()
    {
        Assertions.assertTrue(multiQueue.isEmpty())
        val type = QueueType("type")
        val otherData = Payload("some Other data")
        val messageThatDoesntExist = QueueMessage(otherData, type)
        Assertions.assertFalse(multiQueue.contains(messageThatDoesntExist))
    }

    /**
     * Ensure that `true` is returned from [MultiQueue.contains] when the entry does exist.
     */
    @Test
    fun testContains_whenEntryExists()
    {
        Assertions.assertTrue(multiQueue.isEmpty())
        val message = QueueMessage(0x52347, QueueType("type"))

        Assertions.assertTrue(multiQueue.add(message))
        Assertions.assertFalse(multiQueue.isEmpty())
        Assertions.assertEquals(1, multiQueue.size)
        Assertions.assertTrue(multiQueue.contains(message))
    }

    /**
     * Ensure that `true` is returned from [MultiQueue.contains] when the entry does exist.
     * And when the `@EqualsAndHashCode.Exclude` properties are changed. This is the make sure that even if
     * we change some metadata properties, that we can still find the correct entry, since the metadata fields
     * should be ignored when the [QueueMessage.equals] method is called.
     */
    @Test
    fun testContains_whenMetadataPropertiesAreSet()
    {
        Assertions.assertTrue(multiQueue.isEmpty())
        val message = QueueMessage(0x5234, QueueType("type"))

        Assertions.assertTrue(multiQueue.add(message))
        Assertions.assertFalse(multiQueue.isEmpty())

        Assertions.assertTrue(multiQueue.contains(message))
        message.isConsumed = true
        message.consumedBy = "Instance_11242"
        Assertions.assertTrue(multiQueue.contains(message))
    }

    /**
     * Ensure that all elements are added, and contained and removed via the provided [Collection].
     */
    @Test
    fun testAddAll_containsAll_removeAll()
    {
        Assertions.assertTrue(multiQueue.isEmpty())
        val list = listOf(QueueMessage(81273648, QueueType("type")), QueueMessage("test test test", QueueType("type")))
        Assertions.assertTrue(multiQueue.addAll(list))
        Assertions.assertFalse(multiQueue.isEmpty())
        Assertions.assertEquals(2, multiQueue.size)

        Assertions.assertTrue(multiQueue.containsAll(list))
        Assertions.assertTrue(multiQueue.contains(list[0]))
        Assertions.assertTrue(multiQueue.contains(list[1]))

        Assertions.assertTrue(multiQueue.removeAll(list))
        Assertions.assertTrue(multiQueue.isEmpty())
        Assertions.assertEquals(0, multiQueue.size)
    }

    /**
     * Ensure that `null` is returned when there are no elements in the [MultiQueue] for the [QueueType].
     * Otherwise, if it does exist make sure that the correct entry is returned and that it is removed.
     */
    @Test
    fun testPollForType()
    {
        Assertions.assertTrue(multiQueue.isEmpty())
        val message = QueueMessage(Payload("poll for type"), QueueType("poll-type"))

        Assertions.assertNull(multiQueue.pollForType(message.type))
        Assertions.assertTrue(multiQueue.add(message))
        Assertions.assertFalse(multiQueue.isEmpty())
        Assertions.assertEquals(message, multiQueue.pollForType(message.type))
        Assertions.assertTrue(multiQueue.isEmpty())
    }

    /**
     * Ensure that `null` is returned when there are no elements in the [MultiQueue] for the [QueueType].
     * Otherwise, if it does exist make sure that the correct entry is returned.
     */
    @Test
    fun testPeekForType()
    {
        Assertions.assertTrue(multiQueue.isEmpty())
        val message = QueueMessage(Payload("peek for type"), QueueType("peek-type"))

        Assertions.assertNull(multiQueue.peekForType(message.type))
        Assertions.assertTrue(multiQueue.add(message))
        Assertions.assertFalse(multiQueue.isEmpty())
        Assertions.assertEquals(message, multiQueue.peekForType(message.type))
        Assertions.assertFalse(multiQueue.isEmpty())

    }

    /**
     * Ensure that [MultiQueue.isEmptyForType] operates as expected when entries exist and don't exist for a specific type.
     */
    @Test
    fun testIsEmptyForType()
    {
        Assertions.assertTrue(multiQueue.isEmpty())

        val type = QueueType("type")
        val data = "test data"
        val message = QueueMessage(data, type)

        Assertions.assertTrue(multiQueue.add(message))
        Assertions.assertFalse(multiQueue.isEmpty())
        Assertions.assertFalse(multiQueue.isEmptyForType(type))
        Assertions.assertTrue(multiQueue.isEmptyForType(QueueType("another-type")))
    }

    /**
     * Ensure that only the [QueueType] entries are removed when [MultiQueue.clearForType] is called.
     */
    @Test
    fun testClearForType()
    {
        Assertions.assertTrue(multiQueue.isEmpty())
        val type = QueueType("clear-for-type")
        val list = listOf(QueueMessage(81273648, type), QueueMessage("test test test", type))
        Assertions.assertTrue(multiQueue.addAll(list))

        val singleEntryType = QueueType("single-entry-type")
        val message = QueueMessage("test message", singleEntryType)
        Assertions.assertTrue(multiQueue.add(message))

        Assertions.assertEquals(3, multiQueue.size)
        multiQueue.clearForType(type)
        Assertions.assertEquals(1, multiQueue.size)
        multiQueue.clearForType(singleEntryType)
        Assertions.assertTrue(multiQueue.isEmpty())
    }

    /**
     * Ensure that no change is made when the [QueueType] has no entries.
     */
    @Test
    fun testClearForType_DoesNotExist()
    {
        Assertions.assertTrue(multiQueue.isEmpty())
        val type = QueueType("clear-for-type-does-not-exist")
        multiQueue.clearForType(type)
        Assertions.assertTrue(multiQueue.isEmpty())
    }

    /**
     * Ensure that the correct entries are retained and that the correct `Boolean` value is returned.
     */
    @Test
    fun testRetainAll()
    {
        Assertions.assertTrue(multiQueue.isEmpty())
        val type = QueueType("type1")
        val type2 = QueueType("type2")
        val data = Payload("some payload")
        val data2 = Payload("some more data")
        val list = listOf(QueueMessage(data, type), QueueMessage(data, type2), QueueMessage(data2, type), QueueMessage(data2, type2))

        Assertions.assertTrue(multiQueue.addAll(list))
        Assertions.assertEquals(4, multiQueue.size)

        val toRetain = ArrayList<QueueMessage>()
        toRetain.addAll(list.subList(0, 2))
        Assertions.assertEquals(2, toRetain.size)
        // No elements of this type to cover all branches of code
        val type3 = QueueType("type3")
        val type3Message = QueueMessage(Payload("type3 data"), type3)
        toRetain.add(type3Message)
        Assertions.assertEquals(3, toRetain.size)

        Assertions.assertTrue(multiQueue.retainAll(toRetain))
        Assertions.assertEquals(2, multiQueue.size)
        Assertions.assertTrue(multiQueue.contains(list[0]))
        Assertions.assertTrue(multiQueue.contains(list[1]))

        Assertions.assertFalse(multiQueue.contains(list[2]))
        Assertions.assertFalse(multiQueue.contains(list[3]))
    }

    /**
     * Ensure that all applicable methods throw an [UnsupportedOperationException].
     */
    @Test
    fun testUnsupportedMethods()
    {
        assertAll( "Unsupported methods",
            {
                assertThrows(UnsupportedOperationException::class.java)
                {
                    multiQueue.peek()
                }
            },
            {
                assertThrows(UnsupportedOperationException::class.java)
                {
                    multiQueue.offer(QueueMessage(Payload("test data"), QueueType("test type")))
                }
            },
            {
                assertThrows(UnsupportedOperationException::class.java)
                {
                    multiQueue.element()
                }
            },
            {
                assertThrows(UnsupportedOperationException::class.java)
                {
                    multiQueue.poll()
                }
            },
            {
                assertThrows(UnsupportedOperationException::class.java)
                {
                    multiQueue.remove()
                }
            },
            {
                assertThrows(UnsupportedOperationException::class.java)
                {
                    multiQueue.iterator()
                }
            }
        )
    }
}

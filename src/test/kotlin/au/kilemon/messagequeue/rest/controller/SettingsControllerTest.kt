package au.kilemon.messagequeue.rest.controller

import au.kilemon.messagequeue.authentication.MultiQueueAuthenticationType
import au.kilemon.messagequeue.authentication.authenticator.MultiQueueAuthenticator
import au.kilemon.messagequeue.configuration.QueueConfiguration
import au.kilemon.messagequeue.logging.LoggingConfiguration
import au.kilemon.messagequeue.settings.MessageQueueSettings
import au.kilemon.messagequeue.settings.MultiQueueType
import com.google.gson.Gson
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers

/**
 * A test class for the [SettingsController] class. Mainly testing the endpoints in the `RestController`.
 *
 * @author github.com/Kilemonn
 */
@ExtendWith(SpringExtension::class)
@WebMvcTest(controllers = [SettingsController::class], properties = ["${MessageQueueSettings.MULTI_QUEUE_TYPE}=IN_MEMORY"])
@Import(*[QueueConfiguration::class, LoggingConfiguration::class])
class SettingsControllerTest
{
    /**
     * The test configuration to be used by the [SettingsControllerTest] class.
     *
     * @author github.com/Kilemonn
     */
    @TestConfiguration
    internal class TestConfig
    {
        /**
         * The bean initialise here will have all its properties overridden by environment variables.
         * Don't set the here, set them in the [WebMvcTest.properties].
         */
        @Bean
        fun getSettings(): MessageQueueSettings
        {
            return MessageQueueSettings()
        }
    }

    @Autowired
    private lateinit var mockMvc: MockMvc

    @SpyBean
    private lateinit var multiQueueAuthenticator: MultiQueueAuthenticator

    private val gson: Gson = Gson()

    /**
     * A helper method to call [SettingsController.getSettings] and verify the response default values.
     */
    private fun testGetSettings_defaultValues(authenticationType: MultiQueueAuthenticationType)
    {
        Assertions.assertEquals(authenticationType, multiQueueAuthenticator.getAuthenticationType())

        val mvcResult: MvcResult = mockMvc.perform(MockMvcRequestBuilders.get(SettingsController.SETTINGS_PATH)
            .contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andReturn()
        val settings = gson.fromJson(mvcResult.response.contentAsString, MessageQueueSettings::class.java)

        Assertions.assertEquals(MultiQueueType.IN_MEMORY.toString(), settings.multiQueueType)
        Assertions.assertEquals(MultiQueueAuthenticationType.NONE.toString(), settings.multiQueueAuthentication)

        Assertions.assertTrue(settings.redisPrefix.isEmpty())
        Assertions.assertEquals(MessageQueueSettings.REDIS_ENDPOINT_DEFAULT, settings.redisEndpoint)
        Assertions.assertEquals("false", settings.redisUseSentinels)
        Assertions.assertEquals(MessageQueueSettings.REDIS_MASTER_NAME_DEFAULT, settings.redisMasterName)

        Assertions.assertTrue(settings.sqlEndpoint.isEmpty())
        Assertions.assertTrue(settings.sqlUsername.isEmpty())

        Assertions.assertTrue(settings.mongoHost.isEmpty())
        Assertions.assertTrue(settings.mongoPort.isEmpty())
        Assertions.assertTrue(settings.mongoDatabase.isEmpty())
        Assertions.assertTrue(settings.mongoUsername.isEmpty())
        Assertions.assertTrue(settings.mongoUri.isEmpty())
    }

    /**
     * Ensure calls to [SettingsController.getSettings] is still available even then the [MultiQueueAuthenticationType]
     * is set to [MultiQueueAuthenticationType.NONE].
     */
    @Test
    fun testGetSettings_noneMode()
    {
        Mockito.doReturn(MultiQueueAuthenticationType.NONE).`when`(multiQueueAuthenticator).getAuthenticationType()
        testGetSettings_defaultValues(MultiQueueAuthenticationType.NONE)
    }

    /**
     * Ensure calls to [SettingsController.getSettings] is still available even then the [MultiQueueAuthenticationType]
     * is set to [MultiQueueAuthenticationType.HYBRID].
     */
    @Test
    fun testGetSettings_hybridMode()
    {
        Mockito.doReturn(MultiQueueAuthenticationType.HYBRID).`when`(multiQueueAuthenticator).getAuthenticationType()
        testGetSettings_defaultValues(MultiQueueAuthenticationType.HYBRID)
    }

    /**
     * Ensure calls to [SettingsController.getSettings] is still available even then the [MultiQueueAuthenticationType]
     * is set to [MultiQueueAuthenticationType.RESTRICTED].
     */
    @Test
    fun testGetSettings_restrictedMode()
    {
        Mockito.doReturn(MultiQueueAuthenticationType.RESTRICTED).`when`(multiQueueAuthenticator).getAuthenticationType()
        testGetSettings_defaultValues(MultiQueueAuthenticationType.RESTRICTED)
    }
}

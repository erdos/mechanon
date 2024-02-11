package dev.erdos.automechanon

import dev.erdos.automechanon.triggers.SmsMessageTrigger
import dev.erdos.automechanon.triggers.SmsMessageTriggerFactory
import org.json.JSONObject
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class SmsMessageTriggerTest {

    @Test
    fun `serialize-deserialize works`() {
        val data = SmsMessageTrigger(UUID.randomUUID())
        val json = SmsMessageTriggerFactory.toJson(data)
        val data2 = SmsMessageTriggerFactory.fromJson(json)
        assertEquals(data, data2)

        val data3 = SmsMessageTriggerFactory.fromJson(JSONObject(json.toString()))
        assertEquals(data, data3)
    }
}
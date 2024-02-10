package dev.erdos.automechanon

import dev.erdos.automechanon.actions.WebhookAction
import dev.erdos.automechanon.actions.WebhookActionFactory
import org.json.JSONObject
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class WebhookActionTest {

    @Test
    fun `serialize-deserialize works`() {
        val data = WebhookAction(UUID.randomUUID(), "a", "b", "c")
        val json = WebhookActionFactory.toJson(data)
        val data2 = WebhookActionFactory.fromJson(json)
        assertEquals(data, data2)

        val data3 = WebhookActionFactory.fromJson(JSONObject(json.toString()))
        assertEquals(data, data3)
    }
}
package dev.erdos.automechanon.triggers

import android.content.Context
import android.telephony.SmsMessage
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import dev.erdos.automechanon.DataPoint
import dev.erdos.automechanon.ItemFactory
import dev.erdos.automechanon.Lens
import dev.erdos.automechanon.StepData
import dev.erdos.automechanon.StepIssue
import dev.erdos.automechanon.StepResult
import dev.erdos.automechanon.TriggerStep
import org.json.JSONObject
import java.util.UUID

val MESSAGE = DataPoint("message")

class SmsMessageTrigger(private val uuid: UUID) : TriggerStep<SmsMessage, SmsMessageTrigger> {

    override suspend fun fire(context: Context, data: StepData): StepResult = StepResult.Proceed(data)

    override fun initialToStepDataImpl(ctx: Context, initial: SmsMessage): StepData {
        return StepData(mapOf(MESSAGE to initial.messageBody))
    }

    override fun tryCastInput(obj: Any) = if (obj is SmsMessage) obj else null

    override fun factory(): ItemFactory<SmsMessageTrigger> = SmsMessageTriggerFactory
    override fun getUuid(): UUID = uuid
    override fun issues(context: Context) = emptyList<StepIssue<SmsMessageTrigger>>()
}

val SmsMessageTriggerFactory = object: ItemFactory<SmsMessageTrigger> {
    override fun name() = "SMS trigger"
    override fun makeDummy() = SmsMessageTrigger(uuid = UUID.randomUUID())
    override fun fromJson(node: JSONObject) = SmsMessageTrigger(UUID.fromString(node.getString("uuid")))
    override fun produces() = setOf(MESSAGE)

    @Composable override fun MakeSettings(model: Lens<SmsMessageTrigger>) {
        Text(text = "Incoming SMS trigger")
    }

    override fun toJson(obj: SmsMessageTrigger) = JSONObject(mapOf("uuid" to obj.getUuid().toString()))
}
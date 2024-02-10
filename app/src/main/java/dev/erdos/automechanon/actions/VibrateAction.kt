package dev.erdos.automechanon.actions

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.core.content.ContextCompat.getSystemService
import dev.erdos.automechanon.ActionStep
import dev.erdos.automechanon.DataPoint
import dev.erdos.automechanon.Lens
import dev.erdos.automechanon.ItemFactory
import dev.erdos.automechanon.StepData
import dev.erdos.automechanon.StepIssue
import org.json.JSONObject
import java.util.UUID

class VibrateAction(private val uuid: UUID) : ActionStep<VibrateAction> {
    override suspend fun fire(context: Context, data: StepData): StepData {
        Log.i(VibrateAction::class.java.canonicalName, "Vibrating")
        vibrator(context).vibrate()
        return data
    }

    private fun vibrator(context: Context) = if (Build.VERSION.SDK_INT>=31) {
        val vibratorManager = getSystemService(context, VibratorManager::class.java)
        vibratorManager!!.defaultVibrator;
    } else {
        getSystemService(context, Vibrator::class.java)!!
    }

    private fun Vibrator.vibrate() {
        if (Build.VERSION.SDK_INT >= 26) {
            vibrate(VibrationEffect.createWaveform(longArrayOf(500, 500),0));
        } else {
            vibrate(longArrayOf(500, 500),0);
        }
    }

    override fun factory() = VibrateActionFactory
    override fun getUuid() = uuid
    override fun issues(context: Context): List<StepIssue<VibrateAction>> = listOf()
}

val VibrateActionFactory = object: ItemFactory<VibrateAction> {
    override fun toJson(obj: VibrateAction) = JSONObject(mapOf("uuid" to obj.getUuid().toString()))
    override fun fromJson(node: JSONObject) = VibrateAction(UUID.fromString(node.getString("uuid")))
    override fun produces() = setOf<DataPoint>()
    @Composable override fun MakeSettings(model: Lens<VibrateAction>) = Unit
    override fun makeDummy() = VibrateAction(UUID.randomUUID())
    override fun name() = "Vibrate"
}
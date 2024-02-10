package dev.erdos.automechanon.triggers

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat.startActivity
import dev.erdos.automechanon.DataPoint
import dev.erdos.automechanon.Lens
import dev.erdos.automechanon.ItemFactory
import dev.erdos.automechanon.StepData
import dev.erdos.automechanon.StepIssue
import dev.erdos.automechanon.TriggerStep
import org.json.JSONObject
import java.util.UUID

val PACK = DataPoint("pack")
val TICKER = DataPoint("ticker")
val TITLE = DataPoint("title")
val TEXT = DataPoint("text")
val KEY = DataPoint("key")
val APP = DataPoint("app")

class AndroidNotificationTrigger(private val uuid: UUID) :
    TriggerStep<StatusBarNotification, AndroidNotificationTrigger> {
    override fun initialToStepDataImpl(ctx: Context, initial: StatusBarNotification): StepData {
        val pack = initial.packageName
        val ticker = initial.notification.tickerText?.toString()
        val extras = initial.notification.extras
        initial.isOngoing
        val title = extras.getString("android.title")
        val text = extras.getCharSequence("android.text").toString()
        Log.i("NotificationService", "extras: $extras")
        return StepData(
            mapOf(
                APP to sourceAppName(ctx, initial),
                PACK to pack,
                TICKER to (ticker ?: ""),
                TITLE to (title ?: ""),
                TEXT to text))
    }

    override fun tryCastInput(obj: Any) = if (obj is StatusBarNotification) obj else null

    override fun factory(): ItemFactory<AndroidNotificationTrigger> = NotificationTriggerFactory
    override fun getUuid(): UUID = uuid
    override fun issues(context: Context): List<StepIssue<AndroidNotificationTrigger>> {
        val notificationListener = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        return if (notificationListener == null || !notificationListener.contains(context.packageName)) {
            listOf(object : StepIssue<AndroidNotificationTrigger> {
                @Composable
                override fun issueComponent() {
                    val context = LocalContext.current
                    Row {
                        Text(text = "Not listening to notifications!")
                        Button(onClick = { startActivity(context, intent, null) }) {
                            Text("Enable")
                        }
                    }
                }
            })
        } else {
            emptyList()
        }
    }
}

val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")

val NotificationTriggerFactory = object: ItemFactory<AndroidNotificationTrigger> {
    override fun name() = "Notification trigger"
    override fun makeDummy() = AndroidNotificationTrigger(uuid = UUID.randomUUID())
    override fun fromJson(node: JSONObject) = AndroidNotificationTrigger(UUID.fromString(node.getString("uuid")))
    override fun produces() = setOf(PACK, TICKER, TITLE, TEXT)

    @Composable override fun MakeSettings(model: Lens<AndroidNotificationTrigger>) {
        Text(text = "Android Notification")
    }

    override fun toJson(obj: AndroidNotificationTrigger) = JSONObject(mapOf("uuid" to obj.getUuid().toString()))
}

private fun sourceAppName(ctx: Context, sbn: StatusBarNotification): String {
    val pack = sbn.packageName
    val pm = ctx.packageManager
    val ai = try {
        pm.getApplicationInfo(pack, 0)
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }
    return ai?.let { pm.getApplicationLabel(it).toString() } ?: "unknown"
}
package dev.erdos.automechanon.triggers

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat.startActivity
import dev.erdos.automechanon.DataPoint
import dev.erdos.automechanon.ItemFactory
import dev.erdos.automechanon.Lens
import dev.erdos.automechanon.StepData
import dev.erdos.automechanon.StepIssue
import dev.erdos.automechanon.StepResult
import dev.erdos.automechanon.TriggerStep
import dev.erdos.automechanon.update
import org.json.JSONObject
import java.util.UUID

val PACKAGE = DataPoint("package")
val TICKER = DataPoint("ticker")
val TITLE = DataPoint("title")
val TEXT = DataPoint("text")
val APP = DataPoint("app")

data class AndroidNotificationTrigger(private val uuid: UUID, val appNamePattern: String?) :
    TriggerStep<StatusBarNotification, AndroidNotificationTrigger> {

    override suspend fun fire(context: Context, data: StepData) =
        if (appNamePattern.isNullOrBlank()) {
            StepResult.Proceed(data)
        } else if (data.values[APP]!!.contains(appNamePattern, true)
            || data.values[PACKAGE]!!.contains(appNamePattern, true)) {
            StepResult.Proceed(data)
        } else {
            StepResult.Skipped
        }

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
                PACKAGE to pack,
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
            listOf(ISSUE_MISSING_ACCESS)
        } else {
            emptyList()
        }
    }
}

val ISSUE_MISSING_ACCESS = object : StepIssue<AndroidNotificationTrigger> {
    @Composable
    override fun IssueComponent() {
        val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
        val context = LocalContext.current
        Row {
            Text(text = "Not listening to notifications!")
            Button(onClick = { startActivity(context, intent, null) }) {
                Text("Enable")
            }
        }
    }
}

val NotificationTriggerFactory = object: ItemFactory<AndroidNotificationTrigger> {
    override fun name() = "Notification trigger"
    override fun makeDummy() = AndroidNotificationTrigger(uuid = UUID.randomUUID(), appNamePattern = null)
    override fun fromJson(node: JSONObject) = AndroidNotificationTrigger(
        UUID.fromString(node.getString("uuid")),
        node.optString("appNamePattern"))
    override fun produces() = setOf(PACKAGE, TICKER, TITLE, TEXT, APP)

    @Composable override fun MakeSettings(model: Lens<AndroidNotificationTrigger>) {
        val data = model.asMutableLiveData()
        Column {
            Text(text = "Android Notification")

            if (data.value!!.appNamePattern == null) {
                TextButton(onClick = { data.update { it.copy(appNamePattern = "") } }) {
                    Text(text = "App name pattern")
                }
            } else {
                Row(modifier = Modifier.fillMaxWidth()) {
                    TextField(
                        value = data.value!!.appNamePattern!!,
                        onValueChange = { txt -> data.update { it.copy(appNamePattern = txt) } },
                        label = { Text(text = "App name contains pattern") })
                    IconButton(onClick = { data.update { it.copy(appNamePattern = null) } }) {
                        Icon(Icons.Filled.Clear, "Remove pattern")
                    }
                }
            }
        }
    }

    override fun toJson(obj: AndroidNotificationTrigger) = JSONObject(mapOf(
        "uuid" to obj.getUuid().toString(),
        "appNamePattern" to obj.appNamePattern))
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
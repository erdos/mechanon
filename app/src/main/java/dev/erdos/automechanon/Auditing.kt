package dev.erdos.automechanon

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.core.database.getStringOrNull
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

/* Data model:
 * (timestamp, automation uuid, status, data-map-json, error-msg)
 */

enum class AutomationRunResult { DONE, ERROR, SKIPPED }
data class AuditLogEntry(
    val createdAt: Instant = Instant.now(),
    val automation: UUID,
    val state: AutomationRunResult,
    val data: StepData,
    val errorMessage: String? = null)

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, "app", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS automation_audit_log (
               created_at TIMESTAMP NOT NULL,
               automation UUID NOT NULL,
               state TEXT NOT NULL,
               data TEXT NOT NULL,
               error TEXT DEFAULT NULL
             )
            """)
        db.execSQL("""
            CREATE INDEX IF NOT EXISTS idx_auditlog1 ON automation_audit_log (automation, created_at)
            """)
    }

    fun insertLogEntry(entry: AuditLogEntry) {
        val values = ContentValues().apply {
            put("created_at", entry.createdAt.toString())
            put("automation", entry.automation.toString())
            put("state", entry.state.toString())
            put("data", JSONObject(entry.data.values.mapKeys { it.key.name }).toString())
            put("error", entry.errorMessage)
        }
        writableDatabase.insertOrThrow("automation_audit_log", "error", values)
    }

    fun listPreviousRuns(automation: UUID): List<AuditLogEntry> {
        val query = "SELECT created_at, automation, state, data, error FROM automation_audit_log WHERE automation = ? ORDER BY created_at DESC"
        readableDatabase.rawQuery(query, arrayOf(automation.toString())).use {
            val result = mutableListOf<AuditLogEntry>()
            if (it.moveToFirst()) {
                do {
                    val entry = AuditLogEntry(
                        createdAt = Instant.parse(it.getString(0)),
                        automation = UUID.fromString(it.getString(1)),
                        state = AutomationRunResult.valueOf(it.getString(2)),
                        data = StepData(JSONObject(it.getString(3)).toMap().mapKeys { DataPoint(it.key) }.mapValues { it.value.toString() }),
                        errorMessage = it.getStringOrNull(4))
                    result.add(entry)
                } while (it.moveToNext())
            }
            return result
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
}

fun JSONObject.toMap(): Map<String, *> {
    val m = mutableMapOf<String, Any>()
    for (key in this.keys()) {
        m[key] = this.get(key)
    }
    return m
}

private var helper: DatabaseHelper? = null
fun getDatabase(ctx: Context): DatabaseHelper {
    if (helper == null) {
        synchronized(DatabaseHelper::class) {
            helper = helper ?: DatabaseHelper(ctx)
        }
    }
    return helper!!
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ViewLogEntry(entry: AuditLogEntry, reloadCallback: (() -> Unit)?) {
    val open = remember { mutableStateOf(false) }
    val context = LocalContext.current

    Card(modifier = Modifier
        .fillMaxWidth()
        .padding(Dp(10F)),
        onClick = { open.value = !open.value }) {
        Row(modifier = Modifier.padding(Dp(10F))) {
            when (entry.state) {
                AutomationRunResult.DONE -> Icon(Icons.Filled.Done, "Done")
                AutomationRunResult.ERROR -> Icon(Icons.Filled.Warning, "Error", tint = MaterialTheme.colorScheme.error)
                AutomationRunResult.SKIPPED -> Icon(Icons.Filled.Clear, "Skipped")
            }

            Column(modifier = Modifier.padding(Dp(6F))) {
                Text(text = entry.createdAt.toString(), fontWeight = FontWeight.ExtraBold,)
                if (entry.errorMessage != null) {
                    Text(text = entry.errorMessage)
                }

                if (open.value) {
                    FlowRow {
                        for ((k, v) in entry.data.values) {
                            k.UI(v)
                        }
                    }
                    if (reloadCallback != null) {
                        Button(onClick = {
                            retry(
                                context,
                                entry
                            ).invokeOnCompletion { reloadCallback() }
                        }) {
                            Icon(Icons.Filled.Refresh, "Retry")
                            Text(text = "Retry")
                        }
                    }
                }
            }
        }
    }
}

@Preview
@Composable
fun TestViewLogEntry() {
    val data = AuditLogEntry(
        errorMessage = null,
        data = StepData(mapOf(DataPoint("message") to "Hello")),
        state = AutomationRunResult.DONE,
        automation = UUID.randomUUID(),
        createdAt = Instant.now())
    ViewLogEntry(entry = data, {})
}

@Preview
@Composable
fun TestViewLogEntryError() {
    val data = AuditLogEntry(
        errorMessage = "Invalid property syntax",
        data = StepData(mapOf(DataPoint("message") to "Hello")),
        state = AutomationRunResult.ERROR,
        automation = UUID.randomUUID(),
        createdAt = Instant.now())
    ViewLogEntry(entry = data, {})
}
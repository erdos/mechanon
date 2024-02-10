package dev.erdos.automechanon.actions

import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import dev.erdos.automechanon.ActionStep
import dev.erdos.automechanon.DataPoint
import dev.erdos.automechanon.ItemFactory
import dev.erdos.automechanon.Lens
import dev.erdos.automechanon.StepData
import dev.erdos.automechanon.StepIssue
import dev.erdos.automechanon.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID


val response = DataPoint("response")

data class WebhookAction(private val uuid: UUID, val url: String, val method: String, val payloadPattern: String) : ActionStep<WebhookAction> {

    override suspend fun fire(context: Context, data: StepData) =
        withContext(Dispatchers.IO) {
            val body = fetch(url, method, data.interpolate(payloadPattern))
            StepData(mapOf(response to body))
        }

    override fun factory() = WebhookActionFactory
    override fun getUuid() = uuid
    override fun issues(context: Context): List<StepIssue<WebhookAction>> = emptyList()

    private fun fetch(url: String, method: String, body: String): String {
        val urlConn = URL(url).openConnection() as HttpURLConnection
        urlConn.requestMethod = method
        urlConn.setRequestProperty("Content-Type", "text/plain; charset=utf-8")
        //urlConn.setRequestProperty("Accept", "application/json")
        urlConn.doOutput = true
        urlConn.connect()

        if (method == "POST" || method == "PUT") {
            Log.i("WebhookAction", "HTTP request was '$body'")
            BufferedWriter(OutputStreamWriter(urlConn.outputStream)).use {
                it.write(body)
            }
        }

        Log.i("WebhookAction", "HTTP response code was ${urlConn.responseCode}")

        val istream = if (urlConn.responseCode in setOf(HttpURLConnection.HTTP_OK)) {
            urlConn.inputStream
        } else {
            urlConn.errorStream
        }

        return if (istream != null) {
            BufferedReader(istream.reader()).use { it.readText() }
        } else {
            ""
        }
    }
}

//private fun isNetworkAvailable(): Boolean {
//    val connectivityManager = getSystemService(ComponentActivity.CONNECTIVITY_SERVICE) as ConnectivityManager?
 //   val activeNetworkInfo = connectivityManager?.activeNetworkInfo
 //   return activeNetworkInfo != null && activeNetworkInfo.isConnected
//}

val WebhookActionFactory = object : ItemFactory<WebhookAction> {
    override fun name() = "Send webhook call"
    override fun makeDummy() = WebhookAction(uuid = UUID.randomUUID(), "https://", "GET", "")

    override fun toJson(obj: WebhookAction) =
        JSONObject(
            mutableMapOf<Any?, Any?>(
                "uuid" to obj.getUuid().toString(),
                "url" to obj.url,
                "method" to obj.method,
                "payload" to obj.payloadPattern
            )
        )

    override fun fromJson(node: JSONObject): WebhookAction = WebhookAction(
        uuid = UUID.fromString(node.getString("uuid")),
        url = node.optString("url", ""),
        method = node.optString("method", "POST"),
        payloadPattern = node.optString("payload", "")
    )

    override fun produces()= setOf<DataPoint>() // TODO: body, status, etc

    @Composable
    override fun MakeSettings(model: Lens<WebhookAction>) {
        val md = model.asMutableLiveData()
        val data = model.observableAsState()

        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Dp(10.0F))) {
            TextField(
                value = data.value.url,
                onValueChange = { txt -> md.update { it.copy(url = txt) } },
                label = { Text(text = "URL") })
            DropDown(
                textLabel = "Method",
                state = data.value.method,
                options = mapOf("POST" to "Post", "GET" to "Get", "PUT" to "Put", "PATCH" to "Patch")) { m ->
                md.update { it.copy(method = m) }
            }
            if (data.value.method in setOf("POST", "PUT", "PATCH")) {
                TextField(
                    value = data.value.payloadPattern,
                    onValueChange = { txt -> md.update { it.copy(payloadPattern = txt) } },
                    label = { Text(text = "Payload") })
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun <T> DropDown(textLabel: String, state: T, options: Map<T, String>, callback: (T) -> Unit) {
    var isExpanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = isExpanded,
        onExpandedChange = { newValue ->
            isExpanded = newValue
        }
    ) {
        TextField(
            value = options.getValue(state),
            onValueChange = {},
            readOnly = true,
            label = { Text(text = textLabel)},
            //trailingIcon = {
             //   ExposedDropdownMenuDefaults.TrailingIcon(expanded = isExpanded)
            //},
            placeholder = {
                //Text(text = "Please select your gender")
            },
            colors = ExposedDropdownMenuDefaults.textFieldColors(),
            modifier = Modifier.menuAnchor()
        )

        ExposedDropdownMenu(
            expanded = isExpanded,
            onDismissRequest = { isExpanded = false }) {
            for ((value, label) in options) {
                DropdownMenuItem(
                    text = { Text(text = label) },
                    onClick = {
                        isExpanded = false
                        callback(value)
                    })
            }
        }
    }
}
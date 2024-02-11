package dev.erdos.automechanon

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.util.Log
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import dev.erdos.automechanon.actions.VibrateActionFactory
import dev.erdos.automechanon.actions.WebhookActionFactory
import dev.erdos.automechanon.triggers.NotificationTriggerFactory
import dev.erdos.automechanon.triggers.SmsMessageTriggerFactory
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.lang.Exception
import java.time.Instant
import java.util.UUID
import java.util.UUID.randomUUID

data class DataPoint(val name: String)

// maker of triggers and actions
interface ItemFactory<A> {
    fun name(): String // to be shown in menus
    fun makeDummy(): A // create dummy, empty item

    fun toJson(obj: A) : JSONObject
    fun fromJson(node: JSONObject) : A
    fun jsonDiscriminator() : String

    fun produces(): Set<DataPoint>

    @Composable
    fun MakeSettings(model: Lens<A>)
}

val TRIGGERS = setOf(SmsMessageTriggerFactory, NotificationTriggerFactory)
val ACTIONS = setOf(WebhookActionFactory, VibrateActionFactory)

interface Step<S: Step<S>> {
    suspend fun fire(context: Context, data: StepData): StepResult
    fun factory() : ItemFactory<S>

    fun getUuid(): UUID

    fun issues(context: Context): List<StepIssue<S>>
}

sealed class StepResult private constructor() {
    data object Skipped : StepResult()

    data class Proceed(val data: StepData) : StepResult()
    data class Erred(val message: String) : StepResult()
}

data class StepData(val values: Map<DataPoint, String>) {
    fun interpolate(pattern: String) =
        values.entries.fold(pattern) { p, (k, v) -> p.replace("{{${k.name}}}", v) }
}

interface StepIssue<S> {
    @Composable
    fun IssueComponent()
}

interface TriggerStep<T : Any, S : TriggerStep<T, S>> : Step<S> {
    fun initialToStepDataImpl(ctx: Context, initial: T): StepData

    fun tryCastInput(obj: Any) : T?
}

interface ActionStep<S : ActionStep<S>> : Step<S>

// fun <T> JSONArray.map(f: (JSONObject) -> T) = (0 until this.length()).map { f(this.getJSONObject(it)) }

private fun <S: Step<*>> toJsonWithClass(step: S): JSONObject {
    val fact = step.factory() as ItemFactory<S>
    val json = fact.toJson(step)
    json.put("class", fact.jsonDiscriminator())
    return json
}

data class Automation(
    val uuid: UUID = randomUUID(),
    val title: String,
    val trigger: TriggerStep<*, *>?,
    val action: ActionStep<*>?,
    ) {
    constructor() : this(title = "New Automation", trigger = null, action = null)
    constructor(json: JSONObject) : this(
        uuid = UUID.fromString(json.getString("uuid")),
        title = json.optString("title", "N/A"),
        trigger = json.optJSONObject("trigger")?.let {
                TRIGGERS.find { f -> it.getString("class") == f.jsonDiscriminator() }?.fromJson(it)
            },
        action = json.optJSONObject("action")?.let {
                ACTIONS.find { f -> it.getString("class") == f.jsonDiscriminator() }?.fromJson(it)
            })

    fun toJson() : JSONObject = JSONObject(mapOf(
        "uuid" to uuid.toString(),
        "title" to title,
        "action" to action?.let { toJsonWithClass(it) },
        "trigger" to trigger?.let { toJsonWithClass(it) },
    ))

    fun isReadyToUse(ctx: Context): Boolean = trigger?.issues(ctx)?.isEmpty() ?: false
            && action?.issues(ctx)?.isEmpty() ?: false
}

data class Automations(val automations: List<Automation>)

// initializes a global static state object
private var __state: MutableLiveData<Automations>? = null
fun getState(cw: Context): LiveData<Automations> {
    val KEY = "automa2"
    synchronized(Automations::class) {
        val preferences by lazy { cw.getSharedPreferences("automations-1112", MODE_PRIVATE) }
        return __state ?: cw.let {
            preferences.getStringSet(KEY, emptySet())!!
            //  emptySet<String>()
                .map { JSONObject(it) }
                .map { Automation(it) }
                .let { Automations(it) }
                .let { MutableLiveData(it) }
        }.also {
            it.observeForever {
                it.automations.map { it.toJson().toString() }
                    .toSet()
                    .also { // write back to storage
                        preferences.edit().putStringSet(KEY, it).apply()
                    }
            }
        }.also { __state = it }
    }
}
fun insertEmptyAutomation() {
    __state!!.postValue(Automations(__state!!.value!!.automations + listOf(Automation())))
}

fun deleteAutomationBy(uuid: UUID) {
    __state!!.postValue(Automations(__state!!.value!!.automations.filter { it.uuid != uuid }))
}

fun bindings() : Lens<Automations> = Lens.of(__state!!)

fun findAutomationLiveDataById(uuid: UUID) : Lens<Automation> = bindings()
    .reading { a -> a.automations.find { it.uuid == uuid }!! }
    .writing { a, x -> a.copy(automations = a.automations.map { if (it.uuid == x.uuid) x else it }) }

private suspend fun run1(ctx: Context, log: AuditLogEntry, step: Step<*>): AuditLogEntry = try {
    when (val result = step.fire(ctx, log.data)) {
        is StepResult.Proceed -> log.copy(data = result.data, state = AutomationRunResult.DONE)
        is StepResult.Skipped -> log.copy(state = AutomationRunResult.SKIPPED)
        is StepResult.Erred -> log.copy(state = AutomationRunResult.ERROR, errorMessage = result.message)
    }
} catch (e: Exception) {
    log.copy(state = AutomationRunResult.ERROR, errorMessage = e.message)
}

suspend fun dispatch(ctx: Context, msg: Any) {
    val db = getDatabase(ctx)
    getState(ctx).value!!.automations
        .filter { it.isReadyToUse(ctx) }
        .forEach {auto ->
            auto.trigger!!.tryCastInput(msg) ?.let {
                Log.i("Model", "Triggering automation $auto")
                val initialData = (auto.trigger as TriggerStep<Any, *>).initialToStepDataImpl(ctx, it)
                var audit = AuditLogEntry(
                    automation = auto.uuid,
                    state = AutomationRunResult.SKIPPED,
                    data = initialData)
                audit = run1(ctx, audit, auto.trigger)
                if (audit.state == AutomationRunResult.DONE) {
                    audit = run1(ctx, audit, auto.action!!)
                }
                db.insertLogEntry(audit)
            }
        }
}

fun retry(ctx: Context, oldEntry: AuditLogEntry): Job {
    var entry = oldEntry.copy(createdAt = Instant.now(), errorMessage = null)
    val automation = findAutomationLiveDataById(entry.automation).asMutableLiveData().value!!
    return GlobalScope.launch {
        entry = run1(ctx, entry, automation.trigger!!)
        if (entry.state == AutomationRunResult.DONE) {
            entry = run1(ctx, entry, automation.action!!)
        }
        getDatabase(ctx).insertLogEntry(entry)
    }
}

@Composable
fun DataPoint.UI(value: String? = null) {
    Text(text = value?.let { "${name}: $it" } ?: name,
        modifier = Modifier
            .padding(4.dp)
            .border(1.dp, Color.LightGray, RoundedCornerShape(6.dp))
            .padding(4.dp))
}
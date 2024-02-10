package dev.erdos.automechanon

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import dev.erdos.automechanon.actions.VibrateActionFactory
import dev.erdos.automechanon.actions.WebhookActionFactory
import dev.erdos.automechanon.triggers.NotificationTriggerFactory
import dev.erdos.automechanon.triggers.SmsMessageTriggerFactory
import org.json.JSONObject
import java.util.UUID
import java.util.UUID.randomUUID

data class DataPoint(val name: String)

// maker of triggers and actions
interface ItemFactory<A> {
    fun name(): String // to be shown in menus
    fun makeDummy(): A // create dummy, empty item

    fun toJson(obj: A) : JSONObject
    fun fromJson(node: JSONObject) : A

    fun produces(): Set<DataPoint>

    @Composable
    fun MakeSettings(model: Lens<A>)
}

val TRIGGERS = setOf(NotificationTriggerFactory, SmsMessageTriggerFactory)
val ACTIONS = setOf(WebhookActionFactory, VibrateActionFactory)

interface Step<S: Step<S>> {
    suspend fun fire(context: Context, data: StepData): StepData
    fun factory() : ItemFactory<S>

    fun getUuid(): UUID

    fun issues(context: Context): List<StepIssue<S>>
}

data class StepData(val values: Map<DataPoint, String>) {
    fun interpolate(pattern: String) =
        values.entries.fold(pattern) { p, (k, v) -> p.replace("{{${k.name}}}", v) }
}

interface StepIssue<S> {
    @Composable
    fun issueComponent()
}

interface TriggerStep<T : Any, S : TriggerStep<T, S>> : Step<S> {
    override suspend fun fire(context: Context, data: StepData) = data

    fun initialToStepDataImpl(ctx: Context, initial: T): StepData

    fun tryCastInput(obj: Any) : T?
}

interface ActionStep<S : ActionStep<S>> : Step<S>

// fun <T> JSONArray.map(f: (JSONObject) -> T) = (0 until this.length()).map { f(this.getJSONObject(it)) }

private fun <S: Step<*>> toJsonWithClass(step: S): JSONObject {
    val fact = step.factory() as ItemFactory<S>
    val json = fact.toJson(step)
    json.put("class", fact.javaClass.simpleName)
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
        title = json.optString("title", "- no title"),
        trigger = json.optJSONObject("trigger")?.let {
                TRIGGERS.find { f -> it.getString("class") == f.javaClass.simpleName }
            }?.fromJson(json.getJSONObject("trigger")),
        action = json.optJSONObject("action")?.let {
                ACTIONS.find { f -> it.getString("class") == f.javaClass.simpleName }
            }?.fromJson(json.getJSONObject("action")))

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

// fun findAutomationBy(uuid: UUID) = __state!!.value!!.automations.first { it.uuid == uuid }

fun bindings() : Lens<Automations> = Lens.of(__state!!)

fun findAutomationLiveDataById(uuid: UUID) : Lens<Automation> = bindings()
    .reading { a -> a.automations.find { it.uuid == uuid }!! }
    .writing { a, x -> a.copy(automations = a.automations.map { if (it.uuid == x.uuid) x else it }) }

suspend fun dispatch(ctx: Context, msg: Any) {
    getState(ctx).value!!.automations
        .filter { it.isReadyToUse(ctx) }
        .forEach {auto ->
            auto.trigger!!.tryCastInput(msg) ?.let {
                Log.i("Model", "Triggering automation $auto")
                var data = (auto.trigger as TriggerStep<Any, *>).initialToStepDataImpl(ctx, it)
                data = auto.trigger.fire(ctx, data)
                data = auto.action!!.fire(ctx, data)
                // output: data
            }
        }
}
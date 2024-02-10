package dev.erdos.automechanon

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.util.UUID


class EditAutomationActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intent = this.intent
        val id = UUID.fromString(intent.getStringExtra("uuid"))
        val alData = findAutomationLiveDataById(id)
        setContent { ViewAutomation(automation = alData) }
    }
}

@Composable
fun ViewAutomation(automation: Lens<Automation>) {
    val state = automation.observableAsState()
    val mutable = remember { automation.asMutableLiveData() }

    Column(modifier = Modifier.fillMaxWidth()) {
        TextField(
            value = state.value.title,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth(),
            label = {Text("Title")},
            onValueChange = { title -> mutable.update { it.copy(title = title) } })

        Spacer(modifier = Modifier.size(30.dp))

        Text("Trigger:", Modifier.padding(10.dp))
        if (state.value.trigger != null) {
            val trigger = automation.reading { it.trigger }.writing { a, t -> a.copy(trigger = t) }
            StepCard(step = trigger as Lens<TriggerStep<*, *>>, delete = { mutable.update { it.copy(trigger = null) } })
        } else {
            MenuButton("Add Trigger", TRIGGERS.associateBy { it.name() }) {
                mutable.update { state -> state.copy(trigger = it.makeDummy()) }
            }
        }

        Text("Action:", Modifier.padding(10.dp))
        if (state.value.action != null) {
            val action = automation.reading { it.action }.writing { a, t -> a.copy(action = t) }
            StepCard(step = action as Lens<ActionStep<*>>, delete = { mutable.update { it.copy(action = null) } })
        } else {
            MenuButton("Add Action", ACTIONS.associateBy { it.name() }) {
                mutable.update { state -> state.copy(action = it.makeDummy()) }
            }
        }
    }
}

@Composable
fun <E> MenuButton(title: String, elems: Map<String, E>, callback: (E) -> Unit) {
    val expanded = remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Button(onClick = { expanded.value = true },
            contentPadding = PaddingValues(12.dp)
        ) {

            Icon(Icons.Filled.Add, "Add a step")

            Text(text = title)
            DropdownMenu(expanded = expanded.value, onDismissRequest = { expanded.value = false }) {
                elems.forEach { (k, v) ->
                    DropdownMenuItem(
                        text = { Text(text = k) },
                        onClick = { expanded.value = false; callback(v) })
                }
            }
        }
    }

}

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun <S: Step<*>> StepCard(step: Lens<S>, delete: () -> Unit) {
    val state = step.asMutableLiveData()
    val factory = state.value!!.factory() as ItemFactory<S>
    Carded(factory.name(), delete) {
        factory.MakeSettings(step)
        state.value!!.issues(LocalContext.current.applicationContext).forEach {
            it.issueComponent()
        }
        FlowRow {
            for (produce in factory.produces()) {
                Text(text = produce.name,
                    modifier = Modifier
                        .padding(4.dp)
                        .border(1.dp, Color.LightGray, RoundedCornerShape(6.dp))
                        .padding(4.dp))
            }
        }
    }
}

@Composable
fun Carded(name: String, removeFn: () -> Unit, builder: @Composable () -> Unit) {
    ElevatedCard(modifier = Modifier
        .fillMaxWidth()
        .padding(Dp(10F))) {
        Column(modifier = Modifier.padding(Dp(10F))) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(text = name, style = MaterialTheme.typography.titleMedium )
                IconButton(onClick = removeFn) {
                    Icon(Icons.Filled.Delete, "Small floating action button.")
                }
            }
            builder()
        }
    }
}
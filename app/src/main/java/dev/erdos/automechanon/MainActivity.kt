package dev.erdos.automechanon

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShapeDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.LiveData


class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i("MainActivity", "onCreate")
        //requestNotificationListenerAccess()
        setContent {
            val state = remember { getState(this) }
            MainScreen(state)
        }
    }
}

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainScreen(liveData: LiveData<Automations>) {
    val ctx = LocalContext.current
    val state = liveData.observeAsState()
    val version = remember { version(ctx) }
    Scaffold(floatingActionButton = {
        FloatingActionButton(
            onClick = { insertEmptyAutomation() },
            shape = ShapeDefaults.ExtraLarge,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.secondary) {
            Icon(Icons.Filled.Add, "Add a new automation.")
        }
    }) {
        Column(modifier = Modifier
            .verticalScroll(rememberScrollState())
            .fillMaxWidth(),
            verticalArrangement = Arrangement.SpaceBetween) {
            Text(text = "Version $version", modifier = Modifier.align(Alignment.End))
            for (automation in state.value!!.automations) {
                val showMenu = remember { mutableStateOf(false) }
                Card(modifier = Modifier
                    .padding(4.dp)
                    .combinedClickable(onClick = {
                        val intent = Intent(ctx, EditAutomationActivity::class.java)
                        intent.putExtra("uuid", automation.uuid.toString())
                        ctx.startActivity(intent)
                    }, onLongClick = { showMenu.value = true })
                ) {
                    DropdownMenu(
                        expanded = showMenu.value,
                        onDismissRequest = { showMenu.value = false }) {
                        DropdownMenuItem(
                            text = { Text(text = "Delete") },
                            onClick = { showMenu.value = false; deleteAutomationBy(automation.uuid) })
                    }
                    Text(text = automation.title,
                        fontSize = 25.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        }
    }
}

private fun version(context: Context): String = try {
    context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName
} catch (e: PackageManager.NameNotFoundException) {
    "unknown"
}
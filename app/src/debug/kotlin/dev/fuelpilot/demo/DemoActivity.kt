package dev.fuelpilot.demo

import android.app.Application
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.fuelpilot.AppShell
import dev.fuelpilot.AssistRuntime
import dev.fuelpilot.feature.assist.PreferencesAssistStateStore
import dev.fuelpilot.ui.Motion

// Distinct file and runtime: never reads FuelPilotApplication.dependencies or production state.
private val Application.syntheticDemoStore by preferencesDataStore(name = "wayfill_synthetic_demo_only")

class DemoSession(application: Application) : AndroidViewModel(application) {
    val sources = DemoSources()
    val runtime = AssistRuntime(sources.dependencies(PreferencesAssistStateStore(application.syntheticDemoStore),
        Settings.Global.getFloat(application.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f), viewModelScope)
    init { runtime.setUiVisible(true) }
    override fun onCleared() { runtime.close() }
}

class DemoActivity : ComponentActivity() {
    private val session: DemoSession by viewModels()
    val runtime get() = session.runtime
    val sources get() = session.sources
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val state by runtime.state.collectAsState()
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFFF0C46E), onPrimary = Color(0xFF171512))) {
                Column(Modifier.fillMaxSize().background(Color(0xFF0C0B09)).safeDrawingPadding()) {
                    Text("WAYFILL_SYNTHETIC_ONLY · 합성 데이터 시연 · 실제 차량 아님",
                        modifier = Modifier.fillMaxWidth().background(Color(0xFF663600)).padding(8.dp).testTag("demo_banner"), color = Color.White)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                        for (name in listOf("recommendation", "passing", "noaction", "nearby", "stale", "unsupported", "unavailable")) {
                            TextButton(onClick = { sources.select(name) }, modifier = Modifier.testTag("demo_$name")) { Text(name) }
                        }
                    }
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                        for ((name, value) in listOf("parked" to Motion.Parked, "moving" to Motion.Moving)) {
                            TextButton(onClick = { sources.motion.value = value }, modifier = Modifier.testTag("demo_$name")) { Text(name) }
                        }
                        for (name in listOf("small", "full", "recovery")) {
                            TextButton(onClick = { sources.refuel(name) }, modifier = Modifier.testTag("demo_$name")) { Text(name) }
                        }
                        TextButton(onClick = sources::duplicate, modifier = Modifier.testTag("demo_duplicate")) { Text("duplicate") }
                        TextButton(onClick = { sources.navigationSucceeds = true }, modifier = Modifier.testTag("demo_navsuccess")) { Text("navsuccess") }
                        TextButton(onClick = { sources.navigationSucceeds = false }, modifier = Modifier.testTag("demo_navfailure")) { Text("navfailure") }
                    }
                    AppShell(state, runtime::dispatch, Modifier.weight(1f), onRetry = runtime::retry)
                }
            }
        }
    }
}

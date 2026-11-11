package dev.fuelpilot

import android.Manifest
import android.content.Intent
import androidx.core.net.toUri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModelProvider

class MainActivity : ComponentActivity() {
    private val app get() = application as FuelPilotApplication
    private val permissionRequest = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        // This is completion of the user's explicit start action, never a boot restart.
        app.monitoring.start()
    }
    private fun startMonitoring() {
        if (app.runtime.state.value.ui.motion == dev.fuelpilot.ui.Motion.Moving) { app.monitoring.problem("정차 후 모니터링을 시작해 주세요."); return }
        if (app.monitoring.permissionsSatisfied()) { app.monitoring.start(); return }
        permissionRequest.launch(buildList {
            add(Manifest.permission.ACCESS_COARSE_LOCATION); add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.toTypedArray())
    }
    override fun onStart() { super.onStart(); app.setUiVisible(true) }
    override fun onStop() { if (!isChangingConfigurations) app.setUiVisible(false); super.onStop() }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val host = ViewModelProvider(this, AppHostViewModel.Factory(app.runtime))[AppHostViewModel::class.java]
        setContent {
            val state by host.state.collectAsState()
            val monitoring by app.monitoring.state.collectAsState()
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFFF0C46E), onPrimary = Color(0xFF171512), background = Color(0xFF0C0B09), surface = Color(0xFF171512))) {
                Surface(Modifier.fillMaxSize().safeDrawingPadding(), color = Color(0xFF0C0B09)) {
                    AppShell(state = state, onIntent = host::dispatch, modifier = Modifier.fillMaxSize(),
                        monitoring = monitoring,
                        onMonitoringChange = { enabled -> if (enabled) startMonitoring() else app.monitoring.stop() },
                        onRetry = host.runtime::retry,
                        onOpenPermissionSettings = { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri())) },
                        onDemoLaunch = VariantDemo.intent(this)?.let { demo -> { startActivity(demo) } })
                }
            }
        }
    }
}

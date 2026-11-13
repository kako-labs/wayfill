package dev.fuelpilot

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
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
import dev.fuelpilot.data.location.PleosFusedLocationPort

class MainActivity : ComponentActivity() {
    private val app get() = application as FuelPilotApplication
    private val permissionRequest = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        // This is completion of the user's explicit start action, never a boot restart.
        if (app.monitoring.permissionsSatisfied()) app.monitoring.start()
        else app.monitoring.problem("위치와 알림 권한을 허용해야 모니터링을 시작할 수 있어요.")
    }
    private fun startMonitoring() {
        if (!monitoringStartAllowed(app.runtime.state.value.ui.motion)) {
            app.monitoring.problem("P단에 놓고 정차한 뒤 모니터링을 시작해 주세요.")
            return
        }
        if (app.monitoring.permissionsSatisfied()) { app.monitoring.start(); return }
        permissionRequest.launch(monitoringRuntimePermissions(
            automotive = packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE),
            sdkInt = Build.VERSION.SDK_INT,
        ).toTypedArray())
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

internal fun monitoringRuntimePermissions(automotive: Boolean, sdkInt: Int): List<String> = buildList {
    add(Manifest.permission.ACCESS_COARSE_LOCATION)
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    // A permission name is safe on older APIs; sdkInt is injectable so this builder can be tested on the JVM.
    if (sdkInt >= 33) add("android.permission.POST_NOTIFICATIONS")
    if (automotive) add(PleosFusedLocationPort.FUSED_LOCATION_PERMISSION)
    // RANGE_REMAINING needs this runtime permission on AAOS/PLEOS; without it range stays Unsupported.
    if (automotive) add(CAR_ENERGY_PERMISSION)
}

internal const val CAR_ENERGY_PERMISSION = "android.car.permission.CAR_ENERGY"

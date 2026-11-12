package dev.fuelpilot

import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import dev.fuelpilot.feature.assist.AssistAvailability
import dev.fuelpilot.feature.assist.AssistIntent
import dev.fuelpilot.ui.Motion
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Service lifecycle harness. Only Parked is injected in tests; SDK ports remain real/Unsupported. */
class MonitoringRuntimeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = instrumentation.targetContext.applicationContext as FuelPilotApplication
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText() }
    private fun waitFor(predicate: () -> Boolean) = compose.waitUntil(10_000, predicate)
    private var originalLocationEnabled = true

    @Before fun permissionsAndCleanService() {
        originalLocationEnabled = shell("cmd location is-location-enabled").trim() == "true"
        shell("cmd location set-location-enabled true")
        val name = instrumentation.targetContext.packageName
        shell("pm grant $name android.permission.ACCESS_COARSE_LOCATION")
        shell("pm grant $name android.permission.ACCESS_FINE_LOCATION")
        if (Build.VERSION.SDK_INT >= 33) shell("pm grant $name android.permission.POST_NOTIFICATIONS")
        shell("appops set $name POST_NOTIFICATION allow")
        compose.runOnIdle { app.monitoring.stop() }
        waitFor { app.runtime.state.value.ready }
        compose.runOnIdle { app.runtime.dispatch(AssistIntent.MotionChanged(Motion.Unknown)) }
        waitFor { app.runtime.state.value.ui.motion==Motion.Unknown }
    }

    private fun provideParkedTestState() {
        compose.runOnIdle { app.runtime.dispatch(AssistIntent.MotionChanged(Motion.Parked)) }
        waitFor { app.runtime.state.value.ui.motion==Motion.Parked }
    }

    @After fun stopAndRestoreDeviceSettings() {
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.runOnIdle { app.monitoring.stop() }
        shell("appops set ${instrumentation.targetContext.packageName} POST_NOTIFICATION allow")
        shell("cmd location set-location-enabled $originalLocationEnabled")
    }

    @Test fun foregroundServiceSharesRuntimeAcrossRotationAndBackground() {
        provideParkedTestState()
        val runtime = app.runtime
        compose.runOnIdle { app.monitoring.start() }
        waitFor { app.monitoring.state.value.running && runtime.monitoringActive.value }
        compose.activityRule.scenario.recreate()
        waitFor { app.uiVisible }
        assertSame(runtime, app.runtime)
        assertTrue(runtime.monitoringActive.value)
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        waitFor { !app.uiVisible }
        assertTrue(runtime.monitoringActive.value)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.runOnIdle { app.monitoring.stop() }
        waitFor { !runtime.monitoringActive.value && !app.monitoring.state.value.running }
        assertFalse(app.monitoring.requested)
    }

    @Test fun disabledLocationRejectsStart() {
        provideParkedTestState()
        shell("cmd location set-location-enabled false")
        waitFor { !app.monitoring.locationEnabled() }
        compose.runOnIdle { app.monitoring.start() }
        assertFalse(app.monitoring.state.value.running)
        assertFalse(app.monitoring.requested)
    }

    @Test fun disablingLocationStopsAnAlreadyRunningServiceWithoutNewSdkData() {
        provideParkedTestState()
        compose.runOnIdle { app.monitoring.start() }
        waitFor { app.monitoring.state.value.running && app.runtime.monitoringActive.value }
        shell("cmd location set-location-enabled false")
        waitFor { !app.runtime.monitoringActive.value && !app.monitoring.state.value.running }
        // Preserve intent for an explicit retry; never restart silently when location returns.
        assertTrue(app.monitoring.requested)
        shell("cmd location set-location-enabled true")
        assertFalse(app.runtime.monitoringActive.value)
    }

    @Test fun unsupportedProductionHomeIsHonestAndUnknownPolicyIsDisabled() {
        waitFor { app.runtime.state.value.presentation.availability == AssistAvailability.UNSUPPORTED }
        assertEquals(Motion.Unknown, app.runtime.state.value.ui.motion)
        compose.onNodeWithTag("unknown_companion").assertIsDisplayed()
        compose.onNodeWithTag("nav_home").assertDoesNotExist()
        compose.onNodeWithTag("nav_settings").assertDoesNotExist()
        compose.onNodeWithTag("guide_now").assertDoesNotExist()
        compose.runOnIdle { app.monitoring.start() }
        assertFalse(app.monitoring.requested)
        assertFalse(app.monitoring.state.value.running)
    }
}

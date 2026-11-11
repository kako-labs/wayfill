package dev.fuelpilot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModelProvider
import dev.fuelpilot.ui.FuelPilotUi

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dependencies = (application as FuelPilotApplication).dependencies
        val host = ViewModelProvider(this, AppHostViewModel.Factory(dependencies))[AppHostViewModel::class.java]
        setContent {
            val state by host.assist.state.collectAsState()
            MaterialTheme {
                Surface(Modifier.fillMaxSize(), color = Color(0xFF0C0B09)) {
                    FuelPilotUi(state = state, onIntent = host.assist::dispatch, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

package com.example.pedometer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pedometer.data.StepRepository
import com.example.pedometer.service.StepCounterService
import com.example.pedometer.ui.PedometerScreen
import com.example.pedometer.ui.PedometerTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository = StepRepository.get(this)
        val hasSensor = Permissions.hasStepCounter(this)

        setContent {
            PedometerTheme {
                val state by repository.state.collectAsStateWithLifecycle()
                var hasPermission by remember { mutableStateOf(Permissions.hasActivityRecognition(this)) }

                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions(),
                ) {
                    hasPermission = Permissions.hasActivityRecognition(this)
                    StepCounterService.start(this)
                }

                LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                    repository.refresh()
                    hasPermission = Permissions.hasActivityRecognition(this@MainActivity)
                    StepCounterService.start(this@MainActivity)
                }

                PedometerScreen(
                    state = state,
                    hasSensor = hasSensor,
                    hasPermission = hasPermission,
                    onRequestPermission = { permissionLauncher.launch(Permissions.required) },
                    onGoalChange = repository::setDailyGoal,
                    onStrideChange = repository::setStrideLength,
                )
            }
        }
    }
}

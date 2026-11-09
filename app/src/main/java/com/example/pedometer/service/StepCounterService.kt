package com.example.pedometer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.example.pedometer.MainActivity
import com.example.pedometer.Permissions
import com.example.pedometer.R
import com.example.pedometer.data.StepRepository
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.text.NumberFormat

/**
 * Foreground service that listens to the hardware step counter and keeps an
 * ongoing notification with today's step count.
 *
 * The step counter sensor keeps counting in hardware even when nothing is listening,
 * so steps taken while the service is stopped are still picked up (as long as the
 * phone was not rebooted in between).
 */
class StepCounterService : LifecycleService(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private lateinit var repository: StepRepository
    private var listening = false

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        repository = StepRepository.get(this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        if (sensor == null || !Permissions.hasActivityRecognition(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
        } else {
            0
        }
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(repository.state.value.todaySteps),
            type,
        )

        if (!listening) {
            listening = sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
            lifecycleScope.launch {
                repository.state
                    .map { it.todaySteps }
                    .distinctUntilChanged()
                    .collect { steps ->
                        getSystemService(NotificationManager::class.java)
                            .notify(NOTIFICATION_ID, buildNotification(steps))
                    }
            }
        }
        return START_STICKY
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_STEP_COUNTER) {
            repository.onSensorReading(event.values[0].toLong())
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onDestroy() {
        if (listening) sensorManager.unregisterListener(this)
        listening = false
        super.onDestroy()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { setShowBadge(false) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(steps: Int): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_steps)
            .setContentTitle(getString(R.string.notification_title, NumberFormat.getInstance().format(steps)))
            .setContentText(getString(R.string.notification_text))
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "step_counter"
        private const val NOTIFICATION_ID = 1

        /** Starts the service if the device has a step counter and permission is granted. */
        fun start(context: Context) {
            if (!Permissions.hasStepCounter(context) || !Permissions.hasActivityRecognition(context)) return
            ContextCompat.startForegroundService(context, Intent(context, StepCounterService::class.java))
        }
    }
}

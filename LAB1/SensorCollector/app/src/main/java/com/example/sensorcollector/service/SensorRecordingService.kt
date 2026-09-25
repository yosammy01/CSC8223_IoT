package com.example.sensorcollector.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.example.sensorcollector.MainActivity
import com.example.sensorcollector.R
import com.example.sensorcollector.model.SensorReading
import com.example.sensorcollector.util.CsvExporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import kotlin.math.sqrt

class SensorRecordingService : Service(), SensorEventListener {

    data class RecordingState(
        val isRecording: Boolean = false,
        val activityLabel: String = "Standing Still",
        val sampleCount: Int = 0,
        val elapsedTimeMs: Long = 0L,
        val currentX: Float = 0f,
        val currentY: Float = 0f,
        val currentZ: Float = 0f,
        val currentMagnitude: Float = 0f,
        val currentFile: File? = null,
        val sensorDelay: Int = SensorManager.SENSOR_DELAY_GAME
    )

    inner class LocalBinder : Binder() {
        fun getService(): SensorRecordingService = this@SensorRecordingService
    }

    private val binder = LocalBinder()
    private val _state = MutableStateFlow(RecordingState())
    val state: StateFlow<RecordingState> = _state.asStateFlow()

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private var fileWriter: BufferedWriter? = null
    private var currentFile: File? = null

    private var startTimeMs: Long = 0L
    private var sampleCount = 0
    private var activityLabel = "Standing Still"

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var timerJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val label = intent.getStringExtra(EXTRA_LABEL) ?: "Standing Still"
                val delayUs = intent.getIntExtra(EXTRA_SENSOR_DELAY, SensorManager.SENSOR_DELAY_GAME)
                startRecording(label, delayUs)
            }
            ACTION_STOP -> {
                stopRecording()
            }
        }
        return START_NOT_STICKY
    }

    fun startRecording(label: String, sensorDelay: Int = SensorManager.SENSOR_DELAY_GAME) {
        if (_state.value.isRecording) return

        activityLabel = label
        sampleCount = 0
        startTimeMs = System.currentTimeMillis()

        // Acquire WakeLock
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SensorCollector::RecordingWakeLock").apply {
            acquire(10 * 60 * 1000L /* 10 minutes max */)
        }

        // Prepare CSV File
        currentFile = CsvExporter.createRecordingFile(this, label)
        try {
            fileWriter = BufferedWriter(FileWriter(currentFile, true)).apply {
                write(SensorReading.CSV_HEADER)
                flush()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Register Sensor Listener
        accelerometer?.let { accel ->
            sensorManager.registerListener(this, accel, sensorDelay)
        }

        // Start Foreground Notification safely
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    buildNotification(label, 0, 0L),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIFICATION_ID, buildNotification(label, 0, 0L))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Update State
        _state.value = RecordingState(
            isRecording = true,
            activityLabel = label,
            sampleCount = 0,
            elapsedTimeMs = 0L,
            currentFile = currentFile,
            sensorDelay = sensorDelay
        )

        // Start Timer for UI updates
        timerJob = serviceScope.launch {
            val startUptime = SystemClock.uptimeMillis()
            while (isActive) {
                delay(100)
                val elapsed = SystemClock.uptimeMillis() - startUptime
                _state.value = _state.value.copy(
                    elapsedTimeMs = elapsed,
                    sampleCount = sampleCount
                )
                updateNotification(label, sampleCount, elapsed)
            }
        }
    }

    fun stopRecording() {
        if (!_state.value.isRecording) return

        timerJob?.cancel()
        timerJob = null

        sensorManager.unregisterListener(this)

        try {
            fileWriter?.flush()
            fileWriter?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        fileWriter = null

        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        wakeLock = null

        _state.value = _state.value.copy(
            isRecording = false
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || !_state.value.isRecording) return
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            val mag = sqrt(x * x + y * y + z * z)

            val nowMs = System.currentTimeMillis()
            val relMs = nowMs - startTimeMs
            sampleCount++

            val reading = SensorReading(
                timestampMs = nowMs,
                relTimestampMs = relMs,
                x = x,
                y = y,
                z = z,
                label = activityLabel
            )

            serviceScope.launch {
                try {
                    fileWriter?.write(reading.toCsvLine())
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            _state.value = _state.value.copy(
                currentX = x,
                currentY = y,
                currentZ = z,
                currentMagnitude = mag,
                sampleCount = sampleCount
            )
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Sensor Recording Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows recording progress for accelerometer data collection"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(label: String, samples: Int, elapsedMs: Long): Notification {
        val seconds = elapsedMs / 1000
        val timeFormatted = String.format("%02d:%02d", seconds / 60, seconds % 60)

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, SensorRecordingService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Recording Accelerometer ($label)")
            .setContentText("Time: $timeFormatted | Samples: $samples")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_launcher_foreground, "Stop", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(label: String, samples: Int, elapsedMs: Long) {
        val notification = buildNotification(label, samples, elapsedMs)
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        stopRecording()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.example.sensorcollector.ACTION_START"
        const val ACTION_STOP = "com.example.sensorcollector.ACTION_STOP"
        const val EXTRA_LABEL = "extra_label"
        const val EXTRA_SENSOR_DELAY = "extra_sensor_delay"
        private const val CHANNEL_ID = "sensor_recording_channel"
        private const val NOTIFICATION_ID = 1001
    }
}

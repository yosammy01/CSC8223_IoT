/**
 * ============================================================================
 * README - SensorService.kt
 * ============================================================================
 * Role: Background Foreground Service for Continuous Data Collection.
 * 
 * Functions:
 * - Ensures Android does not kill the sensor collection when the screen sleeps 
 *   or the user navigates away by running as a Foreground Service.
 * - Registers to the device's hardware `SensorManager` using SENSOR_DELAY_FASTEST.
 * - Streams raw accelerometer/gravity vectors into the `FeatureExtractor`.
 * - Includes a fallback mechanism: uses Gravity sensor if available, otherwise 
 *   falls back to Accelerometer to support a wider range of devices.
 * ============================================================================
 */
package com.gsu.iot.pose

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

class SensorService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var gravitySensor: Sensor? = null
    private var accelerometerSensor: Sensor? = null
    private var gyroscopeSensor: Sensor? = null

    companion object {
        const val CHANNEL_ID = "SensorServiceChannel"
        const val NOTIFICATION_ID = 1
        const val TAG = "SensorService"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
        accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscopeSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Federated Pose Client")
            .setContentText("Collecting sensor data...")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .build()

        startForeground(NOTIFICATION_ID, notification)

        // Register sensors
        gravitySensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST)
        }
        accelerometerSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST)
        }
        gyroscopeSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST)
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null // We don't need to bind to this service
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        when (event.sensor.type) {
            Sensor.TYPE_GRAVITY -> {
                FeatureExtractor.addGravitySample(event.values[0], event.values[1], event.values[2], event.timestamp)
            }
            Sensor.TYPE_ACCELEROMETER -> {
                FeatureExtractor.addAccelSample(event.values[0], event.values[1], event.values[2], event.timestamp)
            }
            Sensor.TYPE_GYROSCOPE -> {
                FeatureExtractor.addGyroSample(event.values[0], event.values[1], event.values[2], event.timestamp)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not used
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Sensor Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }
}
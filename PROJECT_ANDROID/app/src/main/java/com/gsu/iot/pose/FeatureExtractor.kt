/**
 * ============================================================================
 * README - FeatureExtractor.kt
 * ============================================================================
 * Role: Processes raw sensor streams into normalized machine learning features.
 * 
 * Functions:
 * - Collects raw (X,Y,Z) sensor data into a 100-millisecond sliding window.
 * - Once the window hits 100ms, computes 8 statistical features:
 *   1-3. Mean of X, Y, Z
 *   4-6. Standard Deviation of X, Y, Z
 *   7. Mean of the Vector Magnitude
 *   8. Standard Deviation of the Vector Magnitude
 * - Normalizes these values (approx [-1, 1]) and clears the raw data from memory,
 *   strictly enforcing the privacy requirement that raw data never leaves the edge.
 * ============================================================================
 */
package com.gsu.iot.pose

import kotlin.math.sqrt

object FeatureExtractor {

    private const val WINDOW_SIZE_MS = 100L
    private const val NS_TO_MS = 1_000_000L

    private val gravityWindow = mutableListOf<SensorSample>()
    private val accelWindow = mutableListOf<SensorSample>()
    private val gyroWindow = mutableListOf<SensorSample>()

    data class SensorSample(val x: Float, val y: Float, val z: Float, val timestampNs: Long)

    // A callback to receive extracted features. E.g., invoked by the UI or the training loop.
    var onFeaturesExtracted: ((FloatArray) -> Unit)? = null

    @Synchronized
    fun addGravitySample(x: Float, y: Float, z: Float, timestampNs: Long) {
        gravityWindow.add(SensorSample(x, y, z, timestampNs))
        checkWindowAndExtract(timestampNs)
    }

    @Synchronized
    fun addAccelSample(x: Float, y: Float, z: Float, timestampNs: Long) {
        accelWindow.add(SensorSample(x, y, z, timestampNs))
        checkWindowAndExtract(timestampNs)
    }

    @Synchronized
    fun addGyroSample(x: Float, y: Float, z: Float, timestampNs: Long) {
        gyroWindow.add(SensorSample(x, y, z, timestampNs))
        checkWindowAndExtract(timestampNs)
    }

    private fun checkWindowAndExtract(latestTimestampNs: Long) {
        val oldestGrav = gravityWindow.firstOrNull()?.timestampNs ?: Long.MAX_VALUE
        val oldestAccel = accelWindow.firstOrNull()?.timestampNs ?: Long.MAX_VALUE
        val oldestGyro = gyroWindow.firstOrNull()?.timestampNs ?: Long.MAX_VALUE

        val oldest = minOf(oldestGrav, oldestAccel, oldestGyro)
        if (oldest == Long.MAX_VALUE) return // All windows are empty

        val windowDurationMs = (latestTimestampNs - oldest) / NS_TO_MS

        if (windowDurationMs >= WINDOW_SIZE_MS) {
            extractFeaturesAndClear()
        }
    }

    private fun computeStats(window: List<SensorSample>, normFactor: Float): FloatArray {
        if (window.isEmpty()) return FloatArray(8) { 0f }

        val count = window.size

        // Calculate means
        val meanX = window.sumOf { it.x.toDouble() } / count
        val meanY = window.sumOf { it.y.toDouble() } / count
        val meanZ = window.sumOf { it.z.toDouble() } / count

        // Calculate standard deviations
        val stdX = sqrt(window.sumOf { Math.pow(it.x - meanX, 2.0) } / count)
        val stdY = sqrt(window.sumOf { Math.pow(it.y - meanY, 2.0) } / count)
        val stdZ = sqrt(window.sumOf { Math.pow(it.z - meanZ, 2.0) } / count)

        // Calculate magnitudes and its mean/std
        val magnitudes = window.map { sqrt((it.x * it.x + it.y * it.y + it.z * it.z).toDouble()) }
        val meanMag = magnitudes.sum() / count
        val stdMag = sqrt(magnitudes.sumOf { Math.pow(it - meanMag, 2.0) } / count)

        return floatArrayOf(
            (meanX / normFactor).toFloat(),
            (meanY / normFactor).toFloat(),
            (meanZ / normFactor).toFloat(),
            (stdX / normFactor).toFloat(),
            (stdY / normFactor).toFloat(),
            (stdZ / normFactor).toFloat(),
            (meanMag / normFactor).toFloat(),
            (stdMag / normFactor).toFloat()
        )
    }

    private fun extractFeaturesAndClear() {
        // Output 24 features total: 8 Gravity, 8 Accel, 8 Gyro
        val features = FloatArray(24)

        // Gravity normalization (~9.8)
        computeStats(gravityWindow, 9.8f).copyInto(features, 0)
        
        // Accelerometer normalization (~9.8)
        computeStats(accelWindow, 9.8f).copyInto(features, 8)
        
        // Gyroscope normalization (~4.0 rad/s)
        computeStats(gyroWindow, 4.0f).copyInto(features, 16)

        onFeaturesExtracted?.invoke(features)

        gravityWindow.clear()
        accelWindow.clear()
        gyroWindow.clear()
    }
}
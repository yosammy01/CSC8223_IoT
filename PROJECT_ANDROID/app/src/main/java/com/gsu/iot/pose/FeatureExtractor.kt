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

    data class SensorSample(val x: Float, val y: Float, val z: Float, val timestampNs: Long)

    // A callback to receive extracted features. E.g., invoked by the UI or the training loop.
    var onFeaturesExtracted: ((FloatArray) -> Unit)? = null

    @Synchronized
    fun addGravitySample(x: Float, y: Float, z: Float, timestampNs: Long) {
        gravityWindow.add(SensorSample(x, y, z, timestampNs))

        // Check if window is full based on timestamps
        val oldest = gravityWindow.first().timestampNs
        val newest = timestampNs
        
        val windowDurationMs = (newest - oldest) / NS_TO_MS

        if (windowDurationMs >= WINDOW_SIZE_MS) {
            extractFeaturesAndClear()
        }
    }

    private fun extractFeaturesAndClear() {
        if (gravityWindow.isEmpty()) return

        val count = gravityWindow.size

        // Calculate means
        val meanX = gravityWindow.sumOf { it.x.toDouble() } / count
        val meanY = gravityWindow.sumOf { it.y.toDouble() } / count
        val meanZ = gravityWindow.sumOf { it.z.toDouble() } / count

        // Calculate standard deviations
        val stdX = sqrt(gravityWindow.sumOf { Math.pow(it.x - meanX, 2.0) } / count)
        val stdY = sqrt(gravityWindow.sumOf { Math.pow(it.y - meanY, 2.0) } / count)
        val stdZ = sqrt(gravityWindow.sumOf { Math.pow(it.z - meanZ, 2.0) } / count)

        // Calculate magnitudes and its mean/std
        val magnitudes = gravityWindow.map { sqrt((it.x * it.x + it.y * it.y + it.z * it.z).toDouble()) }
        val meanMag = magnitudes.sum() / count
        val stdMag = sqrt(magnitudes.sumOf { Math.pow(it - meanMag, 2.0) } / count)

        // Compile feature vector (Normalized approximately)
        // Values typically between -9.8 and 9.8 (gravity).
        // Let's divide by 9.8f to roughly normalize them to [-1, 1].
        val normFactor = 9.8f
        val features = FloatArray(8)
        features[0] = (meanX / normFactor).toFloat()
        features[1] = (meanY / normFactor).toFloat()
        features[2] = (meanZ / normFactor).toFloat()
        features[3] = (stdX / normFactor).toFloat()
        features[4] = (stdY / normFactor).toFloat()
        features[5] = (stdZ / normFactor).toFloat()
        features[6] = (meanMag / normFactor).toFloat()
        features[7] = (stdMag / normFactor).toFloat()

        onFeaturesExtracted?.invoke(features)

        gravityWindow.clear()
    }
}
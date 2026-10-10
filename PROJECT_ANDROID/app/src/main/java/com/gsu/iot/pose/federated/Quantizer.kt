package com.gsu.iot.pose.federated

import kotlin.math.max
import kotlin.math.min

object Quantizer {

    /**
     * Symmetrically quantizes a 32-bit float array into an 8-bit signed integer byte array.
     * Calculates the max absolute value in the array, scales values into [-127, 127],
     * and appends the max absolute value (as 4 bytes) at the beginning of the byte array
     * so it can be de-quantized properly by the receiver.
     */
    fun quantize(weights: FloatArray): ByteArray {
        if (weights.isEmpty()) return ByteArray(0)

        // Find max absolute value
        var maxAbs = 0f
        for (w in weights) {
            val absW = Math.abs(w)
            if (absW > maxAbs) {
                maxAbs = absW
            }
        }

        // Avoid division by zero
        if (maxAbs == 0f) maxAbs = 1e-9f

        val scale = 127f / maxAbs

        // Create byte array. Size = 4 bytes (for maxAbs) + length of weights
        val quantized = ByteArray(4 + weights.size)

        // Store maxAbs in the first 4 bytes (big-endian)
        val maxAbsBits = java.lang.Float.floatToIntBits(maxAbs)
        quantized[0] = (maxAbsBits ushr 24).toByte()
        quantized[1] = (maxAbsBits ushr 16).toByte()
        quantized[2] = (maxAbsBits ushr 8).toByte()
        quantized[3] = maxAbsBits.toByte()

        // Quantize weights
        for (i in weights.indices) {
            val scaled = weights[i] * scale
            // Clamp to [-127, 127] and convert to Byte
            val clamped = max(-127f, min(127f, scaled))
            quantized[4 + i] = clamped.toInt().toByte()
        }

        return quantized
    }

    /**
     * De-quantizes an 8-bit signed integer byte array back into a 32-bit float array.
     * Expects the first 4 bytes to represent the max absolute value.
     */
    fun dequantize(quantized: ByteArray): FloatArray {
        if (quantized.size <= 4) return FloatArray(0)

        // Reconstruct maxAbs from the first 4 bytes
        val maxAbsBits = (quantized[0].toInt() and 0xFF shl 24) or
                (quantized[1].toInt() and 0xFF shl 16) or
                (quantized[2].toInt() and 0xFF shl 8) or
                (quantized[3].toInt() and 0xFF)

        val maxAbs = java.lang.Float.intBitsToFloat(maxAbsBits)
        val scale = maxAbs / 127f

        val weightCount = quantized.size - 4
        val weights = FloatArray(weightCount)

        for (i in 0 until weightCount) {
            val qValue = quantized[4 + i].toInt()
            weights[i] = qValue * scale
        }

        return weights
    }
}
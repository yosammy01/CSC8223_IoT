package com.example.sensorcollector.model

import java.util.Locale

data class SensorReading(
    val timestampMs: Long,
    val relTimestampMs: Long,
    val x: Float,
    val y: Float,
    val z: Float,
    val label: String
) {
    fun toCsvLine(): String {
        return String.format(Locale.US, "%d,%d,%.4f,%.4f,%.4f,%s\n", timestampMs, relTimestampMs, x, y, z, label)
    }

    companion object {
        const val CSV_HEADER = "timestamp_ms,rel_timestamp_ms,accel_x,accel_y,accel_z,label\n"
    }
}

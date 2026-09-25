package com.example.sensorcollector.model

import java.io.File

data class RecordingSession(
    val file: File,
    val fileName: String,
    val activityLabel: String,
    val timestampMs: Long,
    val durationMs: Long,
    val sampleCount: Int,
    val fileSizeBytes: Long
)

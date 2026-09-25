package com.example.sensorcollector.repository

import android.content.Context
import com.example.sensorcollector.model.RecordingSession
import com.example.sensorcollector.util.CsvExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class RecordingRepository(private val context: Context) {

    suspend fun getRecordings(): List<RecordingSession> = withContext(Dispatchers.IO) {
        val dir = CsvExporter.getRecordingsDir(context)
        val files = dir.listFiles { _, name -> name.endsWith(".csv") } ?: emptyArray()

        files.map { file ->
            CsvExporter.parseRecordingSession(file)
        }.sortedByDescending { it.timestampMs }
    }

    suspend fun deleteRecording(file: File): Boolean = withContext(Dispatchers.IO) {
        if (file.exists()) {
            file.delete()
        } else false
    }

    suspend fun createZipArchive(): File? = withContext(Dispatchers.IO) {
        val dir = CsvExporter.getRecordingsDir(context)
        val files = dir.listFiles { _, name -> name.endsWith(".csv") }?.toList() ?: emptyList()
        if (files.isEmpty()) null
        else CsvExporter.createZipArchive(context, files)
    }
}

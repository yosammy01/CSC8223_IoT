package com.example.sensorcollector.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.sensorcollector.model.RecordingSession
import com.example.sensorcollector.model.SensorReading
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.FileReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object CsvExporter {

    private val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    fun getRecordingsDir(context: Context): File {
        val dir = File(context.filesDir, "recordings")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getExportsDir(context: Context): File {
        val dir = File(context.cacheDir, "exports")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun createRecordingFile(context: Context, label: String): File {
        val cleanLabel = label.trim().lowercase().replace(Regex("[^a-z0-9]"), "_")
        val timeStr = dateFormat.format(Date())
        val fileName = "accel_${cleanLabel}_${timeStr}.csv"
        val file = File(getRecordingsDir(context), fileName)
        if (file.exists()) {
            file.delete()
        }
        return file
    }

    fun parseRecordingSession(file: File): RecordingSession {
        var sampleCount = 0
        var firstRelMs: Long = 0
        var lastRelMs: Long = 0
        var label = "Unknown"
        var timestampMs = file.lastModified()

        try {
            BufferedReader(FileReader(file)).use { reader ->
                val header = reader.readLine() // Header
                var line = reader.readLine()
                var isFirst = true
                while (line != null) {
                    if (line.isNotBlank()) {
                        sampleCount++
                        val parts = line.split(",")
                        if (parts.size >= 6) {
                            if (isFirst) {
                                firstRelMs = parts[1].toLongOrNull() ?: 0L
                                timestampMs = parts[0].toLongOrNull() ?: timestampMs
                                label = parts[5].trim()
                                isFirst = false
                            }
                            lastRelMs = parts[1].toLongOrNull() ?: lastRelMs
                        }
                    }
                    line = reader.readLine()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val durationMs = if (lastRelMs >= firstRelMs) lastRelMs - firstRelMs else 0L

        return RecordingSession(
            file = file,
            fileName = file.name,
            activityLabel = label,
            timestampMs = timestampMs,
            durationMs = durationMs,
            sampleCount = sampleCount,
            fileSizeBytes = file.length()
        )
    }

    fun getContentUri(context: Context, file: File): Uri {
        val authority = "${context.packageName}.fileprovider"
        return FileProvider.getUriForFile(context, authority, file)
    }

    fun createZipArchive(context: Context, files: List<File>): File? {
        if (files.isEmpty()) return null
        val timeStr = dateFormat.format(Date())
        val zipFile = File(getExportsDir(context), "accel_recordings_$timeStr.zip")

        return try {
            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                val buffer = ByteArray(4096)
                for (f in files) {
                    if (!f.exists()) continue
                    val entry = ZipEntry(f.name)
                    zos.putNextEntry(entry)
                    f.inputStream().use { input ->
                        var length: Int
                        while (input.read(buffer).also { length = it } > 0) {
                            zos.write(buffer, 0, length)
                        }
                    }
                    zos.closeEntry()
                }
            }
            zipFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

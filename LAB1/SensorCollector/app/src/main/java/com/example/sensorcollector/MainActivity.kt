package com.example.sensorcollector

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.sensorcollector.adapter.RecordingAdapter
import com.example.sensorcollector.databinding.ActivityMainBinding
import com.example.sensorcollector.model.RecordingSession
import com.example.sensorcollector.repository.RecordingRepository
import com.example.sensorcollector.service.SensorRecordingService
import com.example.sensorcollector.util.CsvExporter
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.sqrt

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: RecordingRepository
    private lateinit var adapter: RecordingAdapter

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null

    private var recordingService: SensorRecordingService? = null
    private var isBound = false

    private var sessionToSave: RecordingSession? = null

    private var toneGenerator: ToneGenerator? = null
    private var testJob: Job? = null

    // ActivityResult Launcher for saving CSV file
    private val createDocumentLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri: Uri? ->
            if (uri != null && sessionToSave != null) {
                saveFileToUri(sessionToSave!!.file, uri)
            }
        }

    // Notification permission launcher for Android 13+
    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (!isGranted) {
                Toast.makeText(
                    this,
                    "Notification permission denied. Recording service alerts may be limited.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as SensorRecordingService.LocalBinder
            recordingService = binder.getService()
            isBound = true
            observeServiceState()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            recordingService = null
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = RecordingRepository(this)

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        checkNotificationPermission()
        setupTabs()
        setupHistoryRecyclerView()
        setupClickListeners()

        bindRecordingService()
    }

    override fun onResume() {
        super.onResume()
        accelerometer?.let { accel ->
            sensorManager.registerListener(this, accel, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        sensorManager.unregisterListener(this)
        super.onPause()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val mag = sqrt(x * x + y * y + z * z)

        binding.tvAccelX.text = String.format(Locale.US, "%.2f", x)
        binding.tvAccelY.text = String.format(Locale.US, "%.2f", y)
        binding.tvAccelZ.text = String.format(Locale.US, "%.2f", z)
        binding.tvMagnitude.text = String.format(Locale.US, "Magnitude: %.2f m/s²", mag)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun bindRecordingService() {
        val intent = Intent(this, SensorRecordingService::class.java)
        bindService(intent, serviceConnection, BIND_AUTO_CREATE)
    }

    private fun observeServiceState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                recordingService?.state?.collectLatest { state ->
                    if (state.isRecording) {
                        binding.tvSampleCount.text =
                            String.format(Locale.US, "%,d samples collected", state.sampleCount)
                    }
                }
            }
        }
    }

    private fun setupTabs() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> {
                        binding.layoutRecordTab.visibility = View.VISIBLE
                        binding.layoutHistoryTab.visibility = View.GONE
                    }

                    1 -> {
                        binding.layoutRecordTab.visibility = View.GONE
                        binding.layoutHistoryTab.visibility = View.VISIBLE
                        loadRecordings()
                    }
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setupHistoryRecyclerView() {
        adapter = RecordingAdapter(
            onSaveClick = { session ->
                sessionToSave = session
                createDocumentLauncher.launch(session.fileName)
            },
            onShareClick = { session ->
                shareRecording(session)
            },
            onDeleteClick = { session ->
                confirmDelete(session)
            }
        )
        binding.rvRecordings.layoutManager = LinearLayoutManager(this)
        binding.rvRecordings.adapter = adapter
    }

    private fun setupClickListeners() {
        binding.btnStartStandTest.setOnClickListener {
            startGuidedTest("Standing Still", 20)
        }

        binding.btnStartWalkTest.setOnClickListener {
            startGuidedTest("Walking", 20)
        }

        binding.btnCancelTest.setOnClickListener {
            cancelGuidedTest()
        }

        binding.btnExportAllZip.setOnClickListener {
            exportAllZip()
        }
    }

    private fun startGuidedTest(activityLabel: String, durationSeconds: Int) {
        testJob?.cancel()

        binding.btnStartStandTest.isEnabled = false
        binding.btnStartWalkTest.isEnabled = false
        binding.btnCancelTest.visibility = View.VISIBLE

        testJob = lifecycleScope.launch {
            // STEP 1: 3-Second Preparation Countdown
            binding.tvStatusBadge.text = "PREPARING TEST"
            binding.tvStatusBadge.setBackgroundColor("#FFF8E1".toColorInt())
            binding.tvStatusBadge.setTextColor("#F57F17".toColorInt())

            for (prepSec in 3 downTo 1) {
                binding.tvTimer.text = "${prepSec}s"
                binding.tvInstruction.text = "GET READY: $activityLabel test in $prepSec seconds..."
                playBeep(ToneGenerator.TONE_CDMA_PIP)
                vibrate(100)
                delay(1000)
            }

            // STEP 2: Start 20-Second Recording Test
            playBeep(ToneGenerator.TONE_PROP_BEEP)
            vibrate(300)

            val serviceIntent = Intent(this@MainActivity, SensorRecordingService::class.java).apply {
                action = SensorRecordingService.ACTION_START
                putExtra(SensorRecordingService.EXTRA_LABEL, activityLabel)
                putExtra(SensorRecordingService.EXTRA_SENSOR_DELAY, SensorManager.SENSOR_DELAY_GAME)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }

            binding.tvStatusBadge.text = "TEST IN PROGRESS (${activityLabel.uppercase()})"
            binding.tvStatusBadge.setBackgroundColor("#FFEBEE".toColorInt())
            binding.tvStatusBadge.setTextColor("#D32F2F".toColorInt())

            if (activityLabel.equals("Standing Still", ignoreCase = true)) {
                binding.tvInstruction.text = "🧍 STAND STILL - Keep phone steady or in pocket"
            } else {
                binding.tvInstruction.text = "🚶 WALK NATURALLY - Walk at your normal pace"
            }

            // 20-Second Countdown
            for (remainingSec in durationSeconds downTo 1) {
                binding.tvTimer.text = "${remainingSec}s"
                val progress = ((durationSeconds - remainingSec) * 100) / durationSeconds
                binding.progressBar.progress = progress

                if (remainingSec <= 3) {
                    playBeep(ToneGenerator.TONE_CDMA_PIP)
                }
                delay(1000)
            }

            // STEP 3: Complete Test
            binding.progressBar.progress = 100
            binding.tvTimer.text = "0s"

            recordingService?.stopRecording()

            playBeep(ToneGenerator.TONE_PROP_ACK)
            vibrate(500)

            resetTestUi()
            Toast.makeText(
                this@MainActivity,
                "$activityLabel Test Completed! CSV saved successfully.",
                Toast.LENGTH_LONG
            ).show()

            loadRecordings()
        }
    }

    private fun cancelGuidedTest() {
        testJob?.cancel()
        testJob = null

        recordingService?.stopRecording()

        resetTestUi()
        Toast.makeText(this, "Test cancelled", Toast.LENGTH_SHORT).show()
    }

    private fun resetTestUi() {
        binding.tvStatusBadge.text = "READY FOR TEST"
        binding.tvStatusBadge.setBackgroundColor("#E8F5E9".toColorInt())
        binding.tvStatusBadge.setTextColor("#2E7D32".toColorInt())

        binding.tvTimer.text = "20s"
        binding.tvInstruction.text = "Select a test below to start"
        binding.progressBar.progress = 0

        binding.btnStartStandTest.isEnabled = true
        binding.btnStartWalkTest.isEnabled = true
        binding.btnCancelTest.visibility = View.GONE
    }

    private fun playBeep(toneType: Int) {
        try {
            toneGenerator?.startTone(toneType, 150)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun vibrate(durationMs: Long) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator.vibrate(
                    VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
                @Suppress("DEPRECATION")
                vibrator.vibrate(durationMs)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadRecordings() {
        lifecycleScope.launch {
            val list = repository.getRecordings()
            adapter.submitList(list)
            if (list.isEmpty()) {
                binding.tvEmptyHistory.visibility = View.VISIBLE
                binding.rvRecordings.visibility = View.GONE
            } else {
                binding.tvEmptyHistory.visibility = View.GONE
                binding.rvRecordings.visibility = View.VISIBLE
            }
        }
    }

    private fun shareRecording(session: RecordingSession) {
        val uri = CsvExporter.getContentUri(this, session.file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Accelerometer CSV Data: ${session.activityLabel}")
            putExtra(
                Intent.EXTRA_TEXT,
                "CSV recording of ${session.activityLabel} (${session.sampleCount} samples, ${session.durationMs / 1000}s)."
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, "Share CSV Recording"))
    }

    private fun saveFileToUri(sourceFile: File, destinationUri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                contentResolver.openOutputStream(destinationUri)?.use { output ->
                    sourceFile.inputStream().use { input ->
                        input.copyTo(output)
                    }
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "CSV saved successfully!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Failed to save CSV: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun confirmDelete(session: RecordingSession) {
        AlertDialog.Builder(this)
            .setTitle("Delete Recording?")
            .setMessage("Are you sure you want to delete ${session.fileName}?")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    repository.deleteRecording(session.file)
                    loadRecordings()
                    Toast.makeText(this@MainActivity, "Recording deleted", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun exportAllZip() {
        lifecycleScope.launch {
            val zipFile = repository.createZipArchive()
            if (zipFile != null && zipFile.exists()) {
                val uri = CsvExporter.getContentUri(this@MainActivity, zipFile)
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/zip"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "All Accelerometer CSV Recordings")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(shareIntent, "Export All as ZIP"))
            } else {
                Toast.makeText(this@MainActivity, "No recordings to export", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        toneGenerator?.release()
        toneGenerator = null

        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
        super.onDestroy()
    }
}

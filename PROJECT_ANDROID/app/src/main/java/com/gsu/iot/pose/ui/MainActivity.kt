/**
 * ============================================================================
 * README - MainActivity.kt
 * ============================================================================
 * Role: The primary User Interface and orchestration layer of the Android App.
 * 
 * Functions:
 * - Manages the Compose UI with three tabs: Inference, Training, and Eval/Calib.
 * - Handles Android Permissions and launches the foreground `SensorService`.
 * - Intercepts extracted features from `FeatureExtractor` and routes them to 
 *   either training datasets, evaluation datasets, or live inference.
 * - Triggers MQTT telemetry publishing and handles local UI state updates based
 *   on server commands (like START_TRAINING and Model resets).
 * ============================================================================
 */
package com.gsu.iot.pose.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.gsu.iot.pose.DatasetPersister
import com.gsu.iot.pose.FeatureExtractor
import com.gsu.iot.pose.SensorService
import com.gsu.iot.pose.federated.MqttClientManager
import com.gsu.iot.pose.federated.Quantizer
import com.gsu.iot.pose.mlp.ModelManager
import com.gsu.iot.pose.ui.theme.FederatedPoseClientTheme
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startSensorService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)?.takeLast(4) ?: "Dev"
        MqttClientManager.initialize(this, deviceId)
        MqttClientManager.connect()

        checkPermissionsAndStartService()

        setContent {
            FederatedPoseClientTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PoseClientScreen()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        MqttClientManager.disconnect()
        stopService(Intent(this, SensorService::class.java))
    }

    private fun checkPermissionsAndStartService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        startSensorService()
    }

    private fun startSensorService() {
        val serviceIntent = Intent(this, SensorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }
}

fun playBeepSound(toneType: Int = ToneGenerator.TONE_PROP_BEEP, durationMs: Int = 150) {
    try {
        val toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
        toneGen.startTone(toneType, durationMs)
        Handler(Looper.getMainLooper()).postDelayed({
            try { toneGen.release() } catch (e: Exception) {}
        }, (durationMs + 100).toLong())
    } catch (e: Exception) {
        Log.e("PoseClient", "Failed to play beep sound", e)
    }
}

enum class AppScreenMode {
    INFERENCE, TRAINING, CALIBRATION_EVAL
}

@Composable
fun PoseClientScreen() {
    var screenMode by remember { mutableStateOf(AppScreenMode.INFERENCE) }
    val context = LocalContext.current
    var ipInput by remember { mutableStateOf(MqttClientManager.serverIp) }

    val targetSampleLimit = 50 // 50 training samples per pose
    val evalSampleLimit = 10   // 10 evaluation samples per pose

    var predictedPose by remember { mutableStateOf("Unknown") }
    var activeTargetPose by remember { mutableIntStateOf(0) } // 0-5
    var isCountingDown by remember { mutableStateOf(false) }
    var isRecordingActive by remember { mutableStateOf(false) }
    var countdownSeconds by remember { mutableIntStateOf(3) }

    // Evaluation accuracy results
    var localAccuracyResult by remember { mutableStateOf<Float?>(null) }
    var globalAccuracyResult by remember { mutableStateOf<Float?>(null) }

    // Datasets
    val trainingDataset = remember { mutableStateMapOf<Int, MutableList<FloatArray>>() }
    val evalDataset = remember { mutableStateMapOf<Int, MutableList<FloatArray>>() }

    val poseNames = listOf(
        "Screen Up", "Screen Down", "Portrait Up", 
        "Portrait Down", "Landscape Left", "Landscape Right"
    )

    var globalModelLastSyncTime by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        // Load the datasets from local persistent storage on startup
        val savedEvalData = DatasetPersister.loadEvalDataset(context)
        savedEvalData.forEach { (key, value) ->
            evalDataset[key] = value
        }

        val savedTrainingData = DatasetPersister.loadTrainingDataset(context)
        savedTrainingData.forEach { (key, value) ->
            trainingDataset[key] = value
        }
        if (trainingDataset.isNotEmpty()) {
            ModelManager.trainLocalEpochsOnDataset(trainingDataset, epochs = 20)
        }

        MqttClientManager.onGlobalModelReloaded = {
            val formatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            globalModelLastSyncTime = formatter.format(Date())
        }
        MqttClientManager.onGlobalModelResetCommand = {
            globalModelLastSyncTime = ""
            globalAccuracyResult = null
            if (ModelManager.activeModelForInference == ModelManager.ActiveModel.GLOBAL) {
                predictedPose = "Unknown"
            }
        }
        MqttClientManager.onResetCommand = {
            trainingDataset.clear()
            evalDataset.clear()
            DatasetPersister.clearEvalDataset(context)
            DatasetPersister.clearTrainingDataset(context)
            localAccuracyResult = null
            globalAccuracyResult = null
            predictedPose = "Unknown"
            globalModelLastSyncTime = ""
            isCountingDown = false
            isRecordingActive = false
        }
        MqttClientManager.onStartTrainingCommand = {
            // Server commanded us to start a baseline or quantized training run
            screenMode = AppScreenMode.TRAINING
            ModelManager.localModel.reset()
            if (trainingDataset.isNotEmpty()) {
                ModelManager.trainLocalEpochsOnDataset(trainingDataset, epochs = 20)
                val localWeights = ModelManager.localModel.getWeights()
                MqttClientManager.publishLocalWeights(localWeights)
            }
        }
    }

    // Current pose counts
    val currentTrainingPoseSamples = trainingDataset[activeTargetPose]?.size ?: 0
    val currentEvalPoseSamples = evalDataset[activeTargetPose]?.size ?: 0

    // Helper function to retrain local model on all stored training samples
    fun retrainFromDataset() {
        ModelManager.localModel.reset()
        if (trainingDataset.isNotEmpty()) {
            ModelManager.trainLocalEpochsOnDataset(trainingDataset, epochs = 20)
        }
    }

    // Evaluate accuracy over calibration dataset
    fun runEvaluation() {
        var localCorrect = 0
        var globalCorrect = 0
        var totalEvalSamples = 0

        evalDataset.forEach { (trueLabel, sampleList) ->
            sampleList.forEach { features ->
                totalEvalSamples++
                val localPred = ModelManager.localModel.predict(features)
                val globalPred = ModelManager.federatedModel.predict(features)

                if (localPred == trueLabel) localCorrect++
                if (globalPred == trueLabel) globalCorrect++
            }
        }

        if (totalEvalSamples > 0) {
            localAccuracyResult = (localCorrect.toFloat() / totalEvalSamples) * 100f
            globalAccuracyResult = (globalCorrect.toFloat() / totalEvalSamples) * 100f
            MqttClientManager.publishEvaluation(localAccuracyResult!!, globalAccuracyResult!!)
        } else {
            localAccuracyResult = 0f
            globalAccuracyResult = 0f
        }
    }

    // Countdown Effect before recording starts
    LaunchedEffect(screenMode, activeTargetPose, isCountingDown) {
        if ((screenMode == AppScreenMode.TRAINING || screenMode == AppScreenMode.CALIBRATION_EVAL) && isCountingDown) {
            for (sec in 3 downTo 1) {
                countdownSeconds = sec
                playBeepSound(ToneGenerator.TONE_PROP_BEEP, 100)
                delay(1000L)
            }
            countdownSeconds = 0
            isCountingDown = false
            isRecordingActive = true
            playBeepSound(ToneGenerator.TONE_PROP_ACK, 200)
        }
    }

    // Receive features from FeatureExtractor
    LaunchedEffect(Unit) {
        FeatureExtractor.onFeaturesExtracted = { features ->
            when (screenMode) {
                AppScreenMode.TRAINING -> {
                    if (isRecordingActive) {
                        val samplesList = trainingDataset.getOrPut(activeTargetPose) { mutableListOf() }
                        if (samplesList.size < targetSampleLimit) {
                            samplesList.add(features)
                            ModelManager.trainLocal(features, activeTargetPose)

                            if (samplesList.size >= targetSampleLimit) {
                                isRecordingActive = false
                                retrainFromDataset()
                                DatasetPersister.saveTrainingDataset(context, trainingDataset)
                                playBeepSound(ToneGenerator.TONE_PROP_BEEP2, 300)
                            }

                            val totalSamples = trainingDataset.values.sumOf { it.size }
                            if (totalSamples % 10 == 0) {
                                val localWeights = ModelManager.localModel.getWeights()
                                MqttClientManager.publishLocalWeights(localWeights)
                            }
                        } else {
                            isRecordingActive = false
                        }
                    }
                }
                AppScreenMode.CALIBRATION_EVAL -> {
                    if (isRecordingActive) {
                        val evalList = evalDataset.getOrPut(activeTargetPose) { mutableListOf() }
                        if (evalList.size < evalSampleLimit) {
                            evalList.add(features)

                            if (evalList.size >= evalSampleLimit) {
                                isRecordingActive = false
                                DatasetPersister.saveEvalDataset(context, evalDataset)
                                playBeepSound(ToneGenerator.TONE_PROP_BEEP2, 300)
                            }
                        } else {
                            isRecordingActive = false
                        }
                    }
                }
                AppScreenMode.INFERENCE -> {
                    val predictionIdx = ModelManager.predict(features)
                    val newPose = poseNames.getOrElse(predictionIdx) { "Unknown" }
                    predictedPose = newPose
                    MqttClientManager.publishTelemetry(newPose)
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Federated Pose Client", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(4.dp))

        // Connection Status & Editable Server IP
        val isConnected = MqttClientManager.isConnected
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            OutlinedTextField(
                value = ipInput,
                onValueChange = { ipInput = it },
                label = { Text("Broker IP") },
                singleLine = true,
                modifier = Modifier.width(170.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = { MqttClientManager.updateServerIp(context, ipInput) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            ) {
                Text(if (isConnected) "Connected" else "Connect")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Navigation Tabs
        TabRow(selectedTabIndex = screenMode.ordinal) {
            Tab(
                selected = screenMode == AppScreenMode.INFERENCE,
                onClick = { 
                    screenMode = AppScreenMode.INFERENCE
                    isRecordingActive = false
                    isCountingDown = false
                    retrainFromDataset()
                },
                text = { Text("Inference") }
            )
            Tab(
                selected = screenMode == AppScreenMode.TRAINING,
                onClick = { screenMode = AppScreenMode.TRAINING; isRecordingActive = false; isCountingDown = false },
                text = { Text("Training") }
            )
            Tab(
                selected = screenMode == AppScreenMode.CALIBRATION_EVAL,
                onClick = { screenMode = AppScreenMode.CALIBRATION_EVAL; isRecordingActive = false; isCountingDown = false },
                text = { Text("Eval / Calib") }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (screenMode) {
            AppScreenMode.INFERENCE -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text("Active Model: ", style = MaterialTheme.typography.titleMedium)
                    FilterChip(
                        selected = ModelManager.activeModelForInference == ModelManager.ActiveModel.LOCAL,
                        onClick = { ModelManager.setActiveModel(ModelManager.ActiveModel.LOCAL) },
                        label = { Text("LOCAL") }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FilterChip(
                        selected = ModelManager.activeModelForInference == ModelManager.ActiveModel.GLOBAL,
                        onClick = { ModelManager.setActiveModel(ModelManager.ActiveModel.GLOBAL) },
                        label = { Text("GLOBAL") }
                    )
                }

                if (globalModelLastSyncTime.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Global Model Synced at: $globalModelLastSyncTime",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
                
                Text("Predicted Pose", style = MaterialTheme.typography.labelLarge)
                Text(predictedPose, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                
                Spacer(modifier = Modifier.height(32.dp))
                OutlinedButton(
                    onClick = {
                        trainingDataset.clear()
                        DatasetPersister.clearTrainingDataset(context)
                        ModelManager.resetAll()
                        predictedPose = "Unknown"
                    }
                ) {
                    Text("Reset Model Weights & Training Data")
                }
            }

            AppScreenMode.TRAINING -> {
                Text("Local Model Training", style = MaterialTheme.typography.titleMedium)
                
                val isPoseComplete = currentTrainingPoseSamples >= targetSampleLimit
                val progress = (currentTrainingPoseSamples.toFloat() / targetSampleLimit).coerceIn(0f, 1f)

                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { if (isCountingDown) 0f else progress },
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(8.dp),
                    color = if (isPoseComplete) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                )
                Spacer(modifier = Modifier.height(4.dp))
                
                if (isCountingDown) {
                    Text(
                        "Get Ready! Position phone for ${poseNames[activeTargetPose]}...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        "$countdownSeconds",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else if (isRecordingActive) {
                    Text(
                        "Recording ${poseNames[activeTargetPose]}: $currentTrainingPoseSamples / $targetSampleLimit samples",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                } else if (isPoseComplete) {
                    Text(
                        "✓ Target reached for ${poseNames[activeTargetPose]} ($targetSampleLimit/$targetSampleLimit)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Text(
                        "Selected: ${poseNames[activeTargetPose]} ($currentTrainingPoseSamples / $targetSampleLimit samples)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        isCountingDown = true
                        countdownSeconds = 3
                        isRecordingActive = false
                    },
                    enabled = !isCountingDown && !isRecordingActive && currentTrainingPoseSamples < targetSampleLimit
                ) {
                    Text(
                        if (isCountingDown) "Get Ready..." 
                        else if (isRecordingActive) "Recording..." 
                        else "Start Recording ${poseNames[activeTargetPose]}"
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text("Select Pose to Train:", style = MaterialTheme.typography.labelLarge)
                
                poseNames.forEachIndexed { index, name ->
                    val samplesCount = trainingDataset[index]?.size ?: 0
                    val isTrainedLocally = samplesCount >= targetSampleLimit
                    
                    val statusText = if (isTrainedLocally) {
                        "✓ (50/50)"
                    } else {
                        "($samplesCount/$targetSampleLimit)"
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        RadioButton(
                            selected = activeTargetPose == index,
                            onClick = { 
                                activeTargetPose = index 
                                isCountingDown = false
                                isRecordingActive = false
                            }
                        )
                        Text("$name $statusText")
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(0.9f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            trainingDataset.remove(activeTargetPose)
                            DatasetPersister.saveTrainingDataset(context, trainingDataset)
                            isCountingDown = false
                            isRecordingActive = false
                            retrainFromDataset()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Reset Pose", maxLines = 1)
                    }

                    OutlinedButton(
                        onClick = {
                            trainingDataset.clear()
                            DatasetPersister.clearTrainingDataset(context)
                            isCountingDown = false
                            isRecordingActive = false
                            ModelManager.resetAll()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Reset All", maxLines = 1)
                    }
                }
            }

            AppScreenMode.CALIBRATION_EVAL -> {
                Text("Evaluation & Calibration Mode", style = MaterialTheme.typography.titleMedium)
                
                val isEvalComplete = currentEvalPoseSamples >= evalSampleLimit
                val progress = (currentEvalPoseSamples.toFloat() / evalSampleLimit).coerceIn(0f, 1f)

                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { if (isCountingDown) 0f else progress },
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(8.dp),
                    color = if (isEvalComplete) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.height(4.dp))

                if (isCountingDown) {
                    Text(
                        "Position phone for Calibration: ${poseNames[activeTargetPose]}...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        "$countdownSeconds",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else if (isRecordingActive) {
                    Text(
                        "Collecting Calibration Data for ${poseNames[activeTargetPose]}: $currentEvalPoseSamples / $evalSampleLimit",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                } else if (isEvalComplete) {
                    Text(
                        "✓ Calibration Complete for ${poseNames[activeTargetPose]} ($evalSampleLimit/$evalSampleLimit)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Text(
                        "Calibration Data: ${poseNames[activeTargetPose]} ($currentEvalPoseSamples / $evalSampleLimit samples)",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            isCountingDown = true
                            countdownSeconds = 3
                            isRecordingActive = false
                        },
                        enabled = !isCountingDown && !isRecordingActive && currentEvalPoseSamples < evalSampleLimit
                    ) {
                        Text("Record Calib ${poseNames[activeTargetPose]}")
                    }

                    Button(
                        onClick = { runEvaluation() },
                        enabled = evalDataset.isNotEmpty() && !isCountingDown && !isRecordingActive,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Text("Run Evaluation")
                    }
                }

                // Display Accuracy Benchmark Results
                if (localAccuracyResult != null && globalAccuracyResult != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Benchmark Results (All 6 Poses)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Local Model Accuracy: ${"%.1f".format(localAccuracyResult)}%", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                            Text("Global Model Accuracy: ${"%.1f".format(globalAccuracyResult)}%", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text("Calibration Target Pose:", style = MaterialTheme.typography.labelLarge)

                poseNames.forEachIndexed { index, name ->
                    val samplesCount = evalDataset[index]?.size ?: 0
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 1.dp)
                    ) {
                        RadioButton(
                            selected = activeTargetPose == index,
                            onClick = { 
                                activeTargetPose = index 
                                isCountingDown = false
                                isRecordingActive = false
                            }
                        )
                        Text("$name ${if (samplesCount >= evalSampleLimit) "✓ (10/10)" else "($samplesCount/$evalSampleLimit)"}")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        evalDataset.clear()
                        DatasetPersister.clearEvalDataset(context)
                        localAccuracyResult = null
                        globalAccuracyResult = null
                        isCountingDown = false
                        isRecordingActive = false
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Reset Calibration Data")
                }
            }
        }
    }
}

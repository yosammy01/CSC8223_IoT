package com.gsu.iot.pose.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.gsu.iot.pose.FeatureExtractor
import com.gsu.iot.pose.SensorService
import com.gsu.iot.pose.federated.MqttClientManager
import com.gsu.iot.pose.federated.Quantizer
import com.gsu.iot.pose.mlp.ModelManager
import com.gsu.iot.pose.ui.theme.FederatedPoseClientTheme
import kotlinx.coroutines.delay

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
        
        MqttClientManager.initialize(this, "ClientA") // Or "ClientB"
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

@Composable
fun PoseClientScreen() {
    var predictedPose by remember { mutableStateOf("Unknown") }
    var trainingSamplesCollected by remember { mutableStateOf(0) }
    var activeTargetPose by remember { mutableIntStateOf(0) } // 0-5
    var isTrainingMode by remember { mutableStateOf(false) }

    val poseNames = listOf(
        "Screen Up", "Screen Down", "Portrait Up", 
        "Portrait Down", "Landscape Left", "Landscape Right"
    )

    // Receive features from the extractor
    LaunchedEffect(Unit) {
        FeatureExtractor.onFeaturesExtracted = { features ->
            if (isTrainingMode) {
                ModelManager.trainBoth(features, activeTargetPose)
                trainingSamplesCollected++
                
                // Publish local weights to federated server occasionally
                if (trainingSamplesCollected % 10 == 0) {
                    val localWeights = ModelManager.federatedModel.getWeights()
                    val quantized = Quantizer.quantize(localWeights)
                    MqttClientManager.publishLocalWeights(quantized)
                }
            } else {
                val predictionIdx = ModelManager.predict(features)
                predictedPose = poseNames.getOrElse(predictionIdx) { "Unknown" }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Federated Pose Client", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        // Connection Status
        val isConnected = MqttClientManager.isConnected
        Text("MQTT: ${if (isConnected) "Connected" else "Disconnected"}",
            color = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)

        Spacer(modifier = Modifier.height(32.dp))

        // Live Inference View
        if (!isTrainingMode) {
            Text("Active Model: ${ModelManager.activeModelForInference.name}", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(16.dp))
            
            Text("Predicted Pose", style = MaterialTheme.typography.labelLarge)
            Text(predictedPose, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
            
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = { isTrainingMode = true }) {
                Text("Switch to Data Collection / Training")
            }
        } 
        // Training View
        else {
            Text("Training Mode", style = MaterialTheme.typography.titleMedium)
            Text("Samples Collected: $trainingSamplesCollected", style = MaterialTheme.typography.bodyLarge)
            
            Spacer(modifier = Modifier.height(16.dp))
            Text("Target Pose to Record:", style = MaterialTheme.typography.labelLarge)
            
            // Dropdown / Selection for Target Pose
            poseNames.forEachIndexed { index, name ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = activeTargetPose == index,
                        onClick = { activeTargetPose = index }
                    )
                    Text(name)
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { 
                isTrainingMode = false 
                trainingSamplesCollected = 0 // reset counter
            }) {
                Text("Stop Training & Switch to Inference")
            }
        }
    }
}
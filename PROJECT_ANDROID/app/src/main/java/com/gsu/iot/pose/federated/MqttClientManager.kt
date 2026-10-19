/**
 * ============================================================================
 * README - MqttClientManager.kt
 * ============================================================================
 * Role: Handles all bidirectional network communication with the Federated Server.
 * 
 * Functions:
 * - Maintains a persistent MQTT connection to the central Mosquitto Broker.
 * - Publishes local weights (federated/local/weights/{clientId}) for FedAvg aggregation.
 * - Subscribes to global weights (federated/global/weights) to receive updated models.
 * - Subscribes to backend commands (federated/commands) to intercept dashboard triggers
 *   like SET_MODEL, RESET_GLOBAL, and START_TRAINING (handling 8-bit vs 32-bit flags).
 * - Transmits live prediction telemetry and evaluation benchmark accuracy results.
 * ============================================================================
 */
package com.gsu.iot.pose.federated

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.gsu.iot.pose.mlp.ModelManager
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.MqttAsyncClient
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.json.JSONObject

object MqttClientManager {
    private const val TAG = "MqttClientManager"
    private const val DEFAULT_IP = ""
    private const val CLIENT_PREFIX = "EdgeClient_"

    private const val TOPIC_GLOBAL_WEIGHTS = "federated/global/weights"
    private const val TOPIC_LOCAL_WEIGHTS = "federated/local/weights"
    private const val TOPIC_COMMANDS = "federated/commands"
    private const val TOPIC_TELEMETRY = "federated/telemetry"

    private var mqttClient: MqttAsyncClient? = null
    private var activeClientId: String = "EdgeClient_A"
    private var activeClientIdSuffix: String = "A"
    private var isConnecting = false

    var useQuantization = true
    var onLocalModelReloaded: (() -> Unit)? = null
    var onStartTrainingCommand: (() -> Unit)? = null

    var serverIp by mutableStateOf(DEFAULT_IP)
        private set

    var isConnected by mutableStateOf(false)
        private set

    fun initialize(context: Context, clientIdSuffix: String = "A") {
        try {
            activeClientIdSuffix = clientIdSuffix
            val prefs = context.getSharedPreferences("MqttPrefs", Context.MODE_PRIVATE)
            val savedIp = prefs.getString("server_ip", DEFAULT_IP) ?: DEFAULT_IP
            serverIp = savedIp

            activeClientId = CLIENT_PREFIX + clientIdSuffix.filter { it.isLetterOrDigit() }

            if (savedIp.isNotBlank()) {
                val fullServerUri = "tcp://$savedIp:1883"

                if (mqttClient != null && mqttClient?.isConnected == true) {
                    try { mqttClient?.disconnect() } catch (e: Exception) {}
                }
                isConnected = false
                isConnecting = false
                mqttClient = MqttAsyncClient(fullServerUri, activeClientId, MemoryPersistence())

                mqttClient?.setCallback(object : MqttCallback {
                    override fun connectionLost(cause: Throwable?) {
                        Log.w(TAG, "Connection lost to MQTT Broker: ${cause?.message}")
                        isConnecting = false
                        isConnected = false
                    }

                    override fun messageArrived(topic: String?, message: MqttMessage?) {
                        if (topic == null || message == null) return
                        handleIncomingMessage(topic, message)
                    }

                    override fun deliveryComplete(token: IMqttDeliveryToken?) {
                    }
                })
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize MqttAsyncClient", e)
        }
    }

    fun updateServerIp(context: Context, newIp: String) {
        val cleanIp = newIp.trim()
        serverIp = cleanIp
        val prefs = context.getSharedPreferences("MqttPrefs", Context.MODE_PRIVATE)
        prefs.edit().putString("server_ip", cleanIp).apply()

        initialize(context, activeClientIdSuffix)
        if (cleanIp.isNotBlank()) {
            connect()
        }
    }

    fun connect() {
        val client = mqttClient
        if (client != null && !client.isConnected && serverIp.isNotBlank()) {
            isConnecting = true
            val options = MqttConnectOptions().apply {
                isAutomaticReconnect = true
                isCleanSession = true
                connectionTimeout = 10
                keepAliveInterval = 30
            }

            try {
                client.connect(options, null, object : IMqttActionListener {
                    override fun onSuccess(asyncActionToken: IMqttToken?) {
                        isConnecting = false
                        isConnected = true
                        Log.i(TAG, "Successfully connected to MQTT broker ($activeClientId @ $serverIp)")
                        subscribeToTopics()
                        publishTelemetry("Connected")
                    }

                    override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                        isConnecting = false
                        isConnected = false
                        Log.e(TAG, "Failed to connect to MQTT broker ($activeClientId @ $serverIp): ${exception?.message}", exception)
                    }
                })
            } catch (e: Exception) {
                isConnecting = false
                isConnected = false
                Log.e(TAG, "Exception during connect: ${e.message}", e)
            }
        }
    }

    fun disconnect() {
        val client = mqttClient ?: return
        if (client.isConnected) {
            try {
                client.disconnect()
                isConnected = false
                isConnecting = false
            } catch (e: Exception) {
                Log.e(TAG, "Exception during disconnect", e)
            }
        }
    }

    private fun subscribeToTopics() {
        val client = mqttClient ?: return
        try {
            client.subscribe(TOPIC_GLOBAL_WEIGHTS, 1)
            client.subscribe(TOPIC_COMMANDS, 1)
            
            // Subscribe to this specific client's local weights topic for server-backed local model reloads!
            val myLocalTopic = "$TOPIC_LOCAL_WEIGHTS/$activeClientId"
            client.subscribe(myLocalTopic, 1)
            
            Log.i(TAG, "Subscribed to global weights, commands, and $myLocalTopic")
        } catch (e: Exception) {
            Log.e(TAG, "Exception during subscribe", e)
        }
    }

    fun publishLocalWeights(weights: FloatArray) {
        val client = mqttClient
        if (client != null && client.isConnected) {
            try {
                val payload = if (useQuantization) {
                    Quantizer.quantize(weights)
                } else {
                    Quantizer.toRawByteArray(weights)
                }
                val message = MqttMessage(payload)
                message.qos = 1
                val perClientTopic = "$TOPIC_LOCAL_WEIGHTS/$activeClientId"
                client.publish(perClientTopic, message)
                Log.d(TAG, "Published local weights to $perClientTopic. Quantized: $useQuantization, Size: ${payload.size} bytes")
            } catch (e: Exception) {
                Log.e(TAG, "Exception publishing weights", e)
            }
        } else {
            Log.w(TAG, "Cannot publish weights, MQTT client is disconnected")
        }
    }

    fun publishEvaluation(localAcc: Float, globalAcc: Float) {
        val client = mqttClient
        if (client != null && client.isConnected) {
            try {
                val json = JSONObject().apply {
                    put("clientId", activeClientId)
                    put("localAccuracy", localAcc)
                    put("globalAccuracy", globalAcc)
                    put("timestamp", System.currentTimeMillis())
                }
                val message = MqttMessage(json.toString().toByteArray())
                message.qos = 0
                client.publish("federated/evaluation/$activeClientId", message)
            } catch (e: Exception) {
                Log.e(TAG, "Exception publishing evaluation", e)
            }
        }
    }

    fun publishTelemetry(poseName: String) {
        val client = mqttClient
        if (client != null && client.isConnected) {
            try {
                val json = JSONObject().apply {
                    put("clientId", activeClientId)
                    put("pose", poseName)
                    put("timestamp", System.currentTimeMillis())
                }
                val message = MqttMessage(json.toString().toByteArray())
                message.qos = 0
                val perClientTopic = "$TOPIC_TELEMETRY/$activeClientId"
                client.publish(perClientTopic, message)
            } catch (e: Exception) {
                Log.e(TAG, "Exception publishing telemetry", e)
            }
        }
    }

    private fun handleIncomingMessage(topic: String, message: MqttMessage) {
        val myLocalTopic = "$TOPIC_LOCAL_WEIGHTS/$activeClientId"
        when (topic) {
            TOPIC_GLOBAL_WEIGHTS -> {
                Log.d(TAG, "Received global weights update from broker")
                val payload = message.payload
                val floats = if (payload.size < 500) Quantizer.dequantize(payload) else Quantizer.fromRawByteArray(payload)
                ModelManager.federatedModel.setWeights(floats)
            }
            myLocalTopic -> {
                Log.d(TAG, "Received local model reload from broker for $activeClientId")
                val payload = message.payload
                val floats = if (payload.size < 500) Quantizer.dequantize(payload) else Quantizer.fromRawByteArray(payload)
                ModelManager.localModel.setWeights(floats)
                onLocalModelReloaded?.invoke()
            }
            TOPIC_COMMANDS -> {
                val payloadString = String(message.payload)
                Log.d(TAG, "Received command: $payloadString")
                try {
                    val json = JSONObject(payloadString)
                    val command = json.optString("command")
                    if (command == "SET_MODEL") {
                        val model = json.optString("active_model")
                        if (model == "GLOBAL") {
                            ModelManager.setActiveModel(ModelManager.ActiveModel.GLOBAL)
                        } else if (model == "LOCAL") {
                            ModelManager.setActiveModel(ModelManager.ActiveModel.LOCAL)
                        }
                    } else if (command == "RESET" || command == "RESET_ALL") {
                        ModelManager.resetAll()
                        Log.i(TAG, "Received RESET command from server/dashboard")
                    } else if (command == "RESET_GLOBAL") {
                        ModelManager.federatedModel.reset()
                        Log.i(TAG, "Received RESET_GLOBAL command from server/dashboard")
                    } else if (command == "START_TRAINING") {
                        useQuantization = json.optBoolean("use_quantization", true)
                        Log.d(TAG, "START_TRAINING command received. use_quantization=$useQuantization")
                        onStartTrainingCommand?.invoke()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse command payload", e)
                }
            }
        }
    }
}

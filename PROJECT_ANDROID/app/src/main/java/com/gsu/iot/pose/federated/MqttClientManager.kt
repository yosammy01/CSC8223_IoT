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
    private const val SERVER_URI = "tcp://192.168.1.237:1883"
    private const val CLIENT_PREFIX = "EdgeClient_"

    private const val TOPIC_GLOBAL_WEIGHTS = "federated/global/weights"
    private const val TOPIC_LOCAL_WEIGHTS = "federated/local/weights"
    private const val TOPIC_COMMANDS = "federated/commands"
    private const val TOPIC_TELEMETRY = "federated/telemetry"

    private var mqttClient: MqttAsyncClient? = null
    private var activeClientId: String = "EdgeClient_A"
    private var isConnecting = false

    var isConnected by mutableStateOf(false)
        private set

    fun initialize(context: Context, clientIdSuffix: String = "A") {
        try {
            activeClientId = CLIENT_PREFIX + clientIdSuffix.filter { it.isLetterOrDigit() }
            if (mqttClient != null && mqttClient?.isConnected == true) {
                try { mqttClient?.disconnect() } catch (e: Exception) {}
            }
            isConnected = false
            isConnecting = false
            mqttClient = MqttAsyncClient(SERVER_URI, activeClientId, MemoryPersistence())

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
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize MqttAsyncClient", e)
        }
    }

    fun connect() {
        val client = mqttClient ?: return
        if (!client.isConnected) {
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
                        Log.i(TAG, "Successfully connected to MQTT broker ($activeClientId)")
                        subscribeToTopics()
                        publishTelemetry("Connected")
                    }

                    override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                        isConnecting = false
                        isConnected = false
                        Log.e(TAG, "Failed to connect to MQTT broker ($activeClientId): ${exception?.message}", exception)
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
            Log.i(TAG, "Subscribed to global weights and command topics")
        } catch (e: Exception) {
            Log.e(TAG, "Exception during subscribe", e)
        }
    }

    fun publishLocalWeights(quantizedWeights: ByteArray) {
        val client = mqttClient
        if (client != null && client.isConnected) {
            try {
                val message = MqttMessage(quantizedWeights)
                message.qos = 1
                val perClientTopic = "$TOPIC_LOCAL_WEIGHTS/$activeClientId"
                client.publish(perClientTopic, message)
                Log.d(TAG, "Published quantized local weights to $perClientTopic. Size: ${quantizedWeights.size} bytes")
            } catch (e: Exception) {
                Log.e(TAG, "Exception publishing weights", e)
            }
        } else {
            Log.w(TAG, "Cannot publish weights, MQTT client is disconnected")
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
        when (topic) {
            TOPIC_GLOBAL_WEIGHTS -> {
                Log.d(TAG, "Received global weights update from broker")
                val quantizedWeights = message.payload
                val dequantizedFloatArray = Quantizer.dequantize(quantizedWeights)
                ModelManager.federatedModel.setWeights(dequantizedFloatArray)
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
                        Log.d(TAG, "START_TRAINING command received")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse command payload", e)
                }
            }
        }
    }
}

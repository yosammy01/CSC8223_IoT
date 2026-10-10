package com.gsu.iot.pose.federated

import android.content.Context
import android.util.Log
import com.gsu.iot.pose.mlp.ModelManager
import org.eclipse.paho.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.json.JSONObject

object MqttClientManager {
    private const val TAG = "MqttClientManager"
    private const val SERVER_URI = "tcp://10.0.2.2:1883"
    private const val CLIENT_ID = "AndroidEdgeClient" // Should be unique per device if testing multiple

    private const val TOPIC_GLOBAL_WEIGHTS = "federated/global/weights"
    private const val TOPIC_LOCAL_WEIGHTS = "federated/local/weights"
    private const val TOPIC_COMMANDS = "federated/commands"
    private const val TOPIC_TELEMETRY = "federated/telemetry"

    private lateinit var mqttClient: MqttAndroidClient

    var isConnected = false
        private set

    fun initialize(context: Context, clientIdSuffix: String = "1") {
        mqttClient = MqttAndroidClient(context.applicationContext, SERVER_URI, CLIENT_ID + clientIdSuffix)

        mqttClient.setCallback(object : MqttCallback {
            override fun connectionLost(cause: Throwable?) {
                isConnected = false
                Log.w(TAG, "Connection lost to MQTT Broker", cause)
            }

            override fun messageArrived(topic: String?, message: MqttMessage?) {
                if (topic == null || message == null) return
                handleIncomingMessage(topic, message)
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) {
                // Not needed for this implementation
            }
        })
    }

    fun connect() {
        if (::mqttClient.isInitialized && !mqttClient.isConnected) {
            val options = MqttConnectOptions().apply {
                isAutomaticReconnect = true
                isCleanSession = false
            }

            try {
                mqttClient.connect(options, null, object : IMqttActionListener {
                    override fun onSuccess(asyncActionToken: IMqttToken?) {
                        Log.i(TAG, "Successfully connected to MQTT broker")
                        isConnected = true
                        subscribeToTopics()
                    }

                    override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                        Log.e(TAG, "Failed to connect to MQTT broker", exception)
                        isConnected = false
                    }
                })
            } catch (e: Exception) {
                Log.e(TAG, "Exception during connect", e)
            }
        }
    }

    fun disconnect() {
        if (::mqttClient.isInitialized && mqttClient.isConnected) {
            try {
                mqttClient.disconnect()
                isConnected = false
            } catch (e: Exception) {
                Log.e(TAG, "Exception during disconnect", e)
            }
        }
    }

    private fun subscribeToTopics() {
        try {
            mqttClient.subscribe(TOPIC_GLOBAL_WEIGHTS, 1)
            mqttClient.subscribe(TOPIC_COMMANDS, 1)
            Log.i(TAG, "Subscribed to global weights and command topics")
        } catch (e: Exception) {
            Log.e(TAG, "Exception during subscribe", e)
        }
    }

    fun publishLocalWeights(quantizedWeights: ByteArray) {
        if (isConnected) {
            try {
                val message = MqttMessage(quantizedWeights)
                message.qos = 1
                mqttClient.publish(TOPIC_LOCAL_WEIGHTS, message)
                Log.d(TAG, "Published quantized local weights to broker. Size: ${quantizedWeights.size} bytes")
            } catch (e: Exception) {
                Log.e(TAG, "Exception publishing weights", e)
            }
        } else {
            Log.w(TAG, "Cannot publish weights, MQTT client is disconnected")
        }
    }

    fun publishTelemetry(poseName: String) {
        if (isConnected) {
            try {
                val json = JSONObject().apply {
                    put("clientId", CLIENT_ID)
                    put("pose", poseName)
                    put("timestamp", System.currentTimeMillis())
                }
                val message = MqttMessage(json.toString().toByteArray())
                message.qos = 0 // QoS 0 is fine for high-frequency live telemetry
                mqttClient.publish(TOPIC_TELEMETRY, message)
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
                    } else if (command == "START_TRAINING") {
                        // In a real app, this might reset models or switch pipelines
                        Log.d(TAG, "START_TRAINING command received")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse command payload", e)
                }
            }
        }
    }
}
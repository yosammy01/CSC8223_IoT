/**
 * ============================================================================
 * README - ModelManager.kt
 * ============================================================================
 * Role: State management for the machine learning models.
 * 
 * Functions:
 * - Holds two entirely separate instances of the MLP: `localModel` and `federatedModel`.
 * - Ensures `localModel` is only ever trained on the device's local sensor data.
 * - Ensures `federatedModel` is strictly updated via the server's aggregated weights.
 * - Provides predictions for both `localModel` and `federatedModel` simultaneously 
 *   for real-time side-by-side UI rendering on the Inference tab.
 * ============================================================================
 */
package com.gsu.iot.pose.mlp

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object ModelManager {
    /**
     * The locally trained Multi-Layer Perceptron neural network instance.
     */
    val localModel = MultiLayerPerceptron()

    /**
     * The global Multi-Layer Perceptron model instance updated via MQTT server weight aggregation.
     */
    val federatedModel = MultiLayerPerceptron()

    /**
     * Defines which model ([LOCAL] or [GLOBAL]) is used to output live predictions.
     */
    enum class ActiveModel {
        LOCAL, GLOBAL
    }

    var activeModelForInference by mutableStateOf(ActiveModel.LOCAL)
        private set

    /**
     * Sets the active model target for inference stream telemetry.
     */
    fun setActiveModel(model: ActiveModel) {
        activeModelForInference = model
    }

    /**
     * Performs a single-sample Stochastic Gradient Descent training step on [localModel].
     */
    fun trainLocal(features: FloatArray, label: Int) {
        localModel.train(features, label)
    }

    /**
     * Trains [localModel] on the entire local dataset across multiple epochs.
     */
    fun trainLocalEpochsOnDataset(dataset: Map<Int, List<FloatArray>>, epochs: Int = 20) {
        localModel.trainEpochs(dataset, epochs)
    }

    /**
     * Trains [federatedModel] on the entire local dataset across multiple epochs.
     */
    fun trainGlobalEpochsOnDataset(dataset: Map<Int, List<FloatArray>>, epochs: Int = 20) {
        federatedModel.trainEpochs(dataset, epochs)
    }

    /**
     * Resets both [localModel] and [federatedModel] parameters back to initial random weights.
     */
    fun resetAll() {
        localModel.reset()
        federatedModel.reset()
    }

    /**
     * Runs forward prediction on the currently selected active model for the given 24-feature vector.
     * @return The predicted pose class index (0..5).
     */
    fun predict(features: FloatArray): Int {
        return when (activeModelForInference) {
            ActiveModel.LOCAL -> localModel.predict(features)
            ActiveModel.GLOBAL -> federatedModel.predict(features)
        }
    }
}

package com.gsu.iot.pose.mlp

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object ModelManager {
    val localModel = MultiLayerPerceptron()
    val federatedModel = MultiLayerPerceptron()

    enum class ActiveModel {
        LOCAL, GLOBAL
    }

    var activeModelForInference by mutableStateOf(ActiveModel.LOCAL)
        private set

    fun setActiveModel(model: ActiveModel) {
        activeModelForInference = model
    }

    fun trainLocal(features: FloatArray, label: Int) {
        localModel.train(features, label)
    }

    fun trainLocalEpochsOnDataset(dataset: Map<Int, List<FloatArray>>, epochs: Int = 20) {
        localModel.trainEpochs(dataset, epochs)
    }

    fun resetAll() {
        localModel.reset()
        federatedModel.reset()
    }

    fun predict(features: FloatArray): Int {
        return when (activeModelForInference) {
            ActiveModel.LOCAL -> localModel.predict(features)
            ActiveModel.GLOBAL -> federatedModel.predict(features)
        }
    }
}

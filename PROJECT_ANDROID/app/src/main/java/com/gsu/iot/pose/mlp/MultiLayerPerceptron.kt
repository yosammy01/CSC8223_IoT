/**
 * ============================================================================
 * README - MultiLayerPerceptron.kt
 * ============================================================================
 * Role: The core Mathematical Neural Network Classifier.
 * 
 * Functions:
 * - A lightweight, natively written 2-layer Neural Network (8 -> 16 -> 6).
 * - Forward Pass: Sigmoid activation for hidden layer, Softmax for output layer.
 * - Backward Pass: Computes Cross-Entropy loss and updates 246 parameters 
 *   (W1, B1, W2, B2) using Stochastic Gradient Descent.
 * - `trainEpochs`: Supports batch training over multiple epochs (e.g., 20) for
 *   rapid and confident convergence on edge devices.
 * ============================================================================
 */
package com.gsu.iot.pose.mlp

import kotlin.math.exp
import kotlin.random.Random

class MultiLayerPerceptron(
    private val inputSize: Int = 8,
    private val hiddenSize: Int = 16,
    private val outputSize: Int = 6,
    private val learningRate: Float = 0.1f
) {
    // Weights
    var w1 = FloatArray(inputSize * hiddenSize) { Random.nextFloat() * 2f - 1f }
    var w2 = FloatArray(hiddenSize * outputSize) { Random.nextFloat() * 2f - 1f }

    // Biases
    var b1 = FloatArray(hiddenSize) { Random.nextFloat() * 2f - 1f }
    var b2 = FloatArray(outputSize) { Random.nextFloat() * 2f - 1f }

    fun reset() {
        w1 = FloatArray(inputSize * hiddenSize) { Random.nextFloat() * 2f - 1f }
        w2 = FloatArray(hiddenSize * outputSize) { Random.nextFloat() * 2f - 1f }
        b1 = FloatArray(hiddenSize) { Random.nextFloat() * 2f - 1f }
        b2 = FloatArray(outputSize) { Random.nextFloat() * 2f - 1f }
    }

    // Activation: Sigmoid for hidden, Softmax for output
    private fun sigmoid(x: Float): Float = (1.0 / (1.0 + exp(-x.toDouble()))).toFloat()
    private fun sigmoidDerivative(x: Float): Float = x * (1 - x)

    // Forward pass state
    private var hidden = FloatArray(hiddenSize)
    private var output = FloatArray(outputSize)

    fun forward(input: FloatArray): FloatArray {
        // Input to Hidden
        for (j in 0 until hiddenSize) {
            var sum = b1[j]
            for (i in 0 until inputSize) {
                sum += input[i] * w1[i * hiddenSize + j]
            }
            hidden[j] = sigmoid(sum)
        }

        // Hidden to Output
        for (j in 0 until outputSize) {
            var sum = b2[j]
            for (i in 0 until hiddenSize) {
                sum += hidden[i] * w2[i * outputSize + j]
            }
            output[j] = sum
        }
        
        // Softmax
        var maxOutput = output[0]
        for (j in 1 until outputSize) {
            if (output[j] > maxOutput) maxOutput = output[j]
        }
        
        var sumExp = 0f
        for (j in 0 until outputSize) {
            output[j] = exp((output[j] - maxOutput).toDouble()).toFloat()
            sumExp += output[j]
        }
        
        for (j in 0 until outputSize) {
            output[j] /= sumExp
        }

        return output
    }

    fun train(input: FloatArray, targetClass: Int) {
        forward(input)

        val outputErrors = FloatArray(outputSize)
        for (j in 0 until outputSize) {
            val target = if (j == targetClass) 1f else 0f
            // derivative of cross-entropy with softmax simplifies to (output - target)
            outputErrors[j] = output[j] - target 
        }

        val hiddenErrors = FloatArray(hiddenSize)
        for (i in 0 until hiddenSize) {
            var error = 0f
            for (j in 0 until outputSize) {
                error += outputErrors[j] * w2[i * outputSize + j]
            }
            hiddenErrors[i] = error * sigmoidDerivative(hidden[i])
        }

        // Update W2 and B2
        for (j in 0 until outputSize) {
            b2[j] -= learningRate * outputErrors[j]
            for (i in 0 until hiddenSize) {
                w2[i * outputSize + j] -= learningRate * outputErrors[j] * hidden[i]
            }
        }

        // Update W1 and B1
        for (j in 0 until hiddenSize) {
            b1[j] -= learningRate * hiddenErrors[j]
            for (i in 0 until inputSize) {
                w1[i * hiddenSize + j] -= learningRate * hiddenErrors[j] * input[i]
            }
        }
    }

    fun trainEpochs(dataset: Map<Int, List<FloatArray>>, epochs: Int = 15) {
        for (e in 0 until epochs) {
            dataset.forEach { (targetLabel, samples) ->
                samples.forEach { sample ->
                    train(sample, targetLabel)
                }
            }
        }
    }
    
    // Total weights size = (8*16) + (16*6) + 16 + 6 = 128 + 96 + 22 = 246 floats.
    fun getWeights(): FloatArray {
        val totalWeights = w1.size + w2.size + b1.size + b2.size
        val allWeights = FloatArray(totalWeights)
        var offset = 0
        w1.copyInto(allWeights, offset)
        offset += w1.size
        b1.copyInto(allWeights, offset)
        offset += b1.size
        w2.copyInto(allWeights, offset)
        offset += w2.size
        b2.copyInto(allWeights, offset)
        return allWeights
    }
    
    fun setWeights(allWeights: FloatArray) {
        if (allWeights.size < 246) return
        var offset = 0
        allWeights.copyInto(w1, 0, offset, offset + w1.size)
        offset += w1.size
        allWeights.copyInto(b1, 0, offset, offset + b1.size)
        offset += b1.size
        allWeights.copyInto(w2, 0, offset, offset + w2.size)
        offset += w2.size
        allWeights.copyInto(b2, 0, offset, offset + b2.size)
    }
    
    fun predict(input: FloatArray): Int {
        val probs = forward(input)
        var maxIdx = 0
        var maxProb = probs[0]
        for (i in 1 until probs.size) {
            if (probs[i] > maxProb) {
                maxProb = probs[i]
                maxIdx = i
            }
        }
        return maxIdx
    }
}

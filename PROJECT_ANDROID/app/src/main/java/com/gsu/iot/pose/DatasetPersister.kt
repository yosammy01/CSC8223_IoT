package com.gsu.iot.pose

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

object DatasetPersister {
    private const val EVAL_FILE_NAME = "eval_dataset.dat"
    private const val TRAIN_FILE_NAME = "train_dataset.dat"
    private const val TAG = "DatasetPersister"

    fun saveEvalDataset(context: Context, dataset: Map<Int, List<FloatArray>>) {
        saveDataset(context, dataset, EVAL_FILE_NAME)
    }

    fun loadEvalDataset(context: Context): Map<Int, MutableList<FloatArray>> {
        return loadDataset(context, EVAL_FILE_NAME)
    }

    fun clearEvalDataset(context: Context) {
        clearDataset(context, EVAL_FILE_NAME)
    }

    fun saveTrainingDataset(context: Context, dataset: Map<Int, List<FloatArray>>) {
        saveDataset(context, dataset, TRAIN_FILE_NAME)
    }

    fun loadTrainingDataset(context: Context): Map<Int, MutableList<FloatArray>> {
        return loadDataset(context, TRAIN_FILE_NAME)
    }

    fun clearTrainingDataset(context: Context) {
        clearDataset(context, TRAIN_FILE_NAME)
    }

    private fun saveDataset(context: Context, dataset: Map<Int, List<FloatArray>>, fileName: String) {
        try {
            val file = File(context.filesDir, fileName)
            val mapToSave = HashMap<Int, ArrayList<FloatArray>>()
            dataset.forEach { (key, value) ->
                mapToSave[key] = ArrayList(value)
            }
            ObjectOutputStream(FileOutputStream(file)).use { it.writeObject(mapToSave) }
            Log.d(TAG, "Saved $fileName with ${mapToSave.size} poses")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save $fileName", e)
        }
    }

    private fun loadDataset(context: Context, fileName: String): Map<Int, MutableList<FloatArray>> {
        val file = File(context.filesDir, fileName)
        if (!file.exists()) return emptyMap()
        
        return try {
            ObjectInputStream(FileInputStream(file)).use { ois ->
                @Suppress("UNCHECKED_CAST")
                val loadedMap = ois.readObject() as? HashMap<Int, ArrayList<FloatArray>>
                val resultMap = mutableMapOf<Int, MutableList<FloatArray>>()
                loadedMap?.forEach { (key, value) ->
                    // Only load features that match the new 24-feature architecture
                    val validFeatures = value.filter { it.size == 24 }
                    if (validFeatures.isNotEmpty()) {
                        resultMap[key] = validFeatures.toMutableList()
                    }
                }
                Log.d(TAG, "Loaded $fileName with ${resultMap.size} valid poses")
                resultMap
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load $fileName", e)
            emptyMap()
        }
    }

    private fun clearDataset(context: Context, fileName: String) {
        try {
            val file = File(context.filesDir, fileName)
            if (file.exists()) {
                file.delete()
                Log.d(TAG, "Cleared local $fileName file")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear $fileName", e)
        }
    }
}
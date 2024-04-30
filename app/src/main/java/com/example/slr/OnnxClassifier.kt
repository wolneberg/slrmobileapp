package com.example.slr

import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.media.MediaMetadataRetriever
import android.os.SystemClock
import android.util.Log
import java.util.Collections


class OnnxClassifier {
    fun detect(mmr: MediaMetadataRetriever, labels: List<String>, ortEnv: OrtEnvironment, ortSession: OrtSession): Pair<List<String>, Long> {
        // Step 1: convert video into byte array (raw image bytes)
        Log.d(TAG, "Starting classification")
        val frames = videoFrames(mmr)
        val tensorvideo = bitmapArrayToByteBuffer(frames, 224, 224)

        val inputTensor = OnnxTensor.createTensor(
            ortEnv,
            tensorvideo,
            longArrayOf(1,20,224,224,3),
            OnnxJavaType.FLOAT
        )
        inputTensor.use {
            // Step 3: call ort inferenceSession run
            val startTime = SystemClock.elapsedRealtime()
            val output = ortSession.run(
                Collections.singletonMap("input_1", inputTensor), setOf("prediction")
            )
            val inferenceTime = SystemClock.elapsedRealtime()-startTime
            // Step 4: output analysis
            output.use {
                val rawOutput = (output?.get(0)?.value) as Array<FloatArray>
                // Step 5: set output result
                val resultList = processOutput(rawOutput, labels)
                return Pair(resultList, inferenceTime)
            }
        }
    }

    private fun processOutput(output: Array<FloatArray>, labels: List<String>): List<String>{
        val listOfPredictions = output[0].toList()
        val resultList = listOfPredictions.zip(labels).sortedBy { it.first }.reversed()
        return resultList.map { (score, label) -> "$label: $score"}
    }

}
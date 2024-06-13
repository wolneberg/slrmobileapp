package com.example.slr

import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import android.media.MediaMetadataRetriever
import android.os.SystemClock
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Collections
import kotlin.math.roundToInt

/**
 * Inspired by TensorFlows tutorial for Android video classification with MoViNet stream
 * https://github.com/tensorflow/examples/tree/master/lite/examples/video_classification/android
 * and ONNX tutorial for object detection in an Android application
 * https://github.com/microsoft/onnxruntime-inference-examples/tree/main/mobile/examples/object_detection/android
 */
class OnnxClassifier {
    fun classifyVideo(mmr: MediaMetadataRetriever, labels: List<String>, ortEnv: OrtEnvironment, ortSession: OrtSession): Pair<List<String>, Long> {
        Log.d(TAG, "Starting classification")
        val frames = videoFrames(mmr)
        val tensorvideo = bitmapArrayToByteBuffer(frames, RESOLUTION, RESOLUTION)

        val inputTensor = OnnxTensor.createTensor(
            ortEnv,
            tensorvideo,
            longArrayOf(1, NUM_FRAMES.toLong(), RESOLUTION.toLong(), RESOLUTION.toLong(),3),
            OnnxJavaType.FLOAT
        )

        inputTensor.use {
            // Call ort inferenceSession run
            val startTime = SystemClock.elapsedRealtime()
            val output = ortSession.run(
                Collections.singletonMap("input_2", inputTensor), setOf("prediction")
            )
            val inferenceTime = SystemClock.elapsedRealtime()-startTime
            // Output analysis
            output.use {
                val rawOutput = (output?.get(0)?.value) as Array<FloatArray>
                // Set output result
                val resultList = processOutput(rawOutput, labels)
                return Pair(resultList, inferenceTime)
            }
        }
    }

    /**
     * Get the output from the prediction and refactor it to a list of strings with
     * the label and its score
     */
    private fun processOutput(output: Array<FloatArray>, labels: List<String>): List<String>{
        val listOfPredictions = output[0].toList()
        val resultList = listOfPredictions.zip(labels).sortedBy { it.first }.reversed()
        return resultList.map { (score, label) -> "$label: $score"}
    }

}
/**
 * Getting 20 evenly spread out frames from a video and return them as an array of Bitmaps
 */
fun videoFrames(mmr: MediaMetadataRetriever): Array<Bitmap> {
    var frames = emptyArray<Bitmap>()

    val duration = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
    val numberFrames = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)
    Log.d(TAG, "Video length: $duration ms")

    val frameStep: Int
    if (numberFrames!=null) {
        frameStep = (numberFrames.toDouble()/ NUM_FRAMES).roundToInt()
        for (i in 0 until NUM_FRAMES){
            var frame = i*frameStep
            if(frame >= numberFrames.toInt()){
                frame = numberFrames.toInt()-1
            }
            val bitmap = mmr.getFrameAtIndex(frame)
            if (bitmap!=null) {
                frames += bitmap
            } else{
                Log.d("No bitmap", "Found no bitmap at frame: $frame")
            }

        }
    }
    return frames
}

/**
 * Convert array of Bitmaps to a ByteBuffer
 * https://github.com/farmaker47/Segmentation_and_Style_Transfer/blob/master/app/src/main/java/com/soloupis/sample/ocr_keras/utils/ImageUtils.kt
 */
fun bitmapArrayToByteBuffer(
    bitmaps: Array<Bitmap>,
    width: Int,
    height: Int,
    mean: Float = 0.0f,
    std: Float = 255.0f
): ByteBuffer {
    val totalBytes = bitmaps.size * width * height * 3 * 4 // Check your case for 20 Bitmaps
    val inputImage = ByteBuffer.allocateDirect(totalBytes)
    inputImage.order(ByteOrder.nativeOrder())

    for (bitmap in bitmaps) {
        val scaledBitmap = scaleBitmapAndKeepRatio(bitmap, width, height)
        val intValues = IntArray(width * height)
        scaledBitmap.getPixels(intValues, 0, width, 0, 0, width, height)

        // Normalize and add pixels for each Bitmap
        for (y in 0 until height) {
            for (x in 0 until width) {
                val value = intValues[y * width + x]
                inputImage.putFloat(((value shr 16 and 0xFF) - mean) / std)
                inputImage.putFloat(((value shr 8 and 0xFF) - mean) / std)
                inputImage.putFloat(((value and 0xFF) - mean) / std)
            }
        }
        scaledBitmap.recycle()  // Free memory after processing
        bitmap.recycle()
    }

    inputImage.rewind()
    return inputImage
}

/**
 * Scale Bitmap to given ratio while keeping ratio of original Bitmap
 * https://github.com/farmaker47/Segmentation_and_Style_Transfer/blob/master/app/src/main/java/com/soloupis/sample/ocr_keras/utils/ImageUtils.kt
 */
fun scaleBitmapAndKeepRatio(
    targetBmp: Bitmap,
    reqHeightInPixels: Int,
    reqWidthInPixels: Int
): Bitmap {
    if (targetBmp.height == reqHeightInPixels && targetBmp.width == reqWidthInPixels) {
        return targetBmp
    }
    val matrix = Matrix()
    matrix.setRectToRect(
        RectF(
            0f, 0f,
            targetBmp.width.toFloat(),
            targetBmp.width.toFloat()
        ),
        RectF(
            0f, 0f,
            reqWidthInPixels.toFloat(),
            reqHeightInPixels.toFloat()
        ),
        Matrix.ScaleToFit.FILL
    )
    return Bitmap.createBitmap(
        targetBmp, 0, 0,
        targetBmp.width,
        targetBmp.width, matrix, true
    )
}
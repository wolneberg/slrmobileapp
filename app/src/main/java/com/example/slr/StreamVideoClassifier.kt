package com.example.slr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import android.media.MediaMetadataRetriever
import android.os.SystemClock
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.label.Category
import java.math.RoundingMode
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp
import kotlin.math.max

/*
 * Copyright 2022 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

class StreamVideoClassifier private constructor(
     private val interpreter: Interpreter,
     private val labels: List<String>,
     private val maxResults: Int?
) {
    private val outputCategoryCount = interpreter
        .getOutputTensorFromSignature(LOGITS_OUTPUT_NAME, SIGNATURE_KEY)
        .shape()[1]
    private var inputState = HashMap<String, Any>()

    companion object {
        private const val IMAGE_INPUT_NAME = "image"
        private const val LOGITS_OUTPUT_NAME = "logits"
        private const val SIGNATURE_KEY = "serving_default"

        fun createFromFileAndLabelsAndOptions(
            context: Context,
            modelFile: String,
            labelFile: String,
            options: StreamVideoClassifierOptions
        ): StreamVideoClassifier {
            // Create a TFLite interpreter from the TFLite model file.
            val interpreter = Interpreter(FileUtil.loadMappedFile(context, modelFile))

            // Load the label file.
            val labels = FileUtil.loadLabels(context, labelFile)

            // Save the max result option.
            val maxResults = if (options.maxResults > 0 && options.maxResults <= labels.size)
                options.maxResults else null

            return StreamVideoClassifier(interpreter, labels, maxResults)
        }
    }

    init {
        if (outputCategoryCount != labels.size)
            throw java.lang.IllegalArgumentException(
                "Label list size doesn't match with model output shape " +
                        "(${labels.size} != $outputCategoryCount"
            )
        inputState = initializeInput()
    }

    /**
     * Initialize the input objects and fill them with zeros.
     */
    private fun initializeInput(): HashMap<String, Any> {
        val inputs = HashMap<String, Any>()
        for (inputName in interpreter.getSignatureInputs(SIGNATURE_KEY)) {
            // Skip the input image tensor as it'll be fed in later.
            if (inputName.equals(IMAGE_INPUT_NAME))
                continue

            // Initialize a ByteBuffer filled with zeros as an initial input of the TFLite model.
            val tensor = interpreter.getInputTensorFromSignature(inputName, SIGNATURE_KEY)
            val byteBuffer = ByteBuffer.allocateDirect(tensor.numBytes())
            byteBuffer.order(ByteOrder.nativeOrder())
            inputs[inputName] = byteBuffer
        }

        return inputs
    }

    /**
     * Initialize the output objects to store the TFLite model outputs.
     */
    private fun initializeOutput(): HashMap<String, Any> {
        val outputs = HashMap<String, Any>()
        for (outputName in interpreter.getSignatureOutputs(SIGNATURE_KEY)) {
            // Initialize a ByteBuffer to store the output of the TFLite model.
            val tensor = interpreter.getOutputTensorFromSignature(outputName, SIGNATURE_KEY)
            val byteBuffer = ByteBuffer.allocateDirect(tensor.numBytes())
            byteBuffer.order(ByteOrder.nativeOrder())
            outputs[outputName] = byteBuffer
        }

        return outputs
    }

    /**
     * Run classify on a video and return a list include action and score.
     */
    fun classifyVideo(mmr: MediaMetadataRetriever): Pair<List<Category>, Long>{
        val frames = videoFrames(mmr)
        val tensorvideo = bitmapArrayToByteBuffer(frames, 172, 172)
        inputState[IMAGE_INPUT_NAME] = tensorvideo

        // Initialize a placeholder to store the output objects.
        val outputs = initializeOutput()

        // Run inference using the TFLite model.
        val startTime = SystemClock.elapsedRealtime()
        interpreter.runSignature(inputState, outputs)
        val inferenceTime = SystemClock.elapsedRealtime()-startTime
        // Post-process the outputs.
        var categories = postprocessOutputLogits(outputs[LOGITS_OUTPUT_NAME] as ByteBuffer)

        // Store the output states to feed as input for the next frame.
        outputs.remove(LOGITS_OUTPUT_NAME)
        inputState = outputs

        // Sort the output and return only the top K results.
        categories.sortByDescending { it.score }

        // Take only maxResults number of result.
        maxResults?.let {
            categories = categories.subList(0, max(maxResults, categories.size))
        }
        return Pair(categories, inferenceTime)
    }

    /**
     * Convert output logits of the model to a list of Category objects.
     */
    private fun postprocessOutputLogits(logitsByteBuffer: ByteBuffer): MutableList<Category> {
        // Convert ByteBuffer to FloatArray.
        val logits = FloatArray(outputCategoryCount)
        logitsByteBuffer.rewind()
        logitsByteBuffer.asFloatBuffer().get(logits)

        // Convert logits into probability list.
        val probabilities = softmax(logits)

        // Append label name to form a list of Category objects.
        val categories = mutableListOf<Category>()
        probabilities.forEachIndexed { index, probability ->
            categories.add(Category(labels[index], probability))
        }
        return categories
    }

    /**
     * Close the interpreter when it's no longer needed.
     */
    fun close() {
        interpreter.close()
    }

    class StreamVideoClassifierOptions private constructor(
        val maxResults: Int
    ) {
        companion object {
            fun builder() = Builder()
        }

        class Builder {
            private var numThreads: Int = -1
            private var maxResult: Int = -1

            fun setNumThreads(numThreads: Int): Builder {
                this.numThreads = numThreads
                return this
            }

            fun setMaxResult(maxResults: Int): Builder {
                if ((maxResults <= 0) && (maxResults != -1)) {
                    throw IllegalArgumentException("maxResults must be positive or -1.")
                }
                this.maxResult = maxResults
                return this
            }

            fun build(): StreamVideoClassifierOptions {
                return StreamVideoClassifierOptions(this.maxResult)
            }
        }
    }
}

fun softmax(floatArray: FloatArray): FloatArray {
    var total = 0f
    val result = FloatArray(floatArray.size)
    for (i in floatArray.indices) {
        result[i] = exp(floatArray[i])
        total += result[i]
    }

    for (i in result.indices) {
        result[i] /= total
    }
    return result
}

const val NUM_FRAMES = 20

fun videoFrames(mmr: MediaMetadataRetriever): Array<Bitmap> {
    var frames = emptyArray<Bitmap>()
    var durationMs = 0.0

    val duration = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
    if (duration !=null) {
        durationMs = duration.toDouble()
    }

    val frameStep = (durationMs/ NUM_FRAMES).toBigDecimal().setScale(2, RoundingMode.DOWN).toDouble()
    for (i in 0 until NUM_FRAMES){
        val timeUs = (i*frameStep)
        val bitmap = mmr.getFrameAtTime(timeUs.toLong())
        if (bitmap!=null) {
            frames += bitmap
        } else{
            Log.d("No bitmap", "Found no bitmap at frame: $timeUs")
        }

    }
    return frames
}

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
    }

    inputImage.rewind()
    return inputImage
}

// https://github.com/farmaker47/Segmentation_and_Style_Transfer/blob/master/app/src/main/java/com/soloupis/sample/ocr_keras/utils/ImageUtils.kt
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
package com.example.slr


import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import android.media.MediaMetadataRetriever
import android.os.SystemClock
import android.util.Log
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.IOException
import java.math.RoundingMode
import java.nio.ByteBuffer
import java.nio.ByteOrder

const val RESOLUTION = 172
const val BATCH_SIZE = 1
const val CHANNELS = 3
const val NUM_FRAMES = 20
fun predict(context: Context, mmr: MediaMetadataRetriever): Pair<FloatArray, Long> {
    val frames = videoFrames(mmr)
    val inputbuffer = bitmapArrayToByteBuffer(frames, RESOLUTION, RESOLUTION)
    // Define the shape and data type of the TensorBuffer
    val shape = intArrayOf(BATCH_SIZE, frames.size, RESOLUTION, RESOLUTION, CHANNELS)
    // Create an empty TensorBuffer with the desired shape and data type
    val tensorBuffer = TensorBuffer.createFixedSize(shape, DataType.FLOAT32)
    val floatinput = bitmapArrayToFloatArray(frames, RESOLUTION, RESOLUTION)
    tensorBuffer.loadArray(floatinput)
    Log.i("buffer size", inputbuffer.array().size.toString())
    Log.i("float size", floatinput.size.toString())
    // Calculate the size of a single slice based on the shape of the TensorBuffer
//    val sliceSize = tensorBuffer.buffer.limit() / tensorBuffer.shape[1]
//    // Iterate over each ByteBuffer in the list and load it into the appropriate slice of the TensorBuffer
//    for (i in frames.indices) {
//        val byteBuffer = frames[i]
//        // Calculate the offset for the current slice
//        val offset = i * sliceSize
//        // Copy the contents of the ByteBuffer to the appropriate slice of the TensorBuffer
//        byteBuffer.position(0)
//        tensorBuffer.buffer.position(offset)
//        tensorBuffer.buffer.put(byteBuffer)
//    }

    val output = TensorBuffer.createFixedSize(intArrayOf(1, 100), DataType.FLOAT32)
    var inferenceTime = 0.toLong()

    try {
        val tfliteModel = FileUtil.loadMappedFile(context, "movinet_a1_base_04052024_091340.tflite")
        val tflite = Interpreter(tfliteModel)
//        Log.i("signature inputs",tflite.getSignatureInputs("image").toString())
        Log.i("input 0", tflite.getInputTensor(0).shape().joinToString(separator=","))
        Log.i("output", tflite.getOutputTensor(0).shape().joinToString(separator = ","))
        Log.i("input 0", tflite.getInputTensor(0).dataType().toString())
        // Running inference
        val startTime = SystemClock.elapsedRealtime()
//        tflite.run(inputbuffer, output.buffer); // må finne rikitg input her
        inferenceTime = SystemClock.elapsedRealtime()-startTime
        tflite.close()
    } catch (e: IOException) {
        Log.e("tfliteSupport", "Error reading model", e)
    }

    return Pair(output.floatArray, inferenceTime)
}

private fun videoFrames(mmr: MediaMetadataRetriever): Array<Bitmap> {
    var frames = emptyArray<Bitmap>()
    var durationMs = 0.0

    val duration = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
    if (duration !=null) {
        durationMs = duration.toDouble()
    }

    val frameStep = (durationMs/ NUM_FRAMES).toBigDecimal().setScale(2, RoundingMode.DOWN).toDouble()
    for (i in 0 until NUM_FRAMES){
        val timeUs = (i*frameStep)
        var bitmap = mmr.getFrameAtTime(timeUs.toLong())
        if (bitmap!=null) {
            bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
            val resized = Bitmap.createScaledBitmap(bitmap, RESOLUTION, RESOLUTION,false)
//            val inputImage = bitmapToByteBuffer(resized, RESOLUTION, RESOLUTION)
            frames += resized
            Log.i("Bitmap", "Registered bitmap frame at $timeUs")
        } else{
            Log.i("No bitmap", "Found no bitmap at frame: $timeUs")
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

fun bitmapArrayToFloatArray(
    bitmaps: Array<Bitmap>,
    width: Int,
    height: Int,
    mean: Float = 0.0f,
    std: Float = 255.0f
): FloatArray {
    val totalPixels = bitmaps.size * width * height * 3
    val floatArray = FloatArray(totalPixels)

    var index = 0

    for (bitmap in bitmaps) {
        if (!bitmap.isRecycled) {
            val scaledBitmap = scaleBitmapAndKeepRatio(bitmap, width, height)
            val intValues = IntArray(width * height)
            scaledBitmap.getPixels(intValues, 0, width, 0, 0, width, height)

            // Normalize and add pixels for each Bitmap
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val value = intValues[y * width + x]
                    floatArray[index++] = ((value shr 16 and 0xFF) - mean) / std
                    floatArray[index++] = ((value shr 8 and 0xFF) - mean) / std
                    floatArray[index++] = ((value and 0xFF) - mean) / std
                }
            }

            scaledBitmap.recycle()  // Free memory after processing
        }
    }

    return floatArray
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
fun bitmapToByteBuffer(
    bitmapIn: Bitmap,
    width: Int,
    height: Int,
    mean: Float = 0.0f,
    std: Float = 255.0f
): ByteBuffer {
    val bitmap = scaleBitmapAndKeepRatio(bitmapIn, width, height)
    val inputImage = ByteBuffer.allocateDirect(1 * width * height * 3 * 4)
    inputImage.order(ByteOrder.nativeOrder())
    inputImage.rewind()

    val intValues = IntArray(width * height)
    bitmap.getPixels(intValues, 0, width, 0, 0, width, height)
    var pixel = 0
    for (y in 0 until height) {
        for (x in 0 until width) {
            val value = intValues[pixel++]
            // Normalize channel values to [-1.0, 1.0]. This requirement varies by
            // model. For example, some models might require values to be normalized
            // to the range [0.0, 1.0] instead.
            inputImage.putFloat(((value shr 16 and 0xFF) - mean) / std)
            inputImage.putFloat(((value shr 8 and 0xFF) - mean) / std)
            inputImage.putFloat(((value and 0xFF) - mean) / std)
        }
    }

    inputImage.rewind()
    return inputImage
}

//    val bytebuffer = ByteBuffer.allocate(frames.limit()+4*100)
//    bytebuffer.putFloat(BATCH_SIZE)
//    bytebuffer.put(frames) // how?
//    bytebuffer.putFloat(RESOLUTION.toFloat())
//    bytebuffer.putFloat(RESOLUTION.toFloat())
//    bytebuffer.putFloat(THREE)

//    val input: Any = listOf(BATCH_SIZE,frames, RESOLUTION, RESOLUTION, THREE)
//    val input = TensorBuffer.createFixedSize(intArrayOf(1,5), DataType.FLOAT32)
//    input.loadBuffer(bytebuffer)

//    val imageProcessor = ImageProcessor.Builder()
//        .add(ResizeOp(RESOLUTION, RESOLUTION, ResizeOp.ResizeMethod.BILINEAR))
//        .build()
//    var tensorImage = TensorImage(DataType.FLOAT32)

//    val durationInSec = ceil(durationMs/1000).toInt()
//    Log.i("Duration", durationInSec.toString())
//    for (i in 0 until fps*durationInSec){
//        val timeUs = (i*durationMs/(fps*durationInSec)).toInt()
//        var bitmap = mmr.getFrameAtTime(timeUs.toLong())
//
//        if (bitmap!=null) {
//            bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
//            val resized = Bitmap.createScaledBitmap(bitmap, 172,172,false)
////            tensorImage.load(bitmap)
////            tensorImage = imageProcessor.process(tensorImage)
//            val inputImage = bitmapToByteBuffer(resized, RESOLUTION, RESOLUTION)
//            frames.add(inputImage)
//            Log.i("Bitmap", "Registered bitmap frame at $timeUs")
//        } else{
//            Log.i("No bitmap", "Found no bitmap at frame: $timeUs")
//        }
//    }


// Concatenate all ByteBuffer frames into a single ByteBuffer
//    val concatenatedByteBuffer = ByteBuffer.allocate(frames.size * frames[0].capacity())
//    frames.forEach { tensorImage ->
//        concatenatedByteBuffer.put(tensorImage)
//    }
//    concatenatedByteBuffer.flip() // Set the position to 0 before returning
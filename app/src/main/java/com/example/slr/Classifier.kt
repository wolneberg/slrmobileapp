package com.example.slr


import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.SystemClock
import android.util.Log
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.IOException
import kotlin.math.ceil


fun predict(context: Context, mmr: MediaMetadataRetriever): Pair<FloatArray, Long> {

    val frames = videoFrames(mmr)

    val output = TensorBuffer.createFixedSize(intArrayOf(1, 100), DataType.FLOAT32)
    var inferenceTime = 0.toLong()
    // Option 1: One way to use the model, but unsure how to create the input
//    val model = MovinetA1Base04052024091340.newInstance(context)
//    val input = TensorBuffer.createFixedSize(intArrayOf(1,4), DataType.FLOAT32) // not sure about this
    // Option 2: interpreter
    try {
        val tfliteModel = FileUtil.loadMappedFile(context, "movinet_a1_base_04052024_091340.tflite")
        val tflite = Interpreter(tfliteModel)
        Log.i("signature keys",tflite.signatureKeys.toString())
        // Running inference
        val startTime = SystemClock.elapsedRealtime()
//        tflite.run(frames, output.buffer); // må finne rikitg input her
        inferenceTime = SystemClock.elapsedRealtime()-startTime
    } catch (e: IOException) {
        Log.e("tfliteSupport", "Error reading model", e)
    }

    return Pair(output.floatArray, inferenceTime)
}


private fun videoFrames(mmr: MediaMetadataRetriever): List<TensorImage> {
    val frames = mutableListOf<TensorImage>()
    val fps = 2
    var durationMs = 0.0

    val imageProcessor = ImageProcessor.Builder()
        .add(ResizeOp(172, 172, ResizeOp.ResizeMethod.BILINEAR))
        .build()
    var tensorImage = TensorImage(DataType.FLOAT32)

    val duration = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
    if (duration !=null) {
        durationMs = duration.toDouble()
    }
    val durationInSec = ceil(durationMs/1000).toInt()
    Log.i("Duration", durationInSec.toString())

    for (i in 0 until fps*durationInSec){
        val timeUs = (i*durationMs/(fps*durationInSec)).toInt()
        var bitmap = mmr.getFrameAtTime(timeUs.toLong())

        if (bitmap!=null) {
            bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
//            val resized = Bitmap.createScaledBitmap(bitmap, 172,172,false)
            tensorImage.load(bitmap)
            tensorImage = imageProcessor.process(tensorImage)
            frames.add(tensorImage)
            Log.i("Bitmap", "Registered bitmap frame at $timeUs")
        } else{
            Log.i("No bitmap", "Found no bitmap at frame: $timeUs")
        }

    }
    return frames
}
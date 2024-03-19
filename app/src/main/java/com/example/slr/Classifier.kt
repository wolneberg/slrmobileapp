package com.example.slr


import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.SystemClock
import android.util.Log

fun predict(context: Context, mmr: MediaMetadataRetriever): Pair<List<Int>, Long> {
    //val model = Model.newInstance(context) // funket ikke:((
    val startTime = SystemClock.elapsedRealtime()


    val frames = videoFrames(mmr)

    val inferenceTime = SystemClock.elapsedRealtime()-startTime
    return Pair(emptyList(), inferenceTime)
}

private fun videoFrames(mmr: MediaMetadataRetriever): List<Bitmap> {
    val frames = mutableListOf<Bitmap>()
    val duration = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
    val durationMs = duration?.toDouble()
    Log.i("Duration", duration?.toString() ?: "no duration")
    return emptyList()
}
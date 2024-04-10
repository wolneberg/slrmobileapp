package com.example.slr.domain

import android.graphics.Bitmap

interface StreamClassifier {
    fun classifyFrame(bitmap: Bitmap, rotation: Int): List<Classification>
}
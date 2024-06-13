package com.example.slr

import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Code from Camera X tutorial:
 * https://github.com/philipplackner/CameraXGuide/tree/recording-videos
 */
@Composable
fun CameraPreview(
    controller: LifecycleCameraController,
    modifier: Modifier = Modifier,
){
    val lifecycleOwner = LocalLifecycleOwner.current
    AndroidView(
        factory = {
            PreviewView(it).apply {
                this.scaleType = scaleType
                this.controller = controller
                controller.bindToLifecycle(lifecycleOwner)
            }
        },
        modifier = modifier
    )
}
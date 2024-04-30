package com.example.slr

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.camera.core.CameraSelector
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Recording
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.video.AudioConfig
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.tensorflow.lite.support.label.Category
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale


class RecordActivity: ComponentActivity(){

    companion object {
        private val CAMERAX_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
    }

    private var videoClassifier: StreamVideoClassifier? = null
    private var numThread = 1
    private var recording: Recording? = null

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if(!hasRequiredPermissions()){
            ActivityCompat.requestPermissions(this, CAMERAX_PERMISSIONS,0)
        }
        setContent {
            val scaffoldState = rememberBottomSheetScaffoldState()
            val controller = remember {
                LifecycleCameraController(applicationContext).apply {
                    setEnabledUseCases(
                        CameraController.VIDEO_CAPTURE
                    )
                }
            }
            val recordingStart : MutableState<Boolean> = remember { mutableStateOf(false) }
            val results: MutableState<Pair<List<Category>, Long>?> = remember{mutableStateOf(null) }
            val processTime: MutableState<Long?> = remember{ mutableStateOf(null) }
            val mmr = MediaMetadataRetriever()
            BottomSheetScaffold(
                scaffoldState = scaffoldState,
                sheetPeekHeight = 0.dp,
                sheetContent = {
                    GalleryBottomView(
                        bitmaps = emptyList(),
                        modifier = Modifier.fillMaxWidth())
                }) {padding ->
                Box(modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)){
                    CameraPreview(controller = controller,
                        modifier = Modifier
                            .align(Alignment.TopCenter).fillMaxSize()
                    )
                    IconButton(
                        onClick = {
                            controller.cameraSelector =
                                if (controller.cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
                                    CameraSelector.DEFAULT_FRONT_CAMERA
                                } else CameraSelector.DEFAULT_BACK_CAMERA
                        },
                        modifier = Modifier.offset(16.dp, 16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cameraswitch,
                            contentDescription = "Switch camera"
                        )
                    }
                    if (recordingStart.value){
                        Icon(imageVector = Icons.Default.FiberManualRecord,
                            contentDescription = "",
                            tint = Color.Red,
                            modifier = Modifier
                                .offset((-16).dp, 16.dp)
                                .align(Alignment.TopEnd))
                    }
                    Log.d(TAG, results.value?.second.toString())
                    Column (
                        modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).background(Color.White),
                        verticalArrangement = Arrangement.Bottom,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (results.value != null) {
                            Text(text = "Label: "+ results.value?.first?.get(0)?.label +
                                    ", Score: "+results.value?.first?.get(0)?.score,
                                fontWeight = FontWeight.ExtraBold)
                            Text(text = "Label: "+ results.value?.first?.get(1)?.label +
                                    ", Score: "+results.value?.first?.get(1)?.score)
                            Text(text = "Label: "+ results.value?.first?.get(2)?.label +
                                    ", Score: "+results.value?.first?.get(2)?.score)
                            Text(text = "Label: "+ results.value?.first?.get(3)?.label +
                                    ", Score: "+results.value?.first?.get(3)?.score)
                            Text(text = "Label: "+ results.value?.first?.get(4)?.label +
                                    ", Score: "+results.value?.first?.get(4)?.score)
                            Text(text = "Inference time: ${results.value?.second} ms")
                            Text(text = "Process and inference time: ${processTime.value} ms")
                        }
                        FilledTonalButton(
                            onClick = {
                                recordVideo(controller, recordingStart, this@RecordActivity, mmr, results, processTime) }
                        ) {
                            Text(text =
                            if (recordingStart.value) "Stop Recording" else "Start Recording")
                        }
                    }
                    IconButton(
                        onClick = { goBack() },
                        modifier = Modifier
                            .offset(16.dp, 0.dp)
                            .align(Alignment.BottomStart)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Go back"
                        )
                    }
                }
            }
        }
    }

    private fun recordVideo(controller: LifecycleCameraController, recordingStart: MutableState<Boolean>,
                            context: Context, mmr: MediaMetadataRetriever,
                            results: MutableState<Pair<List<Category>,Long>?>, processTime: MutableState<Long?>){
        if(recording != null) {
            recordingStart.value = false
            recording?.stop()
            recording = null
            return
        }

        if(!hasRequiredPermissions()) {
            return
        }
        recordingStart.value = true

        // File options
        val outputFile = File(filesDir, "my-recording.mp4")
        val fileOutputoptions = FileOutputOptions.Builder(outputFile).build()

        // mediastore options
        val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US)
            .format(System.currentTimeMillis())

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/SLR-videos")
            }
        }

        // MediaStore output options for saving to the androids device photo gallery
        val mediaStoreOutputOptions = MediaStoreOutputOptions
            .Builder(contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(contentValues)
            .build()

        recording = controller.startRecording(
            mediaStoreOutputOptions,
            AudioConfig.AUDIO_DISABLED,
            ContextCompat.getMainExecutor(applicationContext),
        ){ event ->
            when(event) {
                is VideoRecordEvent.Finalize -> {
                    if(event.hasError()) {
                        recording?.close()
                        recording = null
                        val msg = "Video capture failed: " + "${event.error}"
                        Toast.makeText(
                            applicationContext,
                            msg,
                            Toast.LENGTH_LONG
                        ).show()
                        Log.e(TAG, msg)
                    } else {
                        val msg = "Video capture succeeded: " + "${event.outputResults.outputUri}"
                        val startTime = SystemClock.elapsedRealtime()
                        mmr.setDataSource(context, event.outputResults.outputUri)
                        results.value = videoClassifier?.classifyVideo(mmr)
                        processTime.value = SystemClock.elapsedRealtime() - startTime
                        Log.d(TAG, "Finished classifying video")
                        Toast.makeText(
                            applicationContext,
                            msg,
                            Toast.LENGTH_LONG
                        ).show()
                        Log.i(TAG, msg)
                    }
                }
            }
        }
    }
    private fun goBack(){
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
    }
    private fun hasRequiredPermissions(): Boolean{
        return CAMERAX_PERMISSIONS.all { ContextCompat.checkSelfPermission(
            applicationContext, it
        ) == PackageManager.PERMISSION_GRANTED }
    }
}

package com.example.slr

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.extensions.OrtxPackage
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight

/**
 * Inspired by TensorFlows tutorial for Android video classification with MoViNet stream
 * https://github.com/tensorflow/examples/tree/master/lite/examples/video_classification/android
 * Adjusted for using ONNX instead of TensorFlow
 */
const val TAG = "Onnx-VidClassify"
const val NUM_FRAMES = 20
const val RESOLUTION = 224
class MainActivity: ComponentActivity(){

    private var videoClassifier: OnnxClassifier? = null
    private var ortEnv: OrtEnvironment = OrtEnvironment.getEnvironment()
    private lateinit var ortSession: OrtSession
    private val mmr = MediaMetadataRetriever()
    private var sessionOptions: OrtSession.SessionOptions? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val result = remember { mutableStateOf<Uri?>(null) }
            val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) {
                result.value = it
            }
            // Initialize Ort Session and register the onnxruntime extensions package that contains the custom operators.
            // Note: These are used to decode the input image into the format the original model requires,
            // and to encode the model output into png format
            sessionOptions = OrtSession.SessionOptions()
            sessionOptions?.registerCustomOpLibrary(OrtxPackage.getLibraryPath())
            ortSession = ortEnv.createSession(readModel(), sessionOptions)
            videoClassifier = OnnxClassifier()
            Column(
                modifier = Modifier.fillMaxHeight(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ){
                Row (
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    FilledTonalButton(
                        onClick = {
                            launcher.launch(PickVisualMediaRequest(
                                mediaType = ActivityResultContracts.PickVisualMedia.VideoOnly))
                        }
                    ) {
                        Text("Pick video from gallery")
                    }
                }
                Row (
                    horizontalArrangement = Arrangement.Center
                ){
                    FilledTonalButton(
                        onClick = {
                            goToRecord()
                        }
                    ) {
                        Text("Record new video")
                    }
                }
                if (result.value != null) {
                    val startTime = SystemClock.elapsedRealtime()
                    mmr.setDataSource(this@MainActivity, result.value)
                    val results = videoClassifier?.classifyVideo(mmr, readClasses(), ortEnv, ortSession)
                    val processTime = SystemClock.elapsedRealtime() - startTime
                    Log.d(TAG, "Finished classifying video")
                    Text(text = "${results?.first?.get(0)}", fontWeight = FontWeight.ExtraBold)
                    Text(text = "${results?.first?.get(1)}")
                    Text(text = "${results?.first?.get(2)}")
                    Text(text = "${results?.first?.get(3)}")
                    Text(text = "${results?.first?.get(4)}")
                    Text(text = "Inference time: ${results?.second} ms")
                    Text(text = "Process time: $processTime ms")
                    Log.d(TAG, ""+results?.first?.get(0)+" "
                            +results?.first?.get(1)+" "
                            +results?.first?.get(2)+" "
                            +results?.first?.get(3)+" "
                            +results?.first?.get(4))
                }
            }
        }
    }

    /**
     * Go to recording page
     */
    private fun goToRecord(){
        val intent = Intent(this, RecordActivity::class.java)
        startActivity(intent)
        finish()
    }

    /**
     * Read the ONNX model file
     */
    private fun readModel(): ByteArray {
        val modelID = R.raw.i3d_model
        return resources.openRawResource(modelID).readBytes()
    }

    /**
     * Read the label file containing the sign that can be identified
     */
    private fun readClasses(): List<String> {
        return resources.openRawResource(R.raw.labels).bufferedReader().readLines()
    }

    override fun onDestroy() {
        super.onDestroy()
        videoClassifier = null
        mmr.release()
        sessionOptions?.close()
        sessionOptions = null
        ortSession.close()
        ortEnv.close()
    }
}
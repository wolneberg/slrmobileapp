package com.example.slr

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
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
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

const val TAG = "TFLite-VidClassify"
const val MAX_RESULT = 5
const val MODEL_A0_FILE = "Movinet-a0-8.tflite"
const val MODEL_LABEL_FILE = "WLASL_100_labels.txt"
class MainActivity: ComponentActivity(){

    private var videoClassifier: StreamVideoClassifier? = null
    private var numThread = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val result = remember { mutableStateOf<Uri?>(null) }
            val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) {
                result.value = it
            }
            val mmr = MediaMetadataRetriever()
            createClassifier()
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
                    val results = videoClassifier?.classifyVideo(mmr)
                    val processTime = SystemClock.elapsedRealtime() - startTime
                    Log.d(TAG, "Finished classifying video")
                    Text(text = "Label: "+ results?.first?.get(0)?.label +
                            ", Score: "+results?.first?.get(0)?.score,
                        fontWeight = FontWeight.ExtraBold)
                    Text(text = "Label: "+ results?.first?.get(1)?.label +
                            ", Score: "+results?.first?.get(1)?.score)
                    Text(text = "Label: "+ results?.first?.get(2)?.label +
                            ", Score: "+results?.first?.get(2)?.score)
                    Text(text = "Label: "+ results?.first?.get(3)?.label +
                            ", Score: "+results?.first?.get(3)?.score)
                    Text(text = "Label: "+ results?.first?.get(4)?.label +
                            ", Score: "+results?.first?.get(4)?.score)
                    Text(text = "Inference time: ${results?.second} ms")
                    Text(text = "Process and inference time: $processTime ms")
                }
            }
        }
    }

    private fun goToRecord(){
        val intent = Intent(this, RecordActivity::class.java)
        startActivity(intent)
    }

    /**
     * Initialize the TFLite video classifier.
     */
    @OptIn(ExperimentalGetImage::class) private fun createClassifier() {
        if (videoClassifier != null) {
            videoClassifier?.close()
            videoClassifier = null
        }
        val options =
            StreamVideoClassifier.StreamVideoClassifierOptions.builder()
                .setMaxResult(MAX_RESULT)
                .setNumThreads(numThread)
                .build()
        val modelFile = MODEL_A0_FILE

        videoClassifier = StreamVideoClassifier.createFromFileAndLabelsAndOptions(
            this,
            modelFile,
            MODEL_LABEL_FILE,
            options
        )

        Log.d(TAG, "Classifier created.")
    }
}
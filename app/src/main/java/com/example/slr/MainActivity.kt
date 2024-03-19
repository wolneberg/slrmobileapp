package com.example.slr

import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
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

class MainActivity: ComponentActivity(){
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val result = remember { mutableStateOf<Uri?>(null) }
            val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) {
                result.value = it
            }
            val mmr = MediaMetadataRetriever()
            Column(
                modifier = Modifier.fillMaxHeight(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ){
                Row (
                    modifier = Modifier
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    FilledTonalButton(
                        onClick = {
                            launcher.launch(PickVisualMediaRequest(mediaType = ActivityResultContracts.PickVisualMedia.VideoOnly))
                        }
                    ) {
                        Text("Pick video from gallery")
                    }
                    FilledTonalButton(
                        onClick = {
                            goToRecord()
                        }
                    ) {
                        Text("Record new video")
                    }
                }
                if (result.value != null) {
                    mmr.setDataSource(this@MainActivity, result.value)
                    predict(this@MainActivity, mmr)
                }
                result.value?.let {image ->
                    Text(text = "Video Path: "+image.path.toString())
                }
            }
        }
    }

    private fun goToRecord(){
        val intent = Intent(this, RecordActivity::class.java)
        startActivity(intent)
    }
}

/*
class MainActivity : Activity() {
    private lateinit var viewBinding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding =  ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        // Set up the listeners for buttons
        viewBinding.loadButton.setOnClickListener { goToLoad() }
        viewBinding.recordButton.setOnClickListener { goToRecord() }

    }

    private fun goToRecord(){
        val intent = Intent(this, RecordActivity::class.java)
        startActivity(intent)
    }

    private fun goToLoad(){
        val intent = Intent(this, LoadActivity::class.java)
        startActivity(intent)
    }
}

*/
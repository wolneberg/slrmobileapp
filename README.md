# SLR mobile Android app
This repository is the automatic Sign Language Recognition application used in the master thesis of Helene Amlie and Ingrid Marie Wølneberg.
The application has functionality for choosing a video on the mobile phone to be classified or recording a new video that will be classified after ending the recording.
After classification, the top-5 predictions with scores, inference time, and processing time are displayed.

The code is inspired by and has used certain methods from two tutorials found online:
* A tutorial on using Camera X in Android applications: https://github.com/philipplackner/CameraXGuide/tree/recording-videos
* A tutorial on using MoViNet stream version in an Android application for action recognition: https://github.com/tensorflow/examples/tree/master/lite/examples/video_classification/android

### Branches in use
* main: for running MoViNet models in TensorFlow Lite format
  * movinetA- branches are the same as the main branch but use other versions of MoViNet
* onnx: for running I3D in Onnx format.

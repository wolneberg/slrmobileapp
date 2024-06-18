# Isolated SLR for Android devices
This repository contains the automatic isolated Sign Language Recognition application used in the master thesis of Helene Amlie and Ingrid Marie Wølneberg.
The application has functionality for choosing a video on the mobile phone to be classified or recording a new video that will be classified after ending the recording.
After classification, the top-5 predictions with scores, inference time, and processing time are displayed.

The code is inspired by and has used certain methods from two public repositories:
* [Camera X tutorial]( https://github.com/philipplackner/CameraXGuide/tree/recording-videos) for Android applications.
* Using the [MoViNet stream version in an Android application](https://github.com/tensorflow/examples/tree/master/lite/examples/video_classification/android) for action recognition.

We learned how to use ONNX instead of TensorFlow from a [ONNX tutorial on object recognition](https://github.com/microsoft/onnxruntime-inference-examples/tree/main/mobile/examples/object_detection/android).

### File structure
* app/src/main/java/com/example/slr contains all the main functionality of the application
     * MainActivity contains the page for loading a video from the mobile device
     * RecordAcivity contains the page for recording a video
     * StreamVideoClassifier has the code for using the SLR model, processing input and output, and calculating inference time.
* app/src/main/assets contain the TensorFlow Lite model file and the text file with the labels

### Branches in use
* main: for running MoViNet models in TensorFlow Lite format.
  * movinetA- branches are the same as the main branch but use other versions of MoViNet.
* onnx: for running I3D in Onnx format.

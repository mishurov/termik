# Termik

An example of using OpenCV and TensorFlow libraries together.

[![](https://mishurov.000webhostapp.com/github/termik/Screenshot1.png )](https://play.google.com/store/apps/details?id=uk.co.mishurov.termik2)

[The app on Play Market](https://play.google.com/store/apps/details?id=uk.co.mishurov.termik2)

## Notes
* The native code contains commented calls to the DNN contrib module for OpenCV library which also can use the TensorFlow model in order to classify input images. It's very slow, TensorFlow library infers way faster.

* The app uses an external expansion file instead of the "assets" folder in order to make a size of an .apk smaller, the trained neural network model is about 50 mb.

* The project contains a modified version of the TensorFlow demo classifier in order to get the model file from a mounted .obb instead of .apk's assets, and some tweaks for the inference output.

* The project contains a fixed JavaCameraView which correctly uses the YV12 image format.

* The project contains a fixed Android Licensing library which doesn't throw the error about an implicit intent. 

* There's the minor bug. If the .obb file is missing, the app crashes after downloading the file from Play Store. It works well after restart though. I'll fix it eventually.

## Building on Linux
I didn't test the build process in other OSes. I used CMake and Ninja build systems.

* Generate the necessary libraries first. Uncomment "gen-libs" in "setting.gradle" and comment the others, comment/uncomment targets and abis in "gen-libs/build.gradle" to build the OpenCV library and to download a prebuilt TensorFlow files and its sources. OpenCV needs a path to Android SDK, it works with the Android tools from SDK 25.3.0 and earlier. https://github.com/opencv/opencv/issues/8460

* Get Android's licensing library and expansion files library from the SDK manager. Build the app, there's a graddle task, "uploadOBB", it generates and uploads an .obb file onto an ADB device.


# Termik

An example of using OpenCV and TensorFlow libraries together.

![](https://mishurov.000webhostapp.com/github/termik/Screenshot1.png "")

[The app on Play Market](https://play.google.com/store/apps/details?id=uk.co.mishurov.termik2)

## Notes
* The native code contains the commented calls to the DNN contrib addon for OpenCV which also can use TensorFlow model in order to classify input images. It's very slow, the TensorFlow library infers way faster.

* The app uses an external expansion file insread of assets in order to make the size of an .apk smaller, the trained neural network model is about 50 mb.

* The package name is "termik2" instead of "termik" because of the lost certificate for Play market after uploading an early alpha.

* The project contains a modified version of TensorFlow demo classifier in order to get the model file from a mounted .obb instead of .apk's assets, and some tweaks for the inference output.

* The project contains the fixed JavaCameraView which correctly uses YV12 image format.

* The project contains the fixed Android Licensing library which doesn't throw error about an implicit intent. 

* There's the minor bug. If the .obb file is missing, the app crashes after downloading the file from Play Store. It works well after restart though.

## Building on Linux
I didn't test the build process in other OSes. It uses CMake and Ninja build systems.

* Generate the necessary libraries first. Uncomment "gen-libs" in "setting.gradle" and comment the others, comment/uncomment targets and abis in "gen-libs/build.gradle" to build the OpenCV library and to download a prebuilt TensorFlow files and its sources. OpenCV need a path to Android SDK, it works with the Android tools from an SDK less than 25.3.0.

* Get Android's licensing library and expansion files library. Build the app, there's a graddle task, "uploadOBB", it generates and uploads .obb file onto an ADB device.


# Termik 2

An example of using OpenCV and TensorFlow libraries together.

[![](http://mishurov.usite.pro/github/termik/Screenshot1.png)](https://play.google.com/store/apps/details?id=uk.co.mishurov.termik2)

[The app on Play Market](https://play.google.com/store/apps/details?id=uk.co.mishurov.termik2)

## Notes
* The native code contains commented calls to the DNN contrib module for OpenCV library which also can use the TensorFlow model in order to classify input images. It's very slow, TensorFlow library infers way faster.

* The project contains a fixed JavaCameraView which correctly uses the YV12 image format.



package uk.co.mishurov.termik2


import java.io.File

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.support.v4.app.ActivityCompat
import android.support.v7.app.AppCompatActivity
import android.view.SurfaceView
import android.view.WindowManager
import android.view.Display
import android.view.Surface
import android.view.OrientationEventListener
import android.hardware.SensorManager
import android.graphics.Bitmap
import android.graphics.Bitmap.Config
import android.util.DisplayMetrics
import android.content.res.AssetManager

import org.opencv.android.BaseLoaderCallback
import org.opencv.android.CameraBridgeViewBase
import org.opencv.android.LoaderCallbackInterface
import org.opencv.android.OpenCVLoader
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.core.Rect
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.imgproc.Imgproc
import org.opencv.android.Utils

import org.tensorflow.classifier.Classifier
import org.tensorflow.classifier.TensorFlowImageClassifier

import uk.co.mishurov.termik2.opencv.JavaCameraView

import android.util.Log


class MainActivity : AppCompatActivity(), CameraBridgeViewBase.CvCameraViewListener2 {

    // Orientation
    private var mOrientationListener: OrientationEventListener? = null
    private var mOrientation = 0
    private var mScreenRotation = 0
    private var mStatus: String? = null
    private var mMountedPath: String? = null
    private var mClassifier: Classifier? = null
    private var mInputBitmap: Bitmap? = null
    private var mRecentFrame: Mat? = null
    private var handler: Handler? = null
    private var handlerThread: HandlerThread? = null
    private var mPreviewWidth = 0
    private var mPreviewHeight = 0
    private var mProcessing = false
    private var mCameraView: JavaCameraView? = null
    private var mVisuals: ResultsView? = null

    private val mBaseLoaderCallback = object : BaseLoaderCallback(this) {
        override fun onManagerConnected(status: Int) {
            when (status) {
                LoaderCallbackInterface.SUCCESS -> {
                    Log.i(TAG, "OpenCV loaded successfully")
                    // Load ndk built module, as specified in moduleName in build.gradle
                    // after opencv initialization
                    System.loadLibrary("processing")
                    mCameraView?.enableView()
                }
                else -> {
                    super.onManagerConnected(status)
                }
            }
        }
    }

    private fun setUpClassifier() {
        mClassifier = TensorFlowImageClassifier.create(
                getAssets(),
                MODEL_FILENAME,
                LABEL_FILENAME,
                INPUT_SIZE,
                IMAGE_MEAN,
                IMAGE_STD,
                INPUT_NAME,
                OUTPUT_NAME
        )
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )
        setContentView(R.layout.activity_main)

        // Permissions for Android 6+
        // arrayOf(Manifest.permission.CAMERA,
        //    Manifest.permission.READ_EXTERNAL_STORAGE),
        ActivityCompat.requestPermissions(
                this@MainActivity,
                arrayOf(Manifest.permission.CAMERA),
                1
        )

        setUpClassifier()

        mCameraView = findViewById<JavaCameraView>(R.id.main_surface)

        mCameraView?.setVisibility(SurfaceView.VISIBLE)
        mCameraView?.setCvCameraViewListener(this)

        val display = windowManager.defaultDisplay
        val rotation = display.rotation
        when (rotation) {
            Surface.ROTATION_90 -> mScreenRotation = 90
            Surface.ROTATION_180 -> mScreenRotation = 180
            Surface.ROTATION_270 -> mScreenRotation = -90
            else -> mScreenRotation = 0
        }

        // Listen orientation
        mVisuals = findViewById<ResultsView>(R.id.linear)
        mVisuals!!.setResults(VISUAL + "Calculating...")
        mOrientationListener = object : OrientationEventListener(this,
                SensorManager.SENSOR_DELAY_NORMAL) {
            override fun onOrientationChanged(orientation: Int) {
                var orient = orientation
                orient += mScreenRotation
                this@MainActivity.mOrientation = orient
                this@MainActivity.mVisuals?.adjust(orient)
            }
        }

        if (mOrientationListener!!.canDetectOrientation()) {
            Log.v(TAG, "Can detect orientation")
            mOrientationListener?.enable()
        } else {
            Log.v(TAG, "Cannot detect orientation")
            mOrientationListener?.disable()
        }

    }

    @Synchronized public override fun onPause() {
        handlerThread!!.quitSafely()
        try {
            handlerThread!!.join()
            handlerThread = null
            handler = null
        } catch (e: InterruptedException) {
            Log.e(TAG, "Exception!" + e.toString())
        }

        super.onPause()
        disableCamera()
    }

    @Synchronized public override fun onResume() {
        super.onResume()
        if (!OpenCVLoader.initDebug()) {
            Log.d(
                    TAG,
                    "Internal OpenCV library not found. Using OpenCV Manager for initialization"
            )
            OpenCVLoader.initAsync(
                    OpenCVLoader.OPENCV_VERSION_3_0_0, this, mBaseLoaderCallback
            )
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!")
            mBaseLoaderCallback.onManagerConnected(
                LoaderCallbackInterface.SUCCESS
            )
        }

        handlerThread = HandlerThread("inference")
        handlerThread?.start()
        handler = Handler(handlerThread?.looper)
    }

    fun disableCamera() {
        if (mCameraView != null)
            mCameraView?.disableView()
    }

    private fun startInference() {
        runInBackground(
                Runnable {
                    Log.d(TAG, "Starting inference image processing")
                    mProcessing = true
                    // Crop
                    var side : Int
                    var x : Int
                    var y : Int
                    if (mPreviewHeight < mPreviewWidth) {
                        side = mPreviewHeight
                        x = (mPreviewWidth - side) / 2
                        y = 0
                    } else {
                        side = mPreviewWidth
                        y = (mPreviewHeight - side) / 2
                        x = 0
                    }
                    val roi = Rect(x, y, side, side)
                    val img = Mat(mRecentFrame, roi)

                    // Resize
                    val size = Size(
                        INPUT_SIZE.toDouble(), INPUT_SIZE.toDouble()
                    )
                    Imgproc.resize(img, img, size)

                    // Rotate
                    val angle = mOrientation
                    if (angle >= 45 && angle < 135) {
                        // 90 cw
                        Core.flip(img.t(), img, 1)
                    } else if (angle >= 135 && angle < 225) {
                        // 180
                        Core.flip(img, img, -1)
                    } else if (angle >= 225 && angle < 315) {
                        // 90 ccw
                        Core.flip(img.t(), img, 0)
                    }

                    // Convert
                    Utils.matToBitmap(img, mInputBitmap)

                    mProcessing = false

                    Log.d(TAG, "Starting inference")
                    val startTime = SystemClock.uptimeMillis()
                    val results = mClassifier?.recognizeImage(mInputBitmap)
                    val lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime

                    // Output resluts
                    var output = VISUAL
                    for (res in results!!) {
                        output += res.toString() + "\n"
                    }
                    output += lastProcessingTimeMs.toString() + " ms"

                    setInference(output)
                    startInference()
                })
    }

    override fun onCameraViewStarted(width: Int, height: Int) {
        // Calculate ratio to stretch camera preview on screen
        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        val screenHeight = displayMetrics.heightPixels
        val screenWidth = displayMetrics.widthPixels
        var ratio = 0f
        var heightRatio = 0f
        var widthRatio = 0f
        if (width < screenWidth)
            widthRatio = screenWidth.toFloat() / width.toFloat()
        if (height < screenHeight)
            heightRatio = screenHeight.toFloat() / height.toFloat()
        ratio = if (heightRatio > widthRatio) heightRatio else widthRatio
        mCameraView!!.setScale(ratio)

        mPreviewWidth = width
        mPreviewHeight = height

        // Create empty bitmaps until real camera frames arrived
        mInputBitmap = Bitmap.createBitmap(
            INPUT_SIZE, INPUT_SIZE, Config.ARGB_8888
        )
        val empty = Bitmap.createBitmap(
            mPreviewWidth, mPreviewHeight, Config.ARGB_8888
        )
        mRecentFrame = Mat(mPreviewWidth, mPreviewHeight, CvType.CV_8UC4)
        Utils.bitmapToMat(empty, mRecentFrame)

        handlerThread = HandlerThread("inference")
        handlerThread?.start()
        handler = Handler(handlerThread?.looper)
        startInference()
    }

    override fun onCameraViewStopped() {}

    override fun onCameraFrame(
                    inputFrame: CameraBridgeViewBase.CvCameraViewFrame): Mat
    {
        val image = inputFrame.rgba()
        if (!mProcessing)
            mRecentFrame = image.clone()
        salt(image.nativeObjAddr)
        return image
    }

    fun setInference(letters: String) {
        runOnUiThread { mVisuals?.setResults(letters) }
    }

    @Synchronized protected fun runInBackground(r: Runnable) {
        if (handler != null) {
            handler?.post(r)
        }
    }

    @Synchronized override fun onDestroy() {
        super.onDestroy()
        disableCamera()
    }

    external fun salt(image: Long)
    external fun setdir(path: String?): Double

    companion object {

        private val TAG = "Termik"

        private val VISUAL = "Visual:\n"
        private val MODEL_FILENAME = "file:///android_asset/tensorflow_inception_graph.pb"
        private val LABEL_FILENAME = "file:///android_asset/imagenet_comp_graph_label_strings.txt"
        private val INPUT_SIZE = 224
        private val IMAGE_MEAN = 117
        private val IMAGE_STD = 1f
        private val INPUT_NAME = "input"
        private val OUTPUT_NAME = "output"
    }

}



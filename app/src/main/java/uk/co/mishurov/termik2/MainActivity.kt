package uk.co.mishurov.termik2


import java.io.File
import java.io.BufferedInputStream
import java.io.FileOutputStream
import java.io.IOException

import android.Manifest
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.support.v4.app.ActivityCompat
import android.view.WindowManager
import android.view.OrientationEventListener
import android.view.Surface
import android.view.View
import android.view.SurfaceView
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ViewGroup
import android.hardware.SensorManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PixelFormat
import android.content.Intent
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.content.res.AssetManager
import android.preference.PreferenceManager
import android.util.DisplayMetrics

import com.google.vr.sdk.base.GvrActivity
import com.google.vr.sdk.base.GvrView

import org.opencv.android.BaseLoaderCallback
import org.opencv.android.CameraBridgeViewBase
import org.opencv.android.LoaderCallbackInterface
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.core.Scalar
import org.opencv.core.Rect
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.imgproc.Imgproc

import org.opencv.dnn.Net
import org.opencv.dnn.Dnn

import org.tensorflow.classifier.Classifier
import org.tensorflow.classifier.TensorFlowImageClassifier

import android.util.Log


class MainActivity : GvrActivity(), View.OnTouchListener,
                            CameraBridgeViewBase.CvCameraViewListener2
{
    // Orientation
    private var mOrientationListener: OrientationEventListener? = null
    private var mListener: OnSharedPreferenceChangeListener? = null
    private var mPrefs: SharedPreferences? = null
    private var mEnginePref : Int = 0
    private var mOutputPref : Int = 0
    private var mOrientation = 0
    private var mScreenRotation = 0

    private var handler: Handler? = null
    private var handlerThread: HandlerThread? = null
    private var mPreviewWidth = 0
    private var mPreviewHeight = 0
    private var mScreenWidth = 0
    private var mScreenHeight = 0
    private var mProcessing = false
    private var mCameraView: KotlinCameraView? = null
    private var mVisuals: ResultsView? = null
    private var mGestureDetector: GestureDetector? = null

    private var mInputBitmap: Bitmap? = null
    private var mRecentFrame: Mat? = null
    // TensorFlow
    private var mClassifier: Classifier? = null
    // OpenCV DNN
    private var mNet: Net? = null
    private var mLabels: List<String>? = null
    private var mInputMat: Mat? = null

    // VR
    private var gvrRenderer: GvrRenderer? = null

    private val mBaseLoaderCallback = object : BaseLoaderCallback(this)
    {
        override fun onManagerConnected(status: Int)
        {
            when (status) {
                LoaderCallbackInterface.SUCCESS -> {
                    Log.i(TAG, "OpenCV loaded successfully")
                    // Load ndk built module, as specified in moduleName in build.gradle
                    // after opencv initialization
                    System.loadLibrary("processing")

                    if (mEnginePref == 0) {
                        setUpTensorFlow()
                    } else {
                        setUpOpenCvDnn()
                    }

                    mCameraView?.enableView()
                }
                else -> {
                    super.onManagerConnected(status)
                }
            }
        }
    }

    private fun getPath(file: String): String {

        val assetManager: AssetManager = getAssets()
        var inputStream: BufferedInputStream?

        try {
            val actual_file = file.split("file:///android_asset/")[1]
            inputStream = BufferedInputStream(assetManager.open(actual_file))

            val file_data: ByteArray = inputStream.readBytes()

            inputStream.read(file_data)
            inputStream.close()

            val outFile : File = File(getFilesDir(), actual_file)

            val os : FileOutputStream = FileOutputStream(outFile)
            os.write(file_data)
            os.close()

            return outFile.getAbsolutePath()

        } catch (e: IOException) {
            Log.i(TAG, "Failed to open NN file")
        }

        return ""
    }

    private fun readLabels(file: String) {
        val assetManager: AssetManager = getAssets()
        var inputStream: BufferedInputStream?

        try {
            val actual_file = file.split("file:///android_asset/")[1]
            inputStream = BufferedInputStream(assetManager.open(actual_file))
            val reader = inputStream.bufferedReader()
            mLabels = reader.readLines()

        } catch (e: IOException) {
            Log.i(TAG, "Failed to open labels file")
        }
    }

    private fun setUpOpenCvDnn() {
        mNet = Dnn.readNetFromTensorflow(getPath(MODEL_FILENAME))
        readLabels(LABEL_FILENAME)
    }


    private fun setUpTensorFlow() {
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
        setContentView(R.layout.activity_main)
        window.addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )
        ActivityCompat.requestPermissions(
                this@MainActivity,
                arrayOf(Manifest.permission.CAMERA),
                1
        )

        mPrefs = PreferenceManager.getDefaultSharedPreferences(this)
        mListener = object : OnSharedPreferenceChangeListener {
            override fun onSharedPreferenceChanged(prefs: SharedPreferences,
                                                   key: String) {
                setPrefs(prefs)
            }
        }
        mPrefs?.registerOnSharedPreferenceChangeListener(mListener)
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false)
        setPrefs(mPrefs!!)

        mGestureDetector = GestureDetector(this, GestureListener())

        val display = windowManager.defaultDisplay
        val rotation = display.rotation
        when (rotation) {
            Surface.ROTATION_90 -> mScreenRotation = 90
            Surface.ROTATION_180 -> mScreenRotation = 180
            Surface.ROTATION_270 -> mScreenRotation = -90
            else -> mScreenRotation = 0
        }

        // Listen orientation
        mVisuals = findViewById<ResultsView>(R.id.results)
        mVisuals?.setResults(VISUAL + "Calculating...")

        mCameraView = findViewById<KotlinCameraView>(R.id.camera)
        mCameraView?.setCvCameraViewListener(this)


        gvrView = findViewById<GvrView>(R.id.vr)
        if (mOutputPref == 2) {
            gvrView?.setDistortionCorrectionEnabled(false)
        }

        gvrView?.setEGLConfigChooser(8, 8, 8, 8, 0, 0)
        gvrRenderer = GvrRenderer(gvrView!!)
        setGvrView(gvrView!!)
        if (mOutputPref != 0) {
            gvrView?.setVisibility(SurfaceView.VISIBLE)
            gvrView?.setOnTouchListener(this)
        } else {
            gvrView.setStereoModeEnabled(false)
            gvrView?.setVisibility(SurfaceView.GONE)
        }

        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        mScreenWidth = displayMetrics.widthPixels
        mScreenHeight = displayMetrics.heightPixels

        if (mOutputPref != 0) {
            val camParams = mCameraView!!.getLayoutParams()
            camParams.width = mScreenWidth
            camParams.height = mScreenHeight * 2
            mCameraView?.setLayoutParams(camParams)

            // make view trasparent since the image is rendered in GvrView
            mCameraView?.setZOrderOnTop(true)
            val holder = mCameraView?.getHolder()
            holder?.setFormat(PixelFormat.TRANSPARENT)

            val visParams = mVisuals!!.getLayoutParams()
            visParams.width = mScreenWidth / 2
            visParams.height = mScreenHeight
            mVisuals?.setLayoutParams(visParams)
            mVisuals?.setVrStyle()
        }

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

    override fun onPause() {
        gvrView!!.onPause()
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

    override fun onResume() {
        gvrView!!.onResume()
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

    private fun openCvInference(): MutableList<String> {
        Imgproc.cvtColor(mInputMat, mInputMat, Imgproc.COLOR_RGBA2RGB)
        val size = Size(INPUT_SIZE.toDouble(), INPUT_SIZE.toDouble())
        val mean = Scalar(
            IMAGE_MEAN.toDouble(),
            IMAGE_MEAN.toDouble(),
            IMAGE_MEAN.toDouble()
        )
        val blob = Dnn.blobFromImage(mInputMat, 1.0, size, mean, true, true)

        mNet?.setInput(blob, INPUT_NAME)
        //val outputName = "softmax2"
        var result = mNet?.forward()
        result = result?.reshape(1, 1)

        var indices = result?.clone()
        Core.sortIdx(result, indices, Core.SORT_DESCENDING)

        var output: MutableList<String> = ArrayList()
        for (i in 0..4) {
            val index = indices?.get(0, i)?.get(0)!!.toInt()
            val label = mLabels!![index]
            val probability = result?.get(0, index)?.get(0)
            val probStr = "%.1f".format(probability?.times(100))
            output.add(label + " (" + probStr + "%)")
        }
        return output
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
                    mInputMat = Mat(mRecentFrame, roi)

                    // Resize
                    val size = Size(
                        INPUT_SIZE.toDouble(), INPUT_SIZE.toDouble()
                    )
                    Imgproc.resize(mInputMat!!, mInputMat, size)

                    // Rotate
                    val angle = mOrientation
                    if (angle >= 45 && angle < 135) {
                        // 90 cw
                        Core.flip(mInputMat!!.t(), mInputMat, 1)
                    } else if (angle >= 135 && angle < 225) {
                        // 180
                        Core.flip(mInputMat!!, mInputMat, -1)
                    } else if (angle >= 225 && angle < 315) {
                        // 90 ccw
                        Core.flip(mInputMat!!.t(), mInputMat, 0)
                    }

                    // Convert
                    Utils.matToBitmap(mInputMat!!, mInputBitmap)

                    mProcessing = false

                    Log.d(TAG, "Starting inference")
                    val startTime = SystemClock.uptimeMillis()

                    var output = VISUAL

                    if (mEnginePref == 0) {
                        val results = mClassifier?.recognizeImage(mInputBitmap)
                        for (res in results!!) {
                            output += res.toString().split("]")[1] + "\n"
                        }
                    } else {
                        val results = openCvInference()
                        for (res in results) output += res + "\n"
                    }

                    val lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime

                    output += lastProcessingTimeMs.toString() + " ms"

                    setInference(output)
                    startInference()
                })
    }

    override fun onCameraViewStarted(width: Int, height: Int) {
        // Calculate ratio to stretch camera preview on screen
        var heightRatio = 0.0f
        var widthRatio = 0.0f

        if (width < mScreenWidth) {
            widthRatio = mScreenWidth.toFloat() / width.toFloat()
        }
        if (height < mScreenHeight) {
            heightRatio = mScreenHeight.toFloat() / height.toFloat()
        }

        val ratio = if (heightRatio > widthRatio) heightRatio else widthRatio
        mCameraView!!.setScale(ratio)

        mPreviewWidth = width
        mPreviewHeight = height

        mInputBitmap = Bitmap.createBitmap(
            INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888
        )
        val empty = Bitmap.createBitmap(
            mPreviewWidth, mPreviewHeight, Bitmap.Config.ARGB_8888
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
                    inputFrame: CameraBridgeViewBase.CvCameraViewFrame): Mat?
    {
        val image = inputFrame.rgba()
        if (!mProcessing)
            mRecentFrame = image.clone()
        process(image.nativeObjAddr)

        if (mOutputPref > 0) {
            var bmp = Bitmap.createBitmap(
                mPreviewWidth, mPreviewHeight, Bitmap.Config.ARGB_8888
            )
            Utils.matToBitmap(image, bmp)

            val ratio = mScreenHeight.toFloat() / mPreviewHeight.toFloat()

            bmp = Bitmap.createScaledBitmap(
               bmp,
               (mPreviewWidth * ratio).toInt() + 1,
               (mPreviewHeight * ratio).toInt() + 1,
               true
            )

            val offset = (mPreviewWidth * ratio - mScreenWidth * 0.5f) * 0.5f

            bmp = Bitmap.createBitmap(
               bmp, offset.toInt(), 0, mScreenWidth / 2, mScreenHeight
            )

            var vis = Bitmap.createBitmap(
                mScreenWidth / 2, mScreenHeight, Bitmap.Config.ARGB_8888
            )
            //vis.eraseColor(Color.argb(128, 255, 255, 255))
            val c = Canvas(vis)
            mVisuals?.drawText(c)

            gvrRenderer?.setBmp(bmp)
            gvrRenderer?.setVis(vis)
        }

        return if (mOutputPref > 0) null else image
    }

    fun setPrefs(prefs: SharedPreferences) {
        val engine : String = prefs.getString("engine_preference", "")
        mEnginePref = engine.toInt()
        val output : String = prefs.getString("output_preference", "")
        mOutputPref = output.toInt()
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

    override fun onTouchEvent(me: MotionEvent): Boolean
    {
        return mGestureDetector?.onTouchEvent(me) as Boolean
    }

    override fun onTouch(v: View, me: MotionEvent): Boolean
    {
        return mGestureDetector?.onTouchEvent(me) as Boolean
    }

    private inner class GestureListener
                            : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            val intent = Intent(
                    this@MainActivity,
                    SettingsActivity::class.java
            )
            startActivity(intent)
            return true
        }
    }

/*
    override fun onBackPressed() {
    }

    override fun onCardboardTrigger() {
    }
*/
    external fun process(image: Long)

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



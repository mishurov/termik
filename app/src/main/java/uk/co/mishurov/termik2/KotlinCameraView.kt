package uk.co.mishurov.termik2

import android.content.Context
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.hardware.Camera.PreviewCallback
import android.os.Build
import android.util.AttributeSet
import android.util.Log
import android.view.ViewGroup.LayoutParams

import org.opencv.android.CameraBridgeViewBase
import org.opencv.BuildConfig
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc


class KotlinCameraView : CameraBridgeViewBase, PreviewCallback
{
    private var mBuffer: ByteArray? = null
    private var mFrameChain: Array<Mat?> = arrayOfNulls(2)
    private var mChainIdx = 0
    private var mThread: Thread? = null
    private var mStopThread: Boolean = false

    protected var mCamera: Camera? = null
    private var mCameraFrame: Array<JavaCameraFrame?> = arrayOfNulls(2)
    private var mSurfaceTexture: SurfaceTexture? = null

    private var mCameraFrameReady = false

    class JavaCameraSizeAccessor : CameraBridgeViewBase.ListItemAccessor
    {
        override fun getWidth(obj: Any): Int {
            val size = obj as Camera.Size
            return size.width
        }

        override fun getHeight(obj: Any): Int {
            val size = obj as Camera.Size
            return size.height
        }
    }

    constructor(context: Context, cameraId: Int) : super(context, cameraId) {}

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {}

    protected fun initializeCamera(width: Int, height: Int): Boolean
    {
        Log.d(TAG, "Initialize java camera")
        var result = true
        synchronized(this) {
            mCamera = null

            if (mCameraIndex == CameraBridgeViewBase.CAMERA_ID_ANY) {
                Log.d(TAG, "Trying to open camera with old open()")
                try {
                    mCamera = Camera.open()
                } catch (e: Exception) {
                    Log.e(
                        TAG,
                        "Camera is not available (in use or does not exist): " +
                        e.localizedMessage
                    )
                }

                if (mCamera == null && Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.GINGERBREAD) {
                    var connected = false
                    for (camIdx in 0 until Camera.getNumberOfCameras()) {
                        Log.d(
                            TAG, "Trying to open camera with new open(" +
                            Integer.valueOf(camIdx) + ")"
                        )
                        try {
                            mCamera = Camera.open(camIdx)
                            connected = true
                        } catch (e: RuntimeException) {
                            Log.e(
                                TAG, "Camera #" +
                                camIdx + "failed to open: "
                                + e.localizedMessage
                            )
                        }

                        if (connected) break
                    }
                }
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
                    var localCameraIndex = mCameraIndex
                    if (mCameraIndex == CameraBridgeViewBase.CAMERA_ID_BACK) {
                        Log.i(TAG, "Trying to open back camera")
                        val cameraInfo = Camera.CameraInfo()
                        for (camIdx in 0 until Camera.getNumberOfCameras()) {
                            Camera.getCameraInfo(camIdx, cameraInfo)
                            if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                                localCameraIndex = camIdx
                                break
                            }
                        }
                    } else if (mCameraIndex == CameraBridgeViewBase.CAMERA_ID_FRONT) {
                        Log.i(TAG, "Trying to open front camera")
                        val cameraInfo = Camera.CameraInfo()
                        for (camIdx in 0 until Camera.getNumberOfCameras()) {
                            Camera.getCameraInfo(camIdx, cameraInfo)
                            if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                                localCameraIndex = camIdx
                                break
                            }
                        }
                    }
                    if (localCameraIndex == CameraBridgeViewBase.CAMERA_ID_BACK) {
                        Log.e(TAG, "Back camera not found!")
                    } else if (localCameraIndex == CameraBridgeViewBase.CAMERA_ID_FRONT) {
                        Log.e(TAG, "Front camera not found!")
                    } else {
                        Log.d(
                            TAG, "Trying to open camera with new open(" +
                            Integer.valueOf(localCameraIndex) + ")"
                        )
                        try {
                            mCamera = Camera.open(localCameraIndex)
                        } catch (e: RuntimeException) {
                            Log.e(
                                TAG, "Camera #" + localCameraIndex +
                                "failed to open: " + e.localizedMessage
                            )
                        }

                    }
                }
            }

            if (mCamera == null)
                return false

            try {
                var params: Camera.Parameters = mCamera!!.parameters
                Log.d(TAG, "getSupportedPreviewSizes()")
                val sizes = params.supportedPreviewSizes

                if (sizes != null) {
                    val frameSize = calculateCameraFrameSize(
                        sizes, JavaCameraSizeAccessor(), width, height
                    )

                    if (Build.BRAND.equals("generic", ignoreCase = true) ||
                        Build.BRAND.equals("Android", ignoreCase = true)) {
                        params.previewFormat = ImageFormat.YV12
                        // "generic" or "android" = android emulator
                    } else {
                        params.previewFormat = ImageFormat.NV21
                    }

                    mPreviewFormat = params.previewFormat

                    Log.d(
                        TAG, "Set preview size to " +
                        Integer.valueOf(frameSize.width.toInt()) +
                        "x" + Integer.valueOf(frameSize.height.toInt())
                    )
                    params.setPreviewSize(
                        frameSize.width.toInt(), frameSize.height.toInt()
                    )

                    if (Build.VERSION.SDK_INT >=
                        Build.VERSION_CODES.ICE_CREAM_SANDWICH &&
                        android.os.Build.MODEL != "GT-I9100")
                        params.setRecordingHint(true)

                    val FocusModes = params.supportedFocusModes
                    if (FocusModes != null &&
                        FocusModes.contains(
                            Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)
                        ) {
                        params.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO
                    }

                    mCamera!!.parameters = params
                    params = mCamera!!.parameters

                    mFrameWidth = params.previewSize.width
                    mFrameHeight = params.previewSize.height

                    if (layoutParams.width == LayoutParams.MATCH_PARENT &&
                        layoutParams.height == LayoutParams.MATCH_PARENT)
                        mScale = Math.min(
                            height.toFloat() / mFrameHeight,
                            width.toFloat() / mFrameWidth
                        )
                    else
                        mScale = 0f

                    if (mFpsMeter != null) {
                        mFpsMeter.setResolution(mFrameWidth, mFrameHeight)
                    }

                    var size = mFrameWidth * mFrameHeight
                    size = size * ImageFormat.getBitsPerPixel(params.previewFormat) / 8
                    mBuffer = ByteArray(size)

                    mCamera!!.addCallbackBuffer(mBuffer)
                    mCamera!!.setPreviewCallbackWithBuffer(this)

                    mFrameChain = arrayOfNulls(2)
                    mFrameChain[0] = Mat(
                        mFrameHeight + mFrameHeight / 2,
                        mFrameWidth, CvType.CV_8UC1
                    )
                    mFrameChain[1] = Mat(
                        mFrameHeight + mFrameHeight / 2,
                        mFrameWidth, CvType.CV_8UC1
                    )

                    AllocateCache()

                    mCameraFrame = arrayOfNulls(2)
                    mCameraFrame[0] = JavaCameraFrame(
                        mFrameChain[0]!!, mFrameWidth, mFrameHeight
                    )
                    mCameraFrame[1] = JavaCameraFrame(
                        mFrameChain[1]!!, mFrameWidth, mFrameHeight
                    )

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                        mSurfaceTexture = SurfaceTexture(MAGIC_TEXTURE_ID)
                        mCamera!!.setPreviewTexture(mSurfaceTexture)
                    } else
                        mCamera!!.setPreviewDisplay(null)

                    Log.d(TAG, "startPreview")
                    mCamera!!.startPreview()
                } else
                    result = false
            } catch (e: Exception) {
                result = false
                e.printStackTrace()
            }

        }

        return result
    }

    protected fun releaseCamera()
    {
        synchronized(this) {
            if (mCamera != null) {
                mCamera!!.stopPreview()
                mCamera!!.setPreviewCallback(null)

                mCamera!!.release()
            }
            mCamera = null
            if (mFrameChain[0] != null && mFrameChain[1] != null) {
                mFrameChain[0]?.release()
                mFrameChain[1]?.release()
            }
            if (mCameraFrame[0] != null && mCameraFrame[1] != null) {
                mCameraFrame[0]?.release()
                mCameraFrame[1]?.release()
            }
        }
    }

    override fun connectCamera(width: Int, height: Int): Boolean
    {

        Log.d(TAG, "Connecting to camera")
        if (!initializeCamera(width, height))
            return false

        mCameraFrameReady = false

        Log.d(TAG, "Starting processing thread")
        mStopThread = false
        mThread = Thread(CameraWorker())
        mThread!!.start()

        return true
    }

    override fun disconnectCamera()
    {
        Log.d(TAG, "Disconnecting from camera")
        try {
            mStopThread = true
            Log.d(TAG, "Notify thread")
            synchronized(this) {
                (this as java.lang.Object).notify()
            }
            Log.d(TAG, "Waiting for thread")
            if (mThread != null)
                mThread!!.join()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        } finally {
            mThread = null
        }

        /* Now release camera */
        releaseCamera()

        mCameraFrameReady = false
    }

    override fun onPreviewFrame(frame: ByteArray, arg1: Camera)
    {
        if (BuildConfig.DEBUG)
            Log.d(TAG, "Preview Frame received. Frame size: " + frame.size)
        synchronized(this) {
            mFrameChain[mChainIdx]?.put(0, 0, frame)
            mCameraFrameReady = true
            (this as java.lang.Object).notify()
        }
        if (mCamera != null)
            mCamera!!.addCallbackBuffer(mBuffer)
    }

    private inner class JavaCameraFrame(
                    private val mYuvFrameData: Mat, private val mWidth: Int,
                    private val mHeight: Int) :
                                CameraBridgeViewBase.CvCameraViewFrame
    {
        private val mRgba: Mat
        override fun gray(): Mat {
            return mYuvFrameData.submat(0, mHeight, 0, mWidth)
        }

        override fun rgba(): Mat {
            if (mPreviewFormat == ImageFormat.NV21) {
                Imgproc.cvtColor(
                    mYuvFrameData, mRgba, Imgproc.COLOR_YUV2RGBA_NV21, 4
                )
            } else if (mPreviewFormat == ImageFormat.YV12) {
                Imgproc.cvtColor(
                    mYuvFrameData, mRgba, Imgproc.COLOR_YUV2RGB_I420, 4
                )
            } else {
                throw IllegalArgumentException(
                    "Preview Format can be NV21 or YV12"
                )
            }

            return mRgba
        }

        init {
            mRgba = Mat()
        }

        fun release() {
            mRgba.release()
        }
    }

    private inner class CameraWorker : Runnable
    {
        override fun run()
        {
            do {
                var hasFrame = false
                synchronized(this@KotlinCameraView) {
                    try {
                        while (!mCameraFrameReady && !mStopThread) {
                            (this@KotlinCameraView as java.lang.Object).wait()
                        }
                    } catch (e: InterruptedException) {
                        e.printStackTrace()
                    }

                    if (mCameraFrameReady) {
                        mChainIdx = 1 - mChainIdx
                        mCameraFrameReady = false
                        hasFrame = true
                    }
                }

                if (!mStopThread && hasFrame) {
                    if (!mFrameChain[1 - mChainIdx]?.empty()!!)
                        deliverAndDrawFrame(mCameraFrame[1 - mChainIdx]!!)
                }
            } while (!mStopThread)
            Log.d(TAG, "Finish processing thread")
        }
    }

    fun setScale(scale: Float)
    {
        mScale = scale
    }

    companion object
    {
        private val MAGIC_TEXTURE_ID = 10
        private val TAG = "KotlinCameraView"
    }
}


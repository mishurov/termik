package uk.co.mishurov.termik;


import java.io.File;
import java.util.List;

import android.Manifest;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.os.storage.OnObbStateChangeListener;
import android.os.storage.StorageManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.view.Display;
import android.view.Surface;
import android.view.OrientationEventListener;
import android.hardware.SensorManager;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.util.DisplayMetrics;

import com.google.android.vending.expansion.downloader.Helpers;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.core.Rect;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.imgproc.Imgproc;
import org.opencv.android.Utils;

import org.tensorflow.demo.Classifier;
import org.tensorflow.demo.TensorFlowImageClassifier;

import android.util.Log;


public class MainActivity extends AppCompatActivity
                          implements CameraBridgeViewBase.CvCameraViewListener2 {

    private static final String TAG = "Termik";

    // Orientation
    private OrientationEventListener mOrientationListener;
    private int mOrientation = 0;
    private int mScreenRotation = 0;

    // Assets
    private final static String EXP_PATH = "/Android/obb/";
    private final static int VERSION_CODE = 1;
    private static String mObbPath;
    private String mStatus;
    private String mMountedPath;
    private StorageManager mSM;

    // Classification
    private static final String VISUAL = "Visual:\n";
    private static final String MODEL_FILENAME = "/tensorflow_inception_graph.pb";
    private static final String LABEL_FILENAME = "/imagenet_comp_graph_label_strings.txt";
    private static final int INPUT_SIZE = 224;
    private static final int IMAGE_MEAN = 117;
    private static final float IMAGE_STD = 1;
    private static final String INPUT_NAME = "input";
    private static final String OUTPUT_NAME = "output";
    private Classifier mClassifier = null;
    private Bitmap mInputBitmap = null;
    private Mat mRecentFrame = null;
    private Handler handler;
    private HandlerThread handlerThread;
    private int mPreviewWidth = 0;
    private int mPreviewHeight = 0;
    private boolean mProcessing = false;
    private JavaCameraView mCameraView;
    private ResultsView mVisuals;

    private static class XAPKFile {
        public final boolean mIsMain;
        public final int mFileVersion;
        public final long mFileSize;

        XAPKFile(boolean isMain, int fileVersion, long fileSize) {
            mIsMain = isMain;
            mFileVersion = fileVersion;
            mFileSize = fileSize;
        }
    }

    private static final XAPKFile[] xAPKS = {
        new XAPKFile(
            true, // true signifies a main file
            1, // the version of the APK that the file was uploaded against
            54112309L // the length of the file in bytes
        )
    };

    boolean expansionFilesDelivered() {
        for (XAPKFile xf : xAPKS) {
            String fileName = Helpers.getExpansionAPKFileName(
                this, xf.mIsMain, xf.mFileVersion
            );
            if (!Helpers.doesFileExist(this, fileName, xf.mFileSize, false)) {
                Log.e(
                    TAG,
                    "ExpansionAPKFile doesn't exist or has a wrong size (" + fileName + ")."
                );
                return false;
            }
        }
        return true;
    }

    private BaseLoaderCallback mBaseLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS: {
                    Log.i(TAG, "OpenCV loaded successfully");
                    // Load ndk built module, as specified in moduleName in build.gradle
                    // after opencv initialization
                    System.loadLibrary("processing");
                    mCameraView.enableView();
                }
                break;
                default: {
                    super.onManagerConnected(status);
                }
            }
        }
    };

    void setUpClassifier() {
        String modelFile = mMountedPath + MODEL_FILENAME;
        String labelFile = mMountedPath + LABEL_FILENAME;
        mClassifier = TensorFlowImageClassifier.create(
            getAssets(),
            modelFile,
            labelFile,
            INPUT_SIZE,
            IMAGE_MEAN,
            IMAGE_STD,
            INPUT_NAME,
            OUTPUT_NAME
        );
    }

    OnObbStateChangeListener mEventListener = new OnObbStateChangeListener() {
        @Override
        public void onObbStateChange(String path, int state) {
            mStatus = String.valueOf(state);
            if (state == OnObbStateChangeListener.MOUNTED) {
                mMountedPath = mSM.getMountedObbPath(mObbPath);
                setdir(mMountedPath);
                setUpClassifier();
                Log.d(TAG, "Mounted, path: " + mMountedPath);
            } else {
                mMountedPath = "";
                Log.d(TAG, "Mount failed, state: " + state);
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        );
        setContentView(R.layout.activity_main);

        // Permissions for Android 6+
        ActivityCompat.requestPermissions(
            MainActivity.this,
            new String[]{
                Manifest.permission.CAMERA,
                Manifest.permission.READ_EXTERNAL_STORAGE},
            1
        );

        // Check if expansion files are available before going any further
        if (!expansionFilesDelivered()) {
            Intent intent = new Intent(MainActivity.this, ProgressActivity.class);
            startActivity(intent);
        }

        ObbState state = (ObbState) getLastNonConfigurationInstance();
        if (state != null) {
            mSM = state.storageManager;
            mStatus = state.status.toString();
            mMountedPath = state.path.toString();
        } else {
            // Get an instance of the StorageManager
            mSM = (StorageManager) getApplicationContext().getSystemService(STORAGE_SERVICE);
        }

        // Get the path to the obb file
        File root = Environment.getExternalStorageDirectory();
        String path = root.toString() + EXP_PATH + getPackageName();
        String filename = "main."  + VERSION_CODE + "." + getPackageName() + ".obb";
        mObbPath = path + "/" + filename;

        if (mSM.mountObb(mObbPath, null, mEventListener)) {
            Log.d(TAG, "Starting mounting");
        } else {
            Log.d(TAG, "Start of mounting failed");
        }

        mCameraView = (JavaCameraView) findViewById(R.id.main_surface);
        mCameraView.setVisibility(SurfaceView.VISIBLE);
        mCameraView.setCvCameraViewListener(this);

        Display display = getWindowManager().getDefaultDisplay();
        int rotation = display.getRotation();
        switch (rotation) {
            case Surface.ROTATION_90:
                mScreenRotation = 90;
                break;
            case Surface.ROTATION_180:
                mScreenRotation = 180;
                break;
            case Surface.ROTATION_270:
                mScreenRotation = -90;
                break;
            default:
                mScreenRotation = 0;
                break;
        }

        // Listen orientation
        mVisuals = (ResultsView) findViewById(R.id.linear);
        mVisuals.setResults(VISUAL + "Calculating...");
        mOrientationListener = new OrientationEventListener(this,
            SensorManager.SENSOR_DELAY_NORMAL) {
                @Override
                public void onOrientationChanged(int orientation) {
                    orientation += mScreenRotation;
                    MainActivity.this.mOrientation = orientation;
                    MainActivity.this.mVisuals.adjust(orientation);
                }
            };

        if (mOrientationListener.canDetectOrientation()) {
            Log.v(TAG, "Can detect orientation");
            mOrientationListener.enable();
        } else {
            Log.v(TAG, "Cannot detect orientation");
            mOrientationListener.disable();
        }

    }

    @Override
    public synchronized void onPause() {
        handlerThread.quitSafely();
        try {
            handlerThread.join();
            handlerThread = null;
            handler = null;
        } catch (final InterruptedException e) {
            Log.e(TAG, "Exception!" + e.toString());
        }

        super.onPause();
        disableCamera();
    }

    @Override
    public synchronized void onResume() {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.d(
                TAG,
                "Internal OpenCV library not found. Using OpenCV Manager for initialization"
            );
            OpenCVLoader.initAsync(
                OpenCVLoader.OPENCV_VERSION_3_0_0, this, mBaseLoaderCallback
            );
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mBaseLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }

        handlerThread = new HandlerThread("inference");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
    }

    public void disableCamera() {
        if (mCameraView != null)
            mCameraView.disableView();
    }

    void startInference() {
        runInBackground(
            new Runnable() {
                @Override
                public void run() {
                Log.d(TAG, "Starting inference image processing");
                mProcessing = true;
                // Crop
                int side = 0;
                int x = 0;
                int y = 0;
                if (mPreviewHeight < mPreviewWidth) {
                    side = mPreviewHeight;
                    x = (mPreviewWidth - side) / 2;
                    y = 0;
                } else {
                    side = mPreviewWidth;
                    y = (mPreviewHeight - side) / 2;
                    x = 0;
                }
                Rect roi = new Rect(x, y, side, side);
                Mat img = new Mat(mRecentFrame, roi);

                // Resize
                Size size = new Size(INPUT_SIZE, INPUT_SIZE);
                Imgproc.resize(img, img, size);

                // Rotate
                int angle = mOrientation;
                if (angle >= 45 && angle < 135) {
                    // 90 cw
                    Core.flip(img.t(), img, 1);
                } else if (angle >= 135 && angle < 225) {
                    // 180
                    Core.flip(img, img, -1); 
                } else if (angle >= 225 && angle < 315) {
                    // 90 ccw
                    Core.flip(img.t(), img, 0);
                }

                // Convert
                Utils.matToBitmap(img, mInputBitmap);

                mProcessing = false;

                Log.d(TAG, "Starting inference");
                final long startTime = SystemClock.uptimeMillis();
                final List<Classifier.Recognition> results = mClassifier.recognizeImage(mInputBitmap);
                long lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime;

                // Output resluts
                String output = VISUAL;
                for (Classifier.Recognition res : results) {
                    output += res.toString() + "\n";
                }
                output += lastProcessingTimeMs + " ms";

                setInference(output);
                startInference();
            }
        });
    }

    public void onCameraViewStarted(int width, int height) {
        // Calculate ratio to stretch camera preview on screen
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        int screenHeight = displayMetrics.heightPixels;
        int screenWidth = displayMetrics.widthPixels;
        float ratio = 0;
        float heightRatio = 0;
        float widthRatio = 0;
        if (width < screenWidth)
            widthRatio = (float) screenWidth / (float) width;
        if (height < screenHeight)
            heightRatio = (float) screenHeight / (float) height;
        ratio = (heightRatio > widthRatio) ? heightRatio : widthRatio;
        mCameraView.setScale(ratio);

        mPreviewWidth = width;
        mPreviewHeight = height;

        // Create empty bitmaps until real camera frames arrived
        mInputBitmap = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Config.ARGB_8888);
        Bitmap empty = Bitmap.createBitmap(mPreviewWidth, mPreviewHeight, Config.ARGB_8888);
        mRecentFrame = new Mat(mPreviewWidth, mPreviewHeight, CvType.CV_8UC4);
        Utils.bitmapToMat(empty, mRecentFrame);

        handlerThread = new HandlerThread("inference");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
        startInference();
    }

    public void onCameraViewStopped() {
    }

    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        Mat image = inputFrame.rgba();
        if (!mProcessing)
            mRecentFrame = image.clone();
        salt(image.getNativeObjAddr());
        return image;
    }

    public void setInference(String letters) {
        final String inference = letters;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mVisuals.setResults(inference);
            }
        });
    }

    protected synchronized void runInBackground(final Runnable r) {
        if (handler != null) {
            handler.post(r);
        }
    }
    
    @Override
    protected synchronized void onDestroy() {
        super.onDestroy();
        disableCamera();
    }

    private static class ObbState {
        public StorageManager storageManager;
        public CharSequence status;
        public CharSequence path;
        ObbState(StorageManager storageManager, CharSequence status, CharSequence path) {
            this.storageManager = storageManager;
            this.status = status;
            this.path = path;
        }
    }

    public native void salt(long image);
    public native double setdir(String path);

}


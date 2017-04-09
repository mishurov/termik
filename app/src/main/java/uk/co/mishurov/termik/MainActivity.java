package uk.co.mishurov.termik;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Intent;
import android.hardware.Camera;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.SurfaceView;
import android.view.WindowManager;

import com.google.android.vending.expansion.downloader.Helpers;
import com.google.android.vending.expansion.downloader.IDownloaderService;
import com.google.android.vending.expansion.downloader.IStub;

import android.hardware.SensorManager;
import android.view.OrientationEventListener;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;

import android.os.Bundle;
import android.util.Log;

import android.app.Activity;
import android.os.Bundle;
import android.os.Environment;
import android.os.storage.OnObbStateChangeListener;
import android.os.storage.StorageManager;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

import java.io.File;
import android.util.DisplayMetrics;

import org.tensorflow.demo.Classifier;
import org.tensorflow.demo.TensorFlowImageClassifier;
import org.tensorflow.demo.ImageUtils;
import android.view.Display;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Matrix;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import java.util.List;

import org.opencv.imgproc.Imgproc;
import org.opencv.core.Size;
import org.opencv.android.Utils;
import org.opencv.core.Rect;
import org.opencv.core.Core;


public class MainActivity extends AppCompatActivity
                          implements CameraBridgeViewBase.CvCameraViewListener2 {

    private static final String TAG = "Termik";
    private JavaCameraView _cameraBridgeViewBase;

    OrientationEventListener mOrientationListener;
    private int orientation = 0;
    private ResultsView mVisuals;
    private TextView mGuess;
    /* obb */
    private static String mObbPath;
    private String mStatus;
    private String mPath;
    private StorageManager mSM;
    /* downloader */
    private IStub mDownloaderClientStub;
    private IDownloaderService mRemoteService;
    private ProgressDialog mProgressDialog;
    private final static String EXP_PATH = "/Android/obb/";
    
    /* classifier */
    private Classifier classifier = null;
    private static final String MODEL_FILENAME = "/tensorflow_inception_graph.pb";
    private static final String LABEL_FILENAME = "/imagenet_comp_graph_label_strings.txt";
    private static final int INPUT_SIZE = 224;
    private static final int IMAGE_MEAN = 117;
    private static final float IMAGE_STD = 1;
    private static final String INPUT_NAME = "input";
    private static final String OUTPUT_NAME = "output";
    private Bitmap rgbFrameBitmap = null;
    private Bitmap croppedBitmap = null;
    private Matrix frameToCropTransform;
    private Matrix cropToFrameTransform;
    private static final boolean MAINTAIN_ASPECT = true;
    private Handler handler;
    private HandlerThread handlerThread;
    private long lastProcessingTimeMs;
    private int previewWidth = 0;
    private int previewHeight = 0;
    private Mat mRecentFrame;
    private boolean computing = false;

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
            // Log.v(TAG, "XAPKFile name : " + fileName);
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

    private BaseLoaderCallback _baseLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS: {
                    Log.i(TAG, "OpenCV loaded successfully");
                    // Load ndk built module, as specified in moduleName in build.gradle
                    // after opencv initialization
                    System.loadLibrary("native-lib");
                    _cameraBridgeViewBase.enableView();
                }
                break;
                default: {
                    super.onManagerConnected(status);
                }
            }
        }
    };

    void setUpClassifier() {
        String modelFile = mPath + MODEL_FILENAME;
        String labelFile = mPath + LABEL_FILENAME;
        classifier = TensorFlowImageClassifier.create(
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
            Log.d(TAG, "path=" + path + "; state=" + state);
            mStatus = String.valueOf(state);
            if (state == OnObbStateChangeListener.MOUNTED) {
                mPath = mSM.getMountedObbPath(mObbPath);
                Log.d(TAG, "MAUNTED =" + mPath);
                setdir(mPath);
                setUpClassifier();
            } else {
                mPath = "";
                Log.d(TAG, "NE MAUNTED =" + mPath + "state: " + state);
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

        // Check if expansion files are available before going any further
        if (!expansionFilesDelivered()) {
            Intent intent = new Intent(MainActivity.this, ProgressActivity.class);
            startActivity(intent);
        }

        ObbState state = (ObbState) getLastNonConfigurationInstance();
        if (state != null) {
            mSM = state.storageManager;
            mStatus = state.status.toString();
            mPath = state.path.toString();
        } else {
            // Get an instance of the StorageManager
            mSM = (StorageManager) getApplicationContext().getSystemService(STORAGE_SERVICE);
        }

        // Permissions for Android 6+
        ActivityCompat.requestPermissions(
            MainActivity.this,
            new String[]{
                Manifest.permission.CAMERA,
                Manifest.permission.READ_EXTERNAL_STORAGE},
            1
        );
        mObbPath = "/mnt/sdcard/Android/obb/uk.co.mishurov.termik/main.1.uk.co.mishurov.termik.obb";
        if (mSM.mountObb(mObbPath, null, mEventListener)) {
            Log.d(TAG, "STARING MAUNT");
        } else {
            Log.d(TAG, "NE MAUNT");
        }

        _cameraBridgeViewBase = (JavaCameraView) findViewById(R.id.main_surface);
        _cameraBridgeViewBase.setVisibility(SurfaceView.VISIBLE);
        _cameraBridgeViewBase.setCvCameraViewListener(this);

        croppedBitmap = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Config.ARGB_8888);
        // Listen orientation
        mVisuals = (ResultsView) findViewById(R.id.linear);
        mVisuals.setResults("Calculating...");
        mOrientationListener = new OrientationEventListener(this,
            SensorManager.SENSOR_DELAY_NORMAL) {
                @Override
                public void onOrientationChanged(int orientation) {
                    MainActivity.this.orientation = orientation;
                    MainActivity.this.mVisuals.adjust(orientation);
                }
            };

        if (mOrientationListener.canDetectOrientation() == true) {
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
                OpenCVLoader.OPENCV_VERSION_3_0_0, this, _baseLoaderCallback
            );
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            _baseLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
        handlerThread = new HandlerThread("inference");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
    }

    public void disableCamera() {
        if (_cameraBridgeViewBase != null)
            _cameraBridgeViewBase.disableView();
    }

    void startInference() {
        runInBackground(
            new Runnable() {
                @Override
                public void run() {
                Log.i(TAG, "Starting inference");
                computing = true;

                // crop
                int side = 0;
                int x = 0;
                int y = 0;
                if (previewHeight < previewWidth) {
                    side = previewHeight;
                    x = (previewWidth - side) / 2;
                    y = 0;
                } else {
                    side = previewWidth;
                    y = (previewHeight - side) / 2;
                    x = 0;
                }
                Rect roi = new Rect(x, y, side, side);
                Mat procImage = new Mat(mRecentFrame, roi);

                // resize
                Size sz = new Size(INPUT_SIZE, INPUT_SIZE);
                Imgproc.resize(procImage, procImage, sz);

                // rotate
                int angle = orientation;
                Log.i(TAG, "Rotation: " + orientation);
                if (angle >= 45 && angle < 135) {
                    // 90 cw
                    Core.flip(procImage.t(), procImage, 1);
                } else if (angle >= 135 && angle < 225) {
                    // 180
                    Core.flip(procImage, procImage, -1); 
                } else if (angle >= 225 && angle < 315) {
                    // 90 ccw
                    Core.flip(procImage.t(), procImage, 0);
                }

                // convert
                Utils.matToBitmap(procImage, croppedBitmap);

                final long startTime = SystemClock.uptimeMillis();
                computing = false;

                final List<Classifier.Recognition> results = classifier.recognizeImage(croppedBitmap);
                lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime;
                Log.i(TAG, "Calculated");
                String output = "";
                for (Classifier.Recognition res : results) {
                    Log.i(TAG, "res " + res.getTitle());

                    output += String.format(
                        java.util.Locale.US,
                        "%s %.2f\n",
                        res.getTitle(),
                        res.getConfidence()
                    );
                }
                output += "Processing time: " + lastProcessingTimeMs;

                setInference(output);
                startInference();
            }
        });
    }

    public void onCameraViewStarted(int width, int height) {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        int screenHeight = displayMetrics.heightPixels;
        int screenWidth = displayMetrics.widthPixels;
        float ratio = 0;
        float heightRatio = 0;
        float widthRatio = 0;
        if (height < screenHeight)
            heightRatio = (float) screenHeight / (float) height;
        if (width < screenWidth)
            widthRatio = (float) screenWidth / (float) width;
        ratio = (heightRatio > widthRatio) ? heightRatio : widthRatio;
        _cameraBridgeViewBase.setScale(ratio);

        previewWidth = width;
        previewHeight = height;

        startInference();
    }

    public void onCameraViewStopped() {
    }

    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        Mat image = inputFrame.rgba();
        if (!computing)
            mRecentFrame = image.clone();
        //salt(image.getNativeObjAddr());
        return image;
    }

    public native void salt(long image);
    public native double setdir(String path);

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
}


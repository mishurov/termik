package uk.co.mishurov.termik;

import android.Manifest;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Messenger;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.LinearLayout;

import com.google.android.vending.expansion.downloader.DownloadProgressInfo;
import com.google.android.vending.expansion.downloader.DownloaderClientMarshaller;
import com.google.android.vending.expansion.downloader.DownloaderServiceMarshaller;
import com.google.android.vending.expansion.downloader.Helpers;
import com.google.android.vending.expansion.downloader.IDownloaderClient;
import com.google.android.vending.expansion.downloader.IDownloaderService;
import com.google.android.vending.expansion.downloader.IStub;

import android.hardware.SensorManager;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.preference.PreferenceManager;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.widget.Toast;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;

import java.util.List;

public class MainActivity extends AppCompatActivity
                          implements CameraBridgeViewBase.CvCameraViewListener2 {

    private static final String TAG = "Termik";
    private TermView _cameraBridgeViewBase;

    OrientationEventListener mOrientationListener;
    int orientation;
    private LinearLayout mVisuals;

    /* downloader */
    private IStub mDownloaderClientStub;
    private IDownloaderService mRemoteService;
    private ProgressDialog mProgressDialog;
    private static final String LOG_TAG = "Sample";
    private final static String EXP_PATH = "/Android/obb/";

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
            53895393L // the length of the file in bytes
        )
    };

    boolean expansionFilesDelivered() {
        for (XAPKFile xf : xAPKS) {
            String fileName = Helpers.getExpansionAPKFileName(
                this, xf.mIsMain, xf.mFileVersion
            );
            // Log.v(LOG_TAG, "XAPKFile name : " + fileName);
            if (!Helpers.doesFileExist(this, fileName, xf.mFileSize, false)) {
                Log.e(
                    LOG_TAG,
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
            new String[]{Manifest.permission.CAMERA},
            1
        );

        _cameraBridgeViewBase = (TermView) findViewById(R.id.main_surface);
        _cameraBridgeViewBase.setVisibility(SurfaceView.VISIBLE);
        _cameraBridgeViewBase.setCvCameraViewListener(this);
        
        // Check if expansion files are available before going any further
        if (!expansionFilesDelivered()) {
            Intent intent = new Intent(MainActivity.this, ProgressActivity.class);
            startActivity(intent);
        }

        // Listen orientation
        mVisuals = (LinearLayout) findViewById(R.id.linear);
        orientation = 0;
        mOrientationListener = new OrientationEventListener(this,
            SensorManager.SENSOR_DELAY_NORMAL) {
                @Override
                public void onOrientationChanged(int orientation) {
                    MainActivity.this.orientation = orientation;
                    MainActivity.this.mVisuals.setRotation(-orientation-90);
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
    public void onPause() {
        super.onPause();
        disableCamera();
    }

    @Override
    public void onResume() {
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
    }

    public void disableCamera() {
        if (_cameraBridgeViewBase != null)
            _cameraBridgeViewBase.disableView();
    }

    public void onCameraViewStarted(int width, int height) {
    }

    public void onCameraViewStopped() {
    }

    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        Mat matGray = inputFrame.gray();
        salt(matGray.getNativeObjAddr(), 2000);
        return matGray;
    }

    public native void salt(long matAddrGray, int nbrElem);

    @Override
    protected void onDestroy() {  
        super.onDestroy();
        disableCamera();
    }
}


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
import android.widget.LinearLayout;

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

public class MainActivity extends AppCompatActivity
                          implements CameraBridgeViewBase.CvCameraViewListener2 {

    private static final String TAG = "Termik";
    private TermView _cameraBridgeViewBase;

    OrientationEventListener mOrientationListener;
    int orientation;
    private LinearLayout mVisuals;
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

    OnObbStateChangeListener mEventListener = new OnObbStateChangeListener() {
        @Override
        public void onObbStateChange(String path, int state) {
            Log.d(TAG, "path=" + path + "; state=" + state);
            mStatus = String.valueOf(state);
            if (state == OnObbStateChangeListener.MOUNTED) {
                mPath = mSM.getMountedObbPath(mObbPath);
                Log.d(TAG, "MAUNTED =" + mPath);
                setdir(mPath);
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

        _cameraBridgeViewBase = (TermView) findViewById(R.id.main_surface);
        _cameraBridgeViewBase.setVisibility(SurfaceView.VISIBLE);
        _cameraBridgeViewBase.setCvCameraViewListener(this);

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
        Mat image = inputFrame.rgba();
        salt(image.getNativeObjAddr());
        return image;
    }

    public native void salt(long image);
    public native double setdir(String path);

    @Override
    protected void onDestroy() {
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


package uk.co.mishurov.termik;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.os.Messenger;
import android.Manifest;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.util.Log;

import com.google.android.vending.expansion.downloader.DownloadProgressInfo;
import com.google.android.vending.expansion.downloader.DownloaderClientMarshaller;
import com.google.android.vending.expansion.downloader.DownloaderServiceMarshaller;
import com.google.android.vending.expansion.downloader.Helpers;
import com.google.android.vending.expansion.downloader.IDownloaderClient;
import com.google.android.vending.expansion.downloader.IDownloaderService;
import com.google.android.vending.expansion.downloader.IStub;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;


public class MainActivity extends AppCompatActivity
                          implements CameraBridgeViewBase.CvCameraViewListener2,
                                     IDownloaderClient{

    private static final String TAG = "OCVSample::Activity";
    private CameraBridgeViewBase _cameraBridgeViewBase;

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

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        // Permissions for Android 6+
        ActivityCompat.requestPermissions(
            MainActivity.this,
            new String[]{Manifest.permission.CAMERA},
            1
        );

        _cameraBridgeViewBase = (CameraBridgeViewBase) findViewById(R.id.main_surface);
        _cameraBridgeViewBase.setVisibility(SurfaceView.VISIBLE);
        _cameraBridgeViewBase.setCvCameraViewListener(this);

        // Check if expansion files are available before going any further
        if (!expansionFilesDelivered()) {
            disableCamera();

            try {
                Intent launchIntent = this.getIntent();
                // Build an Intent to start this activity from the Notification
                Intent notifierIntent = new Intent(
                    MainActivity.this, MainActivity.this.getClass()
                );
                notifierIntent.setFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                );
                notifierIntent.setAction(launchIntent.getAction());

                if (launchIntent.getCategories() != null) {
                    for (String category : launchIntent.getCategories()) {
                        notifierIntent.addCategory(category);
                    }
                }

                PendingIntent pendingIntent = PendingIntent.getActivity(
                    this, 0, notifierIntent, PendingIntent.FLAG_UPDATE_CURRENT
                );

                // Start the download service (if required)
                Log.v(LOG_TAG, "Start the download service");
                int startResult = DownloaderClientMarshaller.startDownloadServiceIfRequired(
                    this, pendingIntent, AssetDownloaderService.class
                );

                // If download has started, initialize activity to show progress
                if (startResult != DownloaderClientMarshaller.NO_DOWNLOAD_REQUIRED) {
                    Log.v(LOG_TAG, "initialize activity to show progress");
                    // Instantiate a member instance of IStub
                    mDownloaderClientStub = DownloaderClientMarshaller.CreateStub(
                        this, AssetDownloaderService.class
                    );
                    // Shows download progress
                    mProgressDialog = new ProgressDialog(MainActivity.this);
                    mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                    mProgressDialog.setMessage(getResources().getString(R.string.downloading_assets));
                    mProgressDialog.setCancelable(false);
                    mProgressDialog.show();
                    return;
                }
                // If the download wasn't necessary, fall through to start the app
                else {
                    Log.v(LOG_TAG, "No download required");
                }
            }
            catch (NameNotFoundException e) {
                Log.e(LOG_TAG, "Cannot find own package! MAYDAY!");
                e.printStackTrace();
            }
            catch (Exception e) {
                Log.e(LOG_TAG, e.getMessage());
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        disableCamera();
    }

    @Override
    public void onResume() {
        if (null != mDownloaderClientStub) {
            mDownloaderClientStub.connect(this);
        }

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

    /*
    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case 1: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.
                } else {
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    Toast.makeText(MainActivity.this, "Permission denied to read your External storage", Toast.LENGTH_SHORT).show();
                }
                return;
            }
            // other 'case' lines to check for other
            // permissions this app might request
        }
    }
    */

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

    /**
     * Connect the stub to our service on start.
     */
    @Override
    protected void onStart() {
        if (null != mDownloaderClientStub) {
            mDownloaderClientStub.connect(this);
        }
        super.onStart();
    }

    @Override
    protected void onStop() {
        if (null != mDownloaderClientStub) {
            mDownloaderClientStub.disconnect(this);
        }
        super.onStop();
    }

    @Override
    public void onServiceConnected(Messenger m) {
        mRemoteService = DownloaderServiceMarshaller.CreateProxy(m);
        mRemoteService.onClientUpdated(mDownloaderClientStub.getMessenger());
    }

    @Override
    public void onDownloadProgress(DownloadProgressInfo progress) {
        long percents = progress.mOverallProgress * 100 / progress.mOverallTotal;
        Log.v(LOG_TAG, "DownloadProgress:"+Long.toString(percents) + "%");
        mProgressDialog.setProgress((int) percents);
    }

    @Override
    public void onDownloadStateChanged(int newState) {
        Log.v(
            LOG_TAG,
            "DownloadStateChanged : " + getString(
                Helpers.getDownloaderStringResourceIDFromState(newState)
            )
        );

        switch (newState) {
            case STATE_DOWNLOADING:
                Log.v(LOG_TAG, "Downloading...");
                break;
            case STATE_COMPLETED: // The download was finished
                // validateXAPKZipFiles();
                mProgressDialog.setMessage(getResources().getString(
                    R.string.preparing_assets)
                );
                // dismiss progress dialog
                mProgressDialog.dismiss();
                // Load url
                //super.loadUrl(Config.getStartUrl());
                break;
            case STATE_FAILED_UNLICENSED:
            case STATE_FAILED_FETCHING_URL:
            case STATE_FAILED_SDCARD_FULL:
            case STATE_FAILED_CANCELED:
            case STATE_FAILED: 
                Builder alert = new AlertDialog.Builder(this);
                alert.setTitle(getResources().getString(R.string.error));
                alert.setMessage(getResources().getString(R.string.download_failed));
                alert.setNeutralButton(getResources().getString(R.string.close), null);
                alert.show();
                break;
        }
    }
}


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

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;

import uk.co.mishurov.termik.R;

import java.util.List;
import android.widget.ProgressBar;
import android.widget.TextView;

public class ProgressActivity extends AppCompatActivity
                              implements IDownloaderClient{

    private IStub mDownloaderClientStub;
    private IDownloaderService mRemoteService;
    private ProgressBar mProgressDialog;
    private TextView mProgressText;
    private static final String LOG_TAG = "Sample";
    private final static String EXP_PATH = "/Android/obb/";

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_progress);
        mProgressDialog = (ProgressBar) findViewById(R.id.progress_bar);
        mProgressText = (TextView) findViewById(R.id.progress_text);
        try {
            Intent launchIntent = this.getIntent();
            // Build an Intent to start this activity from the Notification
            Intent notifierIntent = new Intent(
                ProgressActivity.this, MainActivity.class
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
                mProgressText.setText(getResources().getString(R.string.downloading_assets));
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



    @Override
    public void onResume() {
        super.onResume();
        if (null != mDownloaderClientStub) {
            mDownloaderClientStub.connect(this);
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
        //mProgressDialog.setProgress((int) percents);
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
                mProgressText.setText(getResources().getString(
                    R.string.preparing_assets)
                );
                // dismiss progress dialog
                //mProgressDialog.dismiss();
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

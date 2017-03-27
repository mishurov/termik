package uk.co.mishurov.termik;
// DownloaderServiceBroadcastReceiver.java
public class DownloaderServiceBroadcastReceiver extends android.content.BroadcastReceiver {  
    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            DownloaderClientMarshaller.startDownloadServiceIfRequired(context, intent, DownloaderService.class);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
    }
}

package ifa.ifatiguealerter;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.view.accessibility.AccessibilityEvent;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Calendar;
import java.util.concurrent.TimeUnit;

public class WindowChangeDetectingService extends AccessibilityService {

    public static final String TAG = "opencv";
    public static int BLINKS_COUNT = 0;
    public static boolean externalStorageWritable;

    private String currentApp = null;
    private long launched_time;
    private PackageManager packageManager;
    private static File file_out;

    private Calendar calendar;
    private Thread testThread;
    private boolean run_flag = true;
    private boolean service_running = false;

    private String last_start_time = "";

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();

        //Configure these here for compatibility with API 13 and below.
        AccessibilityServiceInfo config = new AccessibilityServiceInfo();
        config.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
        config.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;

        if (Build.VERSION.SDK_INT >= 16)
            //Just in case this helps
            config.flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;

        setServiceInfo(config);

        packageManager = getApplicationContext().getPackageManager();

        launched_time = System.currentTimeMillis();
        file_out = new File(Environment.getExternalStorageDirectory() + "/app_watch/log.txt");

        startService(new Intent(this, BlinkCounter.class));
        service_running = true;

        testThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (run_flag) {
                    try {
                        String dataToWrite = "";
                        calendar = Calendar.getInstance();
                        last_start_time = calendar.getTime().toString();
                        dataToWrite += last_start_time + " --> ";
                        Thread.sleep(1000*60);
                        calendar = Calendar.getInstance();
                        dataToWrite += calendar.getTime().toString() + " : " + BLINKS_COUNT + "\n";
                        writeToFile(dataToWrite);
                        BLINKS_COUNT = 0;
                    } catch (InterruptedException | IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
        testThread.start();

        try {
            externalStorageWritable = fileOutCheck();
            writeToFile("\n");
        } catch (IOException e) {
            e.printStackTrace();
        }

        int curBrightness = android.provider.Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS,-1);
        try {
            writeToFile("Brightness:" + curBrightness + "/255\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
        Intent intent = new Intent(this, DisplayResolActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            ComponentName componentName = new ComponentName(
                    event.getPackageName().toString(),
                    event.getClassName().toString()
            );

            if (!isExternalStorageWritable())
                return;


            ActivityInfo activityInfo = tryGetActivity(componentName);
            boolean isActivity = activityInfo != null;
            if (isActivity) {
                if (currentApp == null || !currentApp.equals(componentName.getPackageName())) {
                    long now_time = System.currentTimeMillis();
                    String dataToWrite = "";
                    try {
                        fileOutCheck();

                        if (currentApp != null) {
                            // get duration of the last app
                            long duration = now_time - launched_time;
                            long durationM = TimeUnit.MILLISECONDS.toMinutes(duration);
                            long durationS = TimeUnit.MILLISECONDS.toSeconds(duration);
                            durationS = durationS > 60 ? durationS % 60 : durationS;
                            calendar = Calendar.getInstance();
                            dataToWrite += "END " + calendar.getTime().toString() + " DURATION(m:s) " + durationM + ":" + durationS + "\n";
                        }
                        launched_time = now_time;
                        currentApp = componentName.getPackageName();

                        // Get app name
                        ApplicationInfo applicationInfo;
                        try {
                            applicationInfo = packageManager.getApplicationInfo(currentApp, 0);
                        } catch (PackageManager.NameNotFoundException e) {
                            e.printStackTrace();
                            applicationInfo = null;
                        }
                        String app_name = (String) (applicationInfo != null ? packageManager.getApplicationLabel(applicationInfo) : currentApp);

                        if (app_name.toLowerCase().contains("cam")) {
                            stopService(new Intent(this, BlinkCounter.class));
                            service_running = false;
                        } else if (!service_running) {
                            startService(new Intent(this, BlinkCounter.class));
                            service_running = true;
                        }

                        calendar = Calendar.getInstance();
                        dataToWrite += "START " + calendar.getTime().toString() + "\n";
                        dataToWrite += app_name + "\n";
                        writeToFile(dataToWrite);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        run_flag = false;
        testThread.interrupt();
        stopService(new Intent(getApplicationContext(), BlinkCounter.class));
        try {
            String dataToWrite = "";
            dataToWrite += last_start_time + " --> ";
            calendar = Calendar.getInstance();
            dataToWrite += calendar.getTime().toString() + " : " + BLINKS_COUNT + "\n";
            writeToFile(dataToWrite);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private ActivityInfo tryGetActivity(ComponentName componentName) {
        try {
            return getPackageManager().getActivityInfo(componentName, 0);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    @Override
    public void onInterrupt() {

    }

    private boolean fileOutCheck() throws IOException {
        if (!file_out.exists()) {
            File parent = new File(Environment.getExternalStorageDirectory() + "/app_watch");
            if (!parent.exists())
                if (!parent.mkdir())
                    return false;
            return file_out.createNewFile();
        }
        return true;
    }

    /* Checks if external storage is available for read and write */
    private boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(state);
    }

    public static void writeToFile(String data) throws IOException {
        if (!externalStorageWritable)
            return;
        FileWriter writer = new FileWriter(file_out, true);
        writer.write(data);
        writer.flush();
        writer.close();
    }
}

package com.hilolive.v2;

import android.app.Service;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

public class CaptureService extends Service {
    private static final String TAG = "HiLoCapture";
    private static final String CHANNEL = "hilo_capture";

    private MediaProjection projection;
    private VirtualDisplay display;
    private ImageReader reader;
    private HandlerThread thread;
    private Handler handler;
    private int frameCount = 0;

    @Override
    public void onCreate() {
        super.onCreate();

        NotificationManager nm =
            getSystemService(NotificationManager.class);

        nm.createNotificationChannel(
            new NotificationChannel(
                CHANNEL,
                "HiLo Screen Capture",
                NotificationManager.IMPORTANCE_LOW
            )
        );
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notification =
            new Notification.Builder(this, CHANNEL)
                .setContentTitle("HiLo Analyzer V2")
                .setContentText("Screen capture active")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .build();

        startForeground(
            1001,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        );

        if (intent == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        int resultCode = intent.getIntExtra("resultCode", 0);
        Intent resultData = (Intent)
            intent.getParcelableExtra("resultData");

        if (resultCode == 0 || resultData == null) {
            Log.e(TAG, "Screen capture permission missing");
            stopSelf();
            return START_NOT_STICKY;
        }

        MediaProjectionManager manager =
            (MediaProjectionManager) getSystemService(
                MEDIA_PROJECTION_SERVICE
            );

        projection = manager.getMediaProjection(
            resultCode, resultData
        );

        projection.registerCallback(
            new MediaProjection.Callback() {
                @Override
                public void onStop() {
                    stopSelf();
                }
            },
            new Handler(getMainLooper())
        );

        startFrames();

        return START_NOT_STICKY;
    }

    private void startFrames() {
        DisplayMetrics metrics = new DisplayMetrics();

        ((WindowManager) getSystemService(WINDOW_SERVICE))
            .getDefaultDisplay().getRealMetrics(metrics);

        int width = metrics.widthPixels;
        int height = metrics.heightPixels;

        thread = new HandlerThread("HiLoFrames");
        thread.start();
        handler = new Handler(thread.getLooper());

        reader = ImageReader.newInstance(
            width, height,
            PixelFormat.RGBA_8888, 2
        );

        reader.setOnImageAvailableListener(r -> {
            Image image = r.acquireLatestImage();

            if (image != null) {
                try {
                    frameCount++;

                    
                // HILO_FRAME_COUNTER_V1
                if (frameCount % 30 == 1) {
                    android.app.NotificationManager nm =
                        (android.app.NotificationManager)
                        getSystemService(NOTIFICATION_SERVICE);
                    android.app.Notification n =
                        new android.app.Notification.Builder(this, "hilo_capture")
                        .setSmallIcon(android.R.drawable.ic_menu_camera)
                        .setContentTitle("HiLo Capture Active")
                        .setContentText("Frames received: " + frameCount)
                        .setOnlyAlertOnce(true)
                        .build();
                    nm.notify(2002, n);
                }

                if (frameCount % 30 == 1) {
                        Log.i(TAG,
                            "Frame received: " +
                            image.getWidth() + "x" +
                            image.getHeight() +
                            " count=" + frameCount
                        );
                    }
                } finally {
                    image.close();
                }
            }
        }, handler);

        display = projection.createVirtualDisplay(
            "HiLoCapture",
            width, height,
            metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.getSurface(),
            null,
            handler
        );

        Log.i(TAG, "Capture started: " + width + "x" + height);
    }

    @Override
    public void onDestroy() {
        if (display != null) display.release();
        if (reader != null) reader.close();
        if (projection != null) projection.stop();
        if (thread != null) thread.quitSafely();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

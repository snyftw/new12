package com.hilolive.v2;

import android.app.Service;
import android.graphics.Bitmap;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.google.mlkit.vision.text.TextRecognizer;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
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
    private final java.util.concurrent.atomic.AtomicBoolean ocrBusy = new java.util.concurrent.atomic.AtomicBoolean(false);
    // HILO_AUTO_CARD_V1
    private String pendingCard = "";
    private int pendingCount = 0;
    private String lastCard = "";
    private TextRecognizer sharedRecognizer;


    @Override
    public void onCreate() {
        super.onCreate();
        sharedRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

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

                    // HILO_BITMAP_CAPTURE_V1
                    if (frameCount % 5 == 1) {
                        android.media.Image.Plane plane = image.getPlanes()[0];
                        java.nio.ByteBuffer buffer = plane.getBuffer();
                        int pixelStride = plane.getPixelStride();
                        int rowStride = plane.getRowStride();
                        int padding = rowStride - pixelStride * image.getWidth();

                        Bitmap bitmap = Bitmap.createBitmap(
                            image.getWidth() + padding / pixelStride,
                            image.getHeight(),
                            Bitmap.Config.ARGB_8888
                        );
                        bitmap.copyPixelsFromBuffer(buffer);

                        Bitmap screenBitmap = Bitmap.createBitmap(
                            bitmap, 0, 0,
                            image.getWidth(), image.getHeight()
                        );
                        bitmap.recycle();

                        Log.i(TAG, "Bitmap ready: " +
                            screenBitmap.getWidth() + "x" +
                            screenBitmap.getHeight());

                        
                // OCR: focus on the upper-left rank; upscale for ML Kit.
                final Bitmap ocrBitmap = screenBitmap;
                int cropX = Math.round(ocrBitmap.getWidth() * 0.23f);
                int cropY = Math.round(ocrBitmap.getHeight() * 0.25f);
                int cropW = Math.round(ocrBitmap.getWidth() * 0.115f);
                int cropH = Math.round(ocrBitmap.getHeight() * 0.095f);
                if (cropX >= 0 && cropY >= 0 && cropW > 0 && cropH > 0
                    && cropX + cropW <= ocrBitmap.getWidth()
                    && cropY + cropH <= ocrBitmap.getHeight()
                    && ocrBusy.compareAndSet(false, true)) {
                    Bitmap crop = Bitmap.createBitmap(ocrBitmap, cropX, cropY, cropW, cropH);
                    Bitmap enlarged = Bitmap.createScaledBitmap(crop, cropW * 3, cropH * 3, true);
                    crop.recycle();
                    InputImage input = InputImage.fromBitmap(enlarged, 0);
                    sharedRecognizer.process(input)
                        .addOnSuccessListener(result -> {
                            String raw = result.getText().trim();
                            broadcastValue("com.hilolive.v2.HILO_RAW_UPDATE", "raw", raw);
                            String candidate = normalizeCard(raw);
                            if (candidate == null) {
                                broadcastValue("com.hilolive.v2.OCR_STATUS", "status", "Uncertain OCR");
                                pendingCard = ""; pendingCount = 0;
                                return;
                            }
                            // Ambiguous six/nine requires two consecutive identical reads.
                            int needed = ("6".equals(candidate) || "9".equals(candidate)) ? 2 : 1;
                            if (candidate.equals(pendingCard)) pendingCount++;
                            else { pendingCard = candidate; pendingCount = 1; }
                            if (pendingCount < needed || candidate.equals(lastCard)) return;
                            lastCard = candidate;
                            broadcastValue("com.hilolive.v2.CARD_UPDATE", "card", candidate);
                            broadcastValue("com.hilolive.v2.OCR_STATUS", "status", "Detected");
                        })
                        .addOnFailureListener(e -> Log.e(TAG, "OCR failed", e))
                        .addOnCompleteListener(task -> {
                            enlarged.recycle();
                            ocrBusy.set(false);
                        });
                }
                ocrBitmap.recycle();

                    }

                    // HILO_FRAME_BROADCAST_V1
                    if (frameCount % 30 == 1) {
                        Intent update = new Intent(
                            "com.hilolive.v2.FRAME_UPDATE");
                        update.setPackage(getPackageName());
                        update.putExtra("frames", frameCount);
                        sendBroadcast(update);
                    }

                    
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

    private void broadcastValue(String action, String key, String value) {
        Intent update = new Intent(action);
        update.setPackage(getPackageName());
        update.putExtra(key, value);
        sendBroadcast(update);
    }

    private String normalizeCard(String raw) {
        if (raw == null) return null;
        String value = raw.toUpperCase(java.util.Locale.US)
            .replaceAll("\\s+", "")
            .replace("♠", "").replace("♥", "").replace("♦", "").replace("♣", "");
        if (value.equals("IO") || value.equals("I0") || value.equals("1O")
            || value.equals("LO")) value = "10";
        return value.matches("A|[2-9]|10|J|Q|K") ? value : null;
    }

    @Override
    public void onDestroy() {
        if (sharedRecognizer != null) sharedRecognizer.close();
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

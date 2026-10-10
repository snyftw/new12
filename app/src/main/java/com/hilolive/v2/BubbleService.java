package com.hilolive.v2;

import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.IBinder;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

public class BubbleService extends Service {
    private WindowManager wm;
    private View bubble;
    private WindowManager.LayoutParams params;
    private boolean expanded = false;

    @Override
    public void onCreate() {
        super.onCreate();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        showBubble();
    }

    private GradientDrawable background(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }

    private void showBubble() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(18, 12, 18, 12);
        root.setBackground(background(Color.rgb(20, 30, 47), 35));

        TextView label = new TextView(this);
        label.setText("H");
        label.setTextColor(Color.WHITE);
        label.setTextSize(25);
        label.setGravity(Gravity.CENTER);
        root.addView(label);

        TextView details = new TextView(this);
        // HILO_BUBBLE_RECEIVER_V1
        android.content.BroadcastReceiver frameReceiver =
            new android.content.BroadcastReceiver() {
                @Override
                public void onReceive(
                    android.content.Context context,
                    android.content.Intent intent) {
                    if ("com.hilolive.v2.CARD_UPDATE".equals(intent.getAction())) {
                    String card = intent.getStringExtra("card");
                    if (card != null && !card.trim().isEmpty()) {
                        details.setText("HiLo Analyzer V2\nCard OCR: " + card);
                    }
                    return;
                }
                // HILO_KEEP_CARD_VISIBLE_V1
                if (details.getText().toString().contains("Card OCR:")) {
                    return;
                }
                int frames = intent.getIntExtra("frames", 0);
                    details.setText(
                        "HiLo Analyzer V2\n\n" +
                        "Screen capture: ACTIVE\n" +
                        "Frames received: " + frames + "\n\n" +
                        "Card detection: Not implemented yet");
                }
            };

        android.content.IntentFilter filter =
            new android.content.IntentFilter(
                "com.hilolive.v2.FRAME_UPDATE");
        filter.addAction("com.hilolive.v2.CARD_UPDATE");

        if (android.os.Build.VERSION.SDK_INT >= 33) {
            registerReceiver(
                frameReceiver, filter,
                android.content.Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(frameReceiver, filter);
        }

        details.setText(
            "HiLo Analyzer V2\n\n" +
            "Card: Waiting for detection\n" +
            "HIGH: --\n" +
            "LOW: --\n" +
            "Instinct: Not available\n\n" +
            "Screen capture coming next"
        );
        details.setTextColor(Color.WHITE);
        details.setTextSize(15);
        details.setVisibility(View.GONE);
        root.addView(details);

        params = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.x = 30;
        params.y = 250;

        final float[] down = new float[2];
        final int[] start = new int[2];
        final boolean[] moved = {false};

        root.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    down[0] = event.getRawX();
                    down[1] = event.getRawY();
                    start[0] = params.x;
                    start[1] = params.y;
                    moved[0] = false;
                    return true;

                case MotionEvent.ACTION_MOVE:
                    int dx = (int) (event.getRawX() - down[0]);
                    int dy = (int) (event.getRawY() - down[1]);

                    if (Math.abs(dx) > 12 || Math.abs(dy) > 12) {
                        moved[0] = true;
                    }

                    if (moved[0]) {
                        params.x = start[0] + dx;
                        params.y = start[1] + dy;
                        wm.updateViewLayout(root, params);
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                    if (!moved[0]) {
                        expanded = !expanded;
                        details.setVisibility(
                            expanded ? View.VISIBLE : View.GONE
                        );
                        label.setText(expanded ? "HILO -" : "H");
                    }
                    return true;
            }
            return false;
        });

        bubble = root;
        wm.addView(bubble, params);
    }

    @Override
    public void onDestroy() {
        if (bubble != null) {
            wm.removeView(bubble);
            bubble = null;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

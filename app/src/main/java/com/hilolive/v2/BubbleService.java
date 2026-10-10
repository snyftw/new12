package com.hilolive.v2;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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
import java.util.Locale;

public class BubbleService extends Service {
    private WindowManager wm;
    private View bubble;
    private WindowManager.LayoutParams params;
    private boolean expanded = false;
    private TextView details;
    private BroadcastReceiver receiver;
    private String card = "--";
    private String raw = "--";
    private String status = "Waiting for card";

    @Override public void onCreate() {
        super.onCreate();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        showBubble();
        receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if ("com.hilolive.v2.CARD_UPDATE".equals(action)) {
                    String value = intent.getStringExtra("card");
                    if (value != null && !value.isEmpty()) { card = value; status = "Card detected"; }
                } else if ("com.hilolive.v2.HILO_RAW_UPDATE".equals(action)) {
                    String value = intent.getStringExtra("raw");
                    raw = value == null || value.isEmpty() ? "(empty)" : value;
                } else if ("com.hilolive.v2.OCR_STATUS".equals(action)) {
                    String value = intent.getStringExtra("status");
                    if (value != null) status = value;
                }
                refresh();
            }
        };
        IntentFilter filter = new IntentFilter("com.hilolive.v2.CARD_UPDATE");
        filter.addAction("com.hilolive.v2.HILO_RAW_UPDATE");
        filter.addAction("com.hilolive.v2.OCR_STATUS");
        if (android.os.Build.VERSION.SDK_INT >= 33)
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(receiver, filter);
    }

    private GradientDrawable background(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }

    private void refresh() {
        if (details == null) return;
        String probability = "HIGH: --    LOW: --    TIE: --";
        String instinct = "WAIT";
        int rank = rank(card);
        if (rank >= 2) {
            // Illustrative independent uniform rank model, Ace high.
            double high = (14 - rank) * 100.0 / 13.0;
            double low = (rank - 2) * 100.0 / 13.0;
            double tie = 100.0 / 13.0;
            probability = String.format(Locale.US,
                "HIGH: %.1f%%    LOW: %.1f%%\nTIE: %.1f%%", high, low, tie);
            instinct = high > low ? "HIGH" : low > high ? "LOW" : "NEUTRAL";
        }
        details.setText("HiLo Analyzer V2\nCard: " + card + "\n" + probability +
            "\nInstinct: " + instinct + "\nRaw OCR: " + raw +
            "\nStatus: " + status + "\nModel: equal ranks; Ace high; ties separate");
    }

    private int rank(String c) {
        if ("A".equals(c)) return 14;
        if ("K".equals(c)) return 13;
        if ("Q".equals(c)) return 12;
        if ("J".equals(c)) return 11;
        try { int n = Integer.parseInt(c); return n >= 2 && n <= 10 ? n : -1; }
        catch (Exception ignored) { return -1; }
    }

    private void showBubble() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(18, 12, 18, 12);
        root.setBackground(background(Color.rgb(20, 30, 47), 35));
        TextView label = new TextView(this);
        label.setText("H"); label.setTextColor(Color.WHITE);
        label.setTextSize(25); label.setGravity(Gravity.CENTER);
        root.addView(label);
        details = new TextView(this);
        details.setTextColor(Color.WHITE);
        details.setTextSize(14);
        details.setVisibility(View.GONE);
        root.addView(details);
        refresh();
        params = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.x = 30; params.y = 250;
        final float[] down = new float[2];
        final int[] start = new int[2];
        final boolean[] moved = {false};
        root.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    down[0] = event.getRawX(); down[1] = event.getRawY();
                    start[0] = params.x; start[1] = params.y;
                    moved[0] = false; return true;
                case MotionEvent.ACTION_MOVE:
                    int dx = (int) (event.getRawX() - down[0]);
                    int dy = (int) (event.getRawY() - down[1]);
                    if (Math.abs(dx) > 12 || Math.abs(dy) > 12) moved[0] = true;
                    if (moved[0]) {
                        params.x = start[0] + dx; params.y = start[1] + dy;
                        wm.updateViewLayout(root, params);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (!moved[0]) {
                        expanded = !expanded;
                        details.setVisibility(expanded ? View.VISIBLE : View.GONE);
                        label.setText(expanded ? "HILO -" : "H");
                    }
                    return true;
            }
            return false;
        });
        bubble = root;
        wm.addView(bubble, params);
    }

    @Override public void onDestroy() {
        if (receiver != null) unregisterReceiver(receiver);
        if (bubble != null) wm.removeView(bubble);
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}

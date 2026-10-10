package com.hilolive.v2;

import android.app.Activity;
import android.media.projection.MediaProjectionManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final int CAPTURE_REQUEST = 100;
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 60, 40, 40);

        TextView title = new TextView(this);
        title.setText("HiLo Live Analyzer V2");
        title.setTextSize(26);
        layout.addView(title);

        TextView description = new TextView(this);
        description.setText("Messenger-style floating assistant\n\nStage 1: Floating bubble");
        description.setTextSize(17);
        layout.addView(description);

        Button start = new Button(this);
        start.setText("START FLOATING BUBBLE");
        layout.addView(start);

        start.setOnClickListener(v -> {
            if (!Settings.canDrawOverlays(this)) {
                Intent permission = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())
                );
                startActivity(permission);
            } else {
                startService(new Intent(this, BubbleService.class));
            }
        });

        Button stop = new Button(this);
        stop.setText("STOP FLOATING BUBBLE");
        layout.addView(stop);

        stop.setOnClickListener(v ->
            stopService(new Intent(this, BubbleService.class))
        );


        Button capture = new Button(this);
        capture.setText("START SCREEN CAPTURE");
        layout.addView(capture);

        capture.setOnClickListener(v -> {
            MediaProjectionManager manager =
                (MediaProjectionManager) getSystemService(
                    Context.MEDIA_PROJECTION_SERVICE
                );
            startActivityForResult(
                manager.createScreenCaptureIntent(),
                CAPTURE_REQUEST
            );
        });

        setContentView(layout);
    }

    @Override
    protected void onActivityResult(
        int requestCode, int resultCode, Intent data
    ) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == CAPTURE_REQUEST
                && resultCode == RESULT_OK
                && data != null) {

            Intent service = new Intent(this, CaptureService.class);
            service.putExtra("resultCode", resultCode);
            service.putExtra("resultData", data);
            startForegroundService(service);
        }
    }
}

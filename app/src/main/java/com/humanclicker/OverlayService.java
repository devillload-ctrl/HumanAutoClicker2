package com.humanclicker;

import android.app.*;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.IBinder;
import android.view.*;
import android.widget.TextView;
import android.graphics.Color;
import android.os.Build;

public class OverlayService extends Service {

    public static final String ACTION_PICK = "com.humanclicker.PICK";
    private WindowManager wm;
    private View overlayView;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (ACTION_PICK.equals(intent != null ? intent.getAction() : null)) {
            showPickOverlay();
        }
        return START_NOT_STICKY;
    }

    private void showPickOverlay() {
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (!android.provider.Settings.canDrawOverlays(this)) {
            stopSelf();
            return;
        }

        // สร้าง overlay ใสคลุมทั้งจอ
        overlayView = new View(this);
        overlayView.setBackgroundColor(Color.argb(60, 233, 69, 96));

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        );

        overlayView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                int x = (int) event.getRawX();
                int y = (int) event.getRawY();
                // ส่งพิกัดกลับ MainActivity
                Intent back = new Intent(this, MainActivity.class);
                back.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                back.putExtra("picked_x", x);
                back.putExtra("picked_y", y);
                startActivity(back);
                // ลบ overlay
                removeOverlay();
                return true;
            }
            return false;
        });

        try {
            wm.addView(overlayView, params);
        } catch (Exception e) {
            stopSelf();
        }
    }

    private void removeOverlay() {
        if (overlayView != null && wm != null) {
            try { wm.removeView(overlayView); } catch (Exception ignored) {}
            overlayView = null;
        }
        stopSelf();
    }

    @Override public IBinder onBind(Intent i) { return null; }

    @Override
    public void onDestroy() {
        super.onDestroy();
        removeOverlay();
    }
}

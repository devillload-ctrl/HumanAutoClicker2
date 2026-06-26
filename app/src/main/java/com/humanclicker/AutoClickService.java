package com.humanclicker;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Path;
import android.view.accessibility.AccessibilityEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class AutoClickService extends AccessibilityService {

    public static final String ACTION_START   = "com.humanclicker.START";
    public static final String ACTION_STOP    = "com.humanclicker.STOP";
    public static final String ACTION_STATUS  = "com.humanclicker.STATUS";
    public static AutoClickService instance;

    private boolean running = false;
    private Thread clickThread;
    private final Random random = new Random();

    // Config รับจาก MainActivity
    private List<int[]> targets = new ArrayList<>();
    private float intervalMin = 1.0f;
    private float intervalMax = 3.0f;
    private boolean longBreakEnabled = false;
    private int longBreakMinMin = 3;
    private int longBreakMaxMin = 60;
    private int longBreakChance = 10;
    private int maxClicks = 0;
    private int jitterPx = 5;
    private int wSingle = 60, wHold = 30, wLong = 10;
    private int clickCount = 0;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ctx, Intent intent) {
            String action = intent.getAction();
            if (ACTION_START.equals(action)) {
                loadConfig(intent);
                startClicking();
            } else if (ACTION_STOP.equals(action)) {
                stopClicking();
            }
        }
    };

    @Override
    public void onServiceConnected() {
        instance = this;
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_START);
        filter.addAction(ACTION_STOP);
        registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
    }

    private void loadConfig(Intent intent) {
        targets.clear();
        int count = intent.getIntExtra("target_count", 0);
        for (int i = 0; i < count; i++) {
            int x = intent.getIntExtra("tx_" + i, 500);
            int y = intent.getIntExtra("ty_" + i, 500);
            targets.add(new int[]{x, y});
        }
        intervalMin      = intent.getFloatExtra("interval_min", 1.0f);
        intervalMax      = intent.getFloatExtra("interval_max", 3.0f);
        longBreakEnabled = intent.getBooleanExtra("long_break", false);
        longBreakMinMin  = intent.getIntExtra("break_min", 3);
        longBreakMaxMin  = intent.getIntExtra("break_max", 60);
        longBreakChance  = intent.getIntExtra("break_chance", 10);
        maxClicks        = intent.getIntExtra("max_clicks", 0);
        jitterPx         = intent.getIntExtra("jitter", 5);
        wSingle          = intent.getIntExtra("w_single", 60);
        wHold            = intent.getIntExtra("w_hold", 30);
        wLong            = intent.getIntExtra("w_long", 10);
    }

    private void startClicking() {
        if (running) return;
        if (targets.isEmpty()) {
            sendStatus("⚠️ ยังไม่มีเป้าหมาย");
            return;
        }
        running = true;
        clickCount = 0;
        sendStatus("▶ ทำงาน");

        clickThread = new Thread(() -> {
            while (running) {
                // ตรวจ max clicks
                if (maxClicks > 0 && clickCount >= maxClicks) {
                    sendStatus("✅ ครบ " + maxClicks + " ครั้ง");
                    running = false;
                    break;
                }

                // Long Break
                if (longBreakEnabled && random.nextInt(100) < longBreakChance) {
                    long breakSec = (longBreakMinMin + random.nextInt(longBreakMaxMin - longBreakMinMin + 1)) * 60L;
                    sendStatus("😴 พักยาว " + (breakSec / 60) + " นาที");
                    interruptibleSleep(breakSec * 1000);
                    if (!running) break;
                }

                // เลือกเป้าหมาย
                int[] target = targets.get(random.nextInt(targets.size()));
                int tx = target[0] + randomJitter();
                int ty = target[1] + randomJitter();

                // Human click
                humanClick(tx, ty);
                clickCount++;
                sendStatus("🖱 #" + clickCount + " → (" + tx + "," + ty + ")");

                // หน่วงเวลา
                long waitMs = (long)((intervalMin + random.nextFloat() * (intervalMax - intervalMin)) * 1000);
                // โอกาส 8% พักสั้นเพิ่ม
                if (random.nextInt(100) < 8) {
                    waitMs += (long)(2000 + random.nextFloat() * 4000);
                }
                interruptibleSleep(waitMs);
            }
            sendStatus("⏹ หยุด | คลิก: " + clickCount);
        });
        clickThread.setDaemon(true);
        clickThread.start();
    }

    private void stopClicking() {
        running = false;
        if (clickThread != null) clickThread.interrupt();
        sendStatus("⏹ หยุด | คลิก: " + clickCount);
    }

    // ========== Human Behavior ==========

    private int randomJitter() {
        return (int)(random.nextGaussian() * jitterPx);
    }

    private void humanClick(int x, int y) {
        // เลือกประเภทคลิกตามน้ำหนัก
        int total = wSingle + wHold + wLong;
        int r = random.nextInt(Math.max(total, 1));
        String type;
        if (r < wSingle)        type = "single";
        else if (r < wSingle + wHold) type = "hold";
        else                    type = "long";

        // Reaction delay ก่อนคลิก (80-350ms)
        sleep(80 + random.nextInt(270));

        switch (type) {
            case "single": {
                // กดปล่อยเร็ว 40-180ms
                long dur = 40 + random.nextInt(140);
                performTap(x, y, dur);
                break;
            }
            case "hold": {
                // กดค้างสั้น 200-600ms
                long dur = 200 + random.nextInt(400);
                performTap(x, y, dur);
                break;
            }
            case "long": {
                // กดค้างนาน 500-2500ms
                long dur = 500 + random.nextInt(2000);
                performTap(x, y, dur);
                break;
            }
        }

        // บางครั้ง swipe เล็กน้อยหลังคลิก (drift)
        if (random.nextInt(100) < 40) {
            sleep(50 + random.nextInt(150));
            int dx = (int)(random.nextGaussian() * 15);
            int dy = (int)(random.nextGaussian() * 15);
            performSwipe(x, y, x + dx, y + dy, 80 + random.nextInt(200));
        }

        // หน่วงหลังคลิก
        sleep(50 + random.nextInt(200));
    }

    private void performTap(int x, int y, long durationMs) {
        GestureDescription.Builder builder = new GestureDescription.Builder();
        Path path = new Path();
        path.moveTo(x, y);
        // เพิ่ม micro-movement เล็กน้อย
        path.lineTo(x + random.nextInt(3) - 1, y + random.nextInt(3) - 1);
        builder.addStroke(new GestureDescription.StrokeDescription(path, 0, durationMs));
        dispatchGesture(builder.build(), null, null);
        sleep(durationMs + 20);
    }

    private void performSwipe(int x1, int y1, int x2, int y2, long durationMs) {
        GestureDescription.Builder builder = new GestureDescription.Builder();
        Path path = new Path();
        path.moveTo(x1, y1);
        // Bezier curve เล็กน้อย
        float cx = (x1 + x2) / 2f + random.nextInt(20) - 10;
        float cy = (y1 + y2) / 2f + random.nextInt(20) - 10;
        path.quadTo(cx, cy, x2, y2);
        builder.addStroke(new GestureDescription.StrokeDescription(path, 0, durationMs));
        dispatchGesture(builder.build(), null, null);
        sleep(durationMs + 20);
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private void interruptibleSleep(long ms) {
        long end = System.currentTimeMillis() + ms;
        while (running && System.currentTimeMillis() < end) {
            long remaining = end - System.currentTimeMillis();
            sleep(Math.min(200, remaining));
            // Update countdown
            if (remaining > 1000) {
                sendStatus("😴 พักอีก " + (remaining / 1000) + "s");
            }
        }
    }

    private void sendStatus(String msg) {
        Intent i = new Intent(ACTION_STATUS);
        i.putExtra("msg", msg);
        i.putExtra("count", clickCount);
        sendBroadcast(i);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent e) {}
    @Override public void onInterrupt() { stopClicking(); }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopClicking();
        try { unregisterReceiver(receiver); } catch (Exception ignored) {}
        instance = null;
    }
}

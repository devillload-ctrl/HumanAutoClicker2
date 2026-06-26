package com.humanclicker;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.accessibility.AccessibilityManager;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private TextView tvStatus, tvClickCount, tvTargetList;
    private TextView tvJitter, tvSingle, tvHold, tvLong;
    private EditText etX, etY, etIntervalMin, etIntervalMax;
    private EditText etBreakMin, etBreakMax, etBreakChance, etMaxClicks;
    private CheckBox cbLongBreak;
    private SeekBar seekJitter, seekSingle, seekHold, seekLong;
    private Button btnStart, btnStop, btnAddTarget, btnPickTarget, btnClearTargets;
    private LinearLayout cardPermission, cardOverlay;

    private final List<int[]> targets = new ArrayList<>();
    private boolean picking = false;

    private final BroadcastReceiver statusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ctx, Intent intent) {
            String msg = intent.getStringExtra("msg");
            int count = intent.getIntExtra("count", 0);
            runOnUiThread(() -> {
                if (msg != null) {
                    tvStatus.setText("● " + msg);
                    boolean running = msg.startsWith("▶") || msg.startsWith("🖱") || msg.startsWith("😴");
                    tvStatus.setTextColor(running ? 0xFF00b4d8 : 0xFF888888);
                }
                tvClickCount.setText("คลิกทั้งหมด: " + count);
            });
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bindViews();
        setupSeekBars();
        setupButtons();

        registerReceiver(statusReceiver,
            new IntentFilter(AutoClickService.ACTION_STATUS),
            Context.RECEIVER_NOT_EXPORTED);
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkPermissions();
    }

    private void bindViews() {
        tvStatus      = findViewById(R.id.tvStatus);
        tvClickCount  = findViewById(R.id.tvClickCount);
        tvTargetList  = findViewById(R.id.tvTargetList);
        tvJitter      = findViewById(R.id.tvJitter);
        tvSingle      = findViewById(R.id.tvSingle);
        tvHold        = findViewById(R.id.tvHold);
        tvLong        = findViewById(R.id.tvLong);
        etX           = findViewById(R.id.etX);
        etY           = findViewById(R.id.etY);
        etIntervalMin = findViewById(R.id.etIntervalMin);
        etIntervalMax = findViewById(R.id.etIntervalMax);
        etBreakMin    = findViewById(R.id.etBreakMin);
        etBreakMax    = findViewById(R.id.etBreakMax);
        etBreakChance = findViewById(R.id.etBreakChance);
        etMaxClicks   = findViewById(R.id.etMaxClicks);
        cbLongBreak   = findViewById(R.id.cbLongBreak);
        seekJitter    = findViewById(R.id.seekJitter);
        seekSingle    = findViewById(R.id.seekSingle);
        seekHold      = findViewById(R.id.seekHold);
        seekLong      = findViewById(R.id.seekLong);
        btnStart      = findViewById(R.id.btnStart);
        btnStop       = findViewById(R.id.btnStop);
        btnAddTarget  = findViewById(R.id.btnAddTarget);
        btnPickTarget = findViewById(R.id.btnPickTarget);
        btnClearTargets = findViewById(R.id.btnClearTargets);
        cardPermission = findViewById(R.id.cardPermission);
        cardOverlay    = findViewById(R.id.cardOverlay);

        findViewById(R.id.btnPermission).setOnClickListener(v ->
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        findViewById(R.id.btnOverlay).setOnClickListener(v ->
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()))));
    }

    private void setupSeekBars() {
        seekJitter.setOnSeekBarChangeListener(new SimpleSeekListener() {
            public void onProgressChanged(SeekBar s, int p, boolean u) {
                tvJitter.setText(p + " px");
            }
        });
        seekSingle.setOnSeekBarChangeListener(new SimpleSeekListener() {
            public void onProgressChanged(SeekBar s, int p, boolean u) {
                tvSingle.setText(p + "%");
            }
        });
        seekHold.setOnSeekBarChangeListener(new SimpleSeekListener() {
            public void onProgressChanged(SeekBar s, int p, boolean u) {
                tvHold.setText(p + "%");
            }
        });
        seekLong.setOnSeekBarChangeListener(new SimpleSeekListener() {
            public void onProgressChanged(SeekBar s, int p, boolean u) {
                tvLong.setText(p + "%");
            }
        });
    }

    private void setupButtons() {
        btnAddTarget.setOnClickListener(v -> addTargetManual());
        btnPickTarget.setOnClickListener(v -> pickTargetFromScreen());
        btnClearTargets.setOnClickListener(v -> clearTargets());
        btnStart.setOnClickListener(v -> startClicking());
        btnStop.setOnClickListener(v -> stopClicking());
    }

    private void checkPermissions() {
        boolean hasAccess = isAccessibilityEnabled();
        boolean hasOverlay = Settings.canDrawOverlays(this);
        cardPermission.setVisibility(hasAccess ? android.view.View.GONE : android.view.View.VISIBLE);
        cardOverlay.setVisibility(hasOverlay ? android.view.View.GONE : android.view.View.VISIBLE);
    }

    private boolean isAccessibilityEnabled() {
        AccessibilityManager am = (AccessibilityManager) getSystemService(ACCESSIBILITY_SERVICE);
        for (AccessibilityServiceInfo info : am.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
            if (info.getId().contains(getPackageName())) return true;
        }
        return false;
    }

    private void addTargetManual() {
        try {
            int x = Integer.parseInt(etX.getText().toString());
            int y = Integer.parseInt(etY.getText().toString());
            targets.add(new int[]{x, y});
            updateTargetList();
            etX.setText("");
            etY.setText("");
            Toast.makeText(this, "✅ เพิ่ม (" + x + ", " + y + ")", Toast.LENGTH_SHORT).show();
        } catch (NumberFormatException e) {
            Toast.makeText(this, "⚠️ กรอกตัวเลข X, Y", Toast.LENGTH_SHORT).show();
        }
    }

    private void pickTargetFromScreen() {
        if (!isAccessibilityEnabled()) {
            Toast.makeText(this, "⚠️ เปิด Accessibility ก่อน", Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(this, "👆 แตะจุดที่ต้องการบนหน้าจอ\nแล้วกลับมาที่แอพนี้", Toast.LENGTH_LONG).show();
        // เปิด OverlayService เพื่อจับการแตะ
        Intent i = new Intent(this, OverlayService.class);
        i.setAction(OverlayService.ACTION_PICK);
        startService(i);
        picking = true;
    }

    private void clearTargets() {
        targets.clear();
        updateTargetList();
    }

    private void updateTargetList() {
        if (targets.isEmpty()) {
            tvTargetList.setText("ยังไม่มีเป้าหมาย");
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < targets.size(); i++) {
            sb.append("  #").append(i + 1).append("  (")
              .append(targets.get(i)[0]).append(", ")
              .append(targets.get(i)[1]).append(")\n");
        }
        tvTargetList.setText(sb.toString().trim());
    }

    private void startClicking() {
        if (!isAccessibilityEnabled()) {
            Toast.makeText(this, "⚠️ เปิด Accessibility ก่อน!", Toast.LENGTH_LONG).show();
            return;
        }
        if (targets.isEmpty()) {
            Toast.makeText(this, "⚠️ เพิ่มเป้าหมายก่อน", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent i = new Intent(AutoClickService.ACTION_START);
        i.setPackage(getPackageName());
        i.putExtra("target_count", targets.size());
        for (int idx = 0; idx < targets.size(); idx++) {
            i.putExtra("tx_" + idx, targets.get(idx)[0]);
            i.putExtra("ty_" + idx, targets.get(idx)[1]);
        }
        try {
            i.putExtra("interval_min", Float.parseFloat(etIntervalMin.getText().toString()));
            i.putExtra("interval_max", Float.parseFloat(etIntervalMax.getText().toString()));
        } catch (Exception ignored) { i.putExtra("interval_min", 1.0f); i.putExtra("interval_max", 3.0f); }

        i.putExtra("long_break", cbLongBreak.isChecked());
        try { i.putExtra("break_min", Integer.parseInt(etBreakMin.getText().toString())); } catch (Exception ignored) {}
        try { i.putExtra("break_max", Integer.parseInt(etBreakMax.getText().toString())); } catch (Exception ignored) {}
        try { i.putExtra("break_chance", Integer.parseInt(etBreakChance.getText().toString())); } catch (Exception ignored) {}
        try { i.putExtra("max_clicks", Integer.parseInt(etMaxClicks.getText().toString())); } catch (Exception ignored) {}

        i.putExtra("jitter", seekJitter.getProgress());
        i.putExtra("w_single", seekSingle.getProgress());
        i.putExtra("w_hold", seekHold.getProgress());
        i.putExtra("w_long", seekLong.getProgress());

        sendBroadcast(i);

        btnStart.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFe94560));
        btnStart.setText("■ ทำงานอยู่...");
    }

    private void stopClicking() {
        Intent i = new Intent(AutoClickService.ACTION_STOP);
        i.setPackage(getPackageName());
        sendBroadcast(i);
        btnStart.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF2d6a4f));
        btnStart.setText("▶ เริ่ม");
    }

    // รับพิกัดจาก OverlayService
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (picking && intent != null && intent.hasExtra("picked_x")) {
            int x = intent.getIntExtra("picked_x", 0);
            int y = intent.getIntExtra("picked_y", 0);
            targets.add(new int[]{x, y});
            updateTargetList();
            picking = false;
            Toast.makeText(this, "✅ บันทึก (" + x + ", " + y + ")", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(statusReceiver); } catch (Exception ignored) {}
    }

    abstract static class SimpleSeekListener implements SeekBar.OnSeekBarChangeListener {
        public void onStartTrackingTouch(SeekBar s) {}
        public void onStopTrackingTouch(SeekBar s) {}
    }
}

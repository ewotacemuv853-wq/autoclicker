package com.example.autoclicker;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/**
 * 基于无障碍服务的自动连点器（支持多点序列循环 + 多套方案 + 防检测 + 摇一摇/音量键启停）：
 *  - dispatchGesture 在设定坐标模拟点击（无需 Root）
 *  - 点击点以序列保存，按序循环点击；delayMs 控制每个点之后的等待
 *  - 随机抖动：防检测，每个点坐标加随机偏移
 *  - 摇一摇 / 音量键：免悬浮窗也能启停
 *  - 可拖动悬浮控制条：开始/停止、添加点
 */
public class AutoClickService extends AccessibilityService {

    public static final String ACTION_UPDATE = "com.example.autoclicker.UPDATE";          // activity -> service：重载配置
    public static final String ACTION_POINTS_CHANGED = "com.example.autoclicker.POINTS";   // service -> activity：点序列变化

    private WindowManager wm;
    private View floatingView;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private boolean running = false;

    private List<ClickPoint> points;
    private int cursor = 0;       // 当前点到序列中的位置
    private int cycles = 0;       // 已完成的轮数
    private int loopCount = 0;    // 0 = 无限
    private long defaultDelayMs = 1000;

    private boolean jitterEnabled = false;
    private int jitterPx = 10;

    private SensorManager sensorManager;
    private boolean shakeRegistered = false;
    private long lastVolumeMs = 0;
    private long lastShakeMs = 0;

    private final BroadcastReceiver cmdReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_UPDATE.equals(action)) {
                reloadAndReschedule();
                updateFloatingText();
            }
        }
    };

    private final Runnable clickRunnable = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            if (points == null || points.isEmpty()) {
                stopClicking();
                updateFloatingText();
                return;
            }
            ClickPoint p = points.get(cursor);
            dispatchTapAt(p.x, p.y);

            long wait = Math.max(10, p.delayMs);
            cursor++;
            if (cursor >= points.size()) {
                cursor = 0;
                cycles++;
                if (loopCount > 0 && cycles >= loopCount) {
                    stopClicking();
                    updateFloatingText();
                    Toast.makeText(AutoClickService.this, "已完成 " + loopCount + " 轮", Toast.LENGTH_SHORT).show();
                    return;
                }
            }
            handler.postDelayed(this, wait);
        }
    };

    private final SensorEventListener shakeListener = new SensorEventListener() {
        private static final float THRESHOLD = 14f; // 约 1.4g
        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) { }
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (!Prefs.isShake(AutoClickService.this)) return;
            float x = event.values[0], y = event.values[1], z = event.values[2];
            double g = Math.sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH;
            if (g > THRESHOLD) {
                long now = System.currentTimeMillis();
                if (now - lastShakeMs > 1000) {
                    lastShakeMs = now;
                    toggle();
                }
            }
        }
    };

    @Override
    public void onAccessibilityEvent(android.view.accessibility.AccessibilityEvent event) { }

    @Override
    public void onInterrupt() { stopClicking(); }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        loadPrefs();
        buildFloatingView();
        setupSensors();
        IntentFilter filter = new IntentFilter(ACTION_UPDATE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(cmdReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(cmdReceiver, filter);
        }
        if (Prefs.isAutoStart(this) && !points.isEmpty()) startClicking();
    }

    @Override
    public boolean onKeyEvent(KeyEvent event) {
        if (!Prefs.isVolumeKey(this)) return false;
        if (event.getAction() == KeyEvent.ACTION_DOWN
                && (event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_DOWN
                    || event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_UP)) {
            long now = System.currentTimeMillis();
            if (now - lastVolumeMs > 800) {
                lastVolumeMs = now;
                toggle();
            }
        }
        return false; // 不消费事件，音量键照常调节
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(cmdReceiver); } catch (Exception ignore) { }
        if (sensorManager != null) sensorManager.unregisterListener(shakeListener);
        stopClicking();
        removeFloatingView();
    }

    private void loadPrefs() {
        points = Prefs.getPoints(this);
        loopCount = Prefs.getLoop(this);
        defaultDelayMs = Math.max(10, Prefs.getInterval(this));
        jitterEnabled = Prefs.isJitter(this);
        jitterPx = Math.max(0, Prefs.getJitterPx(this));
    }

    private void setupSensors() {
        if (sensorManager == null) return;
        boolean want = Prefs.isShake(this);
        if (want && !shakeRegistered) {
            Sensor acc = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            if (acc != null) {
                sensorManager.registerListener(shakeListener, acc, SensorManager.SENSOR_DELAY_UI);
                shakeRegistered = true;
            }
        } else if (!want && shakeRegistered) {
            sensorManager.unregisterListener(shakeListener);
            shakeRegistered = false;
        }
    }

    // ---------------- 悬浮窗 UI ----------------

    private void buildFloatingView() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) return;
        LayoutInflater inflater = LayoutInflater.from(this);
        floatingView = inflater.inflate(R.layout.floating_view, null);

        final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 0;
        params.y = 200;

        Button btnToggle = floatingView.findViewById(R.id.btnToggle);
        Button btnPick = floatingView.findViewById(R.id.btnPick);

        btnToggle.setOnClickListener(v -> {
            toggle();
            updateFloatingText();
        });
        btnPick.setOnClickListener(v -> {
            Intent i = new Intent(AutoClickService.this, PickActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        });

        floatingView.findViewById(R.id.dragArea).setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x; initialY = params.y;
                        initialTouchX = event.getRawX(); initialTouchY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        params.x = initialX + (int) (event.getRawX() - initialTouchX);
                        params.y = initialY + (int) (event.getRawY() - initialTouchY);
                        wm.updateViewLayout(floatingView, params);
                        return true;
                    default:
                        return false;
                }
            }
        });

        wm.addView(floatingView, params);
        updateFloatingText();
    }

    private void updateFloatingText() {
        if (floatingView == null) return;
        TextView tv = floatingView.findViewById(R.id.tvInfo);
        int n = (points == null) ? 0 : points.size();
        StringBuilder sb = new StringBuilder();
        sb.append(running ? "● 运行中" : "○ 已停止").append("\n")
          .append(n).append(" 个点")
          .append(loopCount > 0 ? " · 循环" + loopCount + "轮" : " · 无限");
        if (jitterEnabled) sb.append("\n防检测:开");
        if (Prefs.isShake(this) || Prefs.isVolumeKey(this)) {
            sb.append("\n启停:");
            if (Prefs.isShake(this)) sb.append("摇一摇 ");
            if (Prefs.isVolumeKey(this)) sb.append("音量键");
        }
        tv.setText(sb.toString());
        Button btnToggle = floatingView.findViewById(R.id.btnToggle);
        btnToggle.setText(running ? "停止" : "开始");
    }

    private void removeFloatingView() {
        if (floatingView != null && wm != null) {
            try { wm.removeView(floatingView); } catch (Exception ignore) { }
            floatingView = null;
        }
    }

    private void notifyPointsChanged() {
        Intent i = new Intent(ACTION_POINTS_CHANGED);
        i.setPackage(getPackageName());
        sendBroadcast(i);
    }

    private int overlayType() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
    }

    // ---------------- 连点逻辑 ----------------

    private void toggle() {
        if (running) stopClicking(); else startClicking();
        updateFloatingText();
    }

    private void startClicking() {
        if (running) return;
        loadPrefs();
        setupSensors();
        if (points.isEmpty()) {
            Toast.makeText(this, "请先添加至少一个点击点", Toast.LENGTH_SHORT).show();
            return;
        }
        running = true;
        cursor = 0;
        cycles = 0;
        handler.post(clickRunnable);
    }

    private void stopClicking() {
        running = false;
        handler.removeCallbacks(clickRunnable);
    }

    private void reloadAndReschedule() {
        handler.removeCallbacks(clickRunnable);
        loadPrefs();
        setupSensors();
        if (!running) return;
        if (points.isEmpty()) { stopClicking(); return; }
        if (cursor >= points.size()) cursor = 0;
        handler.post(clickRunnable);
    }

    private void dispatchTapAt(int x, int y) {
        int jx = x, jy = y;
        if (jitterEnabled && jitterPx > 0) {
            jx += rnd(-jitterPx, jitterPx);
            jy += rnd(-jitterPx, jitterPx);
        }
        Path path = new Path();
        path.moveTo(jx, jy);
        GestureDescription.Builder b = new GestureDescription.Builder();
        b.addStroke(new GestureDescription.StrokeDescription(path, 0, 10));
        dispatchGesture(b.build(), null, null);
    }

    private int rnd(int min, int max) {
        return min + (int) (Math.random() * (max - min + 1));
    }
}

package com.example.autoclicker;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private static final int REQ_OVERLAY = 1001;

    private TextView tvStatus;
    private EditText etInterval, etLoop, etJitterPx;
    private ListView lvPoints;
    private Spinner spinnerScheme;
    private CheckBox cbJitter, cbShake, cbVolume;
    private ArrayAdapter<String> adapter;
    private ArrayAdapter<String> schemeAdapter;
    private final List<String> displayList = new ArrayList<>();
    private final List<String> schemeNames = new ArrayList<>();

    private final BroadcastReceiver pointsReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            refreshPoints();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus = findViewById(R.id.tvStatus);
        etInterval = findViewById(R.id.etInterval);
        etLoop = findViewById(R.id.etLoop);
        etJitterPx = findViewById(R.id.etJitterPx);
        lvPoints = findViewById(R.id.lvPoints);
        spinnerScheme = findViewById(R.id.spinnerScheme);
        cbJitter = findViewById(R.id.cbJitter);
        cbShake = findViewById(R.id.cbShake);
        cbVolume = findViewById(R.id.cbVolume);

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, displayList);
        lvPoints.setAdapter(adapter);

        schemeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, schemeNames);
        schemeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerScheme.setAdapter(schemeAdapter);

        // 载入当前设置到界面
        etInterval.setText(String.valueOf(Prefs.getInterval(this)));
        etLoop.setText(String.valueOf(Prefs.getLoop(this)));
        cbJitter.setChecked(Prefs.isJitter(this));
        etJitterPx.setText(String.valueOf(Prefs.getJitterPx(this)));
        cbShake.setChecked(Prefs.isShake(this));
        cbVolume.setChecked(Prefs.isVolumeKey(this));

        findViewById(R.id.btnAccessibility).setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));

        findViewById(R.id.btnOverlay).setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivityForResult(i, REQ_OVERLAY);
            } else {
                Toast.makeText(this, "已拥有悬浮窗权限", Toast.LENGTH_SHORT).show();
            }
        });

        findViewById(R.id.btnAddPoint).setOnClickListener(v -> {
            Intent i = new Intent(AutoClickService.ACTION_PICK_ADD);
            i.setPackage(getPackageName());
            sendBroadcast(i);
            Toast.makeText(this, "请点击屏幕任意位置以添加点", Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.btnClear).setOnClickListener(v -> {
            Prefs.clearPoints(this);
            refreshPoints();
            notifyService();
            Toast.makeText(this, "已清空点击点", Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.btnSave).setOnClickListener(v -> saveSettings());

        // 方案：保存 / 删除
        findViewById(R.id.btnSaveScheme).setOnClickListener(v -> saveScheme());
        findViewById(R.id.btnDelScheme).setOnClickListener(v -> deleteScheme());

        spinnerScheme.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (position <= 0) return; // 第 0 项是“当前”
                String name = schemeNames.get(position);
                Scheme s = Prefs.getScheme(MainActivity.this, name);
                if (s != null) applyScheme(s);
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });

        // 点击某点 -> 编辑其延迟；长按 -> 删除
        lvPoints.setOnItemClickListener((parent, view, position, id) -> editDelay(position));
        lvPoints.setOnItemLongClickListener((parent, view, position, id) -> {
            Prefs.removePoint(this, position);
            refreshPoints();
            notifyService();
            return true;
        });
    }

    private void editDelay(int position) {
        List<ClickPoint> pts = Prefs.getPoints(this);
        if (position < 0 || position >= pts.size()) return;
        ClickPoint p = pts.get(position);
        final EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(p.delayMs));
        new AlertDialog.Builder(this)
                .setTitle("点 #" + (position + 1) + " 的点击后延迟(ms)")
                .setView(input)
                .setPositiveButton("保存", (d, w) -> {
                    try {
                        long delay = Math.max(10, Long.parseLong(input.getText().toString().trim()));
                        Prefs.updatePoint(this, position, new ClickPoint(p.x, p.y, delay));
                        refreshPoints();
                        notifyService();
                    } catch (NumberFormatException ignore) { }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void saveSettings() {
        try {
            int interval = Integer.parseInt(etInterval.getText().toString().trim());
            int loop = Integer.parseInt(etLoop.getText().toString().trim());
            int jit = Integer.parseInt(etJitterPx.getText().toString().trim());
            Prefs.setInterval(this, interval);
            Prefs.setLoop(this, loop);
            Prefs.setJitter(this, cbJitter.isChecked());
            Prefs.setJitterPx(this, jit);
            Prefs.setShake(this, cbShake.isChecked());
            Prefs.setVolumeKey(this, cbVolume.isChecked());
            notifyService();
            Toast.makeText(this, "已保存，悬浮条将自动应用", Toast.LENGTH_SHORT).show();
        } catch (NumberFormatException e) {
            Toast.makeText(this, "请输入有效数字", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveScheme() {
        final EditText input = new EditText(this);
        input.setHint(R.string.hint_scheme_name);
        new AlertDialog.Builder(this)
                .setTitle("保存为方案")
                .setView(input)
                .setPositiveButton("保存", (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) { Toast.makeText(this, "名称不能为空", Toast.LENGTH_SHORT).show(); return; }
                    Scheme s = new Scheme(name,
                            new ArrayList<>(Prefs.getPoints(this)),
                            safeInt(etLoop), safeInt(etInterval),
                            cbJitter.isChecked(), safeInt(etJitterPx),
                            cbShake.isChecked(), cbVolume.isChecked());
                    Prefs.saveScheme(this, s);
                    refreshSchemes();
                    spinnerScheme.setSelection(schemeNames.indexOf(name));
                    Toast.makeText(this, "已保存方案：" + name, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void deleteScheme() {
        int pos = spinnerScheme.getSelectedItemPosition();
        if (pos <= 0) { Toast.makeText(this, "请先在上方选择一个方案", Toast.LENGTH_SHORT).show(); return; }
        String name = schemeNames.get(pos);
        Prefs.deleteScheme(this, name);
        refreshSchemes();
        spinnerScheme.setSelection(0);
        Toast.makeText(this, "已删除方案：" + name, Toast.LENGTH_SHORT).show();
    }

    private void applyScheme(Scheme s) {
        Prefs.setPoints(this, new ArrayList<>(s.points));
        Prefs.setLoop(this, s.loopCount);
        Prefs.setInterval(this, s.defaultDelayMs);
        Prefs.setJitter(this, s.jitterEnabled);
        Prefs.setJitterPx(this, s.jitterRangePx);
        Prefs.setShake(this, s.shakeEnabled);
        Prefs.setVolumeKey(this, s.volumeKeyEnabled);
        etLoop.setText(String.valueOf(s.loopCount));
        etInterval.setText(String.valueOf(s.defaultDelayMs));
        cbJitter.setChecked(s.jitterEnabled);
        etJitterPx.setText(String.valueOf(s.jitterRangePx));
        cbShake.setChecked(s.shakeEnabled);
        cbVolume.setChecked(s.volumeKeyEnabled);
        refreshPoints();
        notifyService();
        Toast.makeText(this, "已载入方案：" + s.name, Toast.LENGTH_SHORT).show();
    }

    private int safeInt(EditText et) {
        try { return Integer.parseInt(et.getText().toString().trim()); }
        catch (NumberFormatException e) { return 0; }
    }

    private void notifyService() {
        Intent i = new Intent(AutoClickService.ACTION_UPDATE);
        i.setPackage(getPackageName());
        sendBroadcast(i);
    }

    private void refreshPoints() {
        List<ClickPoint> pts = Prefs.getPoints(this);
        displayList.clear();
        for (int i = 0; i < pts.size(); i++) {
            ClickPoint p = pts.get(i);
            displayList.add("#" + (i + 1) + "  (" + p.x + "," + p.y + ")  延迟 " + p.delayMs + "ms");
        }
        adapter.notifyDataSetChanged();
    }

    private void refreshSchemes() {
        List<Scheme> schemes = Prefs.getSchemes(this);
        schemeNames.clear();
        schemeNames.add("(当前 / 未保存)");
        for (Scheme s : schemes) schemeNames.add(s.name);
        schemeAdapter.notifyDataSetChanged();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
        refreshPoints();
        refreshSchemes();
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter f = new IntentFilter(AutoClickService.ACTION_POINTS_CHANGED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(pointsReceiver, f, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(pointsReceiver, f);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        try { unregisterReceiver(pointsReceiver); } catch (Exception ignore) { }
    }

    private void updateStatus() {
        boolean acc = isAccessibilityEnabled();
        boolean overlay = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this);
        int n = Prefs.getPoints(this).size();
        tvStatus.setText("无障碍服务: " + (acc ? "已开启 ✓" : "未开启 ✗")
                + "\n悬浮窗权限: " + (overlay ? "已授权 ✓" : "未授权 ✗")
                + "\n当前点击点: " + n + " 个"
                + "\n\n用法：\n"
                + "1. 点「添加点」后在屏幕上依次点选位置（顺序即点击序列）\n"
                + "2. 长按列表项删除，单击改延迟\n"
                + "3. 可「保存为方案」（如：抢购A / 游戏B），下拉切换\n"
                + "4. 开启「防检测」随机抖动，「摇一摇/音量键」免悬浮窗启停\n"
                + "5. 开启无障碍后，用悬浮条「开始」循环连点");
    }

    private boolean isAccessibilityEnabled() {
        String enabled = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled != null && enabled.contains("com.example.autoclicker/.AutoClickService")) return true;
        AccessibilityManager am = (AccessibilityManager) getSystemService(ACCESSIBILITY_SERVICE);
        if (am == null) return false;
        List<android.accessibilityservice.AccessibilityServiceInfo> list =
                am.getInstalledAccessibilityServiceList();
        for (android.accessibilityservice.AccessibilityServiceInfo info : list) {
            if (info.getId() != null && info.getId().contains("com.example.autoclicker/.AutoClickService")) {
                return true;
            }
        }
        return false;
    }
}

package com.example.autoclicker;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/**
 * 透明全屏“添加点”界面：不依赖悬浮窗权限、也不依赖无障碍服务后台弹窗。
 * 直接在 Activity 上接收屏幕触摸，用 getRawX/Y 取屏幕绝对坐标（与 dispatchGesture 同一坐标系）。
 * 点屏幕任意位置添加一个点（可连续添加），点右下角「完成」或返回键结束。
 */
public class PickActivity extends Activity {

    private TextView tvHint;
    private TextView tvDone;
    private int count = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pick);

        tvHint = findViewById(R.id.tvPickHint);
        tvDone = findViewById(R.id.tvPickDone);

        List<ClickPoint> existing = Prefs.getPoints(this);
        count = existing.size();
        updateHint();

        final View root = findViewById(R.id.pickRoot);
        root.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() != MotionEvent.ACTION_DOWN) return false;

                // 点中“完成”按钮区域 -> 结束
                Rect r = new Rect();
                tvDone.getHitRect(r);
                if (r.contains((int) event.getX(), (int) event.getY())) {
                    finishPick();
                    return true;
                }

                // 否则：取屏幕绝对坐标，添加一个点击点
                int sx = (int) event.getRawX();
                int sy = (int) event.getRawY();
                Prefs.addPoint(PickActivity.this, new ClickPoint(sx, sy, Prefs.getInterval(PickActivity.this)));
                count++;
                updateHint();
                Toast.makeText(PickActivity.this,
                        "已添加点 #" + count + "  (" + sx + "," + sy + ")", Toast.LENGTH_SHORT).show();
                notifyChanged();
                return true;
            }
        });
    }

    private void updateHint() {
        tvHint.setText("点击屏幕任意位置添加点击点（已添加 " + count + " 个）\n右下角「完成」结束并保存");
    }

    private void notifyChanged() {
        Intent i = new Intent(AutoClickService.ACTION_POINTS_CHANGED);
        i.setPackage(getPackageName());
        sendBroadcast(i);
    }

    private void finishPick() {
        notifyChanged();
        finish();
    }

    @Override
    public void onBackPressed() {
        finishPick();
    }
}

package com.example.autoclicker;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 配置存储（SharedPreferences + JSON）：
 *  - 当前点击点序列 points
 *  - 默认延迟 interval、循环轮数 loop、自启动 auto_start
 *  - 防检测：jitter 开关 + 抖动范围 px
 *  - 启停方式：shake（摇一摇）、volume（音量键）
 *  - 多套方案 schemes
 * 供 MainActivity 与 Service 共享。
 */
public class Prefs {
    private static final String NAME = "autoclicker_prefs";
    private static final String K_POINTS = "points";
    private static final String K_INTERVAL = "interval";
    private static final String K_LOOP = "loop";
    private static final String K_AUTO = "auto_start";
    private static final String K_JITTER_ON = "jitter_on";
    private static final String K_JITTER_PX = "jitter_px";
    private static final String K_SHAKE = "shake";
    private static final String K_VOLUME = "volume";
    private static final String K_SCHEMES = "schemes";

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    public static int getInterval(Context c) { return sp(c).getInt(K_INTERVAL, 1000); }
    public static void setInterval(Context c, int v) { sp(c).edit().putInt(K_INTERVAL, Math.max(10, v)).apply(); }

    public static int getLoop(Context c) { return sp(c).getInt(K_LOOP, 0); }
    public static void setLoop(Context c, int v) { sp(c).edit().putInt(K_LOOP, Math.max(0, v)).apply(); }

    public static boolean isAutoStart(Context c) { return sp(c).getBoolean(K_AUTO, false); }
    public static void setAutoStart(Context c, boolean v) { sp(c).edit().putBoolean(K_AUTO, v).apply(); }

    // ---------------- 防检测 ----------------
    public static boolean isJitter(Context c) { return sp(c).getBoolean(K_JITTER_ON, false); }
    public static void setJitter(Context c, boolean v) { sp(c).edit().putBoolean(K_JITTER_ON, v).apply(); }
    public static int getJitterPx(Context c) { return sp(c).getInt(K_JITTER_PX, 10); }
    public static void setJitterPx(Context c, int v) { sp(c).edit().putInt(K_JITTER_PX, Math.max(0, v)).apply(); }

    // ---------------- 启停方式 ----------------
    public static boolean isShake(Context c) { return sp(c).getBoolean(K_SHAKE, false); }
    public static void setShake(Context c, boolean v) { sp(c).edit().putBoolean(K_SHAKE, v).apply(); }
    public static boolean isVolumeKey(Context c) { return sp(c).getBoolean(K_VOLUME, false); }
    public static void setVolumeKey(Context c, boolean v) { sp(c).edit().putBoolean(K_VOLUME, v).apply(); }

    // ---------------- 点击点序列 ----------------
    public static List<ClickPoint> getPoints(Context c) {
        List<ClickPoint> list = new ArrayList<>();
        String raw = sp(c).getString(K_POINTS, "[]");
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                list.add(new ClickPoint(o.getInt("x"), o.getInt("y"), o.optLong("d", 1000)));
            }
        } catch (JSONException e) { /* 损坏则忽略，返回空序列 */ }
        return list;
    }

    public static void setPoints(Context c, List<ClickPoint> list) {
        JSONArray arr = new JSONArray();
        for (ClickPoint p : list) {
            JSONObject o = new JSONObject();
            try {
                o.put("x", p.x);
                o.put("y", p.y);
                o.put("d", p.delayMs);
                arr.put(o);
            } catch (JSONException ignore) { }
        }
        sp(c).edit().putString(K_POINTS, arr.toString()).apply();
    }

    public static void addPoint(Context c, ClickPoint p) {
        List<ClickPoint> l = getPoints(c);
        l.add(p);
        setPoints(c, l);
    }

    public static void updatePoint(Context c, int index, ClickPoint p) {
        List<ClickPoint> l = getPoints(c);
        if (index >= 0 && index < l.size()) { l.set(index, p); setPoints(c, l); }
    }

    public static void removePoint(Context c, int index) {
        List<ClickPoint> l = getPoints(c);
        if (index >= 0 && index < l.size()) { l.remove(index); setPoints(c, l); }
    }

    public static void clearPoints(Context c) { setPoints(c, new ArrayList<>()); }

    // ---------------- 多套方案 ----------------
    public static List<Scheme> getSchemes(Context c) {
        List<Scheme> list = new ArrayList<>();
        String raw = sp(c).getString(K_SCHEMES, "[]");
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Scheme s = new Scheme();
                s.name = o.getString("name");
                s.loopCount = o.optInt("loop", 0);
                s.defaultDelayMs = o.optInt("delay", 1000);
                s.jitterEnabled = o.optBoolean("jit", false);
                s.jitterRangePx = o.optInt("jitpx", 10);
                s.shakeEnabled = o.optBoolean("shake", false);
                s.volumeKeyEnabled = o.optBoolean("vol", false);
                s.points = new ArrayList<>();
                JSONArray pa = o.optJSONArray("points");
                if (pa != null) {
                    for (int j = 0; j < pa.length(); j++) {
                        JSONObject po = pa.getJSONObject(j);
                        s.points.add(new ClickPoint(po.getInt("x"), po.getInt("y"), po.optLong("d", 1000)));
                    }
                }
                list.add(s);
            }
        } catch (JSONException e) { /* 损坏忽略 */ }
        return list;
    }

    public static Scheme getScheme(Context c, String name) {
        for (Scheme s : getSchemes(c)) if (s.name.equals(name)) return s;
        return null;
    }

    public static void saveScheme(Context c, Scheme s) {
        List<Scheme> list = getSchemes(c);
        boolean replaced = false;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).name.equals(s.name)) { list.set(i, s); replaced = true; break; }
        }
        if (!replaced) list.add(s);
        setSchemes(c, list);
    }

    public static void deleteScheme(Context c, String name) {
        List<Scheme> list = getSchemes(c);
        list.removeIf(s -> s.name.equals(name));
        setSchemes(c, list);
    }

    private static void setSchemes(Context c, List<Scheme> list) {
        JSONArray arr = new JSONArray();
        for (Scheme s : list) {
            JSONObject o = new JSONObject();
            try {
                o.put("name", s.name);
                o.put("loop", s.loopCount);
                o.put("delay", s.defaultDelayMs);
                o.put("jit", s.jitterEnabled);
                o.put("jitpx", s.jitterRangePx);
                o.put("shake", s.shakeEnabled);
                o.put("vol", s.volumeKeyEnabled);
                JSONArray pa = new JSONArray();
                for (ClickPoint p : s.points) {
                    JSONObject po = new JSONObject();
                    po.put("x", p.x); po.put("y", p.y); po.put("d", p.delayMs);
                    pa.put(po);
                }
                o.put("points", pa);
                arr.put(o);
            } catch (JSONException ignore) { }
        }
        sp(c).edit().putString(K_SCHEMES, arr.toString()).apply();
    }
}

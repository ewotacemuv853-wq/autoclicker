package com.example.autoclicker;

import java.util.List;

/** 一套完整的点击方案：点序列 + 循环/延迟/防检测/启停方式，可保存与加载。 */
public class Scheme {
    public String name;
    public List<ClickPoint> points;
    public int loopCount;        // 0 = 无限
    public int defaultDelayMs;   // 新增点的默认延迟
    public boolean jitterEnabled;
    public int jitterRangePx;    // 抖动范围（像素）
    public boolean shakeEnabled; // 摇一摇启停
    public boolean volumeKeyEnabled; // 音量键启停

    public Scheme() { }

    public Scheme(String name, List<ClickPoint> points, int loopCount, int defaultDelayMs,
                  boolean jitterEnabled, int jitterRangePx, boolean shakeEnabled, boolean volumeKeyEnabled) {
        this.name = name;
        this.points = points;
        this.loopCount = loopCount;
        this.defaultDelayMs = defaultDelayMs;
        this.jitterEnabled = jitterEnabled;
        this.jitterRangePx = jitterRangePx;
        this.shakeEnabled = shakeEnabled;
        this.volumeKeyEnabled = volumeKeyEnabled;
    }
}

package com.example.autoclicker;

/** 一个点击点：坐标 + 点击后等待时长（ms）。 */
public class ClickPoint {
    public int x;
    public int y;
    public long delayMs; // 点完该点后，到下一次点击（或下一轮）的等待

    public ClickPoint(int x, int y, long delayMs) {
        this.x = x;
        this.y = y;
        this.delayMs = delayMs;
    }
}

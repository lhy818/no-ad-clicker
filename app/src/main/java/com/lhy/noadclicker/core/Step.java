package com.lhy.noadclicker.core;

/** One action in a script. Coordinates are screen pixels. */
public final class Step {
    public enum Type { TAP, SWIPE }

    public static final long MIN_PRESS = 1, MAX_PRESS = 60_000;
    public static final long MIN_DELAY = 0, MAX_DELAY = 3_600_000;
    public static final long DEFAULT_TAP_PRESS = 50, DEFAULT_SWIPE = 300, DEFAULT_DELAY = 500;

    public Type type;
    public int x, y, x2, y2;
    /** Press time for TAP, travel time for SWIPE. */
    public long pressMs;
    public long delayAfterMs;

    public static Step tap(int x, int y) {
        Step s = new Step(); s.type = Type.TAP; s.x = x; s.y = y;
        s.pressMs = DEFAULT_TAP_PRESS; s.delayAfterMs = DEFAULT_DELAY; return s;
    }

    public static Step swipe(int x, int y, int x2, int y2) {
        Step s = new Step(); s.type = Type.SWIPE; s.x = x; s.y = y; s.x2 = x2; s.y2 = y2;
        s.pressMs = DEFAULT_SWIPE; s.delayAfterMs = DEFAULT_DELAY; return s;
    }

    public static long clampPress(long v) { return Math.max(MIN_PRESS, Math.min(MAX_PRESS, v)); }
    public static long clampDelay(long v) { return Math.max(MIN_DELAY, Math.min(MAX_DELAY, v)); }

    /** @return null when valid, else a human-readable problem. */
    public String validate() {
        if (type == null) return "缺少类型";
        if (x < 0 || y < 0) return "坐标不能为负";
        if (type == Type.SWIPE && (x2 < 0 || y2 < 0)) return "终点坐标不能为负";
        if (pressMs < MIN_PRESS || pressMs > MAX_PRESS) return "按压/滑动时长超出范围";
        if (delayAfterMs < MIN_DELAY || delayAfterMs > MAX_DELAY) return "间隔超出范围";
        return null;
    }

    public Step copy() {
        Step s = new Step(); s.type = type; s.x = x; s.y = y; s.x2 = x2; s.y2 = y2;
        s.pressMs = pressMs; s.delayAfterMs = delayAfterMs; return s;
    }
}

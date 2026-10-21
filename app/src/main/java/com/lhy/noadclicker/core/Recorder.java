package com.lhy.noadclicker.core;

import java.util.ArrayList;
import java.util.List;

/** Turns raw single-finger touch samples into steps. Pure logic, no Android types. */
public final class Recorder {
    public static final double TAP_SLOP_PX = 24.0;

    private final List<Step> steps = new ArrayList<>();
    private boolean down;
    private int downX, downY, lastX, lastY;
    private double maxTravel;
    private long downT;
    private long lastUpT = -1;

    public void onDown(int x, int y, long t) {
        down = true; downX = lastX = x; downY = lastY = y; downT = t; maxTravel = 0;
        if (lastUpT >= 0 && !steps.isEmpty()) {
            steps.get(steps.size() - 1).delayAfterMs = Step.clampDelay(t - lastUpT);
        }
    }

    public void onMove(int x, int y) {
        if (!down) return;
        lastX = x; lastY = y;
        maxTravel = Math.max(maxTravel, Math.hypot(x - downX, y - downY));
    }

    /** @return the step just completed. */
    public Step onUp(int x, int y, long t) {
        if (!down) return null;
        onMove(x, y);
        down = false;
        lastUpT = t;
        Step s = classify(downX, downY, x, y, maxTravel, t - downT);
        steps.add(s);
        return s;
    }

    static Step classify(int x, int y, int x2, int y2, double travel, long durMs) {
        Step s;
        if (travel < TAP_SLOP_PX) {
            s = Step.tap(x, y);
        } else {
            s = Step.swipe(x, y, Math.max(0, x2), Math.max(0, y2));
        }
        s.pressMs = Step.clampPress(durMs);
        s.delayAfterMs = Step.DEFAULT_DELAY;
        return s;
    }

    public List<Step> result() { return new ArrayList<>(steps); }
}

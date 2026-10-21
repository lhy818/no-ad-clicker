package com.lhy.noadclicker.core;

import static org.junit.Assert.*;
import java.util.List;
import org.junit.Test;

public class RecorderTest {
    @Test public void smallMovementIsTap() {
        Recorder r = new Recorder();
        r.onDown(100, 100, 0);
        r.onMove(123, 100);
        Step s = r.onUp(123, 100, 70);
        assertEquals(Step.Type.TAP, s.type);
        assertEquals(100, s.x); assertEquals(100, s.y);
        assertEquals(70, s.pressMs);
    }

    @Test public void thresholdMovementIsSwipe() {
        Recorder r = new Recorder();
        r.onDown(100, 100, 0);
        Step s = r.onUp(124, 100, 200);
        assertEquals(Step.Type.SWIPE, s.type);
        assertEquals(124, s.x2); assertEquals(100, s.y2);
        assertEquals(200, s.pressMs);
    }

    @Test public void wanderingThenReturningIsStillSwipe() {
        Recorder r = new Recorder();
        r.onDown(100, 100, 0);
        r.onMove(300, 100);
        Step s = r.onUp(101, 100, 400);
        assertEquals(Step.Type.SWIPE, s.type);
    }

    @Test public void gapBecomesPreviousDelay() {
        Recorder r = new Recorder();
        r.onDown(10, 10, 1000); r.onUp(10, 10, 1050);
        r.onDown(20, 20, 1800); r.onUp(20, 20, 1850);
        List<Step> steps = r.result();
        assertEquals(2, steps.size());
        assertEquals(750, steps.get(0).delayAfterMs);
        assertEquals(Step.DEFAULT_DELAY, steps.get(1).delayAfterMs);
    }

    @Test public void zeroLengthPressIsClamped() {
        Recorder r = new Recorder();
        r.onDown(5, 5, 10);
        assertEquals(Step.MIN_PRESS, r.onUp(5, 5, 10).pressMs);
    }

    @Test public void hugeGapIsClamped() {
        Recorder r = new Recorder();
        r.onDown(1, 1, 0); r.onUp(1, 1, 10);
        r.onDown(1, 1, 10 + Step.MAX_DELAY + 5); r.onUp(1, 1, 20 + Step.MAX_DELAY + 5);
        assertEquals(Step.MAX_DELAY, r.result().get(0).delayAfterMs);
    }

    @Test public void upWithoutDownIgnored() {
        assertNull(new Recorder().onUp(1, 1, 1));
    }
}

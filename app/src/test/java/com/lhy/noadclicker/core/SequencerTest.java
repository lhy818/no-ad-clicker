package com.lhy.noadclicker.core;

import static org.junit.Assert.*;
import org.junit.Test;

public class SequencerTest {
    @Test public void countRunsExactlyNRounds() {
        Sequencer q = new Sequencer(2, LoopPolicy.count(3));
        int[] expect = {0, 1, 0, 1, 0, 1};
        for (int e : expect) assertEquals(e, q.next(0));
        assertEquals(-1, q.next(0));
        assertEquals(-1, q.next(0));
    }

    @Test public void countOneRunsOnce() {
        Sequencer q = new Sequencer(3, LoopPolicy.count(1));
        assertEquals(0, q.next(0)); assertEquals(1, q.next(0)); assertEquals(2, q.next(0));
        assertEquals(-1, q.next(0));
    }

    @Test public void durationStopsMidRound() {
        Sequencer q = new Sequencer(3, LoopPolicy.duration(1000));
        assertEquals(0, q.next(0));
        assertEquals(1, q.next(999));
        assertEquals(-1, q.next(1000));
    }

    @Test public void infiniteKeepsGoing() {
        Sequencer q = new Sequencer(2, LoopPolicy.infinite());
        for (int i = 0; i < 10_000; i++) assertEquals(i % 2, q.next(Long.MAX_VALUE / 2));
    }

    @Test public void emptyScriptEndsImmediately() {
        assertEquals(-1, new Sequencer(0, LoopPolicy.infinite()).next(0));
    }
}

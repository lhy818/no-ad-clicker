package com.lhy.noadclicker.core;

import static org.junit.Assert.*;
import org.junit.Test;

public class ScriptCodecTest {
    private static Script sample() {
        Script s = new Script();
        s.name = "测试";
        s.loop = LoopPolicy.count(7);
        Step a = Step.tap(10, 20); a.pressMs = 80; a.delayAfterMs = 1234;
        Step b = Step.swipe(1, 2, 300, 400); b.pressMs = 450; b.delayAfterMs = 0;
        s.steps.add(a); s.steps.add(b);
        return s;
    }

    @Test public void roundTripKeepsEveryField() throws Exception {
        Script d = ScriptCodec.decode(ScriptCodec.encode(sample()));
        assertEquals("测试", d.name);
        assertEquals(LoopPolicy.Mode.COUNT, d.loop.mode);
        assertEquals(7, d.loop.count);
        assertEquals(2, d.steps.size());
        Step a = d.steps.get(0), b = d.steps.get(1);
        assertEquals(Step.Type.TAP, a.type);
        assertEquals(10, a.x); assertEquals(20, a.y);
        assertEquals(80, a.pressMs); assertEquals(1234, a.delayAfterMs);
        assertEquals(Step.Type.SWIPE, b.type);
        assertEquals(1, b.x); assertEquals(2, b.y); assertEquals(300, b.x2); assertEquals(400, b.y2);
        assertEquals(450, b.pressMs); assertEquals(0, b.delayAfterMs);
    }

    @Test public void durationLoopRoundTrips() throws Exception {
        Script s = sample(); s.loop = LoopPolicy.duration(90_000);
        Script d = ScriptCodec.decode(ScriptCodec.encode(s));
        assertEquals(LoopPolicy.Mode.DURATION, d.loop.mode);
        assertEquals(90_000, d.loop.durationMs);
    }

    private static void assertRejects(String json) {
        try { ScriptCodec.decode(json); fail("should reject: " + json); }
        catch (ScriptCodec.DecodeException expected) { assertNotNull(expected.getMessage()); }
    }

    private static String withStep(String step) {
        return "{\"format\":\"noadclicker\",\"version\":1,\"name\":\"x\",\"loop\":{\"mode\":\"INFINITE\"},\"steps\":[" + step + "]}";
    }

    @Test public void rejectsForeignFormat() {
        assertRejects("{\"format\":\"other\",\"version\":1,\"loop\":{\"mode\":\"INFINITE\"},\"steps\":[]}");
    }
    @Test public void rejectsWrongVersion() {
        assertRejects("{\"format\":\"noadclicker\",\"version\":2,\"loop\":{\"mode\":\"INFINITE\"},\"steps\":[]}");
    }
    @Test public void rejectsNotJson() { assertRejects("hello"); }
    @Test public void rejectsUnknownType() {
        assertRejects(withStep("{\"type\":\"PINCH\",\"x\":1,\"y\":1,\"pressMs\":50,\"delayAfterMs\":0}"));
    }
    @Test public void rejectsNegativeCoordinate() {
        assertRejects(withStep("{\"type\":\"TAP\",\"x\":-1,\"y\":1,\"pressMs\":50,\"delayAfterMs\":0}"));
    }
    @Test public void rejectsPressOutOfRange() {
        assertRejects(withStep("{\"type\":\"TAP\",\"x\":1,\"y\":1,\"pressMs\":0,\"delayAfterMs\":0}"));
        assertRejects(withStep("{\"type\":\"TAP\",\"x\":1,\"y\":1,\"pressMs\":60001,\"delayAfterMs\":0}"));
    }
    @Test public void rejectsDelayOutOfRange() {
        assertRejects(withStep("{\"type\":\"TAP\",\"x\":1,\"y\":1,\"pressMs\":50,\"delayAfterMs\":3600001}"));
    }
    @Test public void rejectsSwipeWithoutEnd() {
        assertRejects(withStep("{\"type\":\"SWIPE\",\"x\":1,\"y\":1,\"pressMs\":50,\"delayAfterMs\":0}"));
    }
    @Test public void rejectsZeroCount() {
        assertRejects("{\"format\":\"noadclicker\",\"version\":1,\"loop\":{\"mode\":\"COUNT\",\"count\":0},\"steps\":[]}");
    }
    @Test public void acceptsBoundaryValues() throws Exception {
        Script d = ScriptCodec.decode(withStep("{\"type\":\"TAP\",\"x\":0,\"y\":0,\"pressMs\":60000,\"delayAfterMs\":3600000}"));
        assertEquals(60000, d.steps.get(0).pressMs);
    }
}

package com.lhy.noadclicker.core;

/** Decides whether another step may start. */
public final class LoopPolicy {
    public enum Mode { INFINITE, COUNT, DURATION }

    public Mode mode = Mode.INFINITE;
    public int count = 1;
    public long durationMs = 60_000;

    public static LoopPolicy infinite() { return new LoopPolicy(); }
    public static LoopPolicy count(int n) { LoopPolicy p = new LoopPolicy(); p.mode = Mode.COUNT; p.count = n; return p; }
    public static LoopPolicy duration(long ms) { LoopPolicy p = new LoopPolicy(); p.mode = Mode.DURATION; p.durationMs = ms; return p; }

    /**
     * @param completedRounds full passes through the step list already finished
     * @param elapsedMs time since the run started
     */
    public boolean mayStartRound(int completedRounds, long elapsedMs) {
        switch (mode) {
            case COUNT: return completedRounds < count;
            case DURATION: return elapsedMs < durationMs;
            default: return true;
        }
    }

    /** DURATION also stops between steps inside a round. */
    public boolean mayStartStep(long elapsedMs) {
        return mode != Mode.DURATION || elapsedMs < durationMs;
    }

    public String validate() {
        if (mode == null) return "缺少循环模式";
        if (mode == Mode.COUNT && count < 1) return "次数至少为 1";
        if (mode == Mode.DURATION && durationMs < 1) return "时长至少 1 毫秒";
        return null;
    }
}

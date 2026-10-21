package com.lhy.noadclicker.core;

/** Yields which step runs next, honouring the loop policy. */
public final class Sequencer {
    private final int stepCount;
    private final LoopPolicy policy;
    private int index;   // next step within current round
    private int rounds;  // completed rounds

    public Sequencer(int stepCount, LoopPolicy policy) {
        this.stepCount = stepCount;
        this.policy = policy;
    }

    /** @return index of the step to run now, or -1 when the run is over. */
    public int next(long elapsedMs) {
        if (stepCount == 0) return -1;
        if (index == stepCount) { index = 0; rounds++; }
        if (index == 0 && !policy.mayStartRound(rounds, elapsedMs)) return -1;
        if (!policy.mayStartStep(elapsedMs)) return -1;
        return index++;
    }

    public int completedRounds() { return rounds; }
}

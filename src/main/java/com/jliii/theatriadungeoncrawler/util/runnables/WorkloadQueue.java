package com.jliii.theatriadungeoncrawler.util.runnables;

import java.util.ArrayDeque;
import java.util.Deque;

public class WorkloadQueue implements Runnable {

    /** Max wall-clock spent placing blocks per tick, per instance queue. */
    private static final double MAX_MILLIS_PER_TICK = 0.5;
    private static final int MAX_NANOS_PER_TICK = (int) (MAX_MILLIS_PER_TICK * 1E6);

    private final Deque<Workload> workloadDeque = new ArrayDeque<>();
    private boolean manualExecution = false;

    public void addWorkload(Workload workload) {
        this.workloadDeque.add(workload);
    }

    public void setManualExecution(boolean manualExecution) {
        this.manualExecution = manualExecution;
    }

    /** @return {@code true} if there is queued work still to process. */
    public boolean isBusy() {
        return !this.workloadDeque.isEmpty();
    }

    @Override
    public void run() {
        if (manualExecution) {
            return;
        }
        long stopTime = System.nanoTime() + MAX_NANOS_PER_TICK;

        Workload nextLoad;

        while (System.nanoTime() <= stopTime && (nextLoad = this.workloadDeque.poll()) != null) {
            // An incomplete workload (e.g. a region filled in batches) resumes
            // at the front of the queue, preserving order for what follows it.
            if (!nextLoad.compute()) {
                this.workloadDeque.addFirst(nextLoad);
            }
        }

    }

    public void executeNextWorkload() {
        Workload nextLoad = this.workloadDeque.poll();
        if (nextLoad != null && !nextLoad.compute()) {
            this.workloadDeque.addFirst(nextLoad);
        }
    }
}

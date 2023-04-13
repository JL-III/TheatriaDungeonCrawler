package com.jliii.theatriadungeoncrawler.util.runnables;

import java.util.ArrayDeque;
import java.util.Deque;

public class WorkloadRunnable implements Runnable {

    private static final double MAX_MILLIS_PER_TICK = 1;
    private static final int MAX_NANOS_PER_TICK = (int) (MAX_MILLIS_PER_TICK * 1E6);

    private final Deque<Workload> workloadDeque = new ArrayDeque<>();
    private boolean manualExecution = false;

    public void addWorkload(Workload workload) {
        this.workloadDeque.add(workload);
    }

    public void setManualExecution(boolean manualExecution) {
        this.manualExecution = manualExecution;
    }

    @Override
    public void run() {
        if (manualExecution) {
            return;
        }
        long stopTime = System.nanoTime() + MAX_NANOS_PER_TICK;

        Workload nextLoad;

        while (System.nanoTime() <= stopTime && (nextLoad = this.workloadDeque.poll()) != null) {
            nextLoad.compute();
        }

    }

    public void executeNextWorkload() {
        Workload nextLoad = this.workloadDeque.poll();
        if (nextLoad != null) {
            nextLoad.compute();
        }
    }
}

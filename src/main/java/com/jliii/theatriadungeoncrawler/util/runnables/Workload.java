package com.jliii.theatriadungeoncrawler.util.runnables;

public interface Workload {

    /**
     * Performs a slice of work.
     *
     * @return {@code true} if the workload is fully complete, or {@code false} if
     *         it should be resumed (e.g. a large region filled in batches).
     */
    boolean compute();

}

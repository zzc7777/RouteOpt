package org.zzc.bpcsolver.core.branch;

public enum ColumnGenerationStatus {
    NOT_STARTED(false),
    CONVERGED(true),
    ITERATION_LIMIT(false),
    MASTER_INFEASIBLE(false);

    private final boolean closed;

    ColumnGenerationStatus(boolean closed) {
        this.closed = closed;
    }

    public boolean isClosed() {
        return closed;
    }
}

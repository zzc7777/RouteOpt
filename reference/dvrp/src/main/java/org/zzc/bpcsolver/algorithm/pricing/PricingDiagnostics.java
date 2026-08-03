package org.zzc.bpcsolver.algorithm.pricing;

/**
 * Lightweight per-pricing-call diagnostics. The current pricing path can run in
 * parallel, so collection is thread-local and the solver aggregates snapshots.
 */
public final class PricingDiagnostics {

    private static final ThreadLocal<Snapshot> CURRENT = ThreadLocal.withInitial(Snapshot::new);
    private static final ThreadLocal<Long> DEADLINE_NANOS = new ThreadLocal<>();

    private PricingDiagnostics() {
    }

    public static void reset() {
        CURRENT.set(new Snapshot());
        DEADLINE_NANOS.remove();
    }

    public static void setDeadlineNanos(long deadlineNanos) {
        if (deadlineNanos <= 0L || deadlineNanos == Long.MAX_VALUE) {
            DEADLINE_NANOS.remove();
            return;
        }
        DEADLINE_NANOS.set(deadlineNanos);
    }

    public static Snapshot snapshotAndClear() {
        Snapshot snapshot = CURRENT.get().copy();
        CURRENT.remove();
        DEADLINE_NANOS.remove();
        return snapshot;
    }

    public static boolean isTimeLimitReached() {
        Long deadlineNanos = DEADLINE_NANOS.get();
        if (deadlineNanos == null) {
            return false;
        }
        boolean reached = System.nanoTime() >= deadlineNanos;
        if (reached) {
            CURRENT.get().timeLimitReached = true;
        }
        return reached;
    }

    public static void recordLabelCreated() {
        CURRENT.get().labelsCreated++;
    }

    public static void recordLabelExtended() {
        CURRENT.get().labelsExtended++;
    }

    public static void recordDominatedLabel() {
        CURRENT.get().labelsDominated++;
    }

    public static void recordRemovedDominatedLabel() {
        CURRENT.get().labelsRemovedByDominance++;
    }

    public static void recordDominanceElapsed(long elapsedNanos) {
        CURRENT.get().dominanceElapsedNanos += Math.max(0L, elapsedNanos);
    }

    public static void recordExtensionElapsed(long elapsedNanos) {
        CURRENT.get().extensionElapsedNanos += Math.max(0L, elapsedNanos);
    }

    public static void recordMaxLabelsPerVertex(int labels) {
        Snapshot snapshot = CURRENT.get();
        snapshot.maxLabelsPerVertex = Math.max(snapshot.maxLabelsPerVertex, Math.max(0, labels));
    }

    public static void recordSinkLabels(int labels) {
        CURRENT.get().sinkLabels = Math.max(0, labels);
    }

    public static final class Snapshot {
        private long labelsCreated;
        private long labelsExtended;
        private long labelsDominated;
        private long labelsRemovedByDominance;
        private int maxLabelsPerVertex;
        private int sinkLabels;
        private boolean timeLimitReached;
        private long dominanceElapsedNanos;
        private long extensionElapsedNanos;

        public Snapshot copy() {
            Snapshot copy = new Snapshot();
            copy.labelsCreated = this.labelsCreated;
            copy.labelsExtended = this.labelsExtended;
            copy.labelsDominated = this.labelsDominated;
            copy.labelsRemovedByDominance = this.labelsRemovedByDominance;
            copy.maxLabelsPerVertex = this.maxLabelsPerVertex;
            copy.sinkLabels = this.sinkLabels;
            copy.timeLimitReached = this.timeLimitReached;
            copy.dominanceElapsedNanos = this.dominanceElapsedNanos;
            copy.extensionElapsedNanos = this.extensionElapsedNanos;
            return copy;
        }

        public long getLabelsCreated() {
            return labelsCreated;
        }

        public long getLabelsExtended() {
            return labelsExtended;
        }

        public long getLabelsDominated() {
            return labelsDominated;
        }

        public long getLabelsRemovedByDominance() {
            return labelsRemovedByDominance;
        }

        public int getMaxLabelsPerVertex() {
            return maxLabelsPerVertex;
        }

        public int getSinkLabels() {
            return sinkLabels;
        }

        public boolean isTimeLimitReached() {
            return timeLimitReached;
        }

        public long getDominanceElapsedNanos() {
            return dominanceElapsedNanos;
        }

        public long getExtensionElapsedNanos() {
            return extensionElapsedNanos;
        }
    }
}

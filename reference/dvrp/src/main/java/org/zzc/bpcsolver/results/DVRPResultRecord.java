package org.zzc.bpcsolver.results;

public final class DVRPResultRecord {

    private final String runId;
    private final String solver;
    private final String instance;
    private final Integer numCustomers;
    private final String decisionTimes;
    private final double finalActualDistance;
    private final long solveTimeMillis;
    private final String status;
    private final String notes;

    private DVRPResultRecord(Builder builder) {
        this.runId = safe(builder.runId);
        this.solver = safe(builder.solver);
        this.instance = safe(builder.instance);
        this.numCustomers = builder.numCustomers;
        this.decisionTimes = safe(builder.decisionTimes);
        this.finalActualDistance = builder.finalActualDistance;
        this.solveTimeMillis = builder.solveTimeMillis;
        this.status = safe(builder.status);
        this.notes = safe(builder.notes);
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getRunId() {
        return runId;
    }

    public String getSolver() {
        return solver;
    }

    public String getInstance() {
        return instance;
    }

    public Integer getNumCustomers() {
        return numCustomers;
    }

    public String getDecisionTimes() {
        return decisionTimes;
    }

    public double getFinalActualDistance() {
        return finalActualDistance;
    }

    public long getSolveTimeMillis() {
        return solveTimeMillis;
    }

    public String getStatus() {
        return status;
    }

    public String getNotes() {
        return notes;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    public static final class Builder {
        private String runId;
        private String solver;
        private String instance;
        private Integer numCustomers;
        private String decisionTimes;
        private double finalActualDistance;
        private long solveTimeMillis;
        private String status;
        private String notes;

        private Builder() {
        }

        public Builder runId(String runId) {
            this.runId = runId;
            return this;
        }

        public Builder solver(String solver) {
            this.solver = solver;
            return this;
        }

        public Builder instance(String instance) {
            this.instance = instance;
            return this;
        }

        public Builder numCustomers(Integer numCustomers) {
            this.numCustomers = numCustomers;
            return this;
        }

        public Builder decisionTimes(String decisionTimes) {
            this.decisionTimes = decisionTimes;
            return this;
        }

        public Builder finalActualDistance(double finalActualDistance) {
            this.finalActualDistance = finalActualDistance;
            return this;
        }

        public Builder solveTimeMillis(long solveTimeMillis) {
            this.solveTimeMillis = solveTimeMillis;
            return this;
        }

        public Builder status(String status) {
            this.status = status;
            return this;
        }

        public Builder notes(String notes) {
            this.notes = notes;
            return this;
        }

        public DVRPResultRecord build() {
            return new DVRPResultRecord(this);
        }
    }
}

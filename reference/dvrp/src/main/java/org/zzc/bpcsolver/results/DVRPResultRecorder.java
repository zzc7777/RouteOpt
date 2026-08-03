package org.zzc.bpcsolver.results;

import org.zzc.bpcsolver.algorithm.DVRPDynamicSolver;
import org.zzc.bpcsolver.algorithm.config.Configuration;
import org.zzc.bpcsolver.core.branch.StageSolution;
import org.zzc.bpcsolver.exact.BPCExactComparisonMain;
import org.zzc.bpcsolver.exact.OriginalMipDVRPSolver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DVRPResultRecorder {

    public enum SolverFamily {
        JAVA("java"),
        CPLEX("cplex");

        private final String folderName;

        SolverFamily(String folderName) {
            this.folderName = folderName;
        }

        public String getFolderName() {
            return folderName;
        }
    }

    private static final String JAVA_SOLVER_CONFIG_PATH = "src/main/java/org/zzc/bpcsolver/algorithm/config/bpc_solver.yaml";
    private static final String JAVA_INPUT_CONFIG_PATH = "src/main/resources/dvrp_td.yaml";
    private static final String DEFAULT_RESULTS_DIR = "results";

    private final Configuration.ResultRecordingConfig config;
    private final Path resultsDir;
    private final DVRPResultWorkbookWriter workbookWriter;
    private final SolverFamily solverFamily;

    public DVRPResultRecorder(Configuration.ResultRecordingConfig config) {
        this(config, inferSolverFamily(config), new DVRPResultWorkbookWriter());
    }

    public DVRPResultRecorder(Configuration.ResultRecordingConfig config,
                              SolverFamily solverFamily,
                              DVRPResultWorkbookWriter workbookWriter) {
        this.config = config == null ? new Configuration.ResultRecordingConfig() : config;
        this.solverFamily = solverFamily == null ? SolverFamily.JAVA : solverFamily;
        this.resultsDir = Paths.get(resolveResultsDir(this.config)).resolve(this.solverFamily.getFolderName());
        this.workbookWriter = workbookWriter == null ? new DVRPResultWorkbookWriter() : workbookWriter;
    }

    public static DVRPResultRecorder fromDefaultSolverConfig() {
        return fromSolverConfigPath(JAVA_SOLVER_CONFIG_PATH);
    }

    public static DVRPResultRecorder fromSolverConfigPath(String configPath) {
        Configuration configuration = new Configuration();
        configuration.createConfig(configPath);
        return new DVRPResultRecorder(configuration.getResultRecording(), SolverFamily.JAVA, new DVRPResultWorkbookWriter());
    }

    public static DVRPResultRecorder forCplex(Configuration.ResultRecordingConfig config) {
        return new DVRPResultRecorder(config, SolverFamily.CPLEX, new DVRPResultWorkbookWriter());
    }

    public DVRPResultRecorder forSolverFamily(SolverFamily solverFamily) {
        return new DVRPResultRecorder(config, solverFamily, workbookWriter);
    }

    public boolean isEnabled() {
        return config.isEnabled();
    }

    public Path getResultsDir() {
        return resultsDir;
    }

    public String nextRunId() {
        try {
            Files.createDirectories(resultsDir);
            int next = 1;
            try (java.util.stream.Stream<Path> files = Files.list(resultsDir)) {
                next = files
                        .filter(Files::isDirectory)
                        .map(path -> path.getFileName().toString())
                        .filter(name -> name.startsWith("run-"))
                        .map(name -> name.substring("run-".length()))
                        .mapToInt(this::parseRunNumberOrZero)
                        .max()
                        .orElse(0) + 1;
            }
            return String.format("run-%04d", next);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to allocate result run id.", e);
        }
    }

    public void recordJavaRun(String solver,
                              String instance,
                              List<DVRPDynamicSolver.Result> results,
                              long totalSolveTimeMillis,
                              String notes) {
        if (!isEnabled()) {
            return;
        }
        if (results == null) {
            throw new IllegalArgumentException("results must not be null.");
        }
        String runId = nextRunId();
        String runNotes = combineNotes(config.getRunDescription(), notes);
        DVRPResultRecord finalRecord = DVRPResultRecord.builder()
                .runId(runId)
                .solver(solver)
                .instance(instance)
                .numCustomers(resolveNumCustomers(results))
                .decisionTimes(joinStageDecisionTimes(results))
                .finalActualDistance(sumActualDistance(results))
                .solveTimeMillis(totalSolveTimeMillis)
                .status(determineJavaStatus(results))
                .notes(runNotes)
                .build();
        write(runId, Collections.singletonList(finalRecord), runNotes, true);
    }

    public void recordBpcExactComparison(String instance,
                                         List<BPCExactComparisonMain.ComparisonRow> rows,
                                         String notes) {
        if (!isEnabled()) {
            return;
        }
        if (rows == null) {
            throw new IllegalArgumentException("rows must not be null.");
        }
        String runId = nextRunId();
        String runNotes = combineNotes(config.getRunDescription(), notes);
        List<DVRPResultRecord> finalRecords = new ArrayList<>();
        finalRecords.add(buildComparisonFinalRecord(runId, "java-bpc", instance, rows, true, runNotes));
        finalRecords.add(buildComparisonFinalRecord(runId, "cplex-same-stage", instance, rows, false, runNotes));
        write(runId, finalRecords, runNotes, true);
    }

    public void recordOriginalMipRun(String instance,
                                     int numCustomers,
                                     String decisionTimes,
                                     List<OriginalMipStageRecord> rows,
                                     long totalSolveTimeMillis,
                                     String notes) {
        if (!isEnabled()) {
            return;
        }
        if (rows == null) {
            throw new IllegalArgumentException("rows must not be null.");
        }
        String runId = nextRunId();
        String runNotes = combineNotes(config.getRunDescription(), notes);
        DVRPResultRecord finalRecord = DVRPResultRecord.builder()
                .runId(runId)
                .solver("cplex-original-mip")
                .instance(instance)
                .numCustomers(numCustomers)
                .decisionTimes(decisionTimes)
                .finalActualDistance(rows.stream().mapToDouble(OriginalMipStageRecord::getActualDistance).sum())
                .solveTimeMillis(totalSolveTimeMillis)
                .status(determineStatus(rows, totalSolveTimeMillis))
                .notes(runNotes)
                .build();
        write(runId, Collections.singletonList(finalRecord), runNotes, false);
    }

    public void write(String runId,
                      DVRPResultRecord finalRecord,
                      String notes) {
        List<DVRPResultRecord> finalRecords = new ArrayList<>();
        finalRecords.add(finalRecord);
        write(runId, finalRecords, notes, false);
    }

    public void write(String runId,
                      List<DVRPResultRecord> finalRecords,
                      String notes,
                      boolean copyJavaInputs) {
        if (!isEnabled()) {
            return;
        }
        Path runDir = resultsDir.resolve(runId);
        workbookWriter.write(runDir, finalRecords);
        if (copyJavaInputs && solverFamily == SolverFamily.JAVA) {
            copyJavaInputSnapshots(runDir);
        }
    }

    private DVRPResultRecord buildComparisonFinalRecord(String runId,
                                                        String solver,
                                                        String instance,
                                                        List<BPCExactComparisonMain.ComparisonRow> rows,
                                                        boolean bpc,
                                                        String notes) {
        double actualDistance = 0.0;
        long solveTimeMillis = 0L;
        boolean feasible = true;
        for (BPCExactComparisonMain.ComparisonRow row : rows) {
            actualDistance += bpc ? row.getBpcActualDistanceToNextStage() : row.getExactActualDistanceToNextStage();
            solveTimeMillis += bpc
                    ? row.getBpcRunStatistics().getElapsedMillis()
                    : row.getExactResult().getSolveElapsedMillis();
            if (!bpc && !row.getExactResult().isFeasible()) {
                feasible = false;
            }
        }
        return DVRPResultRecord.builder()
                .runId(runId)
                .solver(solver)
                .instance(instance)
                .numCustomers(rows.isEmpty() ? null : rows.get(0).getStageInstance().getProblemData().getCustomers().size())
                .decisionTimes(joinComparisonDecisionTimes(rows))
                .finalActualDistance(actualDistance)
                .solveTimeMillis(solveTimeMillis)
                .status(feasible ? "success" : "timeout")
                .notes(notes)
                .build();
    }

    private double sumActualDistance(List<DVRPDynamicSolver.Result> results) {
        double total = 0.0;
        for (DVRPDynamicSolver.Result result : results) {
            total += result.getActualDistanceToNextStage();
        }
        return total;
    }

    private int resolveNumCustomers(List<DVRPDynamicSolver.Result> results) {
        if (results == null || results.isEmpty() || results.get(0).getStageInstance() == null) {
            return 0;
        }
        return results.get(0).getStageInstance().getProblemData().getCustomers().size();
    }

    private String joinStageDecisionTimes(List<DVRPDynamicSolver.Result> results) {
        if (results == null || results.isEmpty()) {
            return "";
        }
        List<String> values = new ArrayList<>();
        for (DVRPDynamicSolver.Result result : results) {
            values.add(String.valueOf(result.getStageInstance().getCurrentTime()));
        }
        return String.join(";", values);
    }

    private String joinComparisonDecisionTimes(List<BPCExactComparisonMain.ComparisonRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return "";
        }
        List<String> values = new ArrayList<>();
        for (BPCExactComparisonMain.ComparisonRow row : rows) {
            values.add(String.valueOf(row.getStageInstance().getCurrentTime()));
        }
        return String.join(";", values);
    }

    private String determineStatus(List<OriginalMipStageRecord> rows, long totalSolveTimeMillis) {
        if (rows == null || rows.isEmpty()) {
            return "success";
        }
        boolean anyTimeout = false;
        boolean anyInfeasible = false;
        boolean anyUnknown = false;
        for (OriginalMipStageRecord row : rows) {
            String status = row.getSolveStatus();
            if (status == null || status.trim().isEmpty()) {
                anyUnknown = true;
                continue;
            }
            String normalized = status.toLowerCase();
            String compact = normalized.replace("_", "").replace(" ", "");
            if (normalized.contains("optimal")) {
                continue;
            }
            if (normalized.contains("time")
                    || normalized.contains("abort")
                    || normalized.contains("nodelimit")
                    || "feasible".equals(compact)
                    || "infeasibleorunbounded".equals(compact)) {
                anyTimeout = true;
                continue;
            }
            if ("infeasible".equals(compact)) {
                anyInfeasible = true;
                continue;
            }
            if ("unbounded".equals(compact)) {
                anyInfeasible = true;
                continue;
            }
            anyUnknown = true;
        }
        if (anyInfeasible) {
            return "infeasible";
        }
        if (anyTimeout) {
            return "timeout";
        }
        if (anyUnknown) {
            return "error";
        }
        return "success";
    }

    private String determineJavaStatus(List<DVRPDynamicSolver.Result> results) {
        if (results == null) {
            return "error";
        }
        for (DVRPDynamicSolver.Result result : results) {
            if (result == null) {
                return "error";
            }
            StageSolution solution = result.getStageSolution();
            if (solution == null || !solution.isIntegerSolution()) {
                return "lp_fallback";
            }
        }
        return "success";
    }

    private void copyJavaInputSnapshots(Path runDir) {
        try {
            Files.copy(Paths.get(JAVA_INPUT_CONFIG_PATH), runDir.resolve("dvrp_td.yaml"), StandardCopyOption.REPLACE_EXISTING);
            Files.copy(Paths.get(JAVA_SOLVER_CONFIG_PATH), runDir.resolve("bpc_solver.yaml"), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to copy Java result input snapshots.", e);
        }
    }

    private int parseRunNumberOrZero(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String resolveResultsDir(Configuration.ResultRecordingConfig config) {
        if (config == null || config.getResultsDir() == null || config.getResultsDir().trim().isEmpty()) {
            return DEFAULT_RESULTS_DIR;
        }
        return config.getResultsDir().trim();
    }

    private static SolverFamily inferSolverFamily(Configuration.ResultRecordingConfig config) {
        return SolverFamily.JAVA;
    }

    private static String combineNotes(String left, String right) {
        String cleanLeft = left == null ? "" : left.trim();
        String cleanRight = right == null ? "" : right.trim();
        if (cleanLeft.isEmpty()) {
            return cleanRight;
        }
        if (cleanRight.isEmpty()) {
            return cleanLeft;
        }
        return cleanLeft + " | " + cleanRight;
    }

    public static final class OriginalMipStageRecord {
        private final int stageIndex;
        private final double currentTime;
        private final double objective;
        private final double actualDistance;
        private final long solveTimeMillis;
        private final String solveStatus;

        public OriginalMipStageRecord(int stageIndex,
                                      double currentTime,
                                      double objective,
                                      double actualDistance,
                                      long solveTimeMillis,
                                      String solveStatus) {
            this.stageIndex = stageIndex;
            this.currentTime = currentTime;
            this.objective = objective;
            this.actualDistance = actualDistance;
            this.solveTimeMillis = solveTimeMillis;
            this.solveStatus = solveStatus;
        }

        public int getStageIndex() {
            return stageIndex;
        }

        public double getCurrentTime() {
            return currentTime;
        }

        public double getObjective() {
            return objective;
        }

        public double getActualDistance() {
            return actualDistance;
        }

        public long getSolveTimeMillis() {
            return solveTimeMillis;
        }

        public String getSolveStatus() {
            return solveStatus;
        }
    }
}

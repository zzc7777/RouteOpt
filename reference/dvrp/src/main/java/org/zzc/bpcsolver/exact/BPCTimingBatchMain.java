package org.zzc.bpcsolver.exact;

import org.zzc.bpcsolver.algorithm.BPCSolver;
import org.zzc.bpcsolver.algorithm.BPCSolverTemplate;
import org.zzc.bpcsolver.algorithm.DVRPDynamicSolver;
import org.zzc.bpcsolver.core.branch.ColumnGenerationStatus;
import org.zzc.bpcsolver.core.branch.StageSolution;
import org.zzc.bpcsolver.problem.config.ProblemConfig;
import org.zzc.bpcsolver.problem.config.ProblemConfigReader;
import org.zzc.bpcsolver.problem.entity.DynamicInstance;
import org.zzc.bpcsolver.problem.entity.ProblemData;
import org.zzc.bpcsolver.problem.loader.ProblemDataLoader;
import org.zzc.bpcsolver.problem.update.DynamicStateUpdater;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class BPCTimingBatchMain {

    private static final List<String> DEFAULT_INSTANCE_NAMES = BPCExactBatchMain.broadCoverageInstanceNames();
    public static void main(String[] args) {
        List<String> instanceNames = parseOptionalInstanceNamesArg(args, 0);
        int maxAddedColumnsPerIteration = parseOptionalIntArg(
                args,
                1,
                BPCSolver.DEFAULT_MAX_ADDED_COLUMNS_PER_ITERATION
        );
        int maxIterations = parseOptionalIntArg(args, 2, BPCSolver.DEFAULT_MAX_ITERATIONS);
        String heuristicPricingRule = parseOptionalHeuristicPricingRule(args, 3);
        String primalHeuristicRule = parseOptionalPrimalHeuristicRule(args, 4);
        boolean parallelPricing = parseOptionalBooleanArg(args, 5, true);
        String cuttingRule = parseOptionalCuttingRule(args, 6);
        Integer numCustomers = parseOptionalIntegerArg(args, 7);
        String relaxationRule = parseOptionalRelaxationRule(args, 8);

        List<InstanceTimingSummary> summaries = measureInstances(
                instanceNames,
                maxAddedColumnsPerIteration,
                maxIterations,
                heuristicPricingRule,
                primalHeuristicRule,
                parallelPricing,
                cuttingRule,
                numCustomers,
                relaxationRule
        );
        printStageRows(summaries);
        System.out.println();
        printSummaries(summaries);
    }

    public static List<InstanceTimingSummary> measureInstances(List<String> instanceNames,
                                                               int maxAddedColumnsPerIteration,
                                                               int maxIterations) {
        return measureInstances(
                instanceNames,
                maxAddedColumnsPerIteration,
                maxIterations,
                null,
                null,
                true,
                null
        );
    }

    public static List<InstanceTimingSummary> measureInstances(List<String> instanceNames,
                                                               int maxAddedColumnsPerIteration,
                                                               int maxIterations,
                                                               String heuristicPricingRule) {
        return measureInstances(
                instanceNames,
                maxAddedColumnsPerIteration,
                maxIterations,
                heuristicPricingRule,
                null,
                true,
                null
        );
    }

    public static List<InstanceTimingSummary> measureInstances(List<String> instanceNames,
                                                               int maxAddedColumnsPerIteration,
                                                               int maxIterations,
                                                               String heuristicPricingRule,
                                                               String primalHeuristicRule) {
        return measureInstances(
                instanceNames,
                maxAddedColumnsPerIteration,
                maxIterations,
                heuristicPricingRule,
                primalHeuristicRule,
                true,
                null
        );
    }

    public static List<InstanceTimingSummary> measureInstances(List<String> instanceNames,
                                                               int maxAddedColumnsPerIteration,
                                                               int maxIterations,
                                                               String heuristicPricingRule,
                                                               String primalHeuristicRule,
                                                               boolean parallelPricing) {
        return measureInstances(
                instanceNames,
                maxAddedColumnsPerIteration,
                maxIterations,
                heuristicPricingRule,
                primalHeuristicRule,
                parallelPricing,
                null,
                null
        );
    }

    public static List<InstanceTimingSummary> measureInstances(List<String> instanceNames,
                                                               int maxAddedColumnsPerIteration,
                                                               int maxIterations,
                                                               String heuristicPricingRule,
                                                               String primalHeuristicRule,
                                                               boolean parallelPricing,
                                                               String cuttingRule) {
        return measureInstances(
                instanceNames,
                maxAddedColumnsPerIteration,
                maxIterations,
                heuristicPricingRule,
                primalHeuristicRule,
                parallelPricing,
                cuttingRule,
                null,
                null
        );
    }

    public static List<InstanceTimingSummary> measureInstances(List<String> instanceNames,
                                                               int maxAddedColumnsPerIteration,
                                                               int maxIterations,
                                                               String heuristicPricingRule,
                                                               String primalHeuristicRule,
                                                               boolean parallelPricing,
                                                               String cuttingRule,
                                                               Integer numCustomers) {
        return measureInstances(
                instanceNames,
                maxAddedColumnsPerIteration,
                maxIterations,
                heuristicPricingRule,
                primalHeuristicRule,
                parallelPricing,
                cuttingRule,
                numCustomers,
                null
        );
    }

    public static List<InstanceTimingSummary> measureInstances(List<String> instanceNames,
                                                               int maxAddedColumnsPerIteration,
                                                               int maxIterations,
                                                               String heuristicPricingRule,
                                                               String primalHeuristicRule,
                                                               boolean parallelPricing,
                                                               String cuttingRule,
                                                               Integer numCustomers,
                                                               String relaxationRule) {
        if (instanceNames == null) {
            throw new IllegalArgumentException("instanceNames must not be null.");
        }

        List<InstanceTimingSummary> summaries = new ArrayList<>();
        for (String instanceName : instanceNames) {
            if (instanceName == null || instanceName.trim().isEmpty()) {
                continue;
            }
            String normalizedInstanceName = instanceName.trim();
            long instanceStartNanos = System.nanoTime();
            ProblemData problemData = loadProblemData(instanceName.trim(), numCustomers);
            List<TimingRow> rows = measureInstance(
                    normalizedInstanceName,
                    problemData,
                    maxAddedColumnsPerIteration,
                    maxIterations,
                    heuristicPricingRule,
                    primalHeuristicRule,
                    parallelPricing,
                    cuttingRule,
                    relaxationRule
            );
            long instanceWallElapsedMillis = Math.max(0L, Math.round((System.nanoTime() - instanceStartNanos) / 1_000_000.0));
            summaries.add(InstanceTimingSummary.from(instanceName.trim(), rows, instanceWallElapsedMillis));
        }
        return summaries;
    }

    public static List<TimingRow> measureInstance(String instanceName,
                                                  ProblemData problemData,
                                                  int maxAddedColumnsPerIteration,
                                                  int maxIterations,
                                                  String heuristicPricingRule) {
        return measureInstance(
                instanceName,
                problemData,
                maxAddedColumnsPerIteration,
                maxIterations,
                heuristicPricingRule,
                null,
                true,
                null
        );
    }

    public static List<TimingRow> measureInstance(String instanceName,
                                                  ProblemData problemData,
                                                  int maxAddedColumnsPerIteration,
                                                  int maxIterations,
                                                  String heuristicPricingRule,
                                                  String primalHeuristicRule) {
        return measureInstance(
                instanceName,
                problemData,
                maxAddedColumnsPerIteration,
                maxIterations,
                heuristicPricingRule,
                primalHeuristicRule,
                true,
                null
        );
    }

    public static List<TimingRow> measureInstance(String instanceName,
                                                  ProblemData problemData,
                                                  int maxAddedColumnsPerIteration,
                                                  int maxIterations,
                                                  String heuristicPricingRule,
                                                  String primalHeuristicRule,
                                                  boolean parallelPricing) {
        return measureInstance(
                instanceName,
                problemData,
                maxAddedColumnsPerIteration,
                maxIterations,
                heuristicPricingRule,
                primalHeuristicRule,
                parallelPricing,
                null,
                null
        );
    }

    public static List<TimingRow> measureInstance(String instanceName,
                                                  ProblemData problemData,
                                                  int maxAddedColumnsPerIteration,
                                                  int maxIterations,
                                                  String heuristicPricingRule,
                                                  String primalHeuristicRule,
                                                  boolean parallelPricing,
                                                  String cuttingRule) {
        if (instanceName == null) {
            throw new IllegalArgumentException("instanceName must not be null.");
        }
        return measureInstance(
                problemData,
                maxAddedColumnsPerIteration,
                maxIterations,
                heuristicPricingRule,
                primalHeuristicRule,
                parallelPricing,
                cuttingRule,
                instanceName,
                null
        );
    }

    public static List<TimingRow> measureInstance(String instanceName,
                                                  ProblemData problemData,
                                                  int maxAddedColumnsPerIteration,
                                                  int maxIterations,
                                                  String heuristicPricingRule,
                                                  String primalHeuristicRule,
                                                  boolean parallelPricing,
                                                  String cuttingRule,
                                                  String relaxationRule) {
        if (instanceName == null) {
            throw new IllegalArgumentException("instanceName must not be null.");
        }
        return measureInstance(
                problemData,
                maxAddedColumnsPerIteration,
                maxIterations,
                heuristicPricingRule,
                primalHeuristicRule,
                parallelPricing,
                cuttingRule,
                instanceName,
                relaxationRule
        );
    }

    public static List<TimingRow> measureInstance(ProblemData problemData,
                                                  int maxAddedColumnsPerIteration,
                                                  int maxIterations,
                                                  String heuristicPricingRule) {
        return measureInstance(
                problemData,
                maxAddedColumnsPerIteration,
                maxIterations,
                heuristicPricingRule,
                null,
                true,
                null
        );
    }

    public static List<TimingRow> measureInstance(ProblemData problemData,
                                                  int maxAddedColumnsPerIteration,
                                                  int maxIterations,
                                                  String heuristicPricingRule,
                                                  String primalHeuristicRule) {
        return measureInstance(
                problemData,
                maxAddedColumnsPerIteration,
                maxIterations,
                heuristicPricingRule,
                primalHeuristicRule,
                true,
                null
        );
    }

    public static List<TimingRow> measureInstance(ProblemData problemData,
                                                  int maxAddedColumnsPerIteration,
                                                  int maxIterations,
                                                  String heuristicPricingRule,
                                                  String primalHeuristicRule,
                                                  boolean parallelPricing) {
        return measureInstance(
                problemData,
                maxAddedColumnsPerIteration,
                maxIterations,
                heuristicPricingRule,
                primalHeuristicRule,
                parallelPricing,
                null,
                "unknown",
                null
        );
    }

    public static List<TimingRow> measureInstance(ProblemData problemData,
                                                  int maxAddedColumnsPerIteration,
                                                  int maxIterations,
                                                  String heuristicPricingRule,
                                                  String primalHeuristicRule,
                                                  boolean parallelPricing,
                                                  String cuttingRule) {
        return measureInstance(
                problemData,
                maxAddedColumnsPerIteration,
                maxIterations,
                heuristicPricingRule,
                primalHeuristicRule,
                parallelPricing,
                cuttingRule,
                "unknown",
                null
        );
    }

    private static List<TimingRow> measureInstance(ProblemData problemData,
                                                   int maxAddedColumnsPerIteration,
                                                   int maxIterations,
                                                   String heuristicPricingRule,
                                                   String primalHeuristicRule,
                                                   boolean parallelPricing,
                                                   String cuttingRule,
                                                   String instanceName,
                                                   String relaxationRule) {
        if (problemData == null) {
            throw new IllegalArgumentException("problemData must not be null.");
        }
        problemData.validate();

        List<Double> decisionTimes = DVRPDynamicSolver.buildEventDrivenDecisionTimes(problemData);
        DynamicStateUpdater updater = new DynamicStateUpdater();
        BPCSolver solver = new BPCSolver();
        solver.setHeuristicPricingRule(heuristicPricingRule);
        solver.setPrimalHeuristicRule(primalHeuristicRule);
        solver.setParallelPricing(parallelPricing);
        solver.setCuttingRule(cuttingRule);
        solver.setRelaxationRule(relaxationRule);

        DynamicInstance currentInstance = null;
        StageSolution currentSolution = new StageSolution();
        double horizon = problemData.getOriginDepot().getLatestTime();
        List<TimingRow> rows = new ArrayList<>();

        for (int timeIndex = 0; timeIndex < decisionTimes.size(); timeIndex++) {
            double currentTime = decisionTimes.get(timeIndex);
            if (currentTime >= horizon - 1e-9) {
                break;
            }
            if (currentInstance == null) {
                currentInstance = updater.createInitialInstance(problemData, currentTime);
            } else {
                currentInstance = updater.update(currentInstance, currentSolution, currentTime);
            }

            long stageStartNanos = System.nanoTime();
            BPCSolverTemplate.BPCRunStatistics runStatistics;
            BPCSolverTemplate.BranchSearchStatistics branchSearchStatistics;
            BPCSolver.SolveStatus solveStatus;
            ColumnGenerationStatus columnGenerationStatus;
            if (currentInstance.getRemainingCustomerIds().isEmpty()) {
                currentSolution = new StageSolution();
                currentSolution.setIntegerSolution(true);
                runStatistics = BPCSolverTemplate.BPCRunStatistics.empty();
                branchSearchStatistics = BPCSolverTemplate.BranchSearchStatistics.rootInteger(0.0, 0, 0);
                solveStatus = BPCSolver.SolveStatus.INTEGER_ROOT;
                columnGenerationStatus = ColumnGenerationStatus.CONVERGED;
            } else {
                BPCSolver.SolveResult solveResult = solver.solveNode(
                        currentInstance,
                        maxAddedColumnsPerIteration,
                        maxIterations
                );
                currentSolution = solveResult.getSolution();
                runStatistics = solveResult.getRunStatistics();
                branchSearchStatistics = solveResult.getBranchSearchStatistics();
                solveStatus = solveResult.getStatus();
                columnGenerationStatus = solveResult.getResultNode().getColumnGenerationStatus();
            }

            double nextDecisionTime = DVRPDynamicSolver.resolveNextDecisionTime(decisionTimes, timeIndex, horizon);
            double actualDistanceToNextStage = updater.calculateActualDistance(
                    currentInstance,
                    currentSolution,
                    nextDecisionTime
            );

            rows.add(new TimingRow(
                    instanceName,
                    normalizeOption(relaxationRule),
                    currentInstance.copy(),
                    timeIndex,
                    currentTime,
                    nextDecisionTime,
                    currentSolution.copy(),
                    actualDistanceToNextStage,
                    solveStatus,
                    columnGenerationStatus,
                    branchSearchStatistics,
                    runStatistics
                            ,
                    Math.max(0L, Math.round((System.nanoTime() - stageStartNanos) / 1_000_000.0))
            ));
        }
        return rows;
    }

    public static List<TimingRow> measureInstance(ProblemData problemData,
                                                  int maxAddedColumnsPerIteration,
                                                  int maxIterations) {
        return measureInstance(
                problemData,
                maxAddedColumnsPerIteration,
                maxIterations,
                null,
                null
        );
    }

    public static List<String> defaultInstanceNames() {
        return new ArrayList<>(DEFAULT_INSTANCE_NAMES);
    }

    public static List<String> instanceNamesForSuite(String suiteName) {
        return BPCExactBatchMain.instanceNamesForSuite(suiteName);
    }

    private static ProblemData loadProblemData(String instanceName) {
        return loadProblemData(instanceName, null);
    }

    private static ProblemData loadProblemData(String instanceName, Integer numCustomers) {
        ProblemConfig config = new ProblemConfigReader().read("dvrp_td.yaml");
        String fileName = instanceName.endsWith(".xlsx") ? instanceName : instanceName + ".xlsx";
        config.setInstanceFileName(fileName);
        config.setInstanceBaseName(fileName.substring(0, fileName.length() - ".xlsx".length()));
        if (numCustomers != null && numCustomers > 0) {
            config.setNumCustomers(numCustomers);
        }
        return new ProblemDataLoader().load(config);
    }

    private static List<String> parseOptionalInstanceNamesArg(String[] args, int index) {
        if (args == null || index >= args.length || args[index] == null || args[index].trim().isEmpty()) {
            return defaultInstanceNames();
        }
        Set<String> instanceNames = new LinkedHashSet<>();
        String[] tokens = args[index].split(",");
        for (String token : tokens) {
            if (token == null || token.trim().isEmpty()) {
                continue;
            }
            String trimmedToken = token.trim();
            if (isKnownSuiteName(trimmedToken)) {
                instanceNames.addAll(instanceNamesForSuite(trimmedToken));
            } else {
                instanceNames.add(trimmedToken);
            }
        }
        return instanceNames.isEmpty() ? defaultInstanceNames() : new ArrayList<>(instanceNames);
    }

    private static boolean isKnownSuiteName(String token) {
        if (token == null) {
            return false;
        }
        String normalizedToken = token.trim().toLowerCase(Locale.ROOT);
        return "default".equals(normalizedToken)
                || "representative".equals(normalizedToken)
                || "quick".equals(normalizedToken)
                || "broad".equals(normalizedToken)
                || "broad-coverage".equals(normalizedToken)
                || "expanded".equals(normalizedToken);
    }

    private static int parseOptionalIntArg(String[] args, int index, int defaultValue) {
        if (args == null || index >= args.length || args[index] == null || args[index].trim().isEmpty()) {
            return defaultValue;
        }
        return Integer.parseInt(args[index].trim());
    }

    private static Integer parseOptionalIntegerArg(String[] args, int index) {
        if (args == null || index >= args.length || args[index] == null || args[index].trim().isEmpty()) {
            return null;
        }
        String value = args[index].trim();
        if ("default".equalsIgnoreCase(value)) {
            return null;
        }
        return Integer.parseInt(value);
    }

    private static boolean parseOptionalBooleanArg(String[] args, int index, boolean defaultValue) {
        if (args == null || index >= args.length || args[index] == null || args[index].trim().isEmpty()) {
            return defaultValue;
        }
        String value = args[index].trim();
        if ("true".equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value) || "1".equals(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value) || "no".equalsIgnoreCase(value) || "0".equals(value)) {
            return false;
        }
        return defaultValue;
    }

    private static String parseOptionalHeuristicPricingRule(String[] args, int index) {
        if (args == null || index >= args.length || args[index] == null) {
            return null;
        }
        String option = args[index].trim();
        if (option.isEmpty()
                || "none".equalsIgnoreCase(option)
                || "false".equalsIgnoreCase(option)
                || "true".equalsIgnoreCase(option)
                || "default".equalsIgnoreCase(option)
                || "exact".equalsIgnoreCase(option)
                || "exact pricing".equalsIgnoreCase(option)) {
            return null;
        }
        return option;
    }

    private static String parseOptionalPrimalHeuristicRule(String[] args, int index) {
        if (args == null || index >= args.length || args[index] == null) {
            return null;
        }
        String option = args[index].trim();
        if (option.isEmpty()
                || "default".equalsIgnoreCase(option)
                || "true".equalsIgnoreCase(option)
                || "on".equalsIgnoreCase(option)
                || "column-pool".equalsIgnoreCase(option)
                || "column-pool-integer-master".equalsIgnoreCase(option)) {
            return "column-pool-integer-master";
        }
        if ("none".equalsIgnoreCase(option)
                || "false".equalsIgnoreCase(option)
                || "off".equalsIgnoreCase(option)
                || "noop".equalsIgnoreCase(option)) {
            return "none";
        }
        return option;
    }

    private static String parseOptionalCuttingRule(String[] args, int index) {
        if (args == null || index >= args.length || args[index] == null) {
            return null;
        }
        String option = args[index].trim();
        if (option.isEmpty() || "default".equalsIgnoreCase(option)) {
            return null;
        }
        return option;
    }

    private static String parseOptionalRelaxationRule(String[] args, int index) {
        if (args == null || index >= args.length || args[index] == null) {
            return null;
        }
        String option = args[index].trim();
        if (option.isEmpty() || "default".equalsIgnoreCase(option)) {
            return null;
        }
        return option;
    }

    private static String normalizeOption(String option) {
        if (option == null || option.trim().isEmpty()) {
            return "none";
        }
        return option.trim();
    }

    private static void printStageRows(List<InstanceTimingSummary> summaries) {
        System.out.println("instance,relaxation,stageIndex,currentTime,nextDecisionTime,remainingCustomers,externalDepots,selectedRoutes,objectiveValue,actualDistanceToNextStage,integerSolution,solveStatus,cgClosed,cgStatus,bpcElapsedMillis,bpcColumnGenerationRuns,bpcColumnGenerationIterations,bpcMasterSolves,bpcExactPricingCalls,bpcHeuristicPricingCalls,bpcPrimalHeuristicCalls,bpcColumnsAdded,bpcMasterSolveElapsedMillis,bpcExactPricingElapsedMillis,bpcHeuristicPricingElapsedMillis,bpcPrimalHeuristicElapsedMillis,resultCutCount,branchTerminationReason,branchElapsedMillis,branchNodesExplored,branchChildrenSolved,branchInfeasibleChildren,branchPrunedByIncumbent,branchIncumbentUpdates,branchingFailures,stageWallElapsedMillis");
        for (InstanceTimingSummary summary : summaries) {
            for (TimingRow row : summary.getStageRows()) {
                System.out.println(row.toCsvLine());
            }
        }
    }

    private static void printSummaries(List<InstanceTimingSummary> summaries) {
        System.out.println("instance,relaxation,stageCount,wallElapsedMillis,totalBpcElapsedMillis,maxStageBpcElapsedMillis,slowestStageTime,totalMasterSolves,totalExactPricingCalls,totalHeuristicPricingCalls,totalPrimalHeuristicCalls,totalColumnsAdded,totalMasterSolveElapsedMillis,totalExactPricingElapsedMillis,totalHeuristicPricingElapsedMillis,totalPrimalHeuristicElapsedMillis,maxBranchElapsedMillis,maxResultCutCount");
        for (InstanceTimingSummary summary : summaries) {
            System.out.println(summary.toCsvLine());
        }
    }

    public static final class TimingRow {
        private final String instanceName;
        private final String relaxationRule;
        private final DynamicInstance stageInstance;
        private final int stageIndex;
        private final double currentTime;
        private final double nextDecisionTime;
        private final StageSolution stageSolution;
        private final double actualDistanceToNextStage;
        private final BPCSolver.SolveStatus solveStatus;
        private final ColumnGenerationStatus columnGenerationStatus;
        private final BPCSolverTemplate.BranchSearchStatistics branchSearchStatistics;
        private final BPCSolverTemplate.BPCRunStatistics runStatistics;
        private final long stageWallElapsedMillis;

        private TimingRow(String instanceName,
                          DynamicInstance stageInstance,
                          int stageIndex,
                          double currentTime,
                          double nextDecisionTime,
                          StageSolution stageSolution,
                          double actualDistanceToNextStage,
                          BPCSolver.SolveStatus solveStatus,
                          ColumnGenerationStatus columnGenerationStatus,
                          BPCSolverTemplate.BranchSearchStatistics branchSearchStatistics,
                          BPCSolverTemplate.BPCRunStatistics runStatistics) {
            this(instanceName, "none", stageInstance, stageIndex, currentTime, nextDecisionTime, stageSolution, actualDistanceToNextStage,
                    solveStatus, columnGenerationStatus, branchSearchStatistics, runStatistics, 0L);
        }

        private TimingRow(String instanceName,
                          String relaxationRule,
                          DynamicInstance stageInstance,
                          int stageIndex,
                          double currentTime,
                          double nextDecisionTime,
                          StageSolution stageSolution,
                          double actualDistanceToNextStage,
                          BPCSolver.SolveStatus solveStatus,
                          ColumnGenerationStatus columnGenerationStatus,
                          BPCSolverTemplate.BranchSearchStatistics branchSearchStatistics,
                          BPCSolverTemplate.BPCRunStatistics runStatistics,
                          long stageWallElapsedMillis) {
            this.instanceName = instanceName == null ? "" : instanceName;
            this.relaxationRule = normalizeOption(relaxationRule);
            this.stageInstance = stageInstance.copy();
            this.stageIndex = stageIndex;
            this.currentTime = currentTime;
            this.nextDecisionTime = nextDecisionTime;
            this.stageSolution = stageSolution.copy();
            this.actualDistanceToNextStage = actualDistanceToNextStage;
            this.solveStatus = solveStatus;
            this.columnGenerationStatus = columnGenerationStatus == null
                    ? ColumnGenerationStatus.NOT_STARTED
                    : columnGenerationStatus;
            this.branchSearchStatistics = branchSearchStatistics == null
                    ? BPCSolverTemplate.BranchSearchStatistics.notStarted()
                    : branchSearchStatistics.copy();
            this.runStatistics = runStatistics == null
                    ? BPCSolverTemplate.BPCRunStatistics.empty()
                    : runStatistics.copy();
            this.stageWallElapsedMillis = stageWallElapsedMillis;
        }

        public DynamicInstance getStageInstance() {
            return stageInstance.copy();
        }

        public String getInstanceName() {
            return instanceName;
        }

        public String getRelaxationRule() {
            return relaxationRule;
        }

        public int getStageIndex() {
            return stageIndex;
        }

        public double getCurrentTime() {
            return currentTime;
        }

        public double getNextDecisionTime() {
            return nextDecisionTime;
        }

        public StageSolution getStageSolution() {
            return stageSolution.copy();
        }

        public double getActualDistanceToNextStage() {
            return actualDistanceToNextStage;
        }

        public BPCSolver.SolveStatus getSolveStatus() {
            return solveStatus;
        }

        public ColumnGenerationStatus getColumnGenerationStatus() {
            return columnGenerationStatus;
        }

        public BPCSolverTemplate.BranchSearchStatistics getBranchSearchStatistics() {
            return branchSearchStatistics.copy();
        }

        public BPCSolverTemplate.BPCRunStatistics getRunStatistics() {
            return runStatistics.copy();
        }

        public long getStageWallElapsedMillis() {
            return stageWallElapsedMillis;
        }

        private String toCsvLine() {
            return String.format(
                    Locale.ROOT,
                    "%s,%s,%d,%.4f,%.4f,%d,%d,%d,%.6f,%.4f,%s,%s,%s,%s,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%s,%d,%d,%d,%d,%d,%d,%d,%d",
                    instanceName,
                    relaxationRule,
                    stageIndex,
                    currentTime,
                    nextDecisionTime,
                    stageInstance.getRemainingCustomerIds().size(),
                    stageInstance.getExternalDepots().size(),
                    stageSolution.getSelectedRoutes().size(),
                    stageSolution.getObjectiveValue(),
                    actualDistanceToNextStage,
                    Boolean.toString(stageSolution.isIntegerSolution()),
                    solveStatus,
                    Boolean.toString(columnGenerationStatus.isClosed()),
                    columnGenerationStatus,
                    runStatistics.getElapsedMillis(),
                    runStatistics.getColumnGenerationRuns(),
                    runStatistics.getColumnGenerationIterations(),
                    runStatistics.getMasterSolves(),
                    runStatistics.getExactPricingCalls(),
                    runStatistics.getHeuristicPricingCalls(),
                    runStatistics.getPrimalHeuristicCalls(),
                    runStatistics.getColumnsAdded(),
                    runStatistics.getMasterSolveElapsedMillis(),
                    runStatistics.getExactPricingElapsedMillis(),
                    runStatistics.getHeuristicPricingElapsedMillis(),
                    runStatistics.getPrimalHeuristicElapsedMillis(),
                    branchSearchStatistics.getResultCutCount(),
                    branchSearchStatistics.getTerminationReason(),
                    branchSearchStatistics.getElapsedMillis(),
                    branchSearchStatistics.getNodesExplored(),
                    branchSearchStatistics.getChildrenSolved(),
                    branchSearchStatistics.getInfeasibleChildren(),
                    branchSearchStatistics.getNodesPrunedByIncumbent(),
                    branchSearchStatistics.getIncumbentUpdates(),
                    branchSearchStatistics.getBranchingFailures(),
                    stageWallElapsedMillis
            );
        }
    }

    public static final class InstanceTimingSummary {
        private final String instanceName;
        private final String relaxationRule;
        private final int stageCount;
        private final long wallElapsedMillis;
        private final long totalBpcElapsedMillis;
        private final long maxStageBpcElapsedMillis;
        private final double slowestStageTime;
        private final int totalMasterSolves;
        private final int totalExactPricingCalls;
        private final int totalHeuristicPricingCalls;
        private final int totalPrimalHeuristicCalls;
        private final int totalColumnsAdded;
        private final long totalMasterSolveElapsedMillis;
        private final long totalExactPricingElapsedMillis;
        private final long totalHeuristicPricingElapsedMillis;
        private final long totalPrimalHeuristicElapsedMillis;
        private final long maxBranchElapsedMillis;
        private final int maxResultCutCount;
        private final List<TimingRow> stageRows;

        private InstanceTimingSummary(String instanceName,
                                      String relaxationRule,
                                      int stageCount,
                                      long wallElapsedMillis,
                                      long totalBpcElapsedMillis,
                                      long maxStageBpcElapsedMillis,
                                      double slowestStageTime,
                                      int totalMasterSolves,
                                      int totalExactPricingCalls,
                                      int totalHeuristicPricingCalls,
                                      int totalPrimalHeuristicCalls,
                                      int totalColumnsAdded,
                                      long totalMasterSolveElapsedMillis,
                                      long totalExactPricingElapsedMillis,
                                      long totalHeuristicPricingElapsedMillis,
                                      long totalPrimalHeuristicElapsedMillis,
                                      long maxBranchElapsedMillis,
                                      int maxResultCutCount,
                                      List<TimingRow> stageRows) {
            this.instanceName = instanceName;
            this.relaxationRule = normalizeOption(relaxationRule);
            this.stageCount = stageCount;
            this.wallElapsedMillis = wallElapsedMillis;
            this.totalBpcElapsedMillis = totalBpcElapsedMillis;
            this.maxStageBpcElapsedMillis = maxStageBpcElapsedMillis;
            this.slowestStageTime = slowestStageTime;
            this.totalMasterSolves = totalMasterSolves;
            this.totalExactPricingCalls = totalExactPricingCalls;
            this.totalHeuristicPricingCalls = totalHeuristicPricingCalls;
            this.totalPrimalHeuristicCalls = totalPrimalHeuristicCalls;
            this.totalColumnsAdded = totalColumnsAdded;
            this.totalMasterSolveElapsedMillis = totalMasterSolveElapsedMillis;
            this.totalExactPricingElapsedMillis = totalExactPricingElapsedMillis;
            this.totalHeuristicPricingElapsedMillis = totalHeuristicPricingElapsedMillis;
            this.totalPrimalHeuristicElapsedMillis = totalPrimalHeuristicElapsedMillis;
            this.maxBranchElapsedMillis = maxBranchElapsedMillis;
            this.maxResultCutCount = maxResultCutCount;
            this.stageRows = new ArrayList<>(stageRows);
        }

        public static InstanceTimingSummary from(String instanceName, List<TimingRow> rows) {
            return from(instanceName, rows, 0L);
        }

        public static InstanceTimingSummary from(String instanceName, List<TimingRow> rows, long wallElapsedMillisOverride) {
            if (rows == null) {
                throw new IllegalArgumentException("rows must not be null.");
            }
            long wallElapsedMillis = Math.max(0L, wallElapsedMillisOverride);
            long totalBpcElapsedMillis = 0L;
            long maxStageBpcElapsedMillis = 0L;
            double slowestStageTime = -1.0;
            int totalMasterSolves = 0;
            int totalExactPricingCalls = 0;
            int totalHeuristicPricingCalls = 0;
            int totalPrimalHeuristicCalls = 0;
            int totalColumnsAdded = 0;
            long totalMasterSolveElapsedMillis = 0L;
            long totalExactPricingElapsedMillis = 0L;
            long totalHeuristicPricingElapsedMillis = 0L;
            long totalPrimalHeuristicElapsedMillis = 0L;
            long maxBranchElapsedMillis = 0L;
            int maxResultCutCount = 0;
            String relaxationRule = rows.isEmpty() ? "none" : rows.get(0).getRelaxationRule();
            for (TimingRow row : rows) {
                BPCSolverTemplate.BPCRunStatistics runStatistics = row.getRunStatistics();
                BPCSolverTemplate.BranchSearchStatistics branchStatistics = row.getBranchSearchStatistics();
                totalBpcElapsedMillis += runStatistics.getElapsedMillis();
                maxStageBpcElapsedMillis = Math.max(maxStageBpcElapsedMillis, runStatistics.getElapsedMillis());
                if (runStatistics.getElapsedMillis() >= maxStageBpcElapsedMillis) {
                    slowestStageTime = row.getCurrentTime();
                }
                totalMasterSolves += runStatistics.getMasterSolves();
                totalExactPricingCalls += runStatistics.getExactPricingCalls();
                totalHeuristicPricingCalls += runStatistics.getHeuristicPricingCalls();
                totalPrimalHeuristicCalls += runStatistics.getPrimalHeuristicCalls();
                totalColumnsAdded += runStatistics.getColumnsAdded();
                totalMasterSolveElapsedMillis += runStatistics.getMasterSolveElapsedMillis();
                totalExactPricingElapsedMillis += runStatistics.getExactPricingElapsedMillis();
                totalHeuristicPricingElapsedMillis += runStatistics.getHeuristicPricingElapsedMillis();
                totalPrimalHeuristicElapsedMillis += runStatistics.getPrimalHeuristicElapsedMillis();
                maxBranchElapsedMillis = Math.max(maxBranchElapsedMillis, branchStatistics.getElapsedMillis());
                maxResultCutCount = Math.max(maxResultCutCount, branchStatistics.getResultCutCount());
            }
            return new InstanceTimingSummary(
                    instanceName,
                    relaxationRule,
                    rows.size(),
                    wallElapsedMillis,
                    totalBpcElapsedMillis,
                    maxStageBpcElapsedMillis,
                    slowestStageTime,
                    totalMasterSolves,
                    totalExactPricingCalls,
                    totalHeuristicPricingCalls,
                    totalPrimalHeuristicCalls,
                    totalColumnsAdded,
                    totalMasterSolveElapsedMillis,
                    totalExactPricingElapsedMillis,
                    totalHeuristicPricingElapsedMillis,
                    totalPrimalHeuristicElapsedMillis,
                    maxBranchElapsedMillis,
                    maxResultCutCount,
                    rows
            );
        }

        public String getInstanceName() {
            return instanceName;
        }

        public String getRelaxationRule() {
            return relaxationRule;
        }

        public int getStageCount() {
            return stageCount;
        }

        public long getWallElapsedMillis() {
            return wallElapsedMillis;
        }

        public long getTotalBpcElapsedMillis() {
            return totalBpcElapsedMillis;
        }

        public long getMaxStageBpcElapsedMillis() {
            return maxStageBpcElapsedMillis;
        }

        public double getSlowestStageTime() {
            return slowestStageTime;
        }

        public int getTotalMasterSolves() {
            return totalMasterSolves;
        }

        public int getTotalExactPricingCalls() {
            return totalExactPricingCalls;
        }

        public int getTotalHeuristicPricingCalls() {
            return totalHeuristicPricingCalls;
        }

        public int getTotalPrimalHeuristicCalls() {
            return totalPrimalHeuristicCalls;
        }

        public int getTotalColumnsAdded() {
            return totalColumnsAdded;
        }

        public long getTotalMasterSolveElapsedMillis() {
            return totalMasterSolveElapsedMillis;
        }

        public long getTotalExactPricingElapsedMillis() {
            return totalExactPricingElapsedMillis;
        }

        public long getTotalHeuristicPricingElapsedMillis() {
            return totalHeuristicPricingElapsedMillis;
        }

        public long getTotalPrimalHeuristicElapsedMillis() {
            return totalPrimalHeuristicElapsedMillis;
        }

        public long getMaxBranchElapsedMillis() {
            return maxBranchElapsedMillis;
        }

        public int getMaxResultCutCount() {
            return maxResultCutCount;
        }

        public List<TimingRow> getStageRows() {
            return new ArrayList<>(stageRows);
        }

        public String toCsvLine() {
            return String.format(
                    Locale.ROOT,
                    "%s,%s,%d,%d,%d,%d,%.4f,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d",
                    instanceName,
                    relaxationRule,
                    stageCount,
                    wallElapsedMillis,
                    totalBpcElapsedMillis,
                    maxStageBpcElapsedMillis,
                    slowestStageTime,
                    totalMasterSolves,
                    totalExactPricingCalls,
                    totalHeuristicPricingCalls,
                    totalPrimalHeuristicCalls,
                    totalColumnsAdded,
                    totalMasterSolveElapsedMillis,
                    totalExactPricingElapsedMillis,
                    totalHeuristicPricingElapsedMillis,
                    totalPrimalHeuristicElapsedMillis,
                    maxBranchElapsedMillis,
                    maxResultCutCount
            );
        }
    }
}

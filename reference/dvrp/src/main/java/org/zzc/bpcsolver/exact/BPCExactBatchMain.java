package org.zzc.bpcsolver.exact;

import org.zzc.bpcsolver.algorithm.BPCSolver;
import org.zzc.bpcsolver.algorithm.BPCSolverTemplate;
import org.zzc.bpcsolver.problem.config.ProblemConfig;
import org.zzc.bpcsolver.problem.config.ProblemConfigReader;
import org.zzc.bpcsolver.problem.entity.ProblemData;
import org.zzc.bpcsolver.problem.loader.ProblemDataLoader;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class BPCExactBatchMain {

    private static final double DEFAULT_TIME_LIMIT_SECONDS = 20.0;
    private static final double DEFAULT_OBJECTIVE_GAP_TOLERANCE = 1e-4;
    private static final double DEFAULT_BOUND_GAP_TOLERANCE = 1e-4;
    private static final List<String> REPRESENTATIVE_INSTANCE_NAMES = Arrays.asList(
            "r101_1",
            "r205_D_25_E_25",
            "r202_D_25_E_25",
            "r101_D_75_E_75",
            "c201_D_25_E_25"
    );
    private static final List<String> BROAD_COVERAGE_INSTANCE_NAMES = Arrays.asList(
            "r101_1",
            "r101_D_25_E_25",
            "r101_D_50_E_50",
            "r101_D_75_E_75",
            "r202_D_25_E_25",
            "r205_D_25_E_25",
            "r208_D_75_E_75",
            "c101_D_25_E_25",
            "c104_D_75_E_25",
            "c201_D_25_E_25",
            "c205_D_25_E_25",
            "c208_D_50_E_75",
            "rc101_D_25_E_25",
            "rc104_D_50_E_50",
            "rc108_D_75_E_75",
            "rc201_D_25_E_25",
            "rc205_D_75_E_75",
            "rc208_D_25_E_75"
    );

    public static void main(String[] args) {
        List<String> instanceNames = parseOptionalInstanceNamesArg(args, 0);
        double timeLimitSeconds = parseOptionalDoubleArg(args, 1, DEFAULT_TIME_LIMIT_SECONDS);
        int maxAddedColumnsPerIteration = parseOptionalIntArg(
                args,
                2,
                BPCSolver.DEFAULT_MAX_ADDED_COLUMNS_PER_ITERATION
        );
        int maxIterations = parseOptionalIntArg(args, 3, BPCSolver.DEFAULT_MAX_ITERATIONS);
        OriginalMipDVRPSolver.StartTimeMode startTimeMode = parseOptionalStartTimeMode(args, 4);
        String heuristicPricingRule = parseOptionalHeuristicPricingRule(args, 5);
        String cuttingRule = parseOptionalCuttingRule(args, 6);
        Integer numCustomers = parseOptionalIntegerArg(args, 7);
        String relaxationRule = parseOptionalRelaxationRule(args, 8);

        OriginalMipDVRPSolver.Options exactOptions = OriginalMipDVRPSolver.Options.defaults()
                .setTimeLimitSeconds(timeLimitSeconds)
                .setStartTimeMode(startTimeMode);

        List<InstanceValidationSummary> summaries = validateInstances(
                instanceNames,
                maxAddedColumnsPerIteration,
                maxIterations,
                exactOptions,
                heuristicPricingRule,
                cuttingRule,
                numCustomers,
                relaxationRule
        );
        printSummaries(summaries);
    }

    public static List<InstanceValidationSummary> validateInstances(List<String> instanceNames,
                                                                    int maxAddedColumnsPerIteration,
                                                                    int maxIterations,
                                                                    OriginalMipDVRPSolver.Options exactOptions) {
        return validateInstances(
                instanceNames,
                maxAddedColumnsPerIteration,
                maxIterations,
                exactOptions,
                null,
                null,
                null
        );
    }

    public static List<InstanceValidationSummary> validateInstances(List<String> instanceNames,
                                                                    int maxAddedColumnsPerIteration,
                                                                    int maxIterations,
                                                                    OriginalMipDVRPSolver.Options exactOptions,
                                                                    String heuristicPricingRule) {
        return validateInstances(
                instanceNames,
                maxAddedColumnsPerIteration,
                maxIterations,
                exactOptions,
                heuristicPricingRule,
                null,
                null
        );
    }

    public static List<InstanceValidationSummary> validateInstances(List<String> instanceNames,
                                                                    int maxAddedColumnsPerIteration,
                                                                    int maxIterations,
                                                                    OriginalMipDVRPSolver.Options exactOptions,
                                                                    String heuristicPricingRule,
                                                                    String cuttingRule) {
        return validateInstances(
                instanceNames,
                maxAddedColumnsPerIteration,
                maxIterations,
                exactOptions,
                heuristicPricingRule,
                cuttingRule,
                null
        );
    }

    public static List<InstanceValidationSummary> validateInstances(List<String> instanceNames,
                                                                    int maxAddedColumnsPerIteration,
                                                                    int maxIterations,
                                                                    OriginalMipDVRPSolver.Options exactOptions,
                                                                   String heuristicPricingRule,
                                                                   String cuttingRule,
                                                                   Integer numCustomers) {
        return validateInstances(
                instanceNames,
                maxAddedColumnsPerIteration,
                maxIterations,
                exactOptions,
                heuristicPricingRule,
                cuttingRule,
                numCustomers,
                null
        );
    }

    public static List<InstanceValidationSummary> validateInstances(List<String> instanceNames,
                                                                    int maxAddedColumnsPerIteration,
                                                                    int maxIterations,
                                                                    OriginalMipDVRPSolver.Options exactOptions,
                                                                    String heuristicPricingRule,
                                                                    String cuttingRule,
                                                                    Integer numCustomers,
                                                                    String relaxationRule) {
        if (instanceNames == null) {
            throw new IllegalArgumentException("instanceNames must not be null.");
        }
        if (exactOptions == null) {
            throw new IllegalArgumentException("exactOptions must not be null.");
        }

        List<InstanceValidationSummary> summaries = new ArrayList<>();
        for (String instanceName : instanceNames) {
            if (instanceName == null || instanceName.trim().isEmpty()) {
                continue;
            }
            ProblemData problemData = loadProblemData(instanceName.trim(), numCustomers);
            List<BPCExactComparisonMain.ComparisonRow> rows = BPCExactComparisonMain.compare(
                    problemData,
                    maxAddedColumnsPerIteration,
                    maxIterations,
                    exactOptions,
                    heuristicPricingRule,
                    cuttingRule,
                    relaxationRule
            );
            summaries.add(InstanceValidationSummary.from(instanceName.trim(), rows, normalizeOption(relaxationRule)));
        }
        return summaries;
    }

    public static List<String> defaultInstanceNames() {
        return representativeInstanceNames();
    }

    public static List<String> representativeInstanceNames() {
        return new ArrayList<>(REPRESENTATIVE_INSTANCE_NAMES);
    }

    public static List<String> broadCoverageInstanceNames() {
        return new ArrayList<>(BROAD_COVERAGE_INSTANCE_NAMES);
    }

    public static List<String> instanceNamesForSuite(String suiteName) {
        if (suiteName == null || suiteName.trim().isEmpty()) {
            return defaultInstanceNames();
        }
        String normalizedSuiteName = suiteName.trim().toLowerCase(Locale.ROOT);
        switch (normalizedSuiteName) {
            case "default":
            case "representative":
            case "quick":
                return representativeInstanceNames();
            case "broad":
            case "broad-coverage":
            case "expanded":
                return broadCoverageInstanceNames();
            default:
                throw new IllegalArgumentException("Unknown exact batch suite: " + suiteName);
        }
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

    private static void printSummaries(List<InstanceValidationSummary> summaries) {
        System.out.println("instance,relaxation,stages,maxObjectiveGap,maxBoundGap,totalBpcActualDistance,totalExactActualDistance,maxRouteCountDelta,validationElapsedMillis,bpcElapsedMillis,bpcColumnGenerationRuns,bpcColumnGenerationIterations,bpcMasterSolves,bpcExactPricingCalls,bpcHeuristicPricingCalls,bpcColumnsAdded,objectiveClosed");
        for (InstanceValidationSummary summary : summaries) {
            System.out.println(summary.toCsvLine());
        }
    }

    private static String normalizeOption(String option) {
        if (option == null || option.trim().isEmpty()) {
            return "none";
        }
        return option.trim();
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

    private static double parseOptionalDoubleArg(String[] args, int index, double defaultValue) {
        if (args == null || index >= args.length || args[index] == null || args[index].trim().isEmpty()) {
            return defaultValue;
        }
        return Double.parseDouble(args[index].trim());
    }

    private static int parseOptionalIntArg(String[] args, int index, int defaultValue) {
        if (args == null || index >= args.length || args[index] == null || args[index].trim().isEmpty()) {
            return defaultValue;
        }
        return Integer.parseInt(args[index].trim());
    }

    private static OriginalMipDVRPSolver.StartTimeMode parseOptionalStartTimeMode(String[] args, int index) {
        if (args == null || index >= args.length || args[index] == null || args[index].trim().isEmpty()) {
            return OriginalMipDVRPSolver.StartTimeMode.ROUTE_CLOCK;
        }
        String mode = args[index].trim();
        if ("depot-earliest".equalsIgnoreCase(mode)
                || "start-depot-earliest".equalsIgnoreCase(mode)
                || "mathematical".equalsIgnoreCase(mode)) {
            return OriginalMipDVRPSolver.StartTimeMode.START_DEPOT_EARLIEST;
        }
        if ("route-clock".equalsIgnoreCase(mode)
                || "default".equalsIgnoreCase(mode)) {
            return OriginalMipDVRPSolver.StartTimeMode.ROUTE_CLOCK;
        }
        throw new IllegalArgumentException("Unknown exact MIP start-time mode: " + mode);
    }

    private static String parseOptionalHeuristicPricingRule(String[] args, int index) {
        if (args == null || index >= args.length || args[index] == null) {
            return null;
        }
        String option = args[index].trim();
        if (option.isEmpty()
                || "none".equalsIgnoreCase(option)
                || "false".equalsIgnoreCase(option)
                || "exact".equalsIgnoreCase(option)
                || "exact pricing".equalsIgnoreCase(option)) {
            return null;
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

    private static Integer parseOptionalIntegerArg(String[] args, int index) {
        if (args == null || index >= args.length || args[index] == null || args[index].trim().isEmpty()) {
            return null;
        }
        String option = args[index].trim();
        if ("default".equalsIgnoreCase(option)) {
            return null;
        }
        return Integer.parseInt(option);
    }

    public static final class InstanceValidationSummary {

        private final String instanceName;
        private final String relaxationRule;
        private final int stageCount;
        private final double maxObjectiveGap;
        private final double maxBoundGap;
        private final double totalBpcActualDistance;
        private final double totalExactActualDistance;
        private final int maxRouteCountDelta;
        private final long elapsedMillis;
        private final long bpcElapsedMillis;
        private final int bpcColumnGenerationRuns;
        private final int bpcColumnGenerationIterations;
        private final int bpcMasterSolves;
        private final int bpcExactPricingCalls;
        private final int bpcHeuristicPricingCalls;
        private final int bpcColumnsAdded;

        private InstanceValidationSummary(String instanceName,
                                          String relaxationRule,
                                          int stageCount,
                                          double maxObjectiveGap,
                                          double maxBoundGap,
                                          double totalBpcActualDistance,
                                          double totalExactActualDistance,
                                          int maxRouteCountDelta,
                                          long elapsedMillis,
                                          long bpcElapsedMillis,
                                          int bpcColumnGenerationRuns,
                                          int bpcColumnGenerationIterations,
                                          int bpcMasterSolves,
                                          int bpcExactPricingCalls,
                                          int bpcHeuristicPricingCalls,
                                          int bpcColumnsAdded) {
            this.instanceName = instanceName;
            this.relaxationRule = normalizeOption(relaxationRule);
            this.stageCount = stageCount;
            this.maxObjectiveGap = maxObjectiveGap;
            this.maxBoundGap = maxBoundGap;
            this.totalBpcActualDistance = totalBpcActualDistance;
            this.totalExactActualDistance = totalExactActualDistance;
            this.maxRouteCountDelta = maxRouteCountDelta;
            this.elapsedMillis = elapsedMillis;
            this.bpcElapsedMillis = bpcElapsedMillis;
            this.bpcColumnGenerationRuns = bpcColumnGenerationRuns;
            this.bpcColumnGenerationIterations = bpcColumnGenerationIterations;
            this.bpcMasterSolves = bpcMasterSolves;
            this.bpcExactPricingCalls = bpcExactPricingCalls;
            this.bpcHeuristicPricingCalls = bpcHeuristicPricingCalls;
            this.bpcColumnsAdded = bpcColumnsAdded;
        }

        public static InstanceValidationSummary from(String instanceName,
                                                     List<BPCExactComparisonMain.ComparisonRow> rows) {
            return from(instanceName, rows, "none");
        }

        public static InstanceValidationSummary from(String instanceName,
                                                     List<BPCExactComparisonMain.ComparisonRow> rows,
                                                     String relaxationRule) {
            if (rows == null) {
                throw new IllegalArgumentException("rows must not be null.");
            }

            double maxObjectiveGap = 0.0;
            double maxBoundGap = 0.0;
            double totalBpcActualDistance = 0.0;
            double totalExactActualDistance = 0.0;
            int maxRouteCountDelta = 0;
            long firstStageStartNanos = Long.MAX_VALUE;
            long lastStageEndNanos = Long.MIN_VALUE;
            long bpcElapsedMillis = 0L;
            int bpcColumnGenerationRuns = 0;
            int bpcColumnGenerationIterations = 0;
            int bpcMasterSolves = 0;
            int bpcExactPricingCalls = 0;
            int bpcHeuristicPricingCalls = 0;
            int bpcColumnsAdded = 0;
            for (BPCExactComparisonMain.ComparisonRow row : rows) {
                double objectiveGap = Math.abs(
                        row.getBpcSolution().getObjectiveValue() - row.getExactSolution().getObjectiveValue()
                );
                double boundGap = Math.abs(
                        row.getBpcSolution().getObjectiveValue() - row.getExactResult().getBestBound()
                );
                maxObjectiveGap = Math.max(maxObjectiveGap, objectiveGap);
                maxBoundGap = Math.max(maxBoundGap, boundGap);
                totalBpcActualDistance += row.getBpcActualDistanceToNextStage();
                totalExactActualDistance += row.getExactActualDistanceToNextStage();
                maxRouteCountDelta = Math.max(maxRouteCountDelta, Math.abs(
                        row.getBpcSolution().getSelectedRoutes().size()
                                - row.getExactSolution().getSelectedRoutes().size()
                ));
                firstStageStartNanos = Math.min(firstStageStartNanos, row.getElapsedStartNanos());
                lastStageEndNanos = Math.max(lastStageEndNanos, row.getElapsedEndNanos());
                BPCSolverTemplate.BPCRunStatistics runStatistics = row.getBpcRunStatistics();
                bpcElapsedMillis += runStatistics.getElapsedMillis();
                bpcColumnGenerationRuns += runStatistics.getColumnGenerationRuns();
                bpcColumnGenerationIterations += runStatistics.getColumnGenerationIterations();
                bpcMasterSolves += runStatistics.getMasterSolves();
                bpcExactPricingCalls += runStatistics.getExactPricingCalls();
                bpcHeuristicPricingCalls += runStatistics.getHeuristicPricingCalls();
                bpcColumnsAdded += runStatistics.getColumnsAdded();
            }
            long elapsedMillis = rows.isEmpty()
                    ? 0L
                    : Math.max(0L, Math.round((lastStageEndNanos - firstStageStartNanos) / 1_000_000.0));

            return new InstanceValidationSummary(
                    instanceName,
                    relaxationRule,
                    rows.size(),
                    maxObjectiveGap,
                    maxBoundGap,
                    totalBpcActualDistance,
                    totalExactActualDistance,
                    maxRouteCountDelta,
                    elapsedMillis,
                    bpcElapsedMillis,
                    bpcColumnGenerationRuns,
                    bpcColumnGenerationIterations,
                    bpcMasterSolves,
                    bpcExactPricingCalls,
                    bpcHeuristicPricingCalls,
                    bpcColumnsAdded
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

        public double getMaxObjectiveGap() {
            return maxObjectiveGap;
        }

        public double getMaxBoundGap() {
            return maxBoundGap;
        }

        public double getTotalBpcActualDistance() {
            return totalBpcActualDistance;
        }

        public double getTotalExactActualDistance() {
            return totalExactActualDistance;
        }

        public int getMaxRouteCountDelta() {
            return maxRouteCountDelta;
        }

        public long getElapsedMillis() {
            return elapsedMillis;
        }

        public long getBpcElapsedMillis() {
            return bpcElapsedMillis;
        }

        public int getBpcColumnGenerationRuns() {
            return bpcColumnGenerationRuns;
        }

        public int getBpcColumnGenerationIterations() {
            return bpcColumnGenerationIterations;
        }

        public int getBpcMasterSolves() {
            return bpcMasterSolves;
        }

        public int getBpcExactPricingCalls() {
            return bpcExactPricingCalls;
        }

        public int getBpcHeuristicPricingCalls() {
            return bpcHeuristicPricingCalls;
        }

        public int getBpcColumnsAdded() {
            return bpcColumnsAdded;
        }

        public boolean isObjectiveClosed() {
            return maxObjectiveGap <= DEFAULT_OBJECTIVE_GAP_TOLERANCE
                    && maxBoundGap <= DEFAULT_BOUND_GAP_TOLERANCE;
        }

        public String toCsvLine() {
            return String.format(
                    Locale.ROOT,
                    "%s,%s,%d,%.6f,%.6f,%.4f,%.4f,%d,%d,%d,%d,%d,%d,%d,%d,%d,%s",
                    this.instanceName,
                    this.relaxationRule,
                    this.stageCount,
                    this.maxObjectiveGap,
                    this.maxBoundGap,
                    this.totalBpcActualDistance,
                    this.totalExactActualDistance,
                    this.maxRouteCountDelta,
                    this.elapsedMillis,
                    this.bpcElapsedMillis,
                    this.bpcColumnGenerationRuns,
                    this.bpcColumnGenerationIterations,
                    this.bpcMasterSolves,
                    this.bpcExactPricingCalls,
                    this.bpcHeuristicPricingCalls,
                    this.bpcColumnsAdded,
                    Boolean.toString(isObjectiveClosed())
            );
        }
    }
}

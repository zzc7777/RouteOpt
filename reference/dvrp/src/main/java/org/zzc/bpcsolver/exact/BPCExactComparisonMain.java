package org.zzc.bpcsolver.exact;

import org.zzc.bpcsolver.algorithm.BPCSolver;
import org.zzc.bpcsolver.algorithm.BPCSolverTemplate;
import org.zzc.bpcsolver.algorithm.DVRPDynamicSolver;
import org.zzc.bpcsolver.core.branch.StageSolution;
import org.zzc.bpcsolver.core.graph.Route;
import org.zzc.bpcsolver.core.graph.RouteFeasibilityValidator;
import org.zzc.bpcsolver.problem.config.ProblemConfig;
import org.zzc.bpcsolver.problem.config.ProblemConfigReader;
import org.zzc.bpcsolver.problem.entity.DynamicInstance;
import org.zzc.bpcsolver.problem.entity.ProblemData;
import org.zzc.bpcsolver.problem.loader.ProblemDataLoader;
import org.zzc.bpcsolver.problem.update.DynamicStateUpdater;
import org.zzc.bpcsolver.results.DVRPResultRecorder;

import java.util.ArrayList;
import java.util.List;

public class BPCExactComparisonMain {

    private static final double EPSILON = 1e-9;

    public static void main(String[] args) {
        ProblemConfig config = new ProblemConfigReader().read("dvrp_td.yaml");
        applyOptionalInstanceOverride(config, args);
        applyOptionalNumCustomersOverride(config, args);
        ProblemData problemData = new ProblemDataLoader().load(config);

        double timeLimitSeconds = parseOptionalDoubleArg(args, 1, 60.0);
        int maxAddedColumnsPerIteration = parseOptionalIntArg(
                args,
                2,
                BPCSolver.DEFAULT_MAX_ADDED_COLUMNS_PER_ITERATION
        );
        int maxIterations = parseOptionalIntArg(args, 3, BPCSolver.DEFAULT_MAX_ITERATIONS);
        OriginalMipDVRPSolver.StartTimeMode startTimeMode = parseOptionalStartTimeMode(args, 4);
        String heuristicPricingRule = parseOptionalHeuristicPricingRule(args, 5);
        String cuttingRule = parseOptionalCuttingRule(args, 6);
        String relaxationRule = parseOptionalRelaxationRule(args, 8);

        OriginalMipDVRPSolver.Options exactOptions = OriginalMipDVRPSolver.Options.defaults()
                .setTimeLimitSeconds(timeLimitSeconds)
                .setStartTimeMode(startTimeMode);

        List<ComparisonRow> rows = compare(
                problemData,
                maxAddedColumnsPerIteration,
                maxIterations,
                exactOptions,
                heuristicPricingRule,
                cuttingRule,
                relaxationRule
        );
        printRows(rows);
        DVRPResultRecorder.fromDefaultSolverConfig().recordBpcExactComparison(
                config.getInstanceBaseName(),
                rows,
                "same-stage exact comparison, timeLimitSeconds=" + timeLimitSeconds
                        + ", maxAddedColumns=" + maxAddedColumnsPerIteration
                        + ", maxIterations=" + maxIterations
                        + ", startTimeMode=" + startTimeMode
                        + ", heuristicPricing=" + (heuristicPricingRule == null ? "none" : heuristicPricingRule)
                        + ", cutting=" + (cuttingRule == null ? "none" : cuttingRule)
                        + ", relaxation=" + (relaxationRule == null ? "none" : relaxationRule)
        );
    }

    public static List<ComparisonRow> compare(ProblemData problemData,
                                              int maxAddedColumnsPerIteration,
                                              int maxIterations,
                                              OriginalMipDVRPSolver.Options exactOptions) {
        return compare(
                problemData,
                maxAddedColumnsPerIteration,
                maxIterations,
                exactOptions,
                null,
                null
        );
    }

    public static List<ComparisonRow> compare(ProblemData problemData,
                                              int maxAddedColumnsPerIteration,
                                              int maxIterations,
                                              OriginalMipDVRPSolver.Options exactOptions,
                                              String heuristicPricingRule) {
        return compare(
                problemData,
                maxAddedColumnsPerIteration,
                maxIterations,
                exactOptions,
                heuristicPricingRule,
                null
        );
    }

    public static List<ComparisonRow> compare(ProblemData problemData,
                                              int maxAddedColumnsPerIteration,
                                              int maxIterations,
                                              OriginalMipDVRPSolver.Options exactOptions,
                                              String heuristicPricingRule,
                                              String cuttingRule) {
        return compare(
                problemData,
                maxAddedColumnsPerIteration,
                maxIterations,
                exactOptions,
                heuristicPricingRule,
                cuttingRule,
                null
        );
    }

    public static List<ComparisonRow> compare(ProblemData problemData,
                                              int maxAddedColumnsPerIteration,
                                              int maxIterations,
                                              OriginalMipDVRPSolver.Options exactOptions,
                                              String heuristicPricingRule,
                                              String cuttingRule,
                                              String relaxationRule) {
        if (problemData == null) {
            throw new IllegalArgumentException("problemData must not be null.");
        }
        if (exactOptions == null) {
            throw new IllegalArgumentException("exactOptions must not be null.");
        }
        problemData.validate();

        List<Double> decisionTimes = DVRPDynamicSolver.buildEventDrivenDecisionTimes(problemData);
        DynamicStateUpdater updater = new DynamicStateUpdater();
        BPCSolver bpcSolver = new BPCSolver();
        configureOptionalHeuristicPricing(bpcSolver, heuristicPricingRule);
        configureOptionalCutting(bpcSolver, cuttingRule);
        configureOptionalRelaxation(bpcSolver, relaxationRule);
        OriginalMipDVRPSolver exactSolver = new OriginalMipDVRPSolver();
        RouteFeasibilityValidator routeValidator = new RouteFeasibilityValidator();

        DynamicInstance currentInstance = null;
        StageSolution currentBpcSolution = new StageSolution();
        double horizon = problemData.getOriginDepot().getLatestTime();
        List<ComparisonRow> rows = new ArrayList<>();

        for (int timeIndex = 0; timeIndex < decisionTimes.size(); timeIndex++) {
            double currentTime = decisionTimes.get(timeIndex);
            if (currentTime >= horizon - EPSILON) {
                break;
            }
            if (currentInstance == null) {
                currentInstance = updater.createInitialInstance(problemData, currentTime);
            } else {
                currentInstance = updater.update(currentInstance, currentBpcSolution, currentTime);
            }

            long elapsedStartNanos = System.nanoTime();
            BpcStageSolve bpcStageSolve = solveBpcStageOrEmpty(
                    currentInstance,
                    bpcSolver,
                    maxAddedColumnsPerIteration,
                    maxIterations
            );
            StageSolution bpcSolution = bpcStageSolve.getStageSolution();
            validateIntegerBpcSolution(currentInstance, bpcSolution, routeValidator);
            OriginalMipDVRPSolver.Result exactResult = exactSolver.solve(currentInstance, exactOptions);
            StageSolution exactSolution = exactResult.getStageSolution();
            long elapsedEndNanos = System.nanoTime();

            double nextTime = DVRPDynamicSolver.resolveNextDecisionTime(decisionTimes, timeIndex, horizon);
            double bpcActualDistance = updater.calculateActualDistance(currentInstance, bpcSolution, nextTime);
            double exactActualDistance = updater.calculateActualDistance(currentInstance, exactSolution, nextTime);

            rows.add(new ComparisonRow(
                    currentInstance.copy(),
                    bpcSolution.copy(),
                    exactResult,
                    bpcActualDistance,
                    exactActualDistance,
                    elapsedStartNanos,
                    elapsedEndNanos,
                    bpcStageSolve.getRunStatistics()
            ));
            currentBpcSolution = bpcSolution;
        }
        return rows;
    }

    private static void validateIntegerBpcSolution(DynamicInstance instance,
                                                   StageSolution solution,
                                                   RouteFeasibilityValidator validator) {
        if (instance.getRemainingCustomerIds().isEmpty()) {
            return;
        }
        if (!solution.isIntegerSolution()) {
            return;
        }
        List<String> violations = validator.findStageSolutionViolations(instance, solution);
        if (!violations.isEmpty()) {
            throw new IllegalStateException("Invalid integer BPC stage solution at time "
                    + instance.getCurrentTime() + ": " + String.join("; ", violations));
        }
    }

    private static BpcStageSolve solveBpcStageOrEmpty(DynamicInstance instance,
                                                      BPCSolver bpcSolver,
                                                      int maxAddedColumnsPerIteration,
                                                      int maxIterations) {
        if (instance.getRemainingCustomerIds().isEmpty()) {
            StageSolution emptySolution = new StageSolution();
            emptySolution.setIntegerSolution(true);
            return new BpcStageSolve(emptySolution, BPCSolverTemplate.BPCRunStatistics.empty());
        }
        BPCSolver.SolveResult solveResult = bpcSolver.solveNode(
                instance,
                maxAddedColumnsPerIteration,
                maxIterations
        );
        if (!solveResult.getStatus().equals(BPCSolver.SolveStatus.INTEGER_ROOT)
                && !solveResult.getStatus().equals(BPCSolver.SolveStatus.INTEGER_INCUMBENT)) {
            throw new IllegalStateException("BPC stage did not close to an integer exact solution at time "
                    + instance.getCurrentTime()
                    + ": solveStatus=" + solveResult.getStatus()
                    + ", cgStatus=" + solveResult.getResultNode().getColumnGenerationStatus());
        }
        return new BpcStageSolve(solveResult.getSolution(), solveResult.getRunStatistics());
    }

    private static void printRows(List<ComparisonRow> rows) {
        double totalBpcActualDistance = 0.0;
        double totalExactSameStageActualDistance = 0.0;
        for (ComparisonRow row : rows) {
            DynamicInstance instance = row.getStageInstance();
            StageSolution bpcSolution = row.getBpcSolution();
            StageSolution exactSolution = row.getExactSolution();

            totalBpcActualDistance += row.getBpcActualDistanceToNextStage();
            totalExactSameStageActualDistance += row.getExactActualDistanceToNextStage();

            System.out.println("Current time: " + instance.getCurrentTime());
            System.out.println("Remaining customers: " + instance.getRemainingCustomerIds().size());
            System.out.println("External depots: " + instance.getExternalDepots().size());
            System.out.println("BPC selected routes: " + bpcSolution.getSelectedRoutes().size());
            System.out.println("BPC objective value: " + bpcSolution.getObjectiveValue());
            System.out.println("BPC actual distance to next stage: " + row.getBpcActualDistanceToNextStage());
            System.out.println("BPC integer solution: " + bpcSolution.isIntegerSolution());
            BPCSolverTemplate.BPCRunStatistics bpcRunStatistics = row.getBpcRunStatistics();
            System.out.println("BPC elapsed millis: " + bpcRunStatistics.getElapsedMillis());
            System.out.println("BPC CG runs: " + bpcRunStatistics.getColumnGenerationRuns());
            System.out.println("BPC CG iterations: " + bpcRunStatistics.getColumnGenerationIterations());
            System.out.println("BPC master solves: " + bpcRunStatistics.getMasterSolves());
            System.out.println("BPC exact pricing calls: " + bpcRunStatistics.getExactPricingCalls());
            System.out.println("BPC heuristic pricing calls: " + bpcRunStatistics.getHeuristicPricingCalls());
            System.out.println("BPC columns added: " + bpcRunStatistics.getColumnsAdded());
            System.out.println("Exact selected routes: " + exactSolution.getSelectedRoutes().size());
            System.out.println("Exact objective value: " + exactSolution.getObjectiveValue());
            System.out.println("Exact best bound: " + row.getExactResult().getBestBound());
            System.out.println("Exact feasible: " + row.getExactResult().isFeasible());
            System.out.println("Exact actual distance to next stage on same BPC state: "
                    + row.getExactActualDistanceToNextStage());
            System.out.println("Objective gap BPC-minus-exact: "
                    + (bpcSolution.getObjectiveValue() - exactSolution.getObjectiveValue()));
            System.out.println("Bound gap BPC-minus-exact-bound: "
                    + (bpcSolution.getObjectiveValue() - row.getExactResult().getBestBound()));
            for (Route route : bpcSolution.getSelectedRoutes()) {
                System.out.println("BPC route " + route.getVisitedVertices() + " cost=" + route.getCost());
            }
            for (Route route : exactSolution.getSelectedRoutes()) {
                System.out.println("Exact route " + route.getVisitedVertices() + " cost=" + route.getCost());
            }
            System.out.println();
        }
        System.out.println("Total BPC actual distance: " + totalBpcActualDistance);
        System.out.println("Total exact actual distance on BPC states: " + totalExactSameStageActualDistance);
    }

    private static void applyOptionalInstanceOverride(ProblemConfig config, String[] args) {
        int index = 0;
        if (args == null || index >= args.length || args[index] == null || args[index].trim().isEmpty()) {
            return;
        }
        String instanceName = args[index].trim();
        String fileName = instanceName.endsWith(".xlsx") ? instanceName : instanceName + ".xlsx";
        config.setInstanceFileName(fileName);
        config.setInstanceBaseName(fileName.substring(0, fileName.length() - ".xlsx".length()));
    }

    private static void applyOptionalNumCustomersOverride(ProblemConfig config, String[] args) {
        Integer numCustomers = parseOptionalIntegerArg(args, 7);
        if (numCustomers != null && numCustomers > 0) {
            config.setNumCustomers(numCustomers);
        }
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

    private static void configureOptionalHeuristicPricing(BPCSolver bpcSolver, String heuristicPricingRule) {
        if (isDisabledHeuristicRule(heuristicPricingRule)) {
            return;
        }
        bpcSolver.setHeuristicPricingRule(heuristicPricingRule);
    }

    private static void configureOptionalCutting(BPCSolver bpcSolver, String cuttingRule) {
        if (cuttingRule == null || cuttingRule.trim().isEmpty()) {
            return;
        }
        bpcSolver.setCuttingRule(cuttingRule);
    }

    private static void configureOptionalRelaxation(BPCSolver bpcSolver, String relaxationRule) {
        if (relaxationRule == null || relaxationRule.trim().isEmpty()) {
            return;
        }
        bpcSolver.setRelaxationRule(relaxationRule);
    }

    private static boolean isDisabledHeuristicRule(String heuristicPricingRule) {
        if (heuristicPricingRule == null || heuristicPricingRule.trim().isEmpty()) {
            return true;
        }
        String normalizedRule = heuristicPricingRule.trim();
        return "none".equalsIgnoreCase(normalizedRule)
                || "false".equalsIgnoreCase(normalizedRule)
                || "exact".equalsIgnoreCase(normalizedRule)
                || "exact pricing".equalsIgnoreCase(normalizedRule);
    }

    public static class ComparisonRow {

        private final DynamicInstance stageInstance;
        private final StageSolution bpcSolution;
        private final OriginalMipDVRPSolver.Result exactResult;
        private final double bpcActualDistanceToNextStage;
        private final double exactActualDistanceToNextStage;
        private final long elapsedStartNanos;
        private final long elapsedEndNanos;
        private final BPCSolverTemplate.BPCRunStatistics bpcRunStatistics;

        public ComparisonRow(DynamicInstance stageInstance,
                             StageSolution bpcSolution,
                             OriginalMipDVRPSolver.Result exactResult,
                             double bpcActualDistanceToNextStage,
                             double exactActualDistanceToNextStage,
                             long elapsedStartNanos,
                             long elapsedEndNanos,
                             BPCSolverTemplate.BPCRunStatistics bpcRunStatistics) {
            if (stageInstance == null) {
                throw new IllegalArgumentException("stageInstance must not be null.");
            }
            if (bpcSolution == null) {
                throw new IllegalArgumentException("bpcSolution must not be null.");
            }
            if (exactResult == null) {
                throw new IllegalArgumentException("exactResult must not be null.");
            }
            this.stageInstance = stageInstance.copy();
            this.bpcSolution = bpcSolution.copy();
            this.exactResult = exactResult;
            this.bpcActualDistanceToNextStage = bpcActualDistanceToNextStage;
            this.exactActualDistanceToNextStage = exactActualDistanceToNextStage;
            this.elapsedStartNanos = elapsedStartNanos;
            this.elapsedEndNanos = elapsedEndNanos;
            this.bpcRunStatistics = bpcRunStatistics == null
                    ? BPCSolverTemplate.BPCRunStatistics.empty()
                    : bpcRunStatistics.copy();
        }

        public DynamicInstance getStageInstance() {
            return stageInstance.copy();
        }

        public StageSolution getBpcSolution() {
            return bpcSolution.copy();
        }

        public StageSolution getExactSolution() {
            return exactResult.getStageSolution();
        }

        public OriginalMipDVRPSolver.Result getExactResult() {
            return exactResult;
        }

        public double getBpcActualDistanceToNextStage() {
            return bpcActualDistanceToNextStage;
        }

        public double getExactActualDistanceToNextStage() {
            return exactActualDistanceToNextStage;
        }

        public long getElapsedStartNanos() {
            return elapsedStartNanos;
        }

        public long getElapsedEndNanos() {
            return elapsedEndNanos;
        }

        public BPCSolverTemplate.BPCRunStatistics getBpcRunStatistics() {
            return bpcRunStatistics.copy();
        }
    }

    private static final class BpcStageSolve {
        private final StageSolution stageSolution;
        private final BPCSolverTemplate.BPCRunStatistics runStatistics;

        private BpcStageSolve(StageSolution stageSolution, BPCSolverTemplate.BPCRunStatistics runStatistics) {
            this.stageSolution = stageSolution == null ? new StageSolution() : stageSolution.copy();
            this.runStatistics = runStatistics == null
                    ? BPCSolverTemplate.BPCRunStatistics.empty()
                    : runStatistics.copy();
        }

        private StageSolution getStageSolution() {
            return stageSolution.copy();
        }

        private BPCSolverTemplate.BPCRunStatistics getRunStatistics() {
            return runStatistics.copy();
        }
    }
}

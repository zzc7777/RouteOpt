package org.zzc.bpcsolver.benchmark;

import org.zzc.bpcsolver.algorithm.BPCSolver;
import org.zzc.bpcsolver.algorithm.BPCSolverTemplate;
import org.zzc.bpcsolver.core.branch.ColumnGenerationStatus;
import org.zzc.bpcsolver.core.branch.StageSolution;
import org.zzc.bpcsolver.problem.config.ProblemConfig;
import org.zzc.bpcsolver.problem.config.ProblemConfigReader;
import org.zzc.bpcsolver.problem.entity.Customer;
import org.zzc.bpcsolver.problem.entity.DynamicInstance;
import org.zzc.bpcsolver.problem.entity.ProblemData;
import org.zzc.bpcsolver.problem.loader.ProblemDataLoader;
import org.zzc.bpcsolver.problem.metric.DecimalRoundingMode;
import org.zzc.bpcsolver.problem.update.DynamicStateUpdater;
import org.zzc.bpcsolver.exact.OriginalMipDVRPSolver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class StaticScaleLimitBenchmarkMain {

    private static final int DEFAULT_MAX_ADDED_COLUMNS = BPCSolver.DEFAULT_MAX_ADDED_COLUMNS_PER_ITERATION;
    private static final int DEFAULT_MAX_ITERATIONS = BPCSolver.DEFAULT_MAX_ITERATIONS;
    private static final double DEFAULT_TIME_LIMIT_SECONDS = 600.0;
    private static final String DEFAULT_HEURISTIC_PRICING = "none";
    private static final String DEFAULT_PRIMAL_HEURISTIC = "column-pool-integer-master";
    private static final boolean DEFAULT_PARALLEL_PRICING = true;
    private static final String DEFAULT_CUTTING = "none";
    private static final String DEFAULT_RELAXATION = "none";
    private static final String DEFAULT_VARIABLE_SELECTION = "default";
    private static final String DEFAULT_RESOURCE_PATH = "DVRP_Instance/";

    public static void main(String[] args) {
        String solver = parseOptionalString(args, 0, "java").toLowerCase(Locale.ROOT);
        String instanceFileName = parseOptionalString(args, 1, "c101_D_25_E_25_1.xlsx");
        int numCustomers = parseOptionalInt(args, 2, 25);
        double timeLimitSeconds = parseOptionalDouble(args, 3, DEFAULT_TIME_LIMIT_SECONDS);
        int maxAddedColumns = parseOptionalInt(args, 4, DEFAULT_MAX_ADDED_COLUMNS);
        int maxIterations = parseOptionalInt(args, 5, DEFAULT_MAX_ITERATIONS);
        String heuristicPricing = parseOptionalString(args, 6, DEFAULT_HEURISTIC_PRICING);
        String primalHeuristic = parseOptionalString(args, 7, DEFAULT_PRIMAL_HEURISTIC);
        boolean parallelPricing = parseOptionalBoolean(args, 8, DEFAULT_PARALLEL_PRICING);
        String cutting = parseOptionalString(args, 9, DEFAULT_CUTTING);
        String relaxation = parseOptionalString(args, 10, DEFAULT_RELAXATION);
        String variableSelection = parseOptionalString(args, 11, DEFAULT_VARIABLE_SELECTION);
        String resourcePath = parseOptionalString(args, 12, DEFAULT_RESOURCE_PATH);
        DecimalRoundingMode distanceRoundingMode = parseOptionalRoundingMode(args, 13, null);
        DecimalRoundingMode travelTimeRoundingMode = parseOptionalRoundingMode(args, 14, null);

        ProblemData problemData = loadStaticProblemData(
                instanceFileName,
                numCustomers,
                resourcePath,
                distanceRoundingMode,
                travelTimeRoundingMode
        );
        DynamicInstance initialInstance = new DynamicStateUpdater().createInitialInstance(
                problemData,
                problemData.getOriginDepot().getEarliestTime()
        );

        BenchmarkRow row;
        if ("java".equals(solver) || "bpc".equals(solver)) {
            row = runJavaBpc(
                    instanceFileName,
                    numCustomers,
                    initialInstance,
                    maxAddedColumns,
                    maxIterations,
                    timeLimitSeconds,
                    heuristicPricing,
                    primalHeuristic,
                    parallelPricing,
                    cutting,
                    relaxation,
                    variableSelection
            );
        } else if ("cplex".equals(solver) || "exact".equals(solver)) {
            row = runCplex(instanceFileName, numCustomers, initialInstance, timeLimitSeconds);
        } else {
            throw new IllegalArgumentException("Unknown solver: " + solver + ". Expected java or cplex.");
        }

        System.out.println("RESULT_CSV:" + row.toCsv());
    }

    private static ProblemData loadStaticProblemData(String instanceFileName,
                                                     int numCustomers,
                                                     String resourcePath,
                                                     DecimalRoundingMode distanceRoundingMode,
                                                     DecimalRoundingMode travelTimeRoundingMode) {
        ProblemConfig config = new ProblemConfigReader().read("dvrp_td.yaml");
        String normalizedFileName = instanceFileName.endsWith(".xlsx")
                ? instanceFileName
                : instanceFileName + ".xlsx";
        config.setResourcePath(normalizeResourcePath(resourcePath));
        config.setInstanceFileName(normalizedFileName);
        config.setInstanceBaseName(normalizedFileName.substring(0, normalizedFileName.length() - ".xlsx".length()));
        config.setNumCustomers(numCustomers);
        applySolomon100StaticConfig(config);
        if (distanceRoundingMode != null) {
            config.setDistanceRoundingMode(distanceRoundingMode);
        }
        if (travelTimeRoundingMode != null) {
            config.setTravelTimeRoundingMode(travelTimeRoundingMode);
        }

        ProblemData problemData = new ProblemDataLoader().load(config);
        double originEarliest = problemData.getOriginDepot().getEarliestTime();
        for (Customer customer : problemData.getCustomers()) {
            customer.setCallInTime(originEarliest);
        }
        return problemData;
    }

    private static void applySolomon100StaticConfig(ProblemConfig config) {
        if (config == null || !isSolomon100ResourcePath(config.getResourcePath())) {
            return;
        }
        config.setInitialVehicleCount(25);
        Integer capacity = readSolomon100Capacity(config.getInstanceBaseName());
        if (capacity != null) {
            config.setVehicleCapacity(capacity);
        }
    }

    private static boolean isSolomon100ResourcePath(String resourcePath) {
        if (resourcePath == null) {
            return false;
        }
        return resourcePath.replace('\\', '/').toLowerCase(Locale.ROOT).contains("solomon100_instance/");
    }

    static Integer readSolomon100Capacity(String instanceBaseName) {
        if (instanceBaseName == null || instanceBaseName.trim().isEmpty()) {
            return null;
        }
        String resourceName = "solomon-100/In/" + instanceBaseName.trim().toLowerCase(Locale.ROOT) + ".txt";
        try (InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourceName)) {
            if (inputStream == null) {
                return null;
            }
            return parseSolomonCapacity(inputStream, resourceName);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read Solomon capacity from " + resourceName, e);
        }
    }

    private static int parseSolomonCapacity(InputStream inputStream, String resourceName) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            boolean afterVehicleHeader = false;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                String upper = trimmed.toUpperCase(Locale.ROOT);
                if (upper.contains("NUMBER") && upper.contains("CAPACITY")) {
                    afterVehicleHeader = true;
                    continue;
                }
                if (!afterVehicleHeader) {
                    continue;
                }
                String[] tokens = trimmed.split("\\s+");
                if (tokens.length >= 2 && isIntegerToken(tokens[0]) && isIntegerToken(tokens[1])) {
                    return Integer.parseInt(tokens[1]);
                }
            }
        }
        throw new IllegalArgumentException("Vehicle capacity line not found in Solomon resource: " + resourceName);
    }

    private static boolean isIntegerToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            return false;
        }
        try {
            Integer.parseInt(token.trim());
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private static String normalizeResourcePath(String resourcePath) {
        String normalized = resourcePath == null || resourcePath.trim().isEmpty()
                ? DEFAULT_RESOURCE_PATH
                : resourcePath.trim().replace('\\', '/');
        return normalized.endsWith("/") ? normalized : normalized + "/";
    }

    private static BenchmarkRow runJavaBpc(String instanceFileName,
                                           int numCustomers,
                                           DynamicInstance initialInstance,
                                           int maxAddedColumns,
                                           int maxIterations,
                                           double timeLimitSeconds,
                                           String heuristicPricing,
                                           String primalHeuristic,
                                           boolean parallelPricing,
                                           String cutting,
                                           String relaxation,
                                           String variableSelection) {
        BPCSolver solver = new BPCSolver();
        solver.setHeuristicPricingRule(heuristicPricing);
        solver.setPrimalHeuristicRule(primalHeuristic);
        solver.setParallelPricing(parallelPricing);
        solver.setCuttingRule(cutting);
        solver.setRelaxationRule(relaxation);
        solver.setVariableSelectionRule(variableSelection);
        solver.setBranchingTimeLimitSeconds(Math.max(1L, (long) Math.floor(timeLimitSeconds)));

        long startNanos = System.nanoTime();
        BPCSolver.SolveResult result = solver.solveNode(initialInstance, maxAddedColumns, maxIterations);
        long elapsedMillis = elapsedMillisSince(startNanos);

        StageSolution solution = result.getSolution();
        BPCSolverTemplate.BranchSearchStatistics branchStatistics = result.getBranchSearchStatistics();
        BPCSolverTemplate.BPCRunStatistics runStatistics = result.getRunStatistics();
        ColumnGenerationStatus columnGenerationStatus = result.getResultNode().getColumnGenerationStatus();
        boolean certifiedExact = result.isCertifiedExact();
        String status = result.getStatus().name();
        String notes = "maxAddedColumns=" + maxAddedColumns
                + ";maxIterations=" + maxIterations
                + ";solverTimeLimitSeconds=" + formatDouble(timeLimitSeconds)
                + ";heuristicPricing=" + normalizeNone(heuristicPricing)
                + ";primalHeuristic=" + normalizeNone(primalHeuristic)
                + ";parallelPricing=" + parallelPricing
                + ";cutting=" + normalizeNone(cutting)
                + ";relaxation=" + normalizeNone(relaxation)
                + ";variableSelection=" + normalizeDefault(variableSelection)
                + ";distanceRounding=" + initialInstance.getProblemData().getDistanceRoundingMode()
                + ";travelTimeRounding=" + initialInstance.getProblemData().getTravelTimeRoundingMode()
                + ";vehicleCapacity=" + initialInstance.getProblemData().getVehicleCapacity()
                + ";initialVehicleCount=" + initialInstance.getProblemData().getInitialVehicleCount()
                + ";cgClosed=" + (columnGenerationStatus != null && columnGenerationStatus.isClosed())
                + ";cgStatus=" + (columnGenerationStatus == null ? ColumnGenerationStatus.NOT_STARTED : columnGenerationStatus)
                + ";certifiedExact=" + certifiedExact
                + ";branchSolvedToOptimality=" + branchStatistics.isSolvedToOptimality()
                + ";branchTermination=" + branchStatistics.getTerminationReason()
                + ";cgIterations=" + runStatistics.getColumnGenerationIterations()
                + ";masterSolves=" + runStatistics.getMasterSolves()
                + ";exactPricingCalls=" + runStatistics.getExactPricingCalls()
                + ";heuristicPricingCalls=" + runStatistics.getHeuristicPricingCalls()
                + ";columnsAdded=" + runStatistics.getColumnsAdded()
                + ";cutSeparationCalls=" + runStatistics.getCutSeparationCalls()
                + ";cutsGenerated=" + runStatistics.getCutsGenerated()
                + ";cutsAdded=" + runStatistics.getCutsAdded()
                + ";cutGuardRounds=" + runStatistics.getCutGuardRounds()
                + ";cutStoppedBySlowPricing=" + runStatistics.isCutSeparationStoppedBySlowPricing()
                + ";cutStoppedByTrailing=" + runStatistics.isCutSeparationStoppedByTrailing()
                + ";integerFlowRepairAttempts=" + runStatistics.getIntegerFlowRepairAttempts()
                + ";integerFlowRepairSuccesses=" + runStatistics.getIntegerFlowRepairSuccesses()
                + ";integerFlowRepairLastFailureReason=" + runStatistics.getIntegerFlowRepairLastFailureReason()
                + ";integerFlowRepairFailuresInvalidInput=" + runStatistics.getIntegerFlowRepairFailuresInvalidInput()
                + ";integerFlowRepairFailuresNoArcFlow=" + runStatistics.getIntegerFlowRepairFailuresNoArcFlow()
                + ";integerFlowRepairFailuresNoFractionalRoute=" + runStatistics.getIntegerFlowRepairFailuresNoFractionalRoute()
                + ";integerFlowRepairFailuresArcFlowNotIntegral=" + runStatistics.getIntegerFlowRepairFailuresArcFlowNotIntegral()
                + ";integerFlowRepairFailuresCustomerDegree=" + runStatistics.getIntegerFlowRepairFailuresCustomerDegree()
                + ";integerFlowRepairFailuresDecomposition=" + runStatistics.getIntegerFlowRepairFailuresDecomposition()
                + ";integerFlowRepairFailuresStageInfeasible=" + runStatistics.getIntegerFlowRepairFailuresStageInfeasible()
                + ";integerFlowRepairFailuresBranchInfeasible=" + runStatistics.getIntegerFlowRepairFailuresBranchInfeasible()
                + ";integerFlowRepairFailuresObjectiveMismatch=" + runStatistics.getIntegerFlowRepairFailuresObjectiveMismatch()
                + ";labelsCreated=" + runStatistics.getLabelsCreated()
                + ";labelsExtended=" + runStatistics.getLabelsExtended()
                + ";labelsDominated=" + runStatistics.getLabelsDominated()
                + ";labelsRemovedByDominance=" + runStatistics.getLabelsRemovedByDominance()
                + ";maxLabelsPerVertex=" + runStatistics.getMaxLabelsPerVertex()
                + ";sinkLabels=" + runStatistics.getSinkLabels()
                + ";pricingTimeLimitReached=" + runStatistics.isPricingTimeLimitReached()
                + ";bpcElapsedMs=" + runStatistics.getElapsedMillis()
                + ";masterSolveElapsedMs=" + runStatistics.getMasterSolveElapsedMillis()
                + ";exactPricingElapsedMs=" + runStatistics.getExactPricingElapsedMillis()
                + ";heuristicPricingElapsedMs=" + runStatistics.getHeuristicPricingElapsedMillis()
                + ";primalHeuristicElapsedMs=" + runStatistics.getPrimalHeuristicElapsedMillis()
                + ";cutSeparationElapsedMs=" + runStatistics.getCutSeparationElapsedMillis()
                + ";dominanceElapsedMs=" + runStatistics.getDominanceElapsedMillis()
                + ";extensionElapsedMs=" + runStatistics.getExtensionElapsedMillis()
                + ";cutGuardMaxExactPricingCallMs=" + runStatistics.getCutGuardMaxExactPricingCallMillis()
                + ";cutGuardLastGap=" + formatDouble(runStatistics.getCutGuardLastGap())
                + ";branchElapsedMs=" + branchStatistics.getElapsedMillis()
                + ";branchNodesExplored=" + branchStatistics.getNodesExplored()
                + ";branchNodesProcessed=" + branchStatistics.getNodesProcessed()
                + ";branchChildrenSolved=" + branchStatistics.getChildrenSolved()
                + ";branchPrunedByIncumbent=" + branchStatistics.getNodesPrunedByIncumbent()
                + ";branchIncumbentUpdates=" + branchStatistics.getIncumbentUpdates()
                + ";branchInfeasibleChildren=" + branchStatistics.getInfeasibleChildren()
                + ";branchFractionalNodesQueued=" + branchStatistics.getFractionalNodesQueued()
                + ";branchingFailures=" + branchStatistics.getBranchingFailures()
                + ";branchingFailuresNoFractionalVariables=" + branchStatistics.getBranchingFailuresNoFractionalVariables()
                + ";branchingFailuresNoSelectedVariable=" + branchStatistics.getBranchingFailuresNoSelectedVariable()
                + ";branchingFailuresCgNotClosed=" + branchStatistics.getBranchingFailuresCgNotClosed()
                + ";branchMaxOpenNodes=" + branchStatistics.getMaxOpenNodes()
                + ";branchOpenNodesAtEnd=" + branchStatistics.getOpenNodesAtEnd()
                + ";resultCutCount=" + branchStatistics.getResultCutCount();
        return new BenchmarkRow(
                "java-bpc",
                instanceFileName,
                numCustomers,
                1,
                status,
                elapsedMillis,
                solution.getObjectiveValue(),
                Double.NaN,
                solution.getSelectedRoutes().size(),
                initialInstance.getRemainingCustomerIds().size(),
                notes
        );
    }

    private static BenchmarkRow runCplex(String instanceFileName,
                                         int numCustomers,
                                         DynamicInstance initialInstance,
                                         double timeLimitSeconds) {
        OriginalMipDVRPSolver solver = new OriginalMipDVRPSolver();
        OriginalMipDVRPSolver.Options options = OriginalMipDVRPSolver.Options.defaults()
                .setTimeLimitSeconds(timeLimitSeconds)
                .setStartTimeMode(OriginalMipDVRPSolver.StartTimeMode.ROUTE_CLOCK);

        OriginalMipDVRPSolver.Result result = solver.solve(initialInstance, options);
        String status = result.isFeasible() ? result.getSolveStatus() : "NO_FEASIBLE_" + result.getSolveStatus();
        return new BenchmarkRow(
                "cplex-original-mip",
                instanceFileName,
                numCustomers,
                1,
                status,
                result.getSolveElapsedMillis(),
                result.getCplexObjectiveValue(),
                result.getBestBound(),
                result.getStageSolution().getSelectedRoutes().size(),
                initialInstance.getRemainingCustomerIds().size(),
                "timeLimitSeconds=" + formatDouble(timeLimitSeconds)
                        + ";distanceRounding=" + initialInstance.getProblemData().getDistanceRoundingMode()
                        + ";travelTimeRounding=" + initialInstance.getProblemData().getTravelTimeRoundingMode()
        );
    }

    private static long elapsedMillisSince(long startNanos) {
        return Math.max(0L, Math.round((System.nanoTime() - startNanos) / 1_000_000.0));
    }

    private static String normalizeNone(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "none";
        }
        return value.trim();
    }

    private static String normalizeDefault(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "default";
        }
        return value.trim();
    }

    private static String parseOptionalString(String[] args, int index, String defaultValue) {
        if (args == null || index >= args.length || args[index] == null || args[index].trim().isEmpty()) {
            return defaultValue;
        }
        return args[index].trim();
    }

    private static int parseOptionalInt(String[] args, int index, int defaultValue) {
        if (args == null || index >= args.length || args[index] == null || args[index].trim().isEmpty()) {
            return defaultValue;
        }
        return Integer.parseInt(args[index].trim());
    }

    private static double parseOptionalDouble(String[] args, int index, double defaultValue) {
        if (args == null || index >= args.length || args[index] == null || args[index].trim().isEmpty()) {
            return defaultValue;
        }
        return Double.parseDouble(args[index].trim());
    }

    private static boolean parseOptionalBoolean(String[] args, int index, boolean defaultValue) {
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

    private static DecimalRoundingMode parseOptionalRoundingMode(String[] args,
                                                                 int index,
                                                                 DecimalRoundingMode defaultValue) {
        if (args == null || index >= args.length || args[index] == null || args[index].trim().isEmpty()) {
            return defaultValue;
        }
        String value = args[index].trim();
        if ("default".equalsIgnoreCase(value)) {
            return defaultValue;
        }
        return DecimalRoundingMode.valueOf(value.toUpperCase(Locale.ROOT));
    }

    private static String formatDouble(double value) {
        if (Double.isNaN(value)) {
            return "";
        }
        if (Double.isInfinite(value)) {
            return value > 0.0 ? "Infinity" : "-Infinity";
        }
        return String.format(Locale.US, "%.4f", value);
    }

    private static String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        String sanitized = value.replace('\n', ' ').replace('\r', ' ');
        if (sanitized.contains(",") || sanitized.contains("\"")) {
            return "\"" + sanitized.replace("\"", "\"\"") + "\"";
        }
        return sanitized;
    }

    private static final class BenchmarkRow {
        private final String solver;
        private final String instance;
        private final int numCustomers;
        private final int stageCount;
        private final String status;
        private final long solveTimeMillis;
        private final double objectiveValue;
        private final double bestBound;
        private final int routeCount;
        private final int remainingCustomers;
        private final String notes;

        private BenchmarkRow(String solver,
                             String instance,
                             int numCustomers,
                             int stageCount,
                             String status,
                             long solveTimeMillis,
                             double objectiveValue,
                             double bestBound,
                             int routeCount,
                             int remainingCustomers,
                             String notes) {
            this.solver = solver;
            this.instance = instance;
            this.numCustomers = numCustomers;
            this.stageCount = stageCount;
            this.status = status;
            this.solveTimeMillis = Math.max(0L, solveTimeMillis);
            this.objectiveValue = objectiveValue;
            this.bestBound = bestBound;
            this.routeCount = routeCount;
            this.remainingCustomers = remainingCustomers;
            this.notes = notes;
        }

        private String toCsv() {
            return escapeCsv(solver)
                    + "," + escapeCsv(instance)
                    + "," + numCustomers
                    + "," + stageCount
                    + "," + escapeCsv(status)
                    + "," + solveTimeMillis
                    + "," + formatDouble(objectiveValue)
                    + "," + formatDouble(bestBound)
                    + "," + routeCount
                    + "," + remainingCustomers
                    + "," + escapeCsv(notes);
        }
    }
}

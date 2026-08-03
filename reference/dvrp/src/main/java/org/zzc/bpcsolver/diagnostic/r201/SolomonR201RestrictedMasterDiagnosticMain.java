package org.zzc.bpcsolver.diagnostic.r201;

import gnu.trove.list.array.TIntArrayList;
import org.zzc.bpcsolver.core.branch.StageSolution;
import org.zzc.bpcsolver.core.graph.Route;
import org.zzc.bpcsolver.core.graph.RouteFeasibilityValidator;
import org.zzc.bpcsolver.model.DVRPMasterProblem;
import org.zzc.bpcsolver.problem.entity.Depot;
import org.zzc.bpcsolver.problem.entity.DynamicInstance;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SolomonR201RestrictedMasterDiagnosticMain {

    static final double BENCHMARK_OBJECTIVE = R201DiagnosticSupport.SOLOMON_BPC_BENCHMARK_OBJECTIVE;
    static final double WITNESS_JAVA_FLOOR_OBJECTIVE = R201DiagnosticSupport.PUBLIC_WITNESS_JAVA_FLOOR_OBJECTIVE;
    private static final double EPSILON = 1e-6;

    public static void main(String[] args) throws IOException {
        Path witnessPath = Paths.get(parseOptionalString(args, 0,
                R201DiagnosticSupport.DEFAULT_WITNESS_PATH.toString()));
        DiagnosticRun run = runDiagnostic(witnessPath);

        System.out.println("R201_RESTRICTED_MASTER_DIAGNOSTIC"
                + ",witnessPath=" + csv(witnessPath.toString())
                + ",witnessFeasible=" + run.witnessFeasible
                + ",witnessObjective=" + format(run.witnessObjective)
                + ",benchmarkObjective=" + format(BENCHMARK_OBJECTIVE)
                + ",witnessRoutes=" + run.witnessRoutes.size()
                + ",initialRoutes=" + run.initialRoutes.size());
        System.out.println("model,integerModel,solved,objective,witnessGap,benchmarkGap,columns,selectedColumns,"
                + "selectedInitialRoutes,selectedWitnessRoutes,fullySelectedWitnessRoutes,witnessValueSum,"
                + "allWitnessFullySelected,solutionInteger,stageFeasible,underCoveredCustomers,"
                + "overCoveredCustomers,minCoverage,maxCoverage,violationCount,violations");
        for (ModelResult result : run.results) {
            printSummaryRow(result);
        }
        System.out.println("DETAILS");
        System.out.println("model,routeType,routeIndex,columnIndex,value,cost,selected,fullySelected,"
                + "customerVisits,routeFeasible,routeViolationCount,visitedVertices,routeViolations");
        for (ModelResult result : run.results) {
            for (ColumnResult column : result.columns) {
                if ("witness".equals(column.record.type) || column.value > EPSILON) {
                    printDetailRow(result.modelName, column);
                }
            }
        }
    }

    static DiagnosticRun runDiagnostic(Path witnessPath) throws IOException {
        DynamicInstance instance = R201DiagnosticSupport.loadR201Instance();
        List<Route> witnessRoutes = R201DiagnosticSupport.loadWitnessRoutes(instance, witnessPath);
        List<Route> initialRoutes = generateDiagnosticInitialRoutes(instance);

        double witnessObjective = R201DiagnosticSupport.sumCosts(witnessRoutes);
        boolean witnessFeasible = R201DiagnosticSupport.isStageSolutionFeasible(instance, witnessRoutes);

        List<ModelResult> results = new ArrayList<>();
        results.add(solveModel(instance, "witness_only_lp", witnessRoutes, false, 0));
        results.add(solveModel(instance, "witness_only_ip", witnessRoutes, true, 0));

        List<Route> initialPlusWitness = new ArrayList<>();
        initialPlusWitness.addAll(R201DiagnosticSupport.copyRoutes(initialRoutes));
        initialPlusWitness.addAll(R201DiagnosticSupport.copyRoutes(witnessRoutes));
        results.add(solveModel(instance, "initial_plus_witness_lp", initialPlusWitness, false, initialRoutes.size()));
        results.add(solveModel(instance, "initial_plus_witness_ip", initialPlusWitness, true, initialRoutes.size()));

        return new DiagnosticRun(initialRoutes, witnessRoutes, witnessObjective, witnessFeasible, results);
    }

    private static ModelResult solveModel(DynamicInstance instance,
                                          String modelName,
                                          List<Route> routes,
                                          boolean integerModel,
                                          int witnessStartIndex) {
        List<ColumnRecord> records = buildRecords(routes, witnessStartIndex);
        try (DVRPMasterProblem masterProblem = new DVRPMasterProblem()) {
            masterProblem.initialize(instance, routes, integerModel);
            masterProblem.build();
            boolean solved = masterProblem.solve();
            if (!solved) {
                return ModelResult.unsolved(modelName, integerModel, records);
            }

            StageSolution solution = new StageSolution();
            masterProblem.updateSolution(solution);
            List<String> violations = new ArrayList<>();
            if (solution.isIntegerSolution()) {
                violations.addAll(new RouteFeasibilityValidator().findStageSolutionViolations(instance, solution));
            }

            List<Route> solvedRoutes = masterProblem.getRoutes();
            List<ColumnResult> columns = new ArrayList<>();
            for (int i = 0; i < solvedRoutes.size(); i++) {
                Route route = solvedRoutes.get(i);
                ColumnRecord record = records.get(i);
                columns.add(new ColumnResult(record, i, route.getValue(), route,
                        new RouteFeasibilityValidator().findRouteViolations(instance, route)));
            }
            CoverageStats coverageStats = calculateCoverageStats(instance, solvedRoutes);
            return ModelResult.solved(modelName, integerModel, masterProblem.getObjectiveValue(), columns,
                    solution.isIntegerSolution(), violations, coverageStats);
        }
    }

    private static List<Route> generateDiagnosticInitialRoutes(DynamicInstance instance) {
        List<Route> initialRoutes = new ArrayList<>();
        int routeIndex = 0;
        for (int customerId : instance.getRemainingCustomerIds()) {
            Route route = buildRouteIfFeasible(
                    instance,
                    instance.getOriginDepot().getId(),
                    routeIndex,
                    instance.getOriginDepot().getId(),
                    customerId,
                    instance.getReturnDepot().getId()
            );
            if (route != null) {
                initialRoutes.add(route);
                routeIndex++;
            }
        }

        for (Depot externalDepot : instance.getExternalDepots()) {
            Route route = buildRouteIfFeasible(
                    instance,
                    externalDepot.getId(),
                    routeIndex,
                    externalDepot.getId(),
                    instance.getReturnDepot().getId()
            );
            if (route != null) {
                initialRoutes.add(route);
                routeIndex++;
            }
        }
        return initialRoutes;
    }

    private static Route buildRouteIfFeasible(DynamicInstance instance, int startDepotId, int routeIndex, int... nodeIds) {
        Route route = new Route(startDepotId, routeIndex, routeVectorSize(instance));
        route.setVisitedVertices(new TIntArrayList(nodeIds));
        route.update(instance);
        return new RouteFeasibilityValidator().isRouteFeasible(instance, route) ? route : null;
    }

    private static int routeVectorSize(DynamicInstance instance) {
        int maxNodeId = instance.getReturnDepot().getId();
        for (Depot externalDepot : instance.getExternalDepots()) {
            maxNodeId = Math.max(maxNodeId, externalDepot.getId());
        }
        return maxNodeId + 1;
    }

    private static List<ColumnRecord> buildRecords(List<Route> routes, int witnessStartIndex) {
        List<ColumnRecord> records = new ArrayList<>();
        for (int i = 0; i < routes.size(); i++) {
            if (i >= witnessStartIndex) {
                records.add(new ColumnRecord("witness", i - witnessStartIndex + 1));
            } else {
                records.add(new ColumnRecord("initial", i + 1));
            }
        }
        return records;
    }

    private static CoverageStats calculateCoverageStats(DynamicInstance instance, List<Route> routes) {
        Map<Integer, Double> coverage = new HashMap<>();
        for (Integer customerId : instance.getRemainingCustomerIds()) {
            coverage.put(customerId, 0.0);
        }
        for (Route route : routes) {
            if (route == null || route.getVisitedVertices() == null || route.getValue() <= EPSILON) {
                continue;
            }
            for (int nodeId : route.getVisitedVertices().toArray()) {
                if (coverage.containsKey(nodeId)) {
                    coverage.put(nodeId, coverage.get(nodeId) + route.getValue());
                }
            }
        }

        int underCovered = 0;
        int overCovered = 0;
        double minCoverage = Double.POSITIVE_INFINITY;
        double maxCoverage = Double.NEGATIVE_INFINITY;
        for (double value : coverage.values()) {
            minCoverage = Math.min(minCoverage, value);
            maxCoverage = Math.max(maxCoverage, value);
            if (value < 1.0 - EPSILON) {
                underCovered++;
            }
            if (value > 1.0 + EPSILON) {
                overCovered++;
            }
        }
        if (coverage.isEmpty()) {
            minCoverage = 0.0;
            maxCoverage = 0.0;
        }
        return new CoverageStats(underCovered, overCovered, minCoverage, maxCoverage);
    }

    private static void printSummaryRow(ModelResult result) {
        System.out.println(csv(result.modelName)
                + "," + result.integerModel
                + "," + result.solved
                + "," + format(result.objective)
                + "," + format(result.objective - WITNESS_JAVA_FLOOR_OBJECTIVE)
                + "," + format(result.objective - BENCHMARK_OBJECTIVE)
                + "," + result.columns.size()
                + "," + result.selectedColumns
                + "," + result.selectedInitialRoutes
                + "," + result.selectedWitnessRoutes
                + "," + result.fullySelectedWitnessRoutes
                + "," + format(result.witnessValueSum)
                + "," + result.allWitnessFullySelected
                + "," + result.solutionInteger
                + "," + result.stageFeasible
                + "," + result.coverageStats.underCoveredCustomers
                + "," + result.coverageStats.overCoveredCustomers
                + "," + format(result.coverageStats.minCoverage)
                + "," + format(result.coverageStats.maxCoverage)
                + "," + result.violations.size()
                + "," + csv(join(result.violations)));
    }

    private static void printDetailRow(String modelName, ColumnResult column) {
        Route route = column.route;
        String visitedVertices = route == null || route.getVisitedVertices() == null
                ? ""
                : route.getVisitedVertices().toString();
        System.out.println(csv(modelName)
                + "," + csv(column.record.type)
                + "," + column.record.routeIndex
                + "," + column.columnIndex
                + "," + format(column.value)
                + "," + format(route == null ? Double.NaN : route.getCost())
                + "," + (column.value > EPSILON)
                + "," + (column.value >= 1.0 - EPSILON)
                + "," + countCustomerVisits(route)
                + "," + column.routeViolations.isEmpty()
                + "," + column.routeViolations.size()
                + "," + csv(visitedVertices)
                + "," + csv(join(column.routeViolations)));
    }

    private static int countCustomerVisits(Route route) {
        if (route == null || route.getVisitedVertices() == null) {
            return 0;
        }
        return Math.max(0, route.getVisitedVertices().size() - 2);
    }

    private static String join(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                builder.append(" | ");
            }
            builder.append(values.get(i));
        }
        return builder.toString();
    }

    private static String format(double value) {
        if (Double.isInfinite(value)) {
            return value > 0 ? "Infinity" : "-Infinity";
        }
        if (Double.isNaN(value)) {
            return "NaN";
        }
        return String.format(Locale.ROOT, "%.6f", value);
    }

    private static String csv(String value) {
        String text = value == null ? "" : value;
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    private static String parseOptionalString(String[] args, int index, String defaultValue) {
        if (args == null || args.length <= index || args[index] == null || args[index].trim().isEmpty()) {
            return defaultValue;
        }
        return args[index].trim();
    }

    static final class DiagnosticRun {
        final List<Route> initialRoutes;
        final List<Route> witnessRoutes;
        final double witnessObjective;
        final boolean witnessFeasible;
        final List<ModelResult> results;

        private DiagnosticRun(List<Route> initialRoutes,
                              List<Route> witnessRoutes,
                              double witnessObjective,
                              boolean witnessFeasible,
                              List<ModelResult> results) {
            this.initialRoutes = R201DiagnosticSupport.copyRoutes(initialRoutes);
            this.witnessRoutes = R201DiagnosticSupport.copyRoutes(witnessRoutes);
            this.witnessObjective = witnessObjective;
            this.witnessFeasible = witnessFeasible;
            this.results = new ArrayList<>(results);
        }
    }

    static final class ModelResult {
        final String modelName;
        final boolean integerModel;
        final boolean solved;
        final double objective;
        final List<ColumnResult> columns;
        final int selectedColumns;
        final int selectedInitialRoutes;
        final int selectedWitnessRoutes;
        final int fullySelectedWitnessRoutes;
        final double witnessValueSum;
        final boolean allWitnessFullySelected;
        final boolean solutionInteger;
        final boolean stageFeasible;
        final List<String> violations;
        final CoverageStats coverageStats;

        private ModelResult(String modelName,
                            boolean integerModel,
                            boolean solved,
                            double objective,
                            List<ColumnResult> columns,
                            boolean solutionInteger,
                            List<String> violations,
                            CoverageStats coverageStats) {
            this.modelName = modelName;
            this.integerModel = integerModel;
            this.solved = solved;
            this.objective = objective;
            this.columns = new ArrayList<>(columns);
            this.solutionInteger = solutionInteger;
            this.violations = new ArrayList<>(violations);
            this.coverageStats = coverageStats;

            int selected = 0;
            int selectedInitial = 0;
            int selectedWitness = 0;
            int fullWitness = 0;
            int witnessColumns = 0;
            double witnessSum = 0.0;
            for (ColumnResult column : columns) {
                if (column.value > EPSILON) {
                    selected++;
                    if ("initial".equals(column.record.type)) {
                        selectedInitial++;
                    }
                    if ("witness".equals(column.record.type)) {
                        selectedWitness++;
                    }
                }
                if ("witness".equals(column.record.type)) {
                    witnessColumns++;
                    witnessSum += column.value;
                    if (column.value >= 1.0 - EPSILON) {
                        fullWitness++;
                    }
                }
            }
            this.selectedColumns = selected;
            this.selectedInitialRoutes = selectedInitial;
            this.selectedWitnessRoutes = selectedWitness;
            this.fullySelectedWitnessRoutes = fullWitness;
            this.witnessValueSum = witnessSum;
            this.allWitnessFullySelected = witnessColumns > 0 && fullWitness == witnessColumns;
            this.stageFeasible = solutionInteger && violations.isEmpty();
        }

        static ModelResult solved(String modelName,
                                  boolean integerModel,
                                  double objective,
                                  List<ColumnResult> columns,
                                  boolean solutionInteger,
                                  List<String> violations,
                                  CoverageStats coverageStats) {
            return new ModelResult(modelName, integerModel, true, objective, columns,
                    solutionInteger, violations, coverageStats);
        }

        static ModelResult unsolved(String modelName, boolean integerModel, List<ColumnRecord> records) {
            List<ColumnResult> columns = new ArrayList<>();
            for (int i = 0; i < records.size(); i++) {
                columns.add(new ColumnResult(records.get(i), i, 0.0,
                        new Route(0, i, 1), new ArrayList<String>()));
            }
            return new ModelResult(modelName, integerModel, false, Double.POSITIVE_INFINITY, columns,
                    false, new ArrayList<String>(), new CoverageStats(0, 0, 0.0, 0.0));
        }
    }

    static final class ColumnRecord {
        final String type;
        final int routeIndex;

        private ColumnRecord(String type, int routeIndex) {
            this.type = type;
            this.routeIndex = routeIndex;
        }
    }

    static final class ColumnResult {
        final ColumnRecord record;
        final int columnIndex;
        final double value;
        final Route route;
        final List<String> routeViolations;

        private ColumnResult(ColumnRecord record,
                             int columnIndex,
                             double value,
                             Route route,
                             List<String> routeViolations) {
            this.record = record;
            this.columnIndex = columnIndex;
            this.value = value;
            this.route = route == null ? null : route.copy();
            this.routeViolations = new ArrayList<>(routeViolations);
        }
    }

    static final class CoverageStats {
        final int underCoveredCustomers;
        final int overCoveredCustomers;
        final double minCoverage;
        final double maxCoverage;

        private CoverageStats(int underCoveredCustomers,
                              int overCoveredCustomers,
                              double minCoverage,
                              double maxCoverage) {
            this.underCoveredCustomers = underCoveredCustomers;
            this.overCoveredCustomers = overCoveredCustomers;
            this.minCoverage = minCoverage;
            this.maxCoverage = maxCoverage;
        }
    }
}

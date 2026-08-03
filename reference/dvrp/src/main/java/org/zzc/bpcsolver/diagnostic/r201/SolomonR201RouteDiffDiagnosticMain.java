package org.zzc.bpcsolver.diagnostic.r201;

import gnu.trove.list.array.TIntArrayList;
import org.zzc.bpcsolver.algorithm.BPCSolver;
import org.zzc.bpcsolver.algorithm.BPCSolverTemplate;
import org.zzc.bpcsolver.core.branch.StageSolution;
import org.zzc.bpcsolver.core.graph.Route;
import org.zzc.bpcsolver.problem.entity.DynamicInstance;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class SolomonR201RouteDiffDiagnosticMain {

    private static final double EPSILON = 1e-6;

    public static void main(String[] args) {
        int maxAddedColumns = parseOptionalInt(args, 0, 20);
        int maxIterations = parseOptionalInt(args, 1, 0);
        String heuristicPricing = parseOptionalString(args, 2, "multi-stage");
        String cutting = parseOptionalString(args, 3, "lm-src");
        String relaxation = parseOptionalString(args, 4, "ng-route");
        String variableSelection = parseOptionalString(args, 5, "most fractional branching");
        int branchingTimeLimitSeconds = parseOptionalInt(args, 6, 6900);

        DynamicInstance instance = R201DiagnosticSupport.loadR201Instance();
        List<Route> witnessRoutes = R201DiagnosticSupport.buildPublicWitnessRoutes(instance);
        List<String> witnessViolations = R201DiagnosticSupport.stageSolutionViolations(instance, witnessRoutes);

        BPCSolver solver = new BPCSolver();
        solver.setHeuristicPricingRule(heuristicPricing);
        solver.setCuttingRule(cutting);
        solver.setRelaxationRule(relaxation);
        solver.setVariableSelectionRule(variableSelection);
        solver.setBranchingTimeLimitSeconds(branchingTimeLimitSeconds);

        long startNanos = System.nanoTime();
        BPCSolver.SolveResult result = solver.solveNode(instance, maxAddedColumns, maxIterations);
        long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000L;
        StageSolution solution = result.getSolution();
        List<Route> solverRoutes = sortRoutes(solution.getSelectedRoutes());
        List<String> solverViolations = R201DiagnosticSupport.stageSolutionViolations(instance, solverRoutes);
        BPCSolverTemplate.BranchSearchStatistics branchStats = result.getBranchSearchStatistics();

        System.out.println("R201_ROUTE_DIFF"
                + ",maxAddedColumns=" + maxAddedColumns
                + ",maxIterations=" + maxIterations
                + ",heuristicPricing=" + csv(heuristicPricing)
                + ",cutting=" + csv(cutting)
                + ",relaxation=" + csv(relaxation)
                + ",variableSelection=" + csv(variableSelection)
                + ",branchingTimeLimitSeconds=" + branchingTimeLimitSeconds
                + ",elapsedMs=" + elapsedMillis);

        printSummary("SOLVER", result.getStatus().name(), solution.getObjectiveValue(), solverRoutes,
                solverViolations, result.isCertifiedExact(), branchStats);
        printSummary("PUBLIC_WITNESS", "KNOWN_ROUTE_SET", R201DiagnosticSupport.sumCosts(witnessRoutes),
                witnessRoutes, witnessViolations, false, null);

        System.out.println("ROUTE,source,index,cost,load,matchedOtherIndex,customers");
        printRoutes("SOLVER", solverRoutes, witnessRoutes);
        printRoutes("PUBLIC_WITNESS", witnessRoutes, solverRoutes);

        System.out.println("CUSTOMER_DIFF,customer,solverRoute,solverPrev,solverNext,witnessRoute,witnessPrev,witnessNext");
        for (Integer customerId : instance.getRemainingCustomerIds()) {
            CustomerPosition solverPosition = findCustomerPosition(solverRoutes, customerId);
            CustomerPosition witnessPosition = findCustomerPosition(witnessRoutes, customerId);
            if (!solverPosition.sameAs(witnessPosition)) {
                System.out.println("CUSTOMER_DIFF"
                        + "," + customerId
                        + "," + solverPosition.routeIndex
                        + "," + solverPosition.previousNode
                        + "," + solverPosition.nextNode
                        + "," + witnessPosition.routeIndex
                        + "," + witnessPosition.previousNode
                        + "," + witnessPosition.nextNode);
            }
        }
    }

    private static void printSummary(String source,
                                     String status,
                                     double objective,
                                     List<Route> routes,
                                     List<String> violations,
                                     boolean certifiedExact,
                                     BPCSolverTemplate.BranchSearchStatistics branchStats) {
        double routeCostSum = R201DiagnosticSupport.sumCosts(routes);
        System.out.println("SUMMARY"
                + ",source=" + csv(source)
                + ",status=" + csv(status)
                + ",objective=" + format(objective)
                + ",routeCostSum=" + format(routeCostSum)
                + ",benchmarkObjective=" + format(R201DiagnosticSupport.SOLOMON_BPC_BENCHMARK_OBJECTIVE)
                + ",objectiveGap=" + format(objective - R201DiagnosticSupport.SOLOMON_BPC_BENCHMARK_OBJECTIVE)
                + ",routeCount=" + routes.size()
                + ",feasible=" + violations.isEmpty()
                + ",violations=" + csv(String.join("|", violations))
                + ",certifiedExact=" + certifiedExact
                + ",branchTermination=" + csv(branchStats == null ? "" : branchStats.getTerminationReason().name())
                + ",branchSolvedToOptimality=" + (branchStats != null && branchStats.isSolvedToOptimality())
                + ",branchingFailures=" + (branchStats == null ? "" : branchStats.getBranchingFailures()));
    }

    private static void printRoutes(String source, List<Route> routes, List<Route> otherRoutes) {
        for (int i = 0; i < routes.size(); i++) {
            Route route = routes.get(i);
            int matchedOtherIndex = findExactRouteIndex(otherRoutes, route);
            System.out.println("ROUTE"
                    + "," + csv(source)
                    + "," + (i + 1)
                    + "," + format(route.getCost())
                    + "," + format(route.getLoad())
                    + "," + matchedOtherIndex
                    + "," + csv(customersOnly(route)));
        }
    }

    private static int findExactRouteIndex(List<Route> routes, Route target) {
        if (routes == null || target == null || target.getVisitedVertices() == null) {
            return 0;
        }
        for (int i = 0; i < routes.size(); i++) {
            Route route = routes.get(i);
            if (route != null
                    && route.getVisitedVertices() != null
                    && route.getVisitedVertices().equals(target.getVisitedVertices())) {
                return i + 1;
            }
        }
        return 0;
    }

    private static CustomerPosition findCustomerPosition(List<Route> routes, int customerId) {
        for (int routeIndex = 0; routeIndex < routes.size(); routeIndex++) {
            Route route = routes.get(routeIndex);
            if (route == null || route.getVisitedVertices() == null) {
                continue;
            }
            TIntArrayList visited = route.getVisitedVertices();
            for (int position = 1; position < visited.size() - 1; position++) {
                if (visited.get(position) == customerId) {
                    return new CustomerPosition(routeIndex + 1, visited.get(position - 1), visited.get(position + 1));
                }
            }
        }
        return new CustomerPosition(0, -1, -1);
    }

    private static List<Route> sortRoutes(List<Route> routes) {
        List<Route> copies = R201DiagnosticSupport.copyRoutes(routes);
        copies.sort(Comparator
                .comparingInt((Route route) -> route.getVisitedVertices().size() <= 1
                        ? Integer.MAX_VALUE
                        : route.getVisitedVertices().get(1))
                .thenComparingDouble(Route::getCost));
        return copies;
    }

    private static String customersOnly(Route route) {
        if (route == null || route.getVisitedVertices() == null || route.getVisitedVertices().size() <= 2) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        TIntArrayList visited = route.getVisitedVertices();
        for (int i = 1; i < visited.size() - 1; i++) {
            if (i > 1) {
                builder.append(' ');
            }
            builder.append(visited.get(i));
        }
        return builder.toString();
    }

    private static int parseOptionalInt(String[] args, int index, int defaultValue) {
        if (args == null || args.length <= index || args[index] == null || args[index].trim().isEmpty()) {
            return defaultValue;
        }
        return Integer.parseInt(args[index].trim());
    }

    private static String parseOptionalString(String[] args, int index, String defaultValue) {
        if (args == null || args.length <= index || args[index] == null || args[index].trim().isEmpty()) {
            return defaultValue;
        }
        return args[index].trim();
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

    private static final class CustomerPosition {
        private final int routeIndex;
        private final int previousNode;
        private final int nextNode;

        private CustomerPosition(int routeIndex, int previousNode, int nextNode) {
            this.routeIndex = routeIndex;
            this.previousNode = previousNode;
            this.nextNode = nextNode;
        }

        private boolean sameAs(CustomerPosition other) {
            return other != null
                    && this.routeIndex == other.routeIndex
                    && this.previousNode == other.previousNode
                    && this.nextNode == other.nextNode;
        }
    }
}

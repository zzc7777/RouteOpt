package org.zzc.bpcsolver.diagnostic.r201;

import org.zzc.bpcsolver.algorithm.BPCSolver;
import org.zzc.bpcsolver.algorithm.cutting.LmSrc;
import org.zzc.bpcsolver.core.branch.Node;
import org.zzc.bpcsolver.core.branch.StageSolution;
import org.zzc.bpcsolver.core.graph.Graph;
import org.zzc.bpcsolver.core.graph.Route;
import org.zzc.bpcsolver.problem.entity.DynamicInstance;

import java.util.ArrayList;
import java.util.List;

public class SolomonR201WitnessDiagnosticMain {

    public static void main(String[] args) {
        int maxAddedColumns = parseOptionalInt(args, 0, 20);
        int maxIterations = parseOptionalInt(args, 1, 0);
        String heuristicPricing = parseOptionalString(args, 2, "multi-stage");
        String cutting = parseOptionalString(args, 3, "lm-src");
        String relaxation = parseOptionalString(args, 4, "ng-route");
        String variableSelection = parseOptionalString(args, 5, "most fractional branching");
        int branchingTimeLimitSeconds = parseOptionalInt(args, 6, 7200);
        String branchingSwitch = normalizeOptionalSetting(System.getenv("R201_BRANCHING_SWITCH"), "branch-and-bound");
        String branchVariable = normalizeOptionalSetting(System.getenv("R201_BRANCH_VARIABLE"), "qij");

        DynamicInstance instance = R201DiagnosticSupport.loadR201Instance();
        List<Route> witnessRoutes = R201DiagnosticSupport.buildPublicWitnessRoutes(instance);
        BPCSolver solver = new BPCSolver();
        solver.setHeuristicPricingRule(heuristicPricing);
        solver.setCuttingRule(cutting);
        solver.setRelaxationRule(relaxation);
        solver.setBranchingSwitch(branchingSwitch);
        solver.setBranchVariable(branchVariable);
        solver.setVariableSelectionRule(variableSelection);
        solver.setBranchingTimeLimitSeconds(branchingTimeLimitSeconds);

        System.out.println("R201_WITNESS_DIAGNOSTIC_START"
                + ",maxAddedColumns=" + maxAddedColumns
                + ",maxIterations=" + maxIterations
                + ",heuristicPricing=" + heuristicPricing
                + ",cutting=" + cutting
                + ",relaxation=" + relaxation
                + ",branchingSwitch=" + branchingSwitch
                + ",branchVariable=" + branchVariable
                + ",variableSelection=" + variableSelection
                + ",branchingTimeLimitSeconds=" + branchingTimeLimitSeconds);
        System.out.flush();

        long startNanos = System.nanoTime();
        BPCSolver.SolveResult result = solver.solveNode(instance, maxAddedColumns, maxIterations);
        long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000L;

        StageSolution solution = result.getSolution();
        Node<Route, LmSrc, Graph> resultNode = result.getResultNode();
        List<Route> finalPool = flatten(resultNode.getUsedScheduleSet());
        R201DiagnosticSupport.RoutePoolCoverage coverage = R201DiagnosticSupport.inspectRoutePool(finalPool, witnessRoutes);

        System.out.println("R201_WITNESS_DIAGNOSTIC"
                + ",status=" + result.getStatus()
                + ",elapsedMs=" + elapsedMillis
                + ",objective=" + solution.getObjectiveValue()
                + ",routeCount=" + solution.getSelectedRoutes().size()
                + ",certifiedExact=" + result.isCertifiedExact()
                + ",branchTermination=" + result.getBranchSearchStatistics().getTerminationReason()
                + ",branchSolvedToOptimality=" + result.getBranchSearchStatistics().isSolvedToOptimality()
                + ",nodesExplored=" + result.getBranchSearchStatistics().getNodesExplored()
                + ",nodesProcessed=" + result.getBranchSearchStatistics().getNodesProcessed()
                + ",childrenSolved=" + result.getBranchSearchStatistics().getChildrenSolved()
                + ",nodesPrunedByIncumbent=" + result.getBranchSearchStatistics().getNodesPrunedByIncumbent()
                + ",infeasibleChildren=" + result.getBranchSearchStatistics().getInfeasibleChildren()
                + ",branchingFailures=" + result.getBranchSearchStatistics().getBranchingFailures()
                + ",branchingFailuresNoFractionalVariables=" + result.getBranchSearchStatistics().getBranchingFailuresNoFractionalVariables()
                + ",branchingFailuresNoSelectedVariable=" + result.getBranchSearchStatistics().getBranchingFailuresNoSelectedVariable()
                + ",branchingFailuresCgNotClosed=" + result.getBranchSearchStatistics().getBranchingFailuresCgNotClosed()
                + ",openNodesAtEnd=" + result.getBranchSearchStatistics().getOpenNodesAtEnd()
                + ",pricingTimeLimitReached=" + result.getRunStatistics().isPricingTimeLimitReached()
                + ",integerFlowRepairAttempts=" + result.getRunStatistics().getIntegerFlowRepairAttempts()
                + ",integerFlowRepairSuccesses=" + result.getRunStatistics().getIntegerFlowRepairSuccesses()
                + ",integerFlowRepairLastFailureReason=" + result.getRunStatistics().getIntegerFlowRepairLastFailureReason()
                + ",integerFlowRepairFailuresInvalidInput=" + result.getRunStatistics().getIntegerFlowRepairFailuresInvalidInput()
                + ",integerFlowRepairFailuresNoArcFlow=" + result.getRunStatistics().getIntegerFlowRepairFailuresNoArcFlow()
                + ",integerFlowRepairFailuresNoFractionalRoute=" + result.getRunStatistics().getIntegerFlowRepairFailuresNoFractionalRoute()
                + ",integerFlowRepairFailuresArcFlowNotIntegral=" + result.getRunStatistics().getIntegerFlowRepairFailuresArcFlowNotIntegral()
                + ",integerFlowRepairFailuresCustomerDegree=" + result.getRunStatistics().getIntegerFlowRepairFailuresCustomerDegree()
                + ",integerFlowRepairFailuresDecomposition=" + result.getRunStatistics().getIntegerFlowRepairFailuresDecomposition()
                + ",integerFlowRepairFailuresStageInfeasible=" + result.getRunStatistics().getIntegerFlowRepairFailuresStageInfeasible()
                + ",integerFlowRepairFailuresBranchInfeasible=" + result.getRunStatistics().getIntegerFlowRepairFailuresBranchInfeasible()
                + ",integerFlowRepairFailuresObjectiveMismatch=" + result.getRunStatistics().getIntegerFlowRepairFailuresObjectiveMismatch()
                + ",witnessObjective=" + R201DiagnosticSupport.PUBLIC_WITNESS_JAVA_FLOOR_OBJECTIVE
                + ",witnessTotal=" + witnessRoutes.size()
                + ",witnessMatched=" + coverage.matchedRoutes
                + ",witnessMissing=" + coverage.missingRoutes.size()
                + ",poolSize=" + finalPool.size()
                + ",missingRouteIndexes=" + coverage.missingRouteIndexes);
    }

    private static List<Route> flatten(List<List<Route>> scheduleSet) {
        List<Route> routes = new ArrayList<>();
        if (scheduleSet == null) {
            return routes;
        }
        for (List<Route> graphRoutes : scheduleSet) {
            if (graphRoutes != null) {
                routes.addAll(graphRoutes);
            }
        }
        return routes;
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

    private static String normalizeOptionalSetting(String value, String defaultValue) {
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return value.trim();
    }

}

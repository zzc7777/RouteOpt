package org.zzc.bpcsolver.algorithm.acceleration;

import org.zzc.bpcsolver.core.branch.Solution;
import org.zzc.bpcsolver.core.branch.StageSolution;
import org.zzc.bpcsolver.core.graph.Graph;
import org.zzc.bpcsolver.core.graph.Route;
import org.zzc.bpcsolver.model.DVRPMasterProblem;
import org.zzc.bpcsolver.problem.entity.DynamicInstance;

import java.util.ArrayList;
import java.util.List;

public class DVRPColumnPoolPrimalHeuristic implements PrimalHeuristic<DynamicInstance, Route, Graph> {

    @Override
    public Solution<Route> obtainInitialSolution(DynamicInstance instance, List<Graph> graphs) {
        return new Solution<>(instance.getNumMachines(), instance.getNumJobs());
    }

    @Override
    public Solution<Route> obtainFeasibleSolution(DynamicInstance instance,
                                                  List<Graph> graphs,
                                                  List<List<Route>> schedules,
                                                  List<double[]> ysk) {
        if (instance == null) {
            Solution<Route> infeasibleSolution = new Solution<>(0, 0);
            infeasibleSolution.setObjValue(Double.POSITIVE_INFINITY);
            return infeasibleSolution;
        }
        Solution<Route> infeasibleSolution = new Solution<>(instance.getNumMachines(), instance.getNumJobs());
        infeasibleSolution.setObjValue(Double.POSITIVE_INFINITY);
        if (graphs == null || schedules == null) {
            return infeasibleSolution;
        }

        List<Route> columnPool = filterExecutableRouteColumns(instance, flattenScheduleSet(schedules));
        if (columnPool.isEmpty()) {
            return infeasibleSolution;
        }

        try (DVRPMasterProblem integerMasterProblem = new DVRPMasterProblem()) {
            integerMasterProblem.initialize(instance, columnPool, true);
            integerMasterProblem.build();
            if (!integerMasterProblem.solve()) {
                return infeasibleSolution;
            }

            StageSolution stageSolution = new StageSolution();
            integerMasterProblem.updateSolution(stageSolution);

            Solution<Route> solution = new Solution<>(graphs.size(), instance.getNumJobs());
            solution.setObjValue(stageSolution.getObjectiveValue());
            solution.setBestSchedules(stageSolution.getSelectedRoutes());
            solution.setYks(buildYksByGraph(graphs, integerMasterProblem.getRoutes()));
            solution.setDualsOfCuts(new ArrayList<>());
            solution.setDualsOfBranches(new ArrayList<>());
            return solution;
        }
    }

    @Override
    public String getStrategyName() {
        return "dvrp column-pool integer master";
    }

    private List<Route> flattenScheduleSet(List<List<Route>> schedules) {
        List<Route> routes = new ArrayList<>();
        for (List<Route> graphRoutes : schedules) {
            if (graphRoutes == null) {
                continue;
            }
            for (Route route : graphRoutes) {
                if (route != null) {
                    routes.add(route.copy());
                }
            }
        }
        return routes;
    }

    private List<Route> filterExecutableRouteColumns(DynamicInstance instance, List<Route> routes) {
        List<Route> executableRoutes = new ArrayList<>();
        for (Route route : routes) {
            if (route != null && !hasRepeatedRemainingCustomer(instance, route)) {
                executableRoutes.add(route.copy());
            }
        }
        return executableRoutes;
    }

    private boolean hasRepeatedRemainingCustomer(DynamicInstance instance, Route route) {
        if (route == null || route.getVisitedVertices() == null) {
            return false;
        }
        java.util.Set<Integer> remainingCustomers = new java.util.HashSet<>(instance.getRemainingCustomerIds());
        java.util.Set<Integer> seenCustomers = new java.util.HashSet<>();
        for (int nodeId : route.getVisitedVertices().toArray()) {
            if (!remainingCustomers.contains(nodeId)) {
                continue;
            }
            if (!seenCustomers.add(nodeId)) {
                return true;
            }
        }
        return false;
    }

    private List<double[]> buildYksByGraph(List<Graph> graphs, List<Route> routes) {
        List<double[]> yks = new ArrayList<>();
        for (Graph graph : graphs) {
            List<Route> graphRoutes = new ArrayList<>();
            for (Route route : routes) {
                if (route != null && route.getStartDepotId() == graph.getStartDepotId()) {
                    graphRoutes.add(route);
                }
            }
            double[] yk = new double[graphRoutes.size()];
            for (int i = 0; i < graphRoutes.size(); i++) {
                yk[i] = graphRoutes.get(i).getValue();
            }
            yks.add(yk);
        }
        return yks;
    }
}

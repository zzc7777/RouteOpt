package org.zzc.bpcsolver.core.graph;

import gnu.trove.list.array.TIntArrayList;
import org.zzc.bpcsolver.core.branch.StageSolution;
import org.zzc.bpcsolver.problem.entity.Depot;
import org.zzc.bpcsolver.problem.entity.DynamicInstance;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RouteFeasibilityValidator {

    private static final double DEFAULT_EPSILON = 1e-6;

    public enum StartTimePolicy {
        ROUTE_CLOCK,
        START_DEPOT_EARLIEST
    }

    private final double epsilon;
    private final StartTimePolicy startTimePolicy;

    public RouteFeasibilityValidator() {
        this(DEFAULT_EPSILON, StartTimePolicy.ROUTE_CLOCK);
    }

    public RouteFeasibilityValidator(double epsilon) {
        this(epsilon, StartTimePolicy.ROUTE_CLOCK);
    }

    public RouteFeasibilityValidator(double epsilon, StartTimePolicy startTimePolicy) {
        if (epsilon < 0.0) {
            throw new IllegalArgumentException("epsilon must be non-negative.");
        }
        if (startTimePolicy == null) {
            throw new IllegalArgumentException("startTimePolicy must not be null.");
        }
        this.epsilon = epsilon;
        this.startTimePolicy = startTimePolicy;
    }

    public boolean isRouteFeasible(DynamicInstance instance, Route route) {
        return findRouteViolations(instance, route).isEmpty();
    }

    public List<String> findRouteViolations(DynamicInstance instance, Route route) {
        validateInputs(instance);

        List<String> violations = new ArrayList<>();
        if (route == null) {
            violations.add("route is null");
            return violations;
        }

        TIntArrayList vertices = route.getVisitedVertices();
        if (vertices == null || vertices.size() < 2) {
            violations.add("route has fewer than two vertices: " + route);
            return violations;
        }

        validateRouteShape(instance, route, vertices, violations);
        validateRouteLoad(instance, route, vertices, violations);
        validateRouteTimes(instance, route, vertices, violations);
        return violations;
    }

    public List<String> findStageSolutionViolations(DynamicInstance instance, StageSolution solution) {
        validateInputs(instance);
        if (solution == null) {
            throw new IllegalArgumentException("solution must not be null.");
        }

        List<String> violations = new ArrayList<>();
        Map<Integer, Integer> customerCoverage = initializeCustomerCoverage(instance);
        Set<Integer> remainingCustomerIds = new HashSet<>(instance.getRemainingCustomerIds());
        Map<Integer, Integer> externalDepotStarts = initializeExternalDepotStarts(instance);

        for (Route route : solution.getSelectedRoutes()) {
            if (route == null) {
                violations.add("selected route is null");
                continue;
            }

            violations.addAll(findRouteViolations(instance, route));
            updateStageCoverage(instance, route, remainingCustomerIds, customerCoverage, externalDepotStarts, violations);
        }

        for (Integer customerId : instance.getRemainingCustomerIds()) {
            int count = customerCoverage.get(customerId);
            if (count != 1) {
                violations.add("customer " + customerId + " covered " + count + " times");
            }
        }

        for (Depot externalDepot : instance.getExternalDepots()) {
            int count = externalDepotStarts.get(externalDepot.getId());
            if (count != externalDepot.getVehicleCount()) {
                violations.add("external depot " + externalDepot.getId() + " starts " + count
                        + " routes, expected " + externalDepot.getVehicleCount());
            }
        }

        return violations;
    }

    private void validateInputs(DynamicInstance instance) {
        if (instance == null) {
            throw new IllegalArgumentException("instance must not be null.");
        }
        instance.validate();
    }

    private void validateRouteShape(DynamicInstance instance,
                                    Route route,
                                    TIntArrayList vertices,
                                    List<String> violations) {
        if (vertices.get(0) != route.getStartDepotId()) {
            violations.add("route first vertex does not match start depot: " + route);
        }
        if (vertices.get(vertices.size() - 1) != instance.getReturnDepot().getId()) {
            violations.add("route does not end at return depot: " + route);
        }
        if (instance.getStartDepotById(route.getStartDepotId()) == null) {
            violations.add("route has unknown start depot: " + route.getStartDepotId());
        }

        for (int i = 1; i < vertices.size() - 1; i++) {
            int nodeId = vertices.get(i);
            if (!instance.getRemainingCustomerIds().contains(nodeId)) {
                violations.add("route visits non-remaining customer or depot in the middle: " + nodeId);
            }
        }
    }

    private void validateRouteLoad(DynamicInstance instance,
                                   Route route,
                                   TIntArrayList vertices,
                                   List<String> violations) {
        double load = 0.0;
        for (int i = 1; i < vertices.size() - 1; i++) {
            int nodeId = vertices.get(i);
            if (instance.isCustomerNode(nodeId)) {
                load += instance.getDemand(nodeId);
            }
        }

        Double capacity = getCapacity(instance, route.getStartDepotId(), violations);
        if (capacity == null) {
            return;
        }
        if (load > capacity + epsilon) {
            violations.add("route load " + load + " exceeds capacity " + capacity + ": " + route);
        }
    }

    private void validateRouteTimes(DynamicInstance instance,
                                    Route route,
                                    TIntArrayList vertices,
                                    List<String> violations) {
        List<Double> arrivalTimes = route.getArrivalTimes();
        if (arrivalTimes == null || arrivalTimes.size() != vertices.size()) {
            violations.add("route arrival time vector size mismatch: " + route);
            return;
        }

        double expectedStartTime = resolveExpectedStartTime(instance, route.getStartDepotId());
        if (Math.abs(arrivalTimes.get(0) - expectedStartTime) > epsilon) {
            violations.add("route start time " + arrivalTimes.get(0)
                    + " does not match expected start time " + expectedStartTime);
        }
        if (arrivalTimes.get(0) > instance.getLatestTime(route.getStartDepotId()) + epsilon) {
            violations.add("route starts after latest time at start depot " + route.getStartDepotId());
        }

        for (int i = 1; i < vertices.size(); i++) {
            int previousNodeId = vertices.get(i - 1);
            int nodeId = vertices.get(i);
            double earliestPossible = arrivalTimes.get(i - 1)
                    + instance.getServiceTime(previousNodeId)
                    + instance.getTravelTime(previousNodeId, nodeId);
            double expectedStartServiceTime = Math.max(earliestPossible, instance.getEarliestTime(nodeId));

            if (arrivalTimes.get(i) + epsilon < earliestPossible) {
                violations.add("route violates travel-time propagation on arc "
                        + previousNodeId + " -> " + nodeId);
            }
            if (Math.abs(arrivalTimes.get(i) - expectedStartServiceTime) > epsilon) {
                violations.add("route start-service time at node " + nodeId
                        + " is " + arrivalTimes.get(i)
                        + ", expected " + expectedStartServiceTime);
            }
            if (arrivalTimes.get(i) > instance.getLatestTime(nodeId) + epsilon) {
                violations.add("route arrives after latest time at node " + nodeId);
            }
        }
    }

    private void updateStageCoverage(DynamicInstance instance,
                                     Route route,
                                     Set<Integer> remainingCustomerIds,
                                     Map<Integer, Integer> customerCoverage,
                                     Map<Integer, Integer> externalDepotStarts,
                                     List<String> violations) {
        TIntArrayList vertices = route.getVisitedVertices();
        if (vertices == null || vertices.isEmpty()) {
            return;
        }

        if (externalDepotStarts.containsKey(route.getStartDepotId())) {
            externalDepotStarts.put(route.getStartDepotId(), externalDepotStarts.get(route.getStartDepotId()) + 1);
        }

        for (int i = 1; i < vertices.size() - 1; i++) {
            int nodeId = vertices.get(i);
            if (!remainingCustomerIds.contains(nodeId)) {
                continue;
            }
            customerCoverage.put(nodeId, customerCoverage.get(nodeId) + 1);
        }
    }

    private Map<Integer, Integer> initializeCustomerCoverage(DynamicInstance instance) {
        Map<Integer, Integer> customerCoverage = new HashMap<>();
        for (Integer customerId : instance.getRemainingCustomerIds()) {
            customerCoverage.put(customerId, 0);
        }
        return customerCoverage;
    }

    private Map<Integer, Integer> initializeExternalDepotStarts(DynamicInstance instance) {
        Map<Integer, Integer> externalDepotStarts = new HashMap<>();
        for (Depot externalDepot : instance.getExternalDepots()) {
            externalDepotStarts.put(externalDepot.getId(), 0);
        }
        return externalDepotStarts;
    }

    private double resolveExpectedStartTime(DynamicInstance instance, int startDepotId) {
        if (startTimePolicy == StartTimePolicy.START_DEPOT_EARLIEST) {
            return instance.getEarliestTime(startDepotId);
        }
        return instance.getRouteStartTime();
    }

    private Double getCapacity(DynamicInstance instance, int startDepotId, List<String> violations) {
        if (startDepotId == instance.getOriginDepot().getId()) {
            return (double) instance.getProblemData().getVehicleCapacity();
        }
        Depot externalDepot = instance.getExternalDepotById(startDepotId);
        if (externalDepot != null) {
            return (double) externalDepot.getRemainingCapacity();
        }
        violations.add("route has unknown start depot: " + startDepotId);
        return null;
    }
}

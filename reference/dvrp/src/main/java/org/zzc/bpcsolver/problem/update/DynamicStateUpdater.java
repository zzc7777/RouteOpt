package org.zzc.bpcsolver.problem.update;

import gnu.trove.list.array.TIntArrayList;
import org.zzc.bpcsolver.core.branch.StageSolution;
import org.zzc.bpcsolver.core.graph.Route;
import org.zzc.bpcsolver.problem.entity.Customer;
import org.zzc.bpcsolver.problem.entity.Depot;
import org.zzc.bpcsolver.problem.entity.DynamicInstance;
import org.zzc.bpcsolver.problem.entity.ProblemData;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class DynamicStateUpdater {

    public DynamicInstance createInitialInstance(ProblemData problemData, double currentTime) {
        problemData.buildStaticData();

        DynamicInstance dynamicInstance = new DynamicInstance();
        dynamicInstance.setProblemData(problemData);
        dynamicInstance.setCurrentTime(currentTime);
        dynamicInstance.setRouteStartTime(currentTime);
        dynamicInstance.setOriginDepot(problemData.getOriginDepot().copy());
        dynamicInstance.getOriginDepot().setEarliestTime(currentTime);
        dynamicInstance.getOriginDepot().setVehicleCount(problemData.getInitialVehicleCount());
        dynamicInstance.getOriginDepot().setRemainingCapacity(problemData.getVehicleCapacity());
        dynamicInstance.setReturnDepot(problemData.getReturnDepot().copy());
        dynamicInstance.getReturnDepot().setEarliestTime(currentTime);
        dynamicInstance.setRemainingCustomerIds(getAvailableCustomerIds(problemData, currentTime));
        dynamicInstance.setServedCustomerIds(new ArrayList<>());
        dynamicInstance.setExternalDepots(new ArrayList<>());
        dynamicInstance.validate();
        return dynamicInstance;
    }

    public DynamicInstance update(DynamicInstance previousInstance, StageSolution previousSolution, double currentTime) {
        if (previousInstance == null) {
            throw new IllegalArgumentException("previousInstance must not be null.");
        }
        if (previousSolution == null) {
            throw new IllegalArgumentException("previousSolution must not be null.");
        }

        previousInstance.validate();
        ProblemData problemData = previousInstance.getProblemData();
        Set<Integer> servedCustomerIds = new LinkedHashSet<>(previousInstance.getServedCustomerIds());
        servedCustomerIds.addAll(getUnavailableCustomerIdsForReoptimization(problemData, previousSolution, currentTime));

        DynamicInstance nextInstance = new DynamicInstance();
        nextInstance.setProblemData(problemData);
        nextInstance.setCurrentTime(currentTime);
        nextInstance.setRouteStartTime(previousInstance.getRouteStartTime());
        nextInstance.setOriginDepot(buildUpdatedOriginDepot(previousInstance, previousSolution, currentTime));
        nextInstance.setReturnDepot(problemData.getReturnDepot().copy());
        nextInstance.getReturnDepot().setEarliestTime(currentTime);
        nextInstance.setRemainingCustomerIds(buildRemainingCustomerIds(problemData, currentTime, servedCustomerIds));
        nextInstance.setServedCustomerIds(new ArrayList<>(servedCustomerIds));
        nextInstance.setExternalDepots(buildExternalDepots(previousInstance, previousSolution, currentTime));
        nextInstance.validate();
        return nextInstance;
    }

    public double calculateActualDistance(DynamicInstance stageInstance, StageSolution stageSolution, double endTime) {
        if (stageInstance == null) {
            throw new IllegalArgumentException("stageInstance must not be null.");
        }
        if (stageSolution == null) {
            throw new IllegalArgumentException("stageSolution must not be null.");
        }
        stageInstance.validate();

        double actualDistance = 0.0;
        for (Route route : stageSolution.getSelectedRoutes()) {
            if (route == null) {
                continue;
            }
            validateRouteTimeline(route);
            List<Double> arrivalTimes = route.getArrivalTimes();
            if (arrivalTimes.get(arrivalTimes.size() - 1) <= endTime) {
                actualDistance += route.getCost();
                continue;
            }
            TIntArrayList visitedVertices = route.getVisitedVertices();
            for (int i = 1; i < visitedVertices.size(); i++) {
                if (arrivalTimes.get(i) > endTime) {
                    break;
                }
                actualDistance += stageInstance.getDistance(visitedVertices.get(i - 1), visitedVertices.get(i));
            }
        }
        return actualDistance;
    }

    private List<Integer> getAvailableCustomerIds(ProblemData problemData, double currentTime) {
        List<Integer> availableCustomerIds = new ArrayList<>();
        for (Customer customer : problemData.getCustomers()) {
            if (customer.getCallInTime() <= currentTime) {
                availableCustomerIds.add(customer.getId());
            }
        }
        return availableCustomerIds;
    }

    private Set<Integer> getUnavailableCustomerIdsForReoptimization(ProblemData problemData, StageSolution solution, double currentTime) {
        Set<Integer> servedCustomerIds = new LinkedHashSet<>();
        for (Route route : solution.getSelectedRoutes()) {
            if (route == null) {
                continue;
            }
            validateRouteTimeline(route);
            TIntArrayList visitedVertices = route.getVisitedVertices();
            List<Double> arrivalTimes = route.getArrivalTimes();
            for (int i = 0; i < visitedVertices.size(); i++) {
                int nodeId = visitedVertices.get(i);
                if (!isCustomerNode(problemData, nodeId)) {
                    continue;
                }
                Customer customer = problemData.getCustomers().get(nodeId - 1);
                double completionTime = arrivalTimes.get(i) + customer.getServiceTime();
                if (completionTime <= currentTime || getLatestOriginDepartureTime(problemData, customer) <= currentTime) {
                    servedCustomerIds.add(nodeId);
                }
            }
        }
        return servedCustomerIds;
    }

    private List<Integer> buildRemainingCustomerIds(ProblemData problemData, double currentTime, Set<Integer> servedCustomerIds) {
        List<Integer> remainingCustomerIds = new ArrayList<>();
        for (Customer customer : problemData.getCustomers()) {
            if (customer.getCallInTime() <= currentTime && !servedCustomerIds.contains(customer.getId())) {
                remainingCustomerIds.add(customer.getId());
            }
        }
        return remainingCustomerIds;
    }

    private Depot buildUpdatedOriginDepot(DynamicInstance previousInstance, StageSolution previousSolution, double currentTime) {
        Depot updatedOriginDepot = previousInstance.getOriginDepot().copy();
        updatedOriginDepot.setEarliestTime(currentTime);
        int availableVehicles = previousInstance.getOriginDepot().getVehicleCount();

        for (Route route : previousSolution.getSelectedRoutes()) {
            if (route == null || route.getStartDepotId() != previousInstance.getOriginDepot().getId()) {
                continue;
            }
            validateRouteTimeline(route);
            double routeStartTime = route.getArrivalTimes().get(0);
            double routeReturnTime = route.getArrivalTimes().get(route.getArrivalTimes().size() - 1);
            if (currentTime >= routeStartTime) {
                availableVehicles--;
            }
            if (currentTime >= routeReturnTime) {
                availableVehicles++;
            }
        }

        updatedOriginDepot.setVehicleCount(Math.max(availableVehicles, 0));
        updatedOriginDepot.setRemainingCapacity(previousInstance.getProblemData().getVehicleCapacity());
        return updatedOriginDepot;
    }

    private List<Depot> buildExternalDepots(DynamicInstance previousInstance, StageSolution previousSolution, double currentTime) {
        List<Depot> externalDepots = new ArrayList<>();
        int nextExternalDepotId = resolveNextExternalDepotId(previousInstance);

        for (Route route : previousSolution.getSelectedRoutes()) {
            if (route == null || !hasRouteStarted(route, currentTime)) {
                continue;
            }

            Depot externalDepot = buildExternalDepotForRoute(previousInstance, route, currentTime, nextExternalDepotId);
            if (externalDepot != null) {
                externalDepots.add(externalDepot);
                nextExternalDepotId++;
            }
        }
        return externalDepots;
    }

    private int resolveNextExternalDepotId(DynamicInstance previousInstance) {
        int nextExternalDepotId = previousInstance.getProblemData().getStaticNodeCount();
        for (Depot externalDepot : previousInstance.getExternalDepots()) {
            if (externalDepot != null) {
                nextExternalDepotId = Math.max(nextExternalDepotId, externalDepot.getId() + 1);
            }
        }
        return nextExternalDepotId;
    }

    private Depot buildExternalDepotForRoute(DynamicInstance previousInstance, Route route, double currentTime, int externalDepotId) {
        validateRouteTimeline(route);

        TIntArrayList visitedVertices = route.getVisitedVertices();
        List<Double> arrivalTimes = route.getArrivalTimes();
        int lastIndex = visitedVertices.size() - 1;
        double routeReturnTime = arrivalTimes.get(lastIndex);
        if (currentTime >= routeReturnTime) {
            return null;
        }

        for (int i = 0; i < lastIndex; i++) {
            int currentNodeId = visitedVertices.get(i);
            int nextNodeId = visitedVertices.get(i + 1);
            double serviceStartTime = arrivalTimes.get(i);
            double departureTime = serviceStartTime + getServiceTime(previousInstance.getProblemData(), currentNodeId);
            double nextArrivalTime = arrivalTimes.get(i + 1);

            if (currentTime < departureTime) {
                return createExternalDepotAtNode(previousInstance, route, currentTime, externalDepotId, currentNodeId, departureTime);
            }
            if (currentTime < nextArrivalTime) {
                return createExternalDepotOnArc(previousInstance, route, currentTime, externalDepotId, currentNodeId, nextNodeId, departureTime);
            }
        }
        return null;
    }

    private Depot createExternalDepotAtNode(DynamicInstance previousInstance, Route route, double currentTime,
                                            int externalDepotId, int nodeId, double earliestTime) {
        Depot externalDepot = new Depot();
        externalDepot.setId(externalDepotId);
        externalDepot.setX(getXById(previousInstance, nodeId));
        externalDepot.setY(getYById(previousInstance, nodeId));
        externalDepot.setEarliestTime(earliestTime);
        externalDepot.setLatestTime(previousInstance.getReturnDepot().getLatestTime());
        externalDepot.setType(Depot.DepotType.EXTERNAL);
        externalDepot.setVehicleCount(1);
        externalDepot.setRemainingCapacity(calculateRemainingCapacity(previousInstance, route, currentTime));
        return externalDepot;
    }

    private Depot createExternalDepotOnArc(DynamicInstance previousInstance, Route route, double currentTime, int externalDepotId,
                                           int fromNodeId, int toNodeId, double departureTime) {
        double ratio = (currentTime - departureTime) / previousInstance.getTravelTime(fromNodeId, toNodeId);
        double fromX = getXById(previousInstance, fromNodeId);
        double fromY = getYById(previousInstance, fromNodeId);
        double toX = getXById(previousInstance, toNodeId);
        double toY = getYById(previousInstance, toNodeId);

        Depot externalDepot = new Depot();
        externalDepot.setId(externalDepotId);
        externalDepot.setX(fromX + ratio * (toX - fromX));
        externalDepot.setY(fromY + ratio * (toY - fromY));
        externalDepot.setEarliestTime(currentTime);
        externalDepot.setLatestTime(previousInstance.getReturnDepot().getLatestTime());
        externalDepot.setType(Depot.DepotType.EXTERNAL);
        externalDepot.setVehicleCount(1);
        externalDepot.setRemainingCapacity(calculateRemainingCapacity(previousInstance, route, currentTime));
        return externalDepot;
    }

    private int calculateRemainingCapacity(DynamicInstance previousInstance, Route route, double currentTime) {
        return getInitialRemainingCapacity(previousInstance, route.getStartDepotId())
                - calculateConsumedLoadOnCurrentRoute(previousInstance, route, currentTime);
    }

    private int calculateConsumedLoadOnCurrentRoute(DynamicInstance previousInstance, Route route, double currentTime) {
        validateRouteTimeline(route);
        TIntArrayList visitedVertices = route.getVisitedVertices();
        List<Double> arrivalTimes = route.getArrivalTimes();
        int consumedLoad = 0;
        int lastIndex = visitedVertices.size() - 1;

        for (int i = 0; i < lastIndex; i++) {
            int currentNodeId = visitedVertices.get(i);
            if (isCustomerNode(previousInstance.getProblemData(), currentNodeId)) {
                consumedLoad += previousInstance.getProblemData().getCustomers().get(currentNodeId - 1).getDemand();
            }
            if (arrivalTimes.get(i) <= currentTime && currentTime < arrivalTimes.get(i + 1)) {
                return consumedLoad;
            }
        }
        return consumedLoad;
    }

    private int getInitialRemainingCapacity(DynamicInstance previousInstance, int startDepotId) {
        if (startDepotId == previousInstance.getOriginDepot().getId()) {
            return previousInstance.getProblemData().getVehicleCapacity();
        }
        Depot externalDepot = previousInstance.getExternalDepotById(startDepotId);
        if (externalDepot != null) {
            return externalDepot.getRemainingCapacity();
        }
        throw new IllegalArgumentException("Unknown start depot id: " + startDepotId);
    }

    private boolean hasRouteStarted(Route route, double currentTime) {
        validateRouteTimeline(route);
        return currentTime >= route.getArrivalTimes().get(0);
    }

    private void validateRouteTimeline(Route route) {
        if (route.getVisitedVertices() == null || route.getArrivalTimes() == null) {
            throw new IllegalArgumentException("Route vertices and arrivalTimes must not be null.");
        }
        if (route.getVisitedVertices().isEmpty()) {
            throw new IllegalArgumentException("Route must contain at least one visited vertex.");
        }
        if (route.getVisitedVertices().size() != route.getArrivalTimes().size()) {
            throw new IllegalArgumentException("Route visitedVertices and arrivalTimes must have the same size.");
        }
    }

    private boolean isCustomerNode(ProblemData problemData, int nodeId) {
        return nodeId >= 1 && nodeId <= problemData.getCustomers().size();
    }

    private double getServiceTime(ProblemData problemData, int nodeId) {
        if (isCustomerNode(problemData, nodeId)) {
            return problemData.getCustomers().get(nodeId - 1).getServiceTime();
        }
        return 0.0;
    }

    private double getLatestOriginDepartureTime(ProblemData problemData, Customer customer) {
        return customer.getLatestTime() - problemData.getStaticTravelTime(
                problemData.getOriginDepot().getId(),
                customer.getId()
        );
    }

    private double getXById(DynamicInstance instance, int nodeId) {
        if (nodeId == instance.getOriginDepot().getId()) {
            return instance.getOriginDepot().getX();
        }
        if (nodeId == instance.getReturnDepot().getId()) {
            return instance.getReturnDepot().getX();
        }
        if (isCustomerNode(instance.getProblemData(), nodeId)) {
            return instance.getProblemData().getCustomers().get(nodeId - 1).getX();
        }
        Depot externalDepot = instance.getExternalDepotById(nodeId);
        if (externalDepot != null) {
            return externalDepot.getX();
        }
        throw new IllegalArgumentException("Unknown node id: " + nodeId);
    }

    private double getYById(DynamicInstance instance, int nodeId) {
        if (nodeId == instance.getOriginDepot().getId()) {
            return instance.getOriginDepot().getY();
        }
        if (nodeId == instance.getReturnDepot().getId()) {
            return instance.getReturnDepot().getY();
        }
        if (isCustomerNode(instance.getProblemData(), nodeId)) {
            return instance.getProblemData().getCustomers().get(nodeId - 1).getY();
        }
        Depot externalDepot = instance.getExternalDepotById(nodeId);
        if (externalDepot != null) {
            return externalDepot.getY();
        }
        throw new IllegalArgumentException("Unknown node id: " + nodeId);
    }

}

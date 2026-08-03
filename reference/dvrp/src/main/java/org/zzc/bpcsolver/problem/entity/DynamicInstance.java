package org.zzc.bpcsolver.problem.entity;

import lombok.Getter;
import lombok.Setter;
import org.zzc.bpcsolver.problem.metric.DistanceCalculator;
import org.zzc.bpcsolver.problem.template.DynamicInstanceTemplate;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class DynamicInstance extends DynamicInstanceTemplate {

    private ProblemData problemData;
    private double currentTime;
    // currentTime is the decision time; routeStartTime is the route/label clock.
    private double routeStartTime;
    private Depot originDepot;
    private Depot returnDepot;
    private List<Integer> remainingCustomerIds;
    private List<Integer> servedCustomerIds;
    private List<Depot> externalDepots;

    public DynamicInstance() {
        this.remainingCustomerIds = new ArrayList<>();
        this.servedCustomerIds = new ArrayList<>();
        this.externalDepots = new ArrayList<>();
    }

    public DynamicInstance(DynamicInstance other) {
        super(other);
        this.problemData = other.problemData == null ? null : other.problemData.copy();
        this.currentTime = other.currentTime;
        this.routeStartTime = other.routeStartTime;
        this.originDepot = other.originDepot == null ? null : other.originDepot.copy();
        this.returnDepot = other.returnDepot == null ? null : other.returnDepot.copy();
        this.remainingCustomerIds = new ArrayList<>(other.remainingCustomerIds);
        this.servedCustomerIds = new ArrayList<>(other.servedCustomerIds);
        this.externalDepots = new ArrayList<>();
        for (Depot depot : other.externalDepots) {
            this.externalDepots.add(depot == null ? null : depot.copy());
        }
    }

    public DynamicInstance copy() {
        return new DynamicInstance(this);
    }

    public void setCurrentTime(double currentTime) {
        this.currentTime = currentTime;
        this.routeStartTime = currentTime;
    }

    public void setRouteStartTime(double routeStartTime) {
        this.routeStartTime = routeStartTime;
    }

    @Override
    public int getNumMachines() {
        return 1 + externalDepots.size();
    }

    @Override
    public int getNumJobs() {
        return remainingCustomerIds.size();
    }

    public void validate() {
        if (problemData == null) {
            throw new IllegalArgumentException("problemData must not be null.");
        }
        if (originDepot == null) {
            throw new IllegalArgumentException("originDepot must not be null.");
        }
        if (returnDepot == null) {
            throw new IllegalArgumentException("returnDepot must not be null.");
        }
        if (remainingCustomerIds == null) {
            throw new IllegalArgumentException("remainingCustomerIds must not be null.");
        }
        if (servedCustomerIds == null) {
            throw new IllegalArgumentException("servedCustomerIds must not be null.");
        }
        if (externalDepots == null) {
            throw new IllegalArgumentException("externalDepots must not be null.");
        }
    }

    public double getDistance(int fromId, int toId) {
        validate();
        if (isStaticNodeId(fromId) && isStaticNodeId(toId)) {
            return problemData.getStaticDistance(fromId, toId);
        }
        DistanceCalculator distanceCalculator = new DistanceCalculator();
        return distanceCalculator.calculateDistance(
                getXById(fromId),
                getYById(fromId),
                getXById(toId),
                getYById(toId),
                problemData.getDistanceScale(),
                problemData.getDistanceDecimalPlaces(),
                problemData.getDistanceRoundingMode()
        );
    }

    public double getTravelTime(int fromId, int toId) {
        validate();
        DistanceCalculator distanceCalculator = new DistanceCalculator();
        return distanceCalculator.calculateTravelTime(
                getDistance(fromId, toId),
                problemData.getSpeed(),
                problemData.getTravelTimeDecimalPlaces(),
                problemData.getTravelTimeRoundingMode()
        );
    }

    public Depot getExternalDepotById(int depotId) {
        for (Depot externalDepot : externalDepots) {
            if (externalDepot != null && externalDepot.getId() == depotId) {
                return externalDepot;
            }
        }
        return null;
    }

    public Depot getStartDepotById(int depotId) {
        if (originDepot != null && originDepot.getId() == depotId) {
            return originDepot;
        }
        Depot externalDepot = getExternalDepotById(depotId);
        if (externalDepot != null) {
            return externalDepot;
        }
        return null;
    }

    public boolean isStaticNodeId(int nodeId) {
        return nodeId >= 0 && nodeId < problemData.getStaticNodeCount();
    }

    public boolean isCustomerNode(int nodeId) {
        return nodeId >= 1 && nodeId <= problemData.getCustomers().size();
    }

    public double getEarliestTime(int nodeId) {
        if (nodeId == originDepot.getId()) {
            return originDepot.getEarliestTime();
        }
        if (nodeId == returnDepot.getId()) {
            return returnDepot.getEarliestTime();
        }
        if (isCustomerNode(nodeId)) {
            return problemData.getCustomers().get(nodeId - 1).getEarliestTime();
        }
        Depot externalDepot = getExternalDepotById(nodeId);
        if (externalDepot != null) {
            return externalDepot.getEarliestTime();
        }
        throw new IllegalArgumentException("Unknown node id: " + nodeId);
    }

    public double getLatestTime(int nodeId) {
        if (nodeId == originDepot.getId()) {
            return originDepot.getLatestTime();
        }
        if (nodeId == returnDepot.getId()) {
            return returnDepot.getLatestTime();
        }
        if (isCustomerNode(nodeId)) {
            return problemData.getCustomers().get(nodeId - 1).getLatestTime();
        }
        Depot externalDepot = getExternalDepotById(nodeId);
        if (externalDepot != null) {
            return externalDepot.getLatestTime();
        }
        throw new IllegalArgumentException("Unknown node id: " + nodeId);
    }

    public double getServiceTime(int nodeId) {
        if (isCustomerNode(nodeId)) {
            return problemData.getCustomers().get(nodeId - 1).getServiceTime();
        }
        return 0.0;
    }

    public int getDemand(int nodeId) {
        if (isCustomerNode(nodeId)) {
            return problemData.getCustomers().get(nodeId - 1).getDemand();
        }
        return 0;
    }

    private double getXById(int nodeId) {
        if (nodeId == originDepot.getId()) {
            return originDepot.getX();
        }
        if (nodeId == returnDepot.getId()) {
            return returnDepot.getX();
        }
        if (isStaticCustomerId(nodeId)) {
            return problemData.getCustomers().get(nodeId - 1).getX();
        }
        Depot externalDepot = getExternalDepotById(nodeId);
        if (externalDepot != null) {
            return externalDepot.getX();
        }
        throw new IllegalArgumentException("Unknown node id: " + nodeId);
    }

    private double getYById(int nodeId) {
        if (nodeId == originDepot.getId()) {
            return originDepot.getY();
        }
        if (nodeId == returnDepot.getId()) {
            return returnDepot.getY();
        }
        if (isStaticCustomerId(nodeId)) {
            return problemData.getCustomers().get(nodeId - 1).getY();
        }
        Depot externalDepot = getExternalDepotById(nodeId);
        if (externalDepot != null) {
            return externalDepot.getY();
        }
        throw new IllegalArgumentException("Unknown node id: " + nodeId);
    }

    private boolean isStaticCustomerId(int nodeId) {
        return isCustomerNode(nodeId);
    }
}

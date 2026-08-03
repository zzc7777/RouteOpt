package org.zzc.bpcsolver.problem.entity;

import lombok.Getter;
import lombok.Setter;
import org.zzc.bpcsolver.problem.metric.DecimalRoundingMode;
import org.zzc.bpcsolver.problem.metric.DistanceCalculator;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Getter
@Setter
public class ProblemData {

    private Depot originDepot;
    private Depot returnDepot;
    private List<Customer> customers;
    private int vehicleCapacity;
    private int initialVehicleCount;
    private double speed;
    private double distanceScale;
    private int distanceDecimalPlaces = 1;
    private int travelTimeDecimalPlaces = 1;
    private DecimalRoundingMode distanceRoundingMode = DecimalRoundingMode.ROUND;
    private DecimalRoundingMode travelTimeRoundingMode = DecimalRoundingMode.ROUND;
    private double[][] staticDistanceMatrix;

    public ProblemData() {
        this.customers = new ArrayList<>();
    }

    public ProblemData(ProblemData other) {
        this.originDepot = other.originDepot == null ? null : other.originDepot.copy();
        this.returnDepot = other.returnDepot == null ? null : other.returnDepot.copy();
        this.customers = new ArrayList<>();
        for (Customer customer : other.customers) {
            this.customers.add(customer == null ? null : customer.copy());
        }
        this.vehicleCapacity = other.vehicleCapacity;
        this.initialVehicleCount = other.initialVehicleCount;
        this.speed = other.speed;
        this.distanceScale = other.distanceScale;
        this.distanceDecimalPlaces = other.distanceDecimalPlaces;
        this.travelTimeDecimalPlaces = other.travelTimeDecimalPlaces;
        this.distanceRoundingMode = other.distanceRoundingMode;
        this.travelTimeRoundingMode = other.travelTimeRoundingMode;
        this.staticDistanceMatrix = copyMatrix(other.staticDistanceMatrix);
    }

    public void validate() {
        if (originDepot == null) {
            throw new IllegalArgumentException("originDepot must not be null.");
        }
        if (returnDepot == null) {
            throw new IllegalArgumentException("returnDepot must not be null.");
        }
        if (customers == null) {
            throw new IllegalArgumentException("customers must not be null.");
        }
        if (vehicleCapacity <= 0) {
            throw new IllegalArgumentException("vehicleCapacity must be positive.");
        }
        if (initialVehicleCount <= 0) {
            throw new IllegalArgumentException("initialVehicleCount must be positive.");
        }
        if (speed <= 0) {
            throw new IllegalArgumentException("speed must be positive.");
        }
        if (distanceScale <= 0) {
            throw new IllegalArgumentException("distanceScale must be positive.");
        }
        if (distanceDecimalPlaces < 0) {
            throw new IllegalArgumentException("distanceDecimalPlaces must be non-negative.");
        }
        if (travelTimeDecimalPlaces < 0) {
            throw new IllegalArgumentException("travelTimeDecimalPlaces must be non-negative.");
        }
        if (distanceRoundingMode == null) {
            throw new IllegalArgumentException("distanceRoundingMode must not be null.");
        }
        if (travelTimeRoundingMode == null) {
            throw new IllegalArgumentException("travelTimeRoundingMode must not be null.");
        }
        if (originDepot.getId() != 0) {
            throw new IllegalArgumentException("originDepot id must be 0.");
        }

        int customerCount = customers.size();
        int expectedReturnDepotId = customerCount + 1;
        if (returnDepot.getId() != expectedReturnDepotId) {
            throw new IllegalArgumentException("returnDepot id must be n + 1.");
        }

        Set<Integer> customerIds = new HashSet<>();
        for (Customer customer : customers) {
            if (customer == null) {
                throw new IllegalArgumentException("customers must not contain null.");
            }
            if (!customerIds.add(customer.getId())) {
                throw new IllegalArgumentException("customer ids must be unique.");
            }
        }

        for (int expectedCustomerId = 1; expectedCustomerId <= customerCount; expectedCustomerId++) {
            if (!customerIds.contains(expectedCustomerId)) {
                throw new IllegalArgumentException("customer ids must be exactly 1..n.");
            }
        }
    }

    public void buildStaticData() {
        validate();
        buildStaticDistanceMatrix();
    }

    public int getStaticNodeCount() {
        return customers.size() + 2;
    }

    public double getStaticDistance(int fromId, int toId) {
        validateStaticNodeId(fromId);
        validateStaticNodeId(toId);
        if (staticDistanceMatrix == null) {
            throw new IllegalStateException("staticDistanceMatrix has not been built.");
        }
        return staticDistanceMatrix[fromId][toId];
    }

    public double getStaticTravelTime(int fromId, int toId) {
        DistanceCalculator distanceCalculator = new DistanceCalculator();
        return distanceCalculator.calculateTravelTime(
                getStaticDistance(fromId, toId),
                speed,
                travelTimeDecimalPlaces,
                travelTimeRoundingMode
        );
    }

    public ProblemData copy() {
        return new ProblemData(this);
    }

    private void buildStaticDistanceMatrix() {
        DistanceCalculator distanceCalculator = new DistanceCalculator();
        int nodeCount = getStaticNodeCount();
        staticDistanceMatrix = new double[nodeCount][nodeCount];

        for (int i = 0; i < nodeCount; i++) {
            double x1 = getXById(i);
            double y1 = getYById(i);
            for (int j = i + 1; j < nodeCount; j++) {
                double x2 = getXById(j);
                double y2 = getYById(j);
                double distance = distanceCalculator.calculateDistance(
                        x1,
                        y1,
                        x2,
                        y2,
                        distanceScale,
                        distanceDecimalPlaces,
                        distanceRoundingMode
                );
                staticDistanceMatrix[i][j] = distance;
                staticDistanceMatrix[j][i] = distance;
            }
        }
    }

    private void validateStaticNodeId(int id) {
        if (id < 0 || id >= getStaticNodeCount()) {
            throw new IllegalArgumentException("static node id out of range: " + id);
        }
    }

    private double getXById(int id) {
        if (id == originDepot.getId()) {
            return originDepot.getX();
        }
        if (id == returnDepot.getId()) {
            return returnDepot.getX();
        }
        return getCustomerById(id).getX();
    }

    private double getYById(int id) {
        if (id == originDepot.getId()) {
            return originDepot.getY();
        }
        if (id == returnDepot.getId()) {
            return returnDepot.getY();
        }
        return getCustomerById(id).getY();
    }

    private Customer getCustomerById(int id) {
        int customerIndex = id - 1;
        if (customerIndex < 0 || customerIndex >= customers.size()) {
            throw new IllegalArgumentException("customer id out of range: " + id);
        }
        Customer customer = customers.get(customerIndex);
        if (customer == null || customer.getId() != id) {
            throw new IllegalArgumentException("customer id not found: " + id);
        }
        return customer;
    }

    private double[][] copyMatrix(double[][] matrix) {
        if (matrix == null) {
            return null;
        }
        double[][] copied = new double[matrix.length][];
        for (int i = 0; i < matrix.length; i++) {
            copied[i] = matrix[i] == null ? null : matrix[i].clone();
        }
        return copied;
    }
}

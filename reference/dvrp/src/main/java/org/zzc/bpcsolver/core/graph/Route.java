package org.zzc.bpcsolver.core.graph;

import gnu.trove.list.array.TIntArrayList;
import lombok.Getter;
import lombok.Setter;
import org.zzc.bpcsolver.core.template.PathTemplate;
import org.zzc.bpcsolver.problem.entity.Depot;
import org.zzc.bpcsolver.problem.entity.DynamicInstance;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class Route extends PathTemplate<Cycle> {

    private int startDepotId;
    private double load;
    private List<Double> arrivalTimes;
    private double reducedCost;
    private double value;

    public Route(int startDepotId, int index, int numVertices) {
        super(startDepotId, index, numVertices);
        this.startDepotId = startDepotId;
        this.load = 0.0;
        this.arrivalTimes = new ArrayList<>();
        this.reducedCost = 0.0;
        this.value = 0.0;
    }

    public Route(Route other) {
        super(other);
        this.startDepotId = other.startDepotId;
        this.load = other.load;
        this.arrivalTimes = new ArrayList<>(other.arrivalTimes);
        this.reducedCost = other.reducedCost;
        this.value = other.value;
    }

    public Route(int startDepotId, int index, TIntArrayList visitedVertices, int[] visitedVector,
                 double cost, double load, List<Double> arrivalTimes, double reducedCost, double value) {
        super(startDepotId, index, visitedVertices, visitedVector, cost);
        this.startDepotId = startDepotId;
        this.load = load;
        this.arrivalTimes = new ArrayList<>(arrivalTimes);
        this.reducedCost = reducedCost;
        this.value = value;
    }

    public void resetRouteState(int numVertices) {
        this.visitedVector = new int[numVertices];
        this.cost = 0.0;
        this.load = 0.0;
        this.arrivalTimes = new ArrayList<>();
        this.reducedCost = 0.0;
    }

    public void update(DynamicInstance instance) {
        if (instance == null) {
            throw new IllegalArgumentException("instance must not be null.");
        }
        if (visitedVertices == null || visitedVertices.isEmpty()) {
            throw new IllegalArgumentException("visitedVertices must not be empty.");
        }

        Depot startDepot = instance.getStartDepotById(startDepotId);
        if (startDepot == null) {
            throw new IllegalArgumentException("Unknown startDepotId: " + startDepotId);
        }
        if (visitedVertices.get(0) != startDepotId) {
            throw new IllegalArgumentException("The first visited vertex must equal startDepotId.");
        }

        resetRouteState(buildVisitedVectorSize());

        double startTime = instance.getRouteStartTime();
        for (int i = 0; i < visitedVertices.size(); i++) {
            int nodeId = visitedVertices.get(i);
            visitedVector[nodeId] += 1;

            if (i == 0) {
                arrivalTimes.add(startTime);
                continue;
            }

            int previousNodeId = visitedVertices.get(i - 1);
            double previousStartServiceTime = arrivalTimes.get(i - 1);
            double departureTime = previousStartServiceTime + instance.getServiceTime(previousNodeId);
            double arrivalCandidate = departureTime + instance.getTravelTime(previousNodeId, nodeId);
            double startServiceTime = Math.max(arrivalCandidate, instance.getEarliestTime(nodeId));

            arrivalTimes.add(startServiceTime);
            cost += instance.getDistance(previousNodeId, nodeId);
        }

        for (int nodeId : visitedVertices.toArray()) {
            load += instance.getDemand(nodeId);
        }
    }

    private int buildVisitedVectorSize() {
        int maxNodeId = 0;
        for (int nodeId : visitedVertices.toArray()) {
            maxNodeId = Math.max(maxNodeId, nodeId);
        }
        return maxNodeId + 1;
    }


    // public void update(Graph graph){
    //     this.completeTime = 0;
    //     this.cost = 0;
    //     this.visitedVector = new int[graph.getNumVertices()];
    //     for(int j : this.visitedVertices.toArray()){
    //         visitedVector[j] = 1;
    //         calculateCompleteTime(graph.getVertex(j));
    //         calculateCost(graph.getVertex(j));
    //     }
    // }

    // public void sort(Graph graph){
    //     List<Vertex> vertexList = new ArrayList<>();
    //     int[] vertices = this.visitedVertices.toArray();
    //     for (int vertexIndex : vertices) {
    //         if(vertexIndex != 0 && vertexIndex != graph.getNumVertices()-1){
    //             vertexList.add(graph.getVertex(vertexIndex));
    //         }
    //     }
    //     vertexList.sort(Comparator.comparingDouble(Vertex::calculateRatio));

    //     this.visitedVertices = new TIntArrayList();
    //     this.visitedVertices.add(0);
    //     for (Vertex vertex : vertexList) {
    //         this.visitedVertices.add(vertex.getIndex());
    //     }
    //     this.visitedVertices.add(graph.getNumVertices()-1);
    // }


    @Override
    public Route copy() {
        return new Route(this);
    }

    @Override
    public Cycle createCycle(int index, TIntArrayList vertices, int startVertex) {
        return new Cycle(index, vertices, startVertex);
    }

    @Override
    public boolean containsCycle(Cycle cycle) {
        if (cycle == null || cycle.getVertices() == null || visitedVertices == null) {
            return false;
        }

        TIntArrayList cycleVertices = cycle.getVertices();
        if (cycleVertices.size() > visitedVertices.size()) {
            return false;
        }

        for (int i = 0; i <= visitedVertices.size() - cycleVertices.size(); i++) {
            boolean found = true;
            for (int j = 0; j < cycleVertices.size(); j++) {
                if (visitedVertices.get(i + j) != cycleVertices.get(j)) {
                    found = false;
                    break;
                }
            }
            if (found) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return String.format(
                "Route{startDepotId=%d, index=%d, load=%.2f, cost=%.2f, reducedCost=%.2f, value=%.2f}",
                startDepotId, getIndex(), load, cost, reducedCost, value
        );
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!super.equals(obj)) return false;
        if (!(obj instanceof Route)) return false;

        Route route = (Route) obj;
        return Double.compare(route.load, load) == 0
                && Double.compare(route.cost, cost) == 0
                && Double.compare(route.reducedCost, reducedCost) == 0
                && Double.compare(route.value, value) == 0
                && arrivalTimes.equals(route.arrivalTimes);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + Double.hashCode(load);
        result = 31 * result + Double.hashCode(cost);
        result = 31 * result + arrivalTimes.hashCode();
        result = 31 * result + Double.hashCode(reducedCost);
        result = 31 * result + Double.hashCode(value);
        return result;
    }
}

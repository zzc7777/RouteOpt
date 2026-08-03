package org.zzc.bpcsolver.algorithm.acceleration;

import gnu.trove.list.array.TIntArrayList;
import org.zzc.bpcsolver.algorithm.config.Configuration;
import org.zzc.bpcsolver.algorithm.cutting.LmSrc;
import org.zzc.bpcsolver.core.branch.Constraint;
import org.zzc.bpcsolver.core.branch.Node;
import org.zzc.bpcsolver.core.graph.Cycle;
import org.zzc.bpcsolver.core.graph.Graph;
import org.zzc.bpcsolver.core.graph.Route;
import org.zzc.bpcsolver.core.graph.Vertex;

import java.util.ArrayList;
import java.util.List;

public class DynaNgRouteRelaxation extends NgRouteRelaxation {

    private static final double EPSILON = 1e-6;

    private int maxNgSize;

    @Override
    public void initialize(Configuration config, List<Graph> graphs) {
        super.initialize(config, graphs);
        this.maxNgSize = resolveMaxNgSize(config);
    }

    @Override
    public boolean update(Node<Route, LmSrc, Graph> node) {
        if (node == null || node.getSolution() == null || node.getUsedScheduleSet() == null) {
            return false;
        }

        List<Cycle> minimumCycles = new ArrayList<>();
        for (int k = 0; k < node.getUsedScheduleSet().size(); k++) {
            Cycle minimumCycle = findMinimumActiveCustomerCycle(node, k);
            if (minimumCycle != null) {
                minimumCycles.add(minimumCycle);
                expandNgSet(node.getGraphs().get(k), minimumCycle);
            }
        }

        if (minimumCycles.isEmpty()) {
            return false;
        }

        for (Cycle cycle : minimumCycles) {
            removeColumnsContainingCycle(node, cycle);
        }
        return true;
    }

    private Cycle findMinimumActiveCustomerCycle(Node<Route, LmSrc, Graph> node, int machineIndex) {
        if (machineIndex >= node.getSolution().getYks().size()
                || machineIndex >= node.getUsedScheduleSet().size()
                || machineIndex >= node.getGraphs().size()) {
            return null;
        }

        List<Cycle> activeCycles = new ArrayList<>();
        double[] values = node.getSolution().getYks().get(machineIndex);
        List<Route> routes = node.getUsedScheduleSet().get(machineIndex);
        int limit = Math.min(values.length, routes.size());
        for (int s = 0; s < limit; s++) {
            if (values[s] <= EPSILON) {
                continue;
            }
            Route route = routes.get(s);
            if (route == null || !hasCustomerCycle(route, node.getGraphs().get(machineIndex))) {
                continue;
            }
            for (Cycle cycle : route.findAllCycles()) {
                if (isCustomerCycle(cycle, node.getGraphs().get(machineIndex))
                        && !hasReachedMaxNgSize(node.getGraphs().get(machineIndex), cycle)) {
                    Cycle copiedCycle = cycle.copy();
                    copiedCycle.setIndex(machineIndex);
                    activeCycles.add(copiedCycle);
                }
            }
        }
        return findMinimumCycle(activeCycles);
    }

    public Cycle findMinimumCycle(List<Cycle> cycles) {
        Cycle minimumCycle = null;
        int minSize = Integer.MAX_VALUE;
        if (cycles == null) {
            return null;
        }
        for (Cycle cycle : cycles) {
            if (cycle != null && cycle.getVertices().size() < minSize) {
                minSize = cycle.getVertices().size();
                minimumCycle = cycle.copy();
            }
        }
        return minimumCycle;
    }

    public boolean hasReachedMaxNgSize(Graph graph, Cycle cycle) {
        if (graph == null || cycle == null || cycle.getVertices() == null) {
            return true;
        }
        TIntArrayList vertices = cycle.getVertices();
        for (int i = 1; i < cycle.getLength(); i++) {
            Vertex vertex = graph.findVertexById(vertices.get(i));
            if (vertex == null || !vertex.isCustomer()) {
                continue;
            }
            if (vertex.getNgSet().size() >= this.maxNgSize) {
                return true;
            }
        }
        return false;
    }

    private void expandNgSet(Graph graph, Cycle cycle) {
        if (graph == null || cycle == null || cycle.getVertices() == null) {
            return;
        }
        int startCustomerId = cycle.getStartVertex();
        TIntArrayList vertices = cycle.getVertices();
        for (int i = 1; i < cycle.getLength(); i++) {
            Vertex vertex = graph.findVertexById(vertices.get(i));
            if (vertex != null && vertex.isCustomer()) {
                vertex.getNgSet().add(startCustomerId);
            }
        }
    }

    private void removeColumnsContainingCycle(Node<Route, LmSrc, Graph> node, Cycle cycle) {
        int machineIndex = cycle.getIndex();
        if (machineIndex < 0 || machineIndex >= node.getUsedScheduleSet().size()) {
            return;
        }

        List<Route> routes = node.getUsedScheduleSet().get(machineIndex);
        for (int s = 0; s < routes.size(); s++) {
            Route route = routes.get(s);
            if (route == null || !route.containsCycle(cycle)) {
                continue;
            }
            routes.remove(s);
            removeCutCoefficient(node, machineIndex, s);
            removeBranchCoefficient(node, machineIndex, s);
            s--;
        }
    }

    private void removeCutCoefficient(Node<Route, LmSrc, Graph> node, int machineIndex, int columnIndex) {
        if (node.getUsedCutSet() == null) {
            return;
        }
        for (LmSrc cut : node.getUsedCutSet()) {
            if (cut == null
                    || cut.getCoefficient() == null
                    || machineIndex >= cut.getCoefficient().size()
                    || cut.getCoefficient().get(machineIndex) == null
                    || columnIndex >= cut.getCoefficient().get(machineIndex).size()) {
                continue;
            }
            cut.getCoefficient().get(machineIndex).remove(columnIndex);
        }
    }

    private void removeBranchCoefficient(Node<Route, LmSrc, Graph> node, int machineIndex, int columnIndex) {
        if (node.getBranchConstraints() == null) {
            return;
        }
        for (Constraint constraint : node.getBranchConstraints()) {
            if (constraint == null
                    || constraint.getCoeff_2() == null
                    || machineIndex >= constraint.getCoeff_2().size()
                    || constraint.getCoeff_2().get(machineIndex) == null
                    || columnIndex >= constraint.getCoeff_2().get(machineIndex).size()) {
                continue;
            }
            constraint.getCoeff_2().get(machineIndex).removeAt(columnIndex);
        }
    }

    private boolean hasCustomerCycle(Route route, Graph graph) {
        for (Cycle cycle : route.findAllCycles()) {
            if (isCustomerCycle(cycle, graph)) {
                return true;
            }
        }
        return false;
    }

    private boolean isCustomerCycle(Cycle cycle, Graph graph) {
        if (cycle == null || graph == null || cycle.getVertices() == null || cycle.getVertices().size() < 2) {
            return false;
        }
        Vertex start = graph.findVertexById(cycle.getStartVertex());
        if (start == null || !start.isCustomer()) {
            return false;
        }
        TIntArrayList vertices = cycle.getVertices();
        for (int i = 0; i < vertices.size(); i++) {
            Vertex vertex = graph.findVertexById(vertices.get(i));
            if (vertex == null || !vertex.isCustomer()) {
                return false;
            }
        }
        return true;
    }

    private int resolveMaxNgSize(Configuration config) {
        if (config == null || config.getRelaxation() == null || config.getRelaxation().getMaxNgSize() <= 0) {
            return Math.max(this.ngSize, 1);
        }
        return Math.max(this.ngSize, config.getRelaxation().getMaxNgSize());
    }

    @Override
    public String getStrategyName() {
        return "dynamic ng-route";
    }
}

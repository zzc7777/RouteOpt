package org.zzc.bpcsolver.algorithm;

import gnu.trove.list.array.TIntArrayList;
import org.junit.jupiter.api.Test;
import org.zzc.bpcsolver.algorithm.pricing.Label;
import org.zzc.bpcsolver.core.branch.Variable;
import org.zzc.bpcsolver.core.graph.Route;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DeterministicOrderingTest {

    @Test
    void shouldBreakRouteTiesByPathSignature() {
        Route laterPath = buildRoute(0, 0, -1.0, 30.0, 0, 2, 4);
        Route earlierPath = buildRoute(0, 1, -1.0, 30.0, 0, 1, 4);

        List<Route> routes = new ArrayList<>(Arrays.asList(laterPath, earlierPath));
        routes.sort(DeterministicOrdering.routeComparator());

        assertArrayEquals(new int[]{0, 1, 4}, routes.get(0).getVisitedVertices().toArray());
        assertArrayEquals(new int[]{0, 2, 4}, routes.get(1).getVisitedVertices().toArray());
    }

    @Test
    void shouldBreakLabelTiesByPathSignature() {
        Label laterPath = buildLabel(0, 0, -1.0, 30.0, 0, 2, 4);
        Label earlierPath = buildLabel(0, 1, -1.0, 30.0, 0, 1, 4);

        List<Label> labels = new ArrayList<>(Arrays.asList(laterPath, earlierPath));
        labels.sort(DeterministicOrdering.labelComparator());

        assertArrayEquals(new int[]{0, 1, 4}, labels.get(0).getVisitedVertices().toArray());
        assertArrayEquals(new int[]{0, 2, 4}, labels.get(1).getVisitedVertices().toArray());
    }

    @Test
    void shouldBreakVariableTiesByTypeAndIndexes() {
        Variable xArc = new Variable("xkij", new int[]{0, 1, 2}, 0.5);
        Variable qLaterArc = new Variable("qij", new int[]{2, 3}, 0.5);
        Variable qEarlierArc = new Variable("qij", new int[]{1, 2}, 0.5);

        List<Variable> variables = new ArrayList<>(Arrays.asList(xArc, qLaterArc, qEarlierArc));
        variables.sort(DeterministicOrdering.variableComparator());

        assertEquals("qij", variables.get(0).getType());
        assertArrayEquals(new int[]{1, 2}, variables.get(0).getIndexes());
        assertEquals("qij", variables.get(1).getType());
        assertArrayEquals(new int[]{2, 3}, variables.get(1).getIndexes());
        assertEquals("xkij", variables.get(2).getType());
    }

    private Route buildRoute(int startDepotId, int index, double reducedCost, double cost, int... nodes) {
        Route route = new Route(startDepotId, index, 8);
        route.setVisitedVertices(new TIntArrayList(nodes));
        route.setReducedCost(reducedCost);
        route.setCost(cost);
        return route;
    }

    private Label buildLabel(int id, int index, double reducedCost, double cost, int... nodes) {
        Label label = new Label(id, index, 8);
        label.setVisitedVertices(new TIntArrayList(nodes));
        label.setReducedCost(reducedCost);
        label.setCost(cost);
        return label;
    }
}

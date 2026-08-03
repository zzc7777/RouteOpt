package org.zzc.bpcsolver.core.graph;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.zzc.bpcsolver.algorithm.pricing.Label;
import org.zzc.bpcsolver.problem.entity.Customer;
import org.zzc.bpcsolver.problem.entity.Depot;
import org.zzc.bpcsolver.problem.entity.DynamicInstance;
import org.zzc.bpcsolver.problem.entity.ProblemData;

import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraphTest {

    private DynamicInstance initialInstance;

    @BeforeEach
    void setUp() {
        initialInstance = buildInitialInstance();
    }

    @Test
    void shouldBuildGraphFromOriginDepot() {
        Graph graph = new Graph();

        graph.buildFromInstance(initialInstance, 0);

        assertEquals(0, graph.getId());
        assertEquals(0, graph.getStartDepotId());
        assertEquals(4, graph.getNumVertices());
        assertEquals(0, graph.getVertex(0).getId());
        assertEquals(1, graph.getVertex(1).getId());
        assertEquals(2, graph.getVertex(2).getId());
        assertEquals(4, graph.getVertex(3).getId());

        assertEquals(7, graph.getArcs().size());
        assertArcExists(graph, 0, 1, 10.0);
        assertArcExists(graph, 0, 2, 20.0);
        assertArcExists(graph, 0, 4, 0.0);
        assertArcExists(graph, 1, 2, 10.0);
        assertArcExists(graph, 1, 4, 10.0);
        assertArcExists(graph, 2, 1, 10.0);
        assertArcExists(graph, 2, 4, 20.0);
        assertFalse(arcExists(graph, 1, 0));
        assertEquals(3, graph.getIncomingArcs(3).size());
        assertTrue(graph.getIncomingArcs(3).stream().anyMatch(arc -> graph.getVertex(arc.getTailIndex()).getId() == 0));
        assertTrue(graph.getIncomingArcs(3).stream().anyMatch(arc -> graph.getVertex(arc.getTailIndex()).getId() == 1));
        assertTrue(graph.getIncomingArcs(3).stream().anyMatch(arc -> graph.getVertex(arc.getTailIndex()).getId() == 2));
    }

    @Test
    void shouldBuildGraphFromExternalDepot() {
        DynamicInstance instance = buildInitialInstance();

        Depot externalDepot = new Depot();
        externalDepot.setId(5);
        externalDepot.setX(13.0);
        externalDepot.setY(0.0);
        externalDepot.setEarliestTime(18.0);
        externalDepot.setLatestTime(100.0);
        externalDepot.setType(Depot.DepotType.EXTERNAL);
        externalDepot.setVehicleCount(1);
        externalDepot.setRemainingCapacity(8);
        instance.getExternalDepots().add(externalDepot);
        instance.setCurrentTime(18.0);
        instance.setRemainingCustomerIds(Arrays.asList(2, 3));

        Graph graph = new Graph();
        graph.buildFromInstance(instance, 5);

        assertEquals(5, graph.getId());
        assertEquals(5, graph.getStartDepotId());
        assertEquals(4, graph.getNumVertices());
        assertEquals(5, graph.getVertex(0).getId());
        assertEquals(2, graph.getVertex(1).getId());
        assertEquals(3, graph.getVertex(2).getId());
        assertEquals(4, graph.getVertex(3).getId());

        assertArcExists(graph, 5, 2, 7.0);
        assertArcExists(graph, 5, 3, 17.0);
        assertArcExists(graph, 5, 4, 13.0);
        assertArcExists(graph, 2, 3, 10.0);
        assertArcExists(graph, 2, 4, 20.0);
        assertArcExists(graph, 3, 4, 30.0);

        Vertex startVertex = graph.findVertexById(5);
        assertNotNull(startVertex);
        assertTrue(startVertex.isExternalDepot());
    }

    @Test
    void shouldRemoveVertexAndKeepArcIndexesConsistent() {
        Graph graph = new Graph();

        graph.buildFromInstance(initialInstance, 0);
        graph.removeVertex(1);

        assertEquals(3, graph.getArcs().size());
        assertEquals(0, graph.getOutgoingArcs(1).size());
        assertEquals(0, graph.getIncomingArcs(1).size());
        assertFalse(arcExists(graph, 0, 1));
        assertFalse(arcExists(graph, 2, 1));
        assertFalse(arcExists(graph, 1, 2));
        assertFalse(arcExists(graph, 1, 4));
        assertTrue(arcExists(graph, 0, 2));
        assertTrue(arcExists(graph, 0, 4));
        assertTrue(arcExists(graph, 2, 4));
    }

    @Test
    void shouldClearLabelsThroughGraphAndVertexHelpers() {
        Graph graph = new Graph();
        graph.buildFromInstance(initialInstance, 0);

        graph.getVertex(0).getLabelSet().add(new Label(0, 0, 5));
        graph.getVertex(1).getLabelSet().add(new Label(0, 0, 5));

        graph.clearLabels();

        assertEquals(0, graph.getVertex(0).getLabelSet().size());
        assertEquals(0, graph.getVertex(1).getLabelSet().size());
    }

    @Test
    void shouldBuildNgSetsFromRealCustomerIdsByDistance() {
        DynamicInstance instance = buildInitialInstance();
        instance.setRemainingCustomerIds(Arrays.asList(1, 2, 3));
        Graph graph = new Graph();
        graph.buildFromInstance(instance, 0);

        graph.updateNgSets(2);

        assertTrue(graph.findVertexById(1).getNgSet().contains(1));
        assertTrue(graph.findVertexById(1).getNgSet().contains(2));
        assertEquals(2, graph.findVertexById(1).getNgSet().size());
        assertTrue(graph.findVertexById(2).getNgSet().contains(2));
        assertTrue(graph.findVertexById(2).getNgSet().contains(1));
        assertEquals(0, graph.findVertexById(0).getNgSet().size());
        assertEquals(0, graph.findVertexById(4).getNgSet().size());
    }

    @Test
    void shouldAlwaysKeepSelfInNgSetWhenDistancesTie() {
        DynamicInstance instance = buildInitialInstance();
        instance.getProblemData().getCustomers().get(0).setX(20.0);
        instance.getProblemData().getCustomers().get(1).setX(20.0);
        instance.setRemainingCustomerIds(Arrays.asList(1, 2, 3));
        Graph graph = new Graph();
        graph.buildFromInstance(instance, 0);

        graph.updateNgSets(1);

        assertTrue(graph.findVertexById(2).getNgSet().contains(2));
        assertEquals(1, graph.findVertexById(2).getNgSet().size());
    }

    private void assertArcExists(Graph graph, int tailId, int headId, double expectedDistance) {
        assertTrue(arcExists(graph, tailId, headId), "Arc " + tailId + " -> " + headId + " should exist.");
        Arc arc = findArc(graph, tailId, headId);
        assertEquals(expectedDistance, arc.getDistance(), 1e-9);
        assertEquals(expectedDistance, arc.getTravelTime(), 1e-9);
    }

    private boolean arcExists(Graph graph, int tailId, int headId) {
        return findArc(graph, tailId, headId) != null;
    }

    private Arc findArc(Graph graph, int tailId, int headId) {
        for (Arc arc : graph.getArcs()) {
            Vertex tail = graph.getVertex(arc.getTailIndex());
            Vertex head = graph.getVertex(arc.getHeadIndex());
            if (tail.getId() == tailId && head.getId() == headId) {
                return arc;
            }
        }
        return null;
    }

    private DynamicInstance buildInitialInstance() {
        ProblemData problemData = new ProblemData();

        Depot originDepot = new Depot();
        originDepot.setId(0);
        originDepot.setX(0.0);
        originDepot.setY(0.0);
        originDepot.setEarliestTime(0.0);
        originDepot.setLatestTime(100.0);
        originDepot.setType(Depot.DepotType.ORIGIN);
        originDepot.setVehicleCount(2);
        originDepot.setRemainingCapacity(10);

        Depot returnDepot = new Depot();
        returnDepot.setId(4);
        returnDepot.setX(0.0);
        returnDepot.setY(0.0);
        returnDepot.setEarliestTime(0.0);
        returnDepot.setLatestTime(100.0);
        returnDepot.setType(Depot.DepotType.RETURN);
        returnDepot.setVehicleCount(0);
        returnDepot.setRemainingCapacity(10);

        problemData.setOriginDepot(originDepot);
        problemData.setReturnDepot(returnDepot);
        problemData.setVehicleCapacity(10);
        problemData.setInitialVehicleCount(2);
        problemData.setSpeed(1.0);
        problemData.setDistanceScale(1.0);
        problemData.setCustomers(new ArrayList<>());
        problemData.getCustomers().add(buildCustomer(1, 10.0, 0.0, 2, 5.0, 0.0));
        problemData.getCustomers().add(buildCustomer(2, 20.0, 0.0, 3, 5.0, 0.0));
        problemData.getCustomers().add(buildCustomer(3, 30.0, 0.0, 1, 5.0, 20.0));
        problemData.buildStaticData();

        DynamicInstance instance = new DynamicInstance();
        instance.setProblemData(problemData);
        instance.setCurrentTime(0.0);
        instance.setOriginDepot(originDepot.copy());
        instance.setReturnDepot(returnDepot.copy());
        instance.setRemainingCustomerIds(Arrays.asList(1, 2));
        instance.setServedCustomerIds(new ArrayList<Integer>());
        instance.setExternalDepots(new ArrayList<Depot>());
        return instance;
    }

    private Customer buildCustomer(int id, double x, double y, int demand, double serviceTime, double callInTime) {
        Customer customer = new Customer();
        customer.setId(id);
        customer.setX(x);
        customer.setY(y);
        customer.setDemand(demand);
        customer.setEarliestTime(0.0);
        customer.setLatestTime(100.0);
        customer.setServiceTime(serviceTime);
        customer.setCallInTime(callInTime);
        return customer;
    }
}

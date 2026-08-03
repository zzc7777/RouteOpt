package org.zzc.bpcsolver.algorithm.acceleration;

import gnu.trove.list.array.TIntArrayList;
import org.junit.jupiter.api.Test;
import org.zzc.bpcsolver.algorithm.config.Configuration;
import org.zzc.bpcsolver.algorithm.cutting.LmSrc;
import org.zzc.bpcsolver.core.branch.Constraint;
import org.zzc.bpcsolver.core.branch.Node;
import org.zzc.bpcsolver.core.branch.Solution;
import org.zzc.bpcsolver.core.branch.Variable;
import org.zzc.bpcsolver.core.graph.Graph;
import org.zzc.bpcsolver.core.graph.Route;
import org.zzc.bpcsolver.problem.entity.Customer;
import org.zzc.bpcsolver.problem.entity.Depot;
import org.zzc.bpcsolver.problem.entity.DynamicInstance;
import org.zzc.bpcsolver.problem.entity.ProblemData;

import gnu.trove.set.hash.TIntHashSet;
import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DynaNgRouteRelaxationTest {

    @Test
    void shouldExpandNgSetAndRemovePositiveCyclicRoutes() {
        DynamicInstance instance = buildInstance();
        Graph graph = new Graph();
        graph.buildFromInstance(instance, 0);
        DynaNgRouteRelaxation relaxation = new DynaNgRouteRelaxation();
        relaxation.initialize(buildRelaxationConfig(), Arrays.asList(graph));
        Route cyclicRoute = buildRoute(0, 0, 0, 1, 2, 1, 3);
        Route elementaryRoute = buildRoute(0, 1, 0, 1, 3);
        Node<Route, LmSrc, Graph> node = new Node<>();
        node.setGraphs(Arrays.asList(graph));
        node.setUsedScheduleSet(new ArrayList<>(Arrays.asList(new ArrayList<>(Arrays.asList(cyclicRoute, elementaryRoute)))));
        Solution<Route> solution = new Solution<>(1, 2);
        solution.setYks(new ArrayList<>(Arrays.asList(new double[]{1.0, 0.0})));
        node.setSolution(solution);

        assertTrue(relaxation.update(node));

        assertTrue(graph.findVertexById(2).getNgSet().contains(1));
        assertEquals(1, node.getUsedScheduleSet().get(0).size());
        assertFalse(node.getUsedScheduleSet().get(0).get(0).containsCycle(cyclicRoute.findAllCycles().get(0)));
    }

    @Test
    void shouldRemoveCutAndBranchCoefficientsWithDeletedCyclicColumn() {
        DynamicInstance instance = buildInstance();
        Graph graph = new Graph();
        graph.buildFromInstance(instance, 0);
        DynaNgRouteRelaxation relaxation = new DynaNgRouteRelaxation();
        relaxation.initialize(buildRelaxationConfig(), Arrays.asList(graph));

        Route cyclicRoute = buildRoute(0, 0, 0, 1, 2, 1, 3);
        Route elementaryRoute = buildRoute(0, 1, 0, 2, 3);
        Node<Route, LmSrc, Graph> node = new Node<>();
        node.setGraphs(Arrays.asList(graph));
        node.setUsedScheduleSet(new ArrayList<>(Arrays.asList(new ArrayList<>(Arrays.asList(cyclicRoute, elementaryRoute)))));

        LmSrc cut = new LmSrc(0.5, 3.0);
        cut.setSubSet(new TIntHashSet(new int[]{1, 2}));
        cut.setMemorySet(new ArrayList<>(Arrays.asList(new TIntHashSet(new int[]{1, 2}))));
        cut.setCoefficient(new ArrayList<>(Arrays.asList(new ArrayList<>(Arrays.asList(9.0, 4.0)))));
        node.setUsedCutSet(new ArrayList<>(Arrays.asList(cut)));

        Constraint constraint = new Constraint();
        constraint.setVariable(new Variable("qij", new int[]{1, 2}, 0.5));
        constraint.setCoeff_1(new TIntArrayList(new int[]{1}));
        constraint.setCoeff_2(new ArrayList<>(Arrays.asList(new TIntArrayList(new int[]{7, 3}))));
        constraint.setRightSideValue(1);
        node.setBranchConstraints(new ArrayList<>(Arrays.asList(constraint)));

        Solution<Route> solution = new Solution<>(1, 2);
        solution.setYks(new ArrayList<>(Arrays.asList(new double[]{1.0, 0.0})));
        node.setSolution(solution);

        assertTrue(relaxation.update(node));

        assertEquals(1, node.getUsedScheduleSet().get(0).size());
        assertEquals(Arrays.asList(4.0), node.getUsedCutSet().get(0).getCoefficient().get(0));
        assertEquals(1, node.getBranchConstraints().get(0).getCoeff_2().get(0).size());
        assertEquals(3, node.getBranchConstraints().get(0).getCoeff_2().get(0).get(0));
    }

    private Configuration buildRelaxationConfig() {
        Configuration config = new Configuration();
        Configuration.RelaxationConfig relaxation = new Configuration.RelaxationConfig();
        relaxation.setRelaxationSwitch("dynamic ng-route");
        relaxation.setNgSize(1);
        relaxation.setMaxNgSize(3);
        config.setRelaxation(relaxation);
        return config;
    }

    private DynamicInstance buildInstance() {
        ProblemData problemData = new ProblemData();
        Depot originDepot = buildDepot(0, Depot.DepotType.ORIGIN);
        Depot returnDepot = buildDepot(3, Depot.DepotType.RETURN);
        problemData.setOriginDepot(originDepot);
        problemData.setReturnDepot(returnDepot);
        problemData.setVehicleCapacity(10);
        problemData.setInitialVehicleCount(1);
        problemData.setSpeed(1.0);
        problemData.setDistanceScale(1.0);
        problemData.setCustomers(Arrays.asList(
                buildCustomer(1, 10.0, 0.0),
                buildCustomer(2, 20.0, 0.0)
        ));
        problemData.buildStaticData();

        DynamicInstance instance = new DynamicInstance();
        instance.setProblemData(problemData);
        instance.setCurrentTime(0.0);
        instance.setOriginDepot(originDepot.copy());
        instance.setReturnDepot(returnDepot.copy());
        instance.setRemainingCustomerIds(Arrays.asList(1, 2));
        instance.setServedCustomerIds(new ArrayList<>());
        instance.setExternalDepots(new ArrayList<>());
        return instance;
    }

    private Depot buildDepot(int id, Depot.DepotType type) {
        Depot depot = new Depot();
        depot.setId(id);
        depot.setType(type);
        depot.setX(0.0);
        depot.setY(0.0);
        depot.setEarliestTime(0.0);
        depot.setLatestTime(100.0);
        depot.setVehicleCount(1);
        depot.setRemainingCapacity(10);
        return depot;
    }

    private Customer buildCustomer(int id, double x, double y) {
        Customer customer = new Customer();
        customer.setId(id);
        customer.setX(x);
        customer.setY(y);
        customer.setDemand(1);
        customer.setEarliestTime(0.0);
        customer.setLatestTime(100.0);
        customer.setServiceTime(0.0);
        customer.setCallInTime(0.0);
        return customer;
    }

    private Route buildRoute(int startDepotId, int index, int... nodes) {
        Route route = new Route(startDepotId, index, 8);
        route.setVisitedVertices(new TIntArrayList(nodes));
        route.setCost(nodes.length);
        return route;
    }
}

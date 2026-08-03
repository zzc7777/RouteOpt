package org.zzc.bpcsolver.algorithm.acceleration;

import gnu.trove.list.array.TIntArrayList;
import org.junit.jupiter.api.Test;
import org.zzc.bpcsolver.core.branch.Solution;
import org.zzc.bpcsolver.core.graph.Graph;
import org.zzc.bpcsolver.core.graph.Route;
import org.zzc.bpcsolver.problem.entity.Customer;
import org.zzc.bpcsolver.problem.entity.Depot;
import org.zzc.bpcsolver.problem.entity.DynamicInstance;
import org.zzc.bpcsolver.problem.entity.ProblemData;

import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DVRPColumnPoolPrimalHeuristicTest {

    @Test
    void shouldIgnoreRelaxedCyclicColumnsWhenBuildingExecutableIncumbent() {
        DynamicInstance instance = buildSingleCustomerInstance();
        Graph graph = new Graph();
        graph.buildFromInstance(instance, 0);
        Route cheapRelaxedCycle = buildRoute(0, 0, 1.0, 0, 1, 1, 2);
        Route executableRoute = buildRoute(0, 1, 10.0, 0, 1, 2);

        DVRPColumnPoolPrimalHeuristic heuristic = new DVRPColumnPoolPrimalHeuristic();
        Solution<Route> solution = heuristic.obtainFeasibleSolution(
                instance,
                Arrays.asList(graph),
                Arrays.asList(Arrays.asList(cheapRelaxedCycle, executableRoute)),
                new ArrayList<>()
        );

        assertEquals(10.0, solution.getObjValue(), 1e-6);
        assertEquals(1, solution.getBestSchedules().size());
        assertArrayEquals(new int[]{0, 1, 2}, solution.getBestSchedules().get(0).getVisitedVertices().toArray());
    }

    private DynamicInstance buildSingleCustomerInstance() {
        ProblemData problemData = new ProblemData();
        Depot originDepot = buildDepot(0, Depot.DepotType.ORIGIN);
        Depot returnDepot = buildDepot(2, Depot.DepotType.RETURN);
        problemData.setOriginDepot(originDepot);
        problemData.setReturnDepot(returnDepot);
        problemData.setVehicleCapacity(10);
        problemData.setInitialVehicleCount(1);
        problemData.setSpeed(1.0);
        problemData.setDistanceScale(1.0);
        problemData.setCustomers(Arrays.asList(buildCustomer(1)));
        problemData.buildStaticData();

        DynamicInstance instance = new DynamicInstance();
        instance.setProblemData(problemData);
        instance.setCurrentTime(0.0);
        instance.setOriginDepot(originDepot.copy());
        instance.setReturnDepot(returnDepot.copy());
        instance.setRemainingCustomerIds(Arrays.asList(1));
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
        depot.setVehicleCount(type == Depot.DepotType.ORIGIN ? 1 : 0);
        depot.setRemainingCapacity(10);
        return depot;
    }

    private Customer buildCustomer(int id) {
        Customer customer = new Customer();
        customer.setId(id);
        customer.setX(10.0);
        customer.setY(0.0);
        customer.setDemand(1);
        customer.setEarliestTime(0.0);
        customer.setLatestTime(100.0);
        customer.setServiceTime(0.0);
        customer.setCallInTime(0.0);
        return customer;
    }

    private Route buildRoute(int startDepotId, int index, double cost, int... nodes) {
        Route route = new Route(startDepotId, index, 8);
        route.setVisitedVertices(new TIntArrayList(nodes));
        route.setCost(cost);
        return route;
    }
}

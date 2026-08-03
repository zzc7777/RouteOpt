package org.zzc.bpcsolver.core.graph;

import gnu.trove.list.array.TIntArrayList;
import org.junit.jupiter.api.Test;
import org.zzc.bpcsolver.problem.entity.Customer;
import org.zzc.bpcsolver.problem.entity.Depot;
import org.zzc.bpcsolver.problem.entity.DynamicInstance;
import org.zzc.bpcsolver.problem.entity.ProblemData;

import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RouteTest {

    @Test
    void shouldUpdateRouteFromOriginDepot() {
        DynamicInstance instance = buildInitialInstance();
        Route route = new Route(0, 0, 5);
        route.setVisitedVertices(new TIntArrayList(new int[]{0, 1, 2, 4}));

        route.update(instance);

        assertEquals(5.0, route.getLoad(), 1e-9);
        assertEquals(40.0, route.getCost(), 1e-9);
        assertEquals(Arrays.asList(0.0, 10.0, 25.0, 50.0), route.getArrivalTimes());
        assertArrayEquals(new int[]{1, 1, 1, 0, 1}, route.getVisitedVector());
    }

    @Test
    void shouldUpdateRouteFromExternalDepot() {
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

        Route route = new Route(5, 1, 6);
        route.setVisitedVertices(new TIntArrayList(new int[]{5, 2, 4}));

        route.update(instance);

        assertEquals(3.0, route.getLoad(), 1e-9);
        assertEquals(27.0, route.getCost(), 1e-9);
        assertEquals(Arrays.asList(18.0, 25.0, 50.0), route.getArrivalTimes());
        assertArrayEquals(new int[]{0, 0, 1, 0, 1, 1}, route.getVisitedVector());
    }

    @Test
    void shouldStartExternalDepotRouteAtCurrentDecisionTimeEvenIfDepotEarliestIsLater() {
        DynamicInstance instance = buildInitialInstance();

        Depot externalDepot = new Depot();
        externalDepot.setId(5);
        externalDepot.setX(13.0);
        externalDepot.setY(0.0);
        externalDepot.setEarliestTime(30.0);
        externalDepot.setLatestTime(100.0);
        externalDepot.setType(Depot.DepotType.EXTERNAL);
        externalDepot.setVehicleCount(1);
        externalDepot.setRemainingCapacity(8);
        instance.getExternalDepots().add(externalDepot);
        instance.setCurrentTime(18.0);

        Route route = new Route(5, 1, 6);
        route.setVisitedVertices(new TIntArrayList(new int[]{5, 4}));

        route.update(instance);

        assertEquals(Arrays.asList(18.0, 31.0), route.getArrivalTimes());
        assertEquals(13.0, route.getCost(), 1e-9);
    }

    @Test
    void shouldReturnAllAdjacentArcsInRouteOrder() {
        Route route = new Route(0, 0, 5);
        route.setVisitedVertices(new TIntArrayList(new int[]{0, 1, 2, 4}));

        java.util.List<int[]> arcs = route.getArcs();

        assertEquals(3, arcs.size());
        assertArrayEquals(new int[]{0, 1}, arcs.get(0));
        assertArrayEquals(new int[]{1, 2}, arcs.get(1));
        assertArrayEquals(new int[]{2, 4}, arcs.get(2));
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

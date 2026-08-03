package org.zzc.bpcsolver.problem.update;

import gnu.trove.list.array.TIntArrayList;
import org.junit.jupiter.api.Test;
import org.zzc.bpcsolver.core.branch.StageSolution;
import org.zzc.bpcsolver.core.graph.Route;
import org.zzc.bpcsolver.problem.entity.Customer;
import org.zzc.bpcsolver.problem.entity.Depot;
import org.zzc.bpcsolver.problem.entity.DynamicInstance;
import org.zzc.bpcsolver.problem.entity.ProblemData;
import org.zzc.bpcsolver.problem.config.ProblemConfig;
import org.zzc.bpcsolver.problem.config.ProblemConfigReader;
import org.zzc.bpcsolver.problem.loader.ProblemDataLoader;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DynamicStateUpdaterTest {

    @Test
    void shouldCreateInitialDynamicInstanceFromProblemData() {
        ProblemData problemData = buildProblemData();
        DynamicStateUpdater updater = new DynamicStateUpdater();

        DynamicInstance initialInstance = updater.createInitialInstance(problemData, 0.0);

        assertEquals(0.0, initialInstance.getCurrentTime(), 1e-9);
        assertEquals(2, initialInstance.getOriginDepot().getVehicleCount());
        assertEquals(Arrays.asList(1, 2), initialInstance.getRemainingCustomerIds());
        assertTrue(initialInstance.getExternalDepots().isEmpty());
    }

    @Test
    void shouldUpdateDynamicInstanceForVehicleInTransit() {
        ProblemData problemData = buildProblemData();
        DynamicStateUpdater updater = new DynamicStateUpdater();
        DynamicInstance initialInstance = updater.createInitialInstance(problemData, 0.0);

        Route route = buildRoute(0, 0, new int[]{0, 1, 2, 4}, Arrays.asList(0.0, 10.0, 25.0, 50.0), 5);
        StageSolution stageSolution = new StageSolution();
        stageSolution.getSelectedRoutes().add(route);

        DynamicInstance nextInstance = updater.update(initialInstance, stageSolution, 18.0);

        assertEquals(18.0, nextInstance.getCurrentTime(), 1e-9);
        assertEquals(0.0, nextInstance.getRouteStartTime(), 1e-9);
        assertEquals(1, nextInstance.getOriginDepot().getVehicleCount());
        assertEquals(Collections.singletonList(2), nextInstance.getRemainingCustomerIds());
        assertEquals(1, nextInstance.getExternalDepots().size());

        Depot externalDepot = nextInstance.getExternalDepots().get(0);
        assertEquals(5, externalDepot.getId());
        assertEquals(13.0, externalDepot.getX(), 1e-9);
        assertEquals(0.0, externalDepot.getY(), 1e-9);
        assertEquals(18.0, externalDepot.getEarliestTime(), 1e-9);
        assertEquals(8, externalDepot.getRemainingCapacity());
        assertEquals(problemData.getDistanceScale() * 7.0, nextInstance.getDistance(externalDepot.getId(), 2), 1e-9);
    }

    @Test
    void shouldUpdateDynamicInstanceFromExternalDepotRoute() {
        ProblemData problemData = buildProblemData();
        DynamicStateUpdater updater = new DynamicStateUpdater();
        DynamicInstance initialInstance = updater.createInitialInstance(problemData, 0.0);

        Route firstRoute = buildRoute(0, 0, new int[]{0, 1, 2, 4}, Arrays.asList(0.0, 10.0, 25.0, 50.0), 5);
        StageSolution firstStageSolution = new StageSolution();
        firstStageSolution.getSelectedRoutes().add(firstRoute);
        DynamicInstance secondStageInput = updater.update(initialInstance, firstStageSolution, 18.0);

        Depot previousExternalDepot = secondStageInput.getExternalDepots().get(0);
        Route secondRoute = buildRoute(previousExternalDepot.getId(), 1, new int[]{previousExternalDepot.getId(), 2, 4}, Arrays.asList(18.0, 25.0, 50.0), 5);
        StageSolution secondStageSolution = new StageSolution();
        secondStageSolution.getSelectedRoutes().add(secondRoute);

        DynamicInstance thirdStageInput = updater.update(secondStageInput, secondStageSolution, 28.0);

        assertEquals(1, thirdStageInput.getOriginDepot().getVehicleCount());
        assertEquals(Arrays.asList(2, 3), thirdStageInput.getRemainingCustomerIds());
        assertEquals(1, thirdStageInput.getExternalDepots().size());

        Depot externalDepot = thirdStageInput.getExternalDepots().get(0);
        assertEquals(6, externalDepot.getId());
        assertEquals(20.0, externalDepot.getX(), 1e-9);
        assertEquals(0.0, externalDepot.getY(), 1e-9);
        assertEquals(30.0, externalDepot.getEarliestTime(), 1e-9);
        assertEquals(5, externalDepot.getRemainingCapacity());
        assertNotNull(thirdStageInput.getExternalDepotById(externalDepot.getId()));
    }

    @Test
    void shouldRemoveCustomersThatCanNoLongerBeReoptimizedFromOrigin() {
        ProblemData problemData = buildProblemData();
        problemData.getCustomers().get(1).setLatestTime(30.0);
        DynamicStateUpdater updater = new DynamicStateUpdater();
        DynamicInstance initialInstance = updater.createInitialInstance(problemData, 0.0);

        Route route = buildRoute(0, 0, new int[]{0, 1, 2, 4}, Arrays.asList(0.0, 10.0, 25.0, 50.0), 5);
        StageSolution stageSolution = new StageSolution();
        stageSolution.getSelectedRoutes().add(route);

        DynamicInstance nextInstance = updater.update(initialInstance, stageSolution, 18.0);

        assertTrue(nextInstance.getRemainingCustomerIds().isEmpty());
        assertEquals(Arrays.asList(1, 2), nextInstance.getServedCustomerIds());
    }

    @Test
    void shouldPlaceExternalDepotByTravelTimeNotWaitingTime() {
        ProblemData problemData = buildProblemData();
        problemData.getCustomers().get(1).setEarliestTime(50.0);
        DynamicStateUpdater updater = new DynamicStateUpdater();
        DynamicInstance initialInstance = updater.createInitialInstance(problemData, 0.0);

        Route route = buildRoute(0, 0, new int[]{0, 1, 2, 4}, Arrays.asList(0.0, 10.0, 50.0, 75.0), 5);
        StageSolution stageSolution = new StageSolution();
        stageSolution.getSelectedRoutes().add(route);

        DynamicInstance nextInstance = updater.update(initialInstance, stageSolution, 18.0);

        Depot externalDepot = nextInstance.getExternalDepots().get(0);
        assertEquals(13.0, externalDepot.getX(), 1e-9);
        assertEquals(0.0, externalDepot.getY(), 1e-9);
    }

    @Test
    void shouldKeepCSharpReferenceRouteTimelineWhenUpdatingDynamicStage() {
        ProblemConfig config = new ProblemConfigReader().read("dvrp_td.yaml");
        config.setInstanceFileName("r101_D_25_E_25.xlsx");
        config.setInstanceBaseName("r101_D_25_E_25");
        ProblemData problemData = new ProblemDataLoader().load(config);
        DynamicStateUpdater updater = new DynamicStateUpdater();
        DynamicInstance initialInstance = updater.createInitialInstance(problemData, 0.0);

        StageSolution stageSolution = new StageSolution();
        Route route = new Route(0, 0, problemData.getStaticNodeCount());
        route.setVisitedVertices(new TIntArrayList(new int[]{0, 14, 26}));
        route.update(initialInstance);
        stageSolution.getSelectedRoutes().add(route);

        DynamicInstance nextInstance = updater.update(initialInstance, stageSolution, 23.0);

        assertEquals(Arrays.asList(0.0, 32.0, 74.0), route.getArrivalTimes());
        assertEquals(23.0, nextInstance.getCurrentTime(), 1e-9);
        assertEquals(0.0, nextInstance.getRouteStartTime(), 1e-9);
        assertEquals(1, nextInstance.getExternalDepots().size());
        Depot externalDepot = nextInstance.getExternalDepots().get(0);
        assertEquals(27, externalDepot.getId());
        assertEquals(20.625, externalDepot.getX(), 1e-4);
        assertEquals(17.0313, externalDepot.getY(), 1e-4);
        assertEquals(23.0, externalDepot.getEarliestTime(), 1e-9);
    }

    @Test
    void shouldCalculateActualDistanceUntilNextDecisionTimeLikeReference() {
        ProblemData problemData = buildProblemData();
        DynamicStateUpdater updater = new DynamicStateUpdater();
        DynamicInstance initialInstance = updater.createInitialInstance(problemData, 0.0);

        Route unfinishedRoute = buildRoute(0, 0, new int[]{0, 1, 2, 4}, Arrays.asList(0.0, 10.0, 25.0, 50.0), 5);
        unfinishedRoute.setCost(50.0);
        Route completedRoute = buildRoute(0, 1, new int[]{0, 3, 4}, Arrays.asList(0.0, 10.0, 18.0), 5);
        completedRoute.setCost(20.0);
        StageSolution stageSolution = new StageSolution();
        stageSolution.getSelectedRoutes().add(unfinishedRoute);
        stageSolution.getSelectedRoutes().add(completedRoute);

        double actualDistance = updater.calculateActualDistance(initialInstance, stageSolution, 18.0);

        assertEquals(30.0, actualDistance, 1e-9);
    }

    @Test
    void shouldCountExternalDepotDirectReturnDistanceFromCurrentDecisionTime() {
        ProblemData problemData = buildProblemData();
        DynamicStateUpdater updater = new DynamicStateUpdater();
        DynamicInstance instance = updater.createInitialInstance(problemData, 18.0);
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

        Route directReturn = new Route(5, 0, 6);
        directReturn.setVisitedVertices(new TIntArrayList(new int[]{5, 4}));
        directReturn.update(instance);
        StageSolution stageSolution = new StageSolution();
        stageSolution.getSelectedRoutes().add(directReturn);

        double actualDistance = updater.calculateActualDistance(instance, stageSolution, 31.0);

        assertEquals(13.0, actualDistance, 1e-9);
    }

    private ProblemData buildProblemData() {
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
        return problemData;
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

    private Route buildRoute(int startDepotId, int index, int[] nodeIds, List<Double> arrivalTimes, int numVertices) {
        Route route = new Route(startDepotId, index, numVertices);
        route.setVisitedVertices(new TIntArrayList(nodeIds));
        route.setArrivalTimes(new ArrayList<>(arrivalTimes));
        return route;
    }
}

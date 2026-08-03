package org.zzc.bpcsolver.core.graph;

import gnu.trove.list.array.TIntArrayList;
import org.junit.jupiter.api.Test;
import org.zzc.bpcsolver.core.branch.StageSolution;
import org.zzc.bpcsolver.problem.entity.Customer;
import org.zzc.bpcsolver.problem.entity.Depot;
import org.zzc.bpcsolver.problem.entity.DynamicInstance;
import org.zzc.bpcsolver.problem.entity.ProblemData;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouteFeasibilityValidatorTest {

    private final RouteFeasibilityValidator validator = new RouteFeasibilityValidator();

    @Test
    void shouldAcceptFeasibleOriginRoute() {
        DynamicInstance instance = buildInitialInstance(10);
        Route route = buildUpdatedRoute(instance, 0, 0, 1, 2, 4);

        List<String> violations = validator.findRouteViolations(instance, route);

        assertTrue(violations.isEmpty(), violations::toString);
    }

    @Test
    void shouldRejectCapacityViolation() {
        DynamicInstance instance = buildInitialInstance(4);
        Route route = buildUpdatedRoute(instance, 0, 0, 1, 2, 4);

        List<String> violations = validator.findRouteViolations(instance, route);

        assertContains(violations, "exceeds capacity");
    }

    @Test
    void shouldRejectRouteThatMissesLatestTime() {
        DynamicInstance instance = buildInitialInstance(10);
        instance.getProblemData().getCustomers().get(1).setLatestTime(15.0);
        Route route = buildUpdatedRoute(instance, 0, 0, 1, 2, 4);

        List<String> violations = validator.findRouteViolations(instance, route);

        assertContains(violations, "arrives after latest time at node 2");
    }

    @Test
    void shouldRejectRouteWithStaleTimeline() {
        DynamicInstance instance = buildInitialInstance(10);
        Route route = buildUpdatedRoute(instance, 0, 0, 1, 2, 4);
        route.getArrivalTimes().set(2, 11.0);

        List<String> violations = validator.findRouteViolations(instance, route);

        assertContains(violations, "travel-time propagation");
    }

    @Test
    void shouldValidateStageCustomerCoverageAndExternalDepotUsage() {
        DynamicInstance instance = buildInitialInstance(10);
        Depot externalDepot = buildDepot(5, Depot.DepotType.EXTERNAL, 13.0, 0.0, 1, 8);
        instance.getExternalDepots().add(externalDepot);

        StageSolution solution = new StageSolution();
        solution.setSelectedRoutes(Arrays.asList(
                buildUpdatedRoute(instance, 0, 0, 1, 4),
                buildUpdatedRoute(instance, 5, 5, 2, 4)
        ));

        List<String> violations = validator.findStageSolutionViolations(instance, solution);

        assertTrue(violations.isEmpty(), violations::toString);
    }

    @Test
    void shouldRejectDuplicateAndMissingCustomerCoverage() {
        DynamicInstance instance = buildInitialInstance(10);

        StageSolution solution = new StageSolution();
        solution.setSelectedRoutes(Arrays.asList(
                buildUpdatedRoute(instance, 0, 0, 1, 4),
                buildUpdatedRoute(instance, 0, 0, 1, 4)
        ));

        List<String> violations = validator.findStageSolutionViolations(instance, solution);

        assertContains(violations, "customer 1 covered 2 times");
        assertContains(violations, "customer 2 covered 0 times");
    }

    @Test
    void shouldRejectMissingExternalDepotRoute() {
        DynamicInstance instance = buildInitialInstance(10);
        Depot externalDepot = buildDepot(5, Depot.DepotType.EXTERNAL, 13.0, 0.0, 1, 8);
        instance.getExternalDepots().add(externalDepot);

        StageSolution solution = new StageSolution();
        solution.setSelectedRoutes(Arrays.asList(
                buildUpdatedRoute(instance, 0, 0, 1, 2, 4)
        ));

        List<String> violations = validator.findStageSolutionViolations(instance, solution);

        assertContains(violations, "external depot 5 starts 0 routes, expected 1");
    }

    @Test
    void shouldSupportMathematicalExternalDepotStartTimePolicy() {
        DynamicInstance instance = buildInitialInstance(10);
        Depot externalDepot = buildDepot(5, Depot.DepotType.EXTERNAL, 13.0, 0.0, 1, 8);
        externalDepot.setEarliestTime(30.0);
        instance.getExternalDepots().add(externalDepot);

        Route route = new Route(5, 0, 6);
        route.setVisitedVertices(new TIntArrayList(new int[]{5, 2, 4}));
        route.resetRouteState(6);
        route.getVisitedVector()[5] = 1;
        route.getVisitedVector()[2] = 1;
        route.getVisitedVector()[4] = 1;
        route.getArrivalTimes().add(30.0);
        route.getArrivalTimes().add(37.0);
        route.getArrivalTimes().add(57.0);
        route.setCost(27.0);
        route.setLoad(3.0);

        List<String> routeClockViolations = validator.findRouteViolations(instance, route);
        RouteFeasibilityValidator mathematicalValidator = new RouteFeasibilityValidator(
                1e-6,
                RouteFeasibilityValidator.StartTimePolicy.START_DEPOT_EARLIEST
        );

        assertFalse(routeClockViolations.isEmpty());
        assertTrue(mathematicalValidator.findRouteViolations(instance, route).isEmpty());
    }

    private Route buildUpdatedRoute(DynamicInstance instance, int startDepotId, int... nodes) {
        Route route = new Route(startDepotId, 0, buildRouteVectorSize(instance));
        route.setVisitedVertices(new TIntArrayList(nodes));
        route.update(instance);
        return route;
    }

    private int buildRouteVectorSize(DynamicInstance instance) {
        int maxNodeId = instance.getReturnDepot().getId();
        for (Depot externalDepot : instance.getExternalDepots()) {
            maxNodeId = Math.max(maxNodeId, externalDepot.getId());
        }
        return maxNodeId + 1;
    }

    private DynamicInstance buildInitialInstance(int capacity) {
        ProblemData problemData = new ProblemData();
        Depot originDepot = buildDepot(0, Depot.DepotType.ORIGIN, 0.0, 0.0, 2, capacity);
        Depot returnDepot = buildDepot(4, Depot.DepotType.RETURN, 0.0, 0.0, 0, capacity);

        problemData.setOriginDepot(originDepot);
        problemData.setReturnDepot(returnDepot);
        problemData.setVehicleCapacity(capacity);
        problemData.setInitialVehicleCount(2);
        problemData.setSpeed(1.0);
        problemData.setDistanceScale(1.0);
        problemData.setCustomers(new ArrayList<>());
        problemData.getCustomers().add(buildCustomer(1, 10.0, 0.0, 3, 5.0));
        problemData.getCustomers().add(buildCustomer(2, 20.0, 0.0, 3, 0.0));
        problemData.getCustomers().add(buildCustomer(3, 30.0, 0.0, 1, 0.0));
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

    private Depot buildDepot(int id, Depot.DepotType type, double x, double y, int vehicleCount, int remainingCapacity) {
        Depot depot = new Depot();
        depot.setId(id);
        depot.setX(x);
        depot.setY(y);
        depot.setEarliestTime(0.0);
        depot.setLatestTime(100.0);
        depot.setType(type);
        depot.setVehicleCount(vehicleCount);
        depot.setRemainingCapacity(remainingCapacity);
        return depot;
    }

    private Customer buildCustomer(int id, double x, double y, int demand, double serviceTime) {
        Customer customer = new Customer();
        customer.setId(id);
        customer.setX(x);
        customer.setY(y);
        customer.setDemand(demand);
        customer.setEarliestTime(0.0);
        customer.setLatestTime(100.0);
        customer.setServiceTime(serviceTime);
        customer.setCallInTime(0.0);
        return customer;
    }

    private void assertContains(List<String> violations, String expectedText) {
        assertTrue(
                violations.stream().anyMatch(violation -> violation.contains(expectedText)),
                () -> "Expected violation containing '" + expectedText + "' but got " + violations
        );
    }
}

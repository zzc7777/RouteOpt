package org.zzc.bpcsolver.model;

import gnu.trove.list.array.TIntArrayList;
import org.junit.jupiter.api.Test;
import org.zzc.bpcsolver.algorithm.branching.QijBranching;
import org.zzc.bpcsolver.algorithm.cutting.LmSrc;
import org.zzc.bpcsolver.core.branch.Constraint;
import org.zzc.bpcsolver.algorithm.pricing.ForwardLabeling;
import org.zzc.bpcsolver.core.branch.Variable;
import org.zzc.bpcsolver.core.graph.Graph;
import org.zzc.bpcsolver.core.branch.StageSolution;
import org.zzc.bpcsolver.core.graph.Route;
import org.zzc.bpcsolver.problem.entity.Customer;
import org.zzc.bpcsolver.problem.entity.Depot;
import org.zzc.bpcsolver.problem.entity.DynamicInstance;
import org.zzc.bpcsolver.problem.entity.ProblemData;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DVRPMasterProblemTest {

    @Test
    void shouldSolveLpAndReturnPricingCustomerDuals() {
        DynamicInstance instance = buildOriginOnlyInstance();
        List<Route> initialRoutes = Arrays.asList(
                buildRoute(0, 0, 10.0, 0, 0, 1, 3),
                buildRoute(0, 1, 20.0, 0, 0, 2, 3)
        );

        DVRPMasterProblem masterProblem = new DVRPMasterProblem();
        masterProblem.initialize(instance, initialRoutes, false);
        masterProblem.build();

        assertTrue(masterProblem.solve());
        assertEquals(30.0, masterProblem.getObjectiveValue(), 1e-6);
        assertEquals(2, masterProblem.getSelectedRoutes().size());

        double[] duals = masterProblem.getPricingCustomerDuals();
        assertEquals(0.0, duals[0], 1e-6);
        assertEquals(10.0, duals[1], 1e-6);
        assertEquals(20.0, duals[2], 1e-6);
    }

    @Test
    void shouldAddColumnAndImproveObjectiveWhileKeepingCustomerDualContract() {
        DynamicInstance instance = buildInstanceWithExternalDepot();
        List<Route> initialRoutes = Arrays.asList(
                buildRoute(0, 0, 10.0, 0, 0, 1, 3),
                buildRoute(4, 1, 100.0, 0, 4, 2, 3)
        );

        DVRPMasterProblem masterProblem = new DVRPMasterProblem();
        masterProblem.initialize(instance, initialRoutes, false);
        masterProblem.build();

        assertTrue(masterProblem.solve());
        assertEquals(110.0, masterProblem.getObjectiveValue(), 1e-6);

        masterProblem.addColumns(Arrays.asList(
                buildRoute(4, 2, 15.0, 0, 4, 1, 2, 3)
        ));

        assertTrue(masterProblem.solve());
        assertEquals(15.0, masterProblem.getObjectiveValue(), 1e-6);

        List<Route> selectedRoutes = masterProblem.getSelectedRoutes();
        assertEquals(1, selectedRoutes.size());
        assertArrayEquals(new int[]{4, 1, 2, 3}, selectedRoutes.get(0).getVisitedVertices().toArray());

        double[] duals = masterProblem.getPricingCustomerDuals();
        double[] legacyDuals = masterProblem.getNodeDuals();
        double[] startDepotDuals = masterProblem.getStartDepotConstraintDuals();
        assertEquals(10.0, duals[1], 1e-6);
        assertEquals(duals[1], legacyDuals[1], 1e-6);
        assertEquals(duals[2], legacyDuals[2], 1e-6);
        assertTrue(duals[2] >= 0.0);
        assertTrue(startDepotDuals[0] <= 0.0);
        assertTrue(startDepotDuals[4] >= 0.0);
    }

    @Test
    void shouldSolveImprovingRoutesDirectlyFromMasterDuals() {
        DynamicInstance instance = buildInstanceWithExternalDepot();
        List<Route> initialRoutes = Arrays.asList(
                buildRoute(0, 0, 10.0, 0, 0, 1, 3),
                buildRoute(4, 1, 100.0, 0, 4, 2, 3)
        );

        DVRPMasterProblem masterProblem = new DVRPMasterProblem();
        masterProblem.initialize(instance, initialRoutes, false);
        masterProblem.build();
        assertTrue(masterProblem.solve());

        ForwardLabeling forwardLabeling = new ForwardLabeling();
        List<Route> improvingRoutes = forwardLabeling.solveImprovingRoutes(
                instance,
                masterProblem.getPricingCustomerDuals(),
                masterProblem.getStartDepotConstraintDuals()
        );
        assertFalse(improvingRoutes.isEmpty());
        assertTrue(improvingRoutes.stream().anyMatch(route -> route.getStartDepotId() == 4));
    }

    @Test
    void shouldUpdateStageSolutionFromSolvedMaster() {
        DynamicInstance instance = buildInstanceWithExternalDepot();
        List<Route> initialRoutes = Arrays.asList(
                buildRoute(0, 0, 10.0, 0, 0, 1, 3),
                buildRoute(4, 1, 100.0, 0, 4, 2, 3)
        );

        DVRPMasterProblem masterProblem = new DVRPMasterProblem();
        masterProblem.initialize(instance, initialRoutes, false);
        masterProblem.build();
        assertTrue(masterProblem.solve());

        StageSolution stageSolution = new StageSolution();
        masterProblem.updateSolution(stageSolution);

        assertEquals(110.0, stageSolution.getObjectiveValue(), 1e-6);
        assertEquals(2, stageSolution.getAllRoutes().size());
        assertEquals(2, stageSolution.getSelectedRoutes().size());
        assertTrue(stageSolution.isIntegerSolution());
        assertEquals(masterProblem.getPricingCustomerDuals()[1], stageSolution.getPricingCustomerDuals()[1], 1e-6);
        assertEquals(masterProblem.getStartDepotConstraintDuals()[4], stageSolution.getStartDepotConstraintDuals()[4], 1e-6);
    }

    @Test
    void shouldWriteLpValuesBackToMasterRoutesAfterSolve() {
        DynamicInstance instance = buildOriginOnlyInstance();
        List<Route> initialRoutes = Arrays.asList(
                buildRoute(0, 0, 10.0, 0, 0, 1, 3),
                buildRoute(0, 1, 20.0, 0, 0, 2, 3)
        );

        DVRPMasterProblem masterProblem = new DVRPMasterProblem();
        masterProblem.initialize(instance, initialRoutes, false);
        masterProblem.build();

        assertTrue(masterProblem.solve());
        assertEquals(2, masterProblem.getRoutes().size());
        assertEquals(1.0, masterProblem.getRoutes().get(0).getValue(), 1e-6);
        assertEquals(1.0, masterProblem.getRoutes().get(1).getValue(), 1e-6);
    }

    @Test
    void shouldBuildAndReadBranchConstraintDuals() {
        DynamicInstance instance = buildOriginOnlyInstance();
        List<Route> initialRoutes = Arrays.asList(
                buildRoute(0, 0, 10.0, 0, 0, 1, 3),
                buildRoute(0, 1, 20.0, 0, 0, 2, 3),
                buildRoute(0, 2, 25.0, 0, 0, 1, 2, 3)
        );

        DVRPMasterProblem masterProblem = new DVRPMasterProblem();
        masterProblem.initialize(instance, initialRoutes, false);
        masterProblem.configureBranching(Arrays.asList(buildRightBranchOnArc(1, 2, initialRoutes)), new QijBranching<Route, LmSrc, Graph>());
        masterProblem.build();

        assertTrue(masterProblem.solve());
        assertEquals(1, masterProblem.getBranchConstraintDuals().size());
        assertTrue(masterProblem.getBranchConstraintDuals().get(0) >= 0.0);
    }

    @Test
    void shouldAddCutRowsAndExposeCutDuals() {
        DynamicInstance instance = buildOriginOnlyInstance();
        List<Route> initialRoutes = Arrays.asList(
                buildRoute(0, 0, 10.0, 0.6, 0, 1, 3),
                buildRoute(0, 1, 20.0, 0.6, 0, 2, 3),
                buildRoute(0, 2, 15.0, 0.0, 0, 1, 2, 3)
        );

        LmSrc cut = new LmSrc(0.5, 3);
        cut.setId(99);
        cut.setSubSet(new gnu.trove.set.hash.TIntHashSet(new int[]{1, 2, 3}));
        cut.setMemorySet(Arrays.asList(new gnu.trove.set.hash.TIntHashSet(new int[]{1, 2, 3})));
        cut.setConstant(1.0);

        DVRPMasterProblem masterProblem = new DVRPMasterProblem();
        masterProblem.initialize(instance, initialRoutes, false);
        masterProblem.build();
        masterProblem.addCuts(Arrays.asList(cut));

        assertTrue(masterProblem.solve());
        assertEquals(1, masterProblem.getCutConstraintDuals().size());

        StageSolution stageSolution = new StageSolution();
        masterProblem.updateSolution(stageSolution);
        assertEquals(1, stageSolution.getCutConstraintDuals().size());
    }

    @Test
    void shouldUseVisitCountsForRepeatedCustomerColumns() {
        DynamicInstance instance = buildOriginOnlyInstance();
        instance.setRemainingCustomerIds(Arrays.asList(1));
        Route repeatedCustomerRoute = buildRoute(0, 0, 10.0, 0.0, 0, 1, 1, 3);

        DVRPMasterProblem masterProblem = new DVRPMasterProblem();
        masterProblem.initialize(instance, Arrays.asList(repeatedCustomerRoute), false);
        masterProblem.build();

        assertTrue(masterProblem.solve());
        assertEquals(5.0, masterProblem.getObjectiveValue(), 1e-6);
        assertEquals(0.5, masterProblem.getRoutes().get(0).getValue(), 1e-6);
    }

    @Test
    void selectedRouteFilterShouldIgnoreEmptyOriginRouteButKeepServedAndExternalRoutes() {
        DynamicInstance instance = buildInstanceWithExternalDepot();
        Route emptyOriginRoute = buildRoute(0, 0, 0.0, 1.0, 0, 3);
        Route servedOriginRoute = buildRoute(0, 1, 10.0, 1.0, 0, 1, 3);
        Route emptyExternalRoute = buildRoute(4, 2, 0.0, 1.0, 4, 3);

        assertFalse(DVRPMasterProblem.isSelectableSelectedRoute(instance, emptyOriginRoute));
        assertTrue(DVRPMasterProblem.isSelectableSelectedRoute(instance, servedOriginRoute));
        assertTrue(DVRPMasterProblem.isSelectableSelectedRoute(instance, emptyExternalRoute));
    }

    private Constraint buildRightBranchOnArc(int tail, int head, List<Route> routes) {
        Constraint constraint = new Constraint();
        constraint.setType("Right");
        constraint.setVariable(new Variable("qij", new int[]{tail, head}, 0.5));
        constraint.setCoeff_1(new TIntArrayList(new int[]{1}));
        TIntArrayList coeff2 = new TIntArrayList();
        QijBranching<Route, LmSrc, Graph> branching = new QijBranching<>();
        for (Route route : routes) {
            coeff2.add(branching.calculateVariableVisitedTimes(route, constraint.getVariable()));
        }
        constraint.setCoeff_2(Arrays.asList(coeff2));
        constraint.setRightSideValue(1);
        return constraint;
    }

    private DynamicInstance buildOriginOnlyInstance() {
        ProblemData problemData = new ProblemData();

        Depot originDepot = buildDepot(0, Depot.DepotType.ORIGIN, 2, 10);
        Depot returnDepot = buildDepot(3, Depot.DepotType.RETURN, 0, 10);

        problemData.setOriginDepot(originDepot);
        problemData.setReturnDepot(returnDepot);
        problemData.setVehicleCapacity(10);
        problemData.setInitialVehicleCount(2);
        problemData.setSpeed(1.0);
        problemData.setDistanceScale(1.0);
        problemData.setCustomers(new ArrayList<>());
        problemData.getCustomers().add(buildCustomer(1, 10.0, 0.0, 2));
        problemData.getCustomers().add(buildCustomer(2, 20.0, 0.0, 3));
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

    private DynamicInstance buildInstanceWithExternalDepot() {
        DynamicInstance instance = buildOriginOnlyInstance();
        Depot externalDepot = buildDepot(4, Depot.DepotType.EXTERNAL, 1, 8);
        externalDepot.setX(13.0);
        externalDepot.setEarliestTime(18.0);
        instance.setCurrentTime(18.0);
        instance.setExternalDepots(new ArrayList<Depot>(Arrays.asList(externalDepot)));
        return instance;
    }

    private Depot buildDepot(int id, Depot.DepotType type, int vehicleCount, int remainingCapacity) {
        Depot depot = new Depot();
        depot.setId(id);
        depot.setX(0.0);
        depot.setY(0.0);
        depot.setEarliestTime(0.0);
        depot.setLatestTime(100.0);
        depot.setType(type);
        depot.setVehicleCount(vehicleCount);
        depot.setRemainingCapacity(remainingCapacity);
        return depot;
    }

    private Customer buildCustomer(int id, double x, double y, int demand) {
        Customer customer = new Customer();
        customer.setId(id);
        customer.setX(x);
        customer.setY(y);
        customer.setDemand(demand);
        customer.setEarliestTime(0.0);
        customer.setLatestTime(100.0);
        customer.setServiceTime(5.0);
        customer.setCallInTime(0.0);
        return customer;
    }

    private Route buildRoute(int startDepotId, int index, double cost, double value, int... nodes) {
        Route route = new Route(startDepotId, index, 16);
        route.setVisitedVertices(new TIntArrayList(nodes));
        route.setCost(cost);
        route.setValue(value);
        return route;
    }

}

package org.zzc.bpcsolver.algorithm.pricing;

import gnu.trove.list.array.TIntArrayList;
import org.junit.jupiter.api.Test;
import org.zzc.bpcsolver.algorithm.branching.QijBranching;
import org.zzc.bpcsolver.algorithm.config.Configuration;
import org.zzc.bpcsolver.algorithm.cutting.LmSrc;
import org.zzc.bpcsolver.algorithm.factory.PricingStrategyFactory;
import org.zzc.bpcsolver.algorithm.pricing.HeuristicPricing;
import org.zzc.bpcsolver.core.branch.Constraint;
import org.zzc.bpcsolver.core.graph.Graph;
import org.zzc.bpcsolver.core.graph.Route;
import org.zzc.bpcsolver.core.graph.RouteFeasibilityValidator;
import org.zzc.bpcsolver.core.graph.Vertex;
import org.zzc.bpcsolver.core.branch.Variable;
import org.zzc.bpcsolver.problem.entity.Customer;
import org.zzc.bpcsolver.problem.entity.Depot;
import org.zzc.bpcsolver.problem.entity.DynamicInstance;
import org.zzc.bpcsolver.problem.entity.ProblemData;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForwardLabelingTest {

    @Test
    void shouldGenerateFeasibleRoutesFromOriginDepot() {
        DynamicInstance instance = buildInitialInstance();
        ForwardLabeling forwardLabeling = new ForwardLabeling();

        List<Route> routes = forwardLabeling.solve(instance, 0);

        assertFalse(routes.isEmpty());
        assertRoutesFeasible(instance, routes);
        assertTrue(containsRoute(routes, new int[]{0, 4}));
        assertTrue(containsRoute(routes, new int[]{0, 1, 4}));
        assertTrue(containsRoute(routes, new int[]{0, 2, 4}));
        assertFalse(containsRoute(routes, new int[]{0, 1, 2, 4}));
    }

    @Test
    void shouldGenerateFeasibleRoutesFromGraph() {
        DynamicInstance instance = buildInitialInstance();
        Graph graph = new Graph();
        graph.buildFromInstance(instance, 0);

        ForwardLabeling forwardLabeling = new ForwardLabeling();
        List<Route> routes = forwardLabeling.solve(graph);

        assertFalse(routes.isEmpty());
        assertRoutesFeasible(instance, routes);
        assertTrue(containsRoute(routes, new int[]{0, 4}));
        assertTrue(containsRoute(routes, new int[]{0, 1, 4}));
        assertTrue(containsRoute(routes, new int[]{0, 2, 4}));
        assertFalse(containsRoute(routes, new int[]{0, 1, 2, 4}));
    }

    @Test
    void shouldRespectCapacityAndTimeWindows() {
        DynamicInstance instance = buildInitialInstance();
        instance.getProblemData().getCustomers().get(1).setDemand(9);
        instance.getProblemData().getCustomers().get(1).setLatestTime(19.0);

        ForwardLabeling forwardLabeling = new ForwardLabeling();
        List<Route> routes = forwardLabeling.solve(instance, 0);

        assertRoutesFeasible(instance, routes);
        assertFalse(containsRoute(routes, new int[]{0, 2, 4}));
        assertFalse(containsRoute(routes, new int[]{0, 1, 2, 4}));
        assertTrue(containsRoute(routes, new int[]{0, 1, 4}));
    }

    @Test
    void shouldGenerateRoutesFromExternalDepot() {
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

        ForwardLabeling forwardLabeling = new ForwardLabeling();
        List<Route> routes = forwardLabeling.solve(instance, 5);

        assertRoutesFeasible(instance, routes);
        assertTrue(containsRoute(routes, new int[]{5, 4}));
        assertTrue(containsRoute(routes, new int[]{5, 2, 4}));
        assertFalse(containsRoute(routes, new int[]{5, 2, 3, 4}));

        Route route = findRoute(routes, new int[]{5, 2, 4});
        assertEquals(Arrays.asList(18.0, 25.0, 50.0), route.getArrivalTimes());
    }

    @Test
    void shouldHandleSparseExternalDepotIdsLikeCsharpDynamicStages() {
        DynamicInstance instance = buildInitialInstance();

        Depot externalDepot = new Depot();
        externalDepot.setId(29);
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

        ForwardLabeling forwardLabeling = new ForwardLabeling();
        List<Route> routes = forwardLabeling.solve(instance, 29);

        assertRoutesFeasible(instance, routes);
        assertTrue(containsRoute(routes, new int[]{29, 4}));
        assertTrue(containsRoute(routes, new int[]{29, 2, 4}));
    }


    @Test
    void shouldAccumulateReducedCostWithCustomerDuals() {
        DynamicInstance instance = buildInitialInstance();
        double[] nodeDuals = new double[5];
        nodeDuals[1] = 8.0;
        nodeDuals[2] = 7.0;

        ForwardLabeling forwardLabeling = new ForwardLabeling();
        List<Route> routes = forwardLabeling.solve(instance, 0, nodeDuals);

        assertRoutesFeasible(instance, routes);
        Route oneCustomerRoute = findRoute(routes, new int[]{0, 1, 4});
        assertEquals(20.0, oneCustomerRoute.getCost());
        assertEquals(12.0, oneCustomerRoute.getReducedCost());
        Route otherOneCustomerRoute = findRoute(routes, new int[]{0, 2, 4});
        assertEquals(40.0, otherOneCustomerRoute.getCost());
        assertEquals(33.0, otherOneCustomerRoute.getReducedCost());
    }

    @Test
    void shouldInitializeLabelWithFullStartDepotDualLikeLuckydragonMachineDual() {
        DynamicInstance instance = buildInitialInstance();
        Graph graph = new Graph();
        graph.buildFromInstance(instance, 0);
        double[] customerDuals = new double[5];
        double[] startDepotDuals = new double[5];
        startDepotDuals[0] = 6.0;

        ForwardLabeling forwardLabeling = new ForwardLabeling();
        Label label = forwardLabeling.createInitialLabel(graph, customerDuals, startDepotDuals);

        assertEquals(-6.0, label.getReducedCost(), 1e-6);
    }

    @Test
    void shouldReturnImprovingExternalDepotRouteFromStartDepotDual() {
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

        double[] customerDuals = new double[6];
        double[] startDepotDuals = new double[6];
        startDepotDuals[5] = 30.0;

        ForwardLabeling forwardLabeling = new ForwardLabeling();
        List<Route> improvingRoutes = forwardLabeling.solveImprovingRoutes(instance, 5, customerDuals, startDepotDuals);

        assertRoutesFeasible(instance, improvingRoutes);
        assertFalse(improvingRoutes.isEmpty());
        Route directReturnRoute = findRoute(improvingRoutes, new int[]{5, 4});
        assertEquals(-17.0, directReturnRoute.getReducedCost(), 1e-6);
    }

    @Test
    void shouldPriceExternalDepotCustomerRoutesWithFullStartDepotDual() {
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
        instance.setRemainingCustomerIds(Arrays.asList(2));

        double[] customerDuals = new double[6];
        double[] startDepotDuals = new double[6];
        startDepotDuals[5] = 30.0;

        ForwardLabeling forwardLabeling = new ForwardLabeling();
        List<Route> improvingRoutes = forwardLabeling.solveImprovingRoutes(instance, 5, customerDuals, startDepotDuals);

        assertRoutesFeasible(instance, improvingRoutes);
        Route customerRoute = findRoute(improvingRoutes, new int[]{5, 2, 4});
        assertEquals(27.0, customerRoute.getCost(), 1e-6);
        assertEquals(-3.0, customerRoute.getReducedCost(), 1e-6);

        double reducedCostWithoutStartDepotDual = customerRoute.getCost();
        assertTrue(reducedCostWithoutStartDepotDual > 0.0);
    }

    @Test
    void shouldReturnOnlyNegativeReducedCostRoutesWhenFilteringImprovingRoutes() {
        DynamicInstance instance = buildInitialInstance();
        double[] nodeDuals = new double[5];
        nodeDuals[1] = 25.0;
        nodeDuals[2] = 50.0;

        ForwardLabeling forwardLabeling = new ForwardLabeling();
        List<Route> improvingRoutes = forwardLabeling.solveImprovingRoutes(instance, 0, nodeDuals);

        assertRoutesFeasible(instance, improvingRoutes);
        assertFalse(improvingRoutes.isEmpty());
        assertTrue(containsRoute(improvingRoutes, new int[]{0, 1, 4}));
        assertTrue(containsRoute(improvingRoutes, new int[]{0, 2, 4}));
        assertTrue(containsRoute(improvingRoutes, new int[]{0, 1, 2, 4}));
        assertFalse(containsRoute(improvingRoutes, new int[]{0, 4}));
        for (Route route : improvingRoutes) {
            assertTrue(route.getReducedCost() < 0.0);
        }
    }

    @Test
    void lightHeuristicPricingShouldReturnFeasibleImprovingRoutes() {
        DynamicInstance instance = buildInitialInstance();
        Graph graph = new Graph();
        graph.buildFromInstance(instance, 0);
        Configuration config = buildPricingConfiguration("light labeling");
        double[] customerDuals = new double[5];
        customerDuals[1] = 25.0;
        customerDuals[2] = 50.0;

        List<Route> routes = PricingStrategyFactory.createHeuristicPricingAlgorithm(config).solve(
                graph,
                0,
                new ArrayList<>(),
                new ArrayList<>(),
                0.0,
                customerDuals,
                new ArrayList<>(),
                new ArrayList<>(),
                null,
                config,
                null,
                1
        );

        assertRoutesFeasible(instance, routes);
        assertFalse(routes.isEmpty());
        for (Route route : routes) {
            assertTrue(route.getReducedCost() < 0.0);
        }
    }

    @Test
    void relaxedHeuristicPricingShouldReturnFeasibleImprovingRoutes() {
        DynamicInstance instance = buildInitialInstance();
        Graph graph = new Graph();
        graph.buildFromInstance(instance, 0);
        Configuration config = buildPricingConfiguration("relaxed labeling");
        double[] customerDuals = new double[5];
        customerDuals[1] = 25.0;
        customerDuals[2] = 50.0;

        List<Route> routes = PricingStrategyFactory.createHeuristicPricingAlgorithm(config).solve(
                graph,
                0,
                new ArrayList<>(),
                new ArrayList<>(),
                0.0,
                customerDuals,
                new ArrayList<>(),
                new ArrayList<>(),
                null,
                config,
                null,
                1
        );

        assertRoutesFeasible(instance, routes);
        assertFalse(routes.isEmpty());
        for (Route route : routes) {
            assertTrue(route.getReducedCost() < 0.0);
        }
    }

    @Test
    void multiStageHeuristicShouldSwitchLightThenRelaxedThenExact() {
        Configuration config = buildPricingConfiguration("multi-stage heuristic pricing");
        HeuristicPricing<Route, Graph, LmSrc> heuristicPricing =
                PricingStrategyFactory.createHeuristicPricingAlgorithm(config);

        heuristicPricing.reset(config);

        assertEquals("light labeling", heuristicPricing.getCurrentHeuristic(config));
        assertEquals("Heuristic Pricing", heuristicPricing.switchHeuristic(config, "Heuristic Pricing"));
        assertEquals("relaxed labeling", heuristicPricing.getCurrentHeuristic(config));
        assertEquals("Exact Pricing", heuristicPricing.switchHeuristic(config, "Heuristic Pricing"));
    }

    @Test
    void shouldAccumulateReducedCostAcrossMultipleLabelExtensions() {
        DynamicInstance instance = buildInitialInstance();
        Graph graph = new Graph();
        graph.buildFromInstance(instance, 0);
        double[] nodeDuals = new double[5];
        nodeDuals[1] = 8.0;
        nodeDuals[2] = 7.0;

        ForwardLabeling forwardLabeling = new ForwardLabeling();
        Label label = forwardLabeling.createInitialLabel(graph, nodeDuals);

        assertTrue(forwardLabeling.labelExtend(label, graph.findVertexById(1), graph, nodeDuals));
        assertEquals(10.0, label.getCost());
        assertEquals(6.0, label.getReducedCost());
        assertEquals(10.0, label.getStartServiceTime());

        assertTrue(forwardLabeling.labelExtend(label, graph.findVertexById(2), graph, nodeDuals));
        assertEquals(20.0, label.getCost());
        assertEquals(8.5, label.getReducedCost());
        assertEquals(25.0, label.getStartServiceTime());
    }

    @Test
    void shouldRejectRepeatedCustomerWhenRelaxationIsNone() {
        DynamicInstance instance = buildInitialInstance();
        Graph graph = new Graph();
        graph.buildFromInstance(instance, 0);
        ForwardLabeling forwardLabeling = new ForwardLabeling();
        Label label = forwardLabeling.createInitialLabel(graph, new double[5]);

        assertTrue(forwardLabeling.labelExtend(label, graph.findVertexById(1), graph, new double[5]));
        assertTrue(forwardLabeling.labelExtend(label, graph.findVertexById(2), graph, new double[5]));
        assertFalse(forwardLabeling.labelExtend(label, graph.findVertexById(1), graph, new double[5]));
    }

    @Test
    void shouldUseNgMemoryRuleWhenRelaxationIsEnabled() {
        DynamicInstance instance = buildInitialInstance();
        Graph graph = new Graph();
        graph.buildFromInstance(instance, 0);
        graph.updateNgSets(1);
        Configuration config = buildPricingConfiguration("none");
        config.getRelaxation().setRelaxationSwitch("ng-route");
        config.getRelaxation().setNgSize(1);
        ForwardLabeling forwardLabeling = new ForwardLabeling();
        Label label = forwardLabeling.createInitialLabel(graph, new double[5]);

        assertTrue(forwardLabeling.labelExtend(label, graph.findVertexById(1), graph, new double[5],
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), null, config));
        assertTrue(label.getNgSet().contains(1));
        assertTrue(forwardLabeling.labelExtend(label, graph.findVertexById(2), graph, new double[5],
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), null, config));
        assertFalse(label.getNgSet().contains(1));
        assertTrue(label.getNgSet().contains(2));
        assertTrue(forwardLabeling.labelExtend(label, graph.findVertexById(1), graph, new double[5],
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), null, config));
        assertEquals(2, label.getVisitedVector()[1]);
    }

    @Test
    void shouldReturnEmptyImprovingRoutesWhenNoNegativeReducedCostExists() {
        DynamicInstance instance = buildInitialInstance();
        double[] nodeDuals = new double[5];

        ForwardLabeling forwardLabeling = new ForwardLabeling();
        List<Route> improvingRoutes = forwardLabeling.solveImprovingRoutes(instance, 0, nodeDuals);

        assertTrue(improvingRoutes.isEmpty());
    }

    @Test
    void shouldApplyBranchDualToReducedCost() {
        DynamicInstance instance = buildInitialInstance();
        Graph graph = new Graph();
        graph.buildFromInstance(instance, 0);
        double[] customerDuals = new double[5];
        customerDuals[1] = 25.0;
        customerDuals[2] = 25.0;

        ForwardLabeling forwardLabeling = new ForwardLabeling();
        List<Route> routes = forwardLabeling.solveImprovingRoutes(
                graph,
                customerDuals,
                new double[5],
                Arrays.asList(buildRightBranch(1, 2)),
                Arrays.asList(10.0),
                new QijBranching<Route, LmSrc, Graph>()
        );

        assertRoutesFeasible(instance, routes);
        Route branchedRoute = findRoute(routes, new int[]{0, 1, 2, 4});
        assertEquals(-20.0, branchedRoute.getReducedCost(), 1e-6);
    }

    @Test
    void shouldForbidLeftBranchArcWithoutOverFilteringRightBranch() {
        DynamicInstance instance = buildInitialInstance();
        Graph graph = new Graph();
        graph.buildFromInstance(instance, 0);
        ForwardLabeling forwardLabeling = new ForwardLabeling();

        Label leftLabel = forwardLabeling.createInitialLabel(graph, new double[5]);
        assertTrue(forwardLabeling.labelExtend(leftLabel, graph.findVertexById(1), graph, new double[5]));
        assertFalse(forwardLabeling.labelExtend(
                leftLabel,
                graph.findVertexById(2),
                graph,
                new double[5],
                Arrays.asList(buildLeftBranch(1, 2)),
                new ArrayList<>(),
                new QijBranching<Route, LmSrc, Graph>()
        ));

        Label rightBranchAlternativeArc = forwardLabeling.createInitialLabel(graph, new double[5]);
        assertTrue(forwardLabeling.labelExtend(rightBranchAlternativeArc, graph.findVertexById(1), graph, new double[5]));
        assertTrue(forwardLabeling.labelExtend(
                rightBranchAlternativeArc,
                graph.findVertexById(4),
                graph,
                new double[5],
                Arrays.asList(buildRightBranch(1, 2)),
                new ArrayList<>(),
                new QijBranching<Route, LmSrc, Graph>()
        ));

        Label rightLabelToRequiredHead = forwardLabeling.createInitialLabel(graph, new double[5]);
        assertTrue(forwardLabeling.labelExtend(rightLabelToRequiredHead, graph.findVertexById(1), graph, new double[5]));
        assertTrue(forwardLabeling.labelExtend(
                rightLabelToRequiredHead,
                graph.findVertexById(2),
                graph,
                new double[5],
                Arrays.asList(buildRightBranch(1, 2)),
                new ArrayList<>(),
                new QijBranching<Route, LmSrc, Graph>()
        ));
    }

    @Test
    void vertexDominanceShouldCompareDifferentStartDepotsByDominanceGroupAndCapacityUsed() {
        Vertex vertex = new Vertex();
        Label originLabel = buildLabel(2, 10.0, 3.0, 20.0, 1, 2);
        originLabel.setId(0);
        Label externalLabel = buildLabel(2, 12.0, 4.0, 25.0, 1, 2, 3);
        externalLabel.setId(5);
        originLabel.setDominanceGroupId(-1);
        externalLabel.setDominanceGroupId(-1);
        originLabel.setCapacityUsed(3.0);
        externalLabel.setCapacityUsed(4.0);

        vertex.getLabelSet().add(originLabel);

        assertTrue(vertex.isDominatedByExistingLabel(externalLabel));
        assertFalse(vertex.addNonDominatedLabel(externalLabel));
        assertEquals(1, vertex.getLabelSet().size());
    }

    @Test
    void vertexDominanceShouldNotCompareDifferentDominanceGroups() {
        Vertex vertex = new Vertex();
        Label originLabel = buildLabel(2, 10.0, 3.0, 20.0, 0, 1, 2);
        originLabel.setId(0);
        originLabel.setDominanceGroupId(0);
        Label externalLabel = buildLabel(2, 12.0, 4.0, 25.0, 1, 2, 3);
        externalLabel.setId(5);
        externalLabel.setDominanceGroupId(5);

        vertex.getLabelSet().add(originLabel);

        assertFalse(vertex.isDominatedByExistingLabel(externalLabel));
        assertTrue(vertex.addNonDominatedLabel(externalLabel));
        assertEquals(2, vertex.getLabelSet().size());
    }

    @Test
    void shouldRejectDominatedLabelByNgSetRule() {
        Vertex vertex = new Vertex();
        Label benchmark = buildLabel(2, 10.0, 3.0, 20.0, 0, 1, 2);
        Label candidate = buildLabel(2, 12.0, 4.0, 25.0, 0, 1, 2, 3);

        vertex.getLabelSet().add(benchmark);
        assertTrue(vertex.isDominatedByExistingLabel(candidate));
        assertEquals(1, vertex.getLabelSet().size());
        assertTrue(vertex.getLabelSet().contains(benchmark));
    }

    @Test
    void shouldRemoveExistingDominatedLabelByNgSetRule() {
        Vertex vertex = new Vertex();
        Label weakLabel = buildLabel(2, 12.0, 4.0, 25.0, 0, 1, 2, 3);
        Label strongLabel = buildLabel(2, 10.0, 3.0, 20.0, 0, 1, 2);

        vertex.getLabelSet().add(weakLabel);
        vertex.removeDominatedLabels(strongLabel);
        vertex.getLabelSet().add(strongLabel);
        assertEquals(1, vertex.getLabelSet().size());
        assertTrue(vertex.getLabelSet().contains(strongLabel));
    }

    @Test
    void shouldAddNonDominatedLabelThroughVertexApi() {
        Vertex vertex = new Vertex();
        Label weakLabel = buildLabel(2, 12.0, 4.0, 25.0, 0, 1, 2, 3);
        Label strongLabel = buildLabel(2, 10.0, 3.0, 20.0, 0, 1, 2);

        vertex.getLabelSet().add(weakLabel);

        assertTrue(vertex.addNonDominatedLabel(strongLabel));
        assertEquals(1, vertex.getLabelSet().size());
        assertTrue(vertex.getLabelSet().contains(strongLabel));
    }

    private boolean containsRoute(List<Route> routes, int[] nodeIds) {
        return findRoute(routes, nodeIds) != null;
    }

    private Route findRoute(List<Route> routes, int[] nodeIds) {
        for (Route route : routes) {
            if (Arrays.equals(route.getVisitedVertices().toArray(), nodeIds)) {
                return route;
            }
        }
        return null;
    }

    private void assertRoutesFeasible(DynamicInstance instance, List<Route> routes) {
        RouteFeasibilityValidator validator = new RouteFeasibilityValidator();
        for (Route route : routes) {
            List<String> violations = validator.findRouteViolations(instance, route);
            assertTrue(violations.isEmpty(), () -> "Route " + route.getVisitedVertices()
                    + " should be feasible, violations=" + violations);
        }
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

    private Configuration buildPricingConfiguration(String heuristicPricingRule) {
        Configuration config = new Configuration();

        Configuration.PricingConfig pricing = new Configuration.PricingConfig();
        pricing.setPricingSwitch("forward labeling");
        pricing.setHeuristicPricing(heuristicPricingRule);
        config.setPricing(pricing);

        Configuration.RelaxationConfig relaxation = new Configuration.RelaxationConfig();
        relaxation.setRelaxationSwitch("none");
        relaxation.setMaxNgSize(4);
        config.setRelaxation(relaxation);

        return config;
    }

    private Label buildLabel(int lastNodeId, double reducedCost, double load, double startServiceTime, int... ngNodes) {
        Label label = new Label(0, 0, 16);
        label.setLastNodeId(lastNodeId);
        label.setReducedCost(reducedCost);
        label.setLoad(load);
        label.setCapacityUsed(load);
        label.setStartServiceTime(startServiceTime);
        for (int ngNode : ngNodes) {
            label.addNgNode(ngNode);
        }
        return label;
    }

    private Constraint buildRightBranch(int tail, int head) {
        Constraint constraint = new Constraint();
        constraint.setType("Right");
        constraint.setVariable(new Variable("qij", new int[]{tail, head}, 0.5));
        constraint.setCoeff_1(new TIntArrayList(new int[]{1}));
        constraint.setCoeff_2(new ArrayList<>());
        constraint.setRightSideValue(1);
        return constraint;
    }

    private Constraint buildLeftBranch(int tail, int head) {
        Constraint constraint = new Constraint();
        constraint.setType("Left");
        constraint.setVariable(new Variable("qij", new int[]{tail, head}, 0.5));
        constraint.setCoeff_1(new TIntArrayList(new int[]{-1}));
        constraint.setCoeff_2(new ArrayList<>());
        constraint.setRightSideValue(0);
        return constraint;
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

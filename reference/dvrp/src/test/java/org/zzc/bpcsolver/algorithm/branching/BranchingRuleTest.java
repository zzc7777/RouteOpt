package org.zzc.bpcsolver.algorithm.branching;

import gnu.trove.list.array.TIntArrayList;
import org.junit.jupiter.api.Test;
import org.zzc.bpcsolver.algorithm.branching.variableSelection.StrongBranching;
import org.zzc.bpcsolver.algorithm.branching.variableSelection.StrongBranchingEvaluator;
import org.zzc.bpcsolver.algorithm.branching.variableSelection.VariableSelectionStrategy;
import org.zzc.bpcsolver.algorithm.config.Configuration;
import org.zzc.bpcsolver.algorithm.cutting.LmSrc;
import org.zzc.bpcsolver.algorithm.factory.BranchingStrategyFactory;
import org.zzc.bpcsolver.algorithm.logger.BPCLogger;
import org.zzc.bpcsolver.core.branch.Node;
import org.zzc.bpcsolver.core.branch.Constraint;
import org.zzc.bpcsolver.core.branch.Solution;
import org.zzc.bpcsolver.core.branch.Variable;
import org.zzc.bpcsolver.core.graph.Graph;
import org.zzc.bpcsolver.core.graph.Route;
import org.zzc.bpcsolver.problem.entity.DynamicInstance;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class BranchingRuleTest {

    @Test
    void qijBranchingShouldUseRealDvrpNodeIdsForFractionalArcs() {
        Node<Route, LmSrc, Graph> node = buildNodeWithSparseCustomerIds();
        QijBranching<Route, LmSrc, Graph> branchingRule = new QijBranching<>();

        List<Variable> fractionalVariables = branchingRule.getFractionalVariables(1, 2, node);

        assertNotNull(findVariable(fractionalVariables, "qij", 0, 2));
        Variable sparseArc = findVariable(fractionalVariables, "qij", 2, 4);
        assertNotNull(sparseArc);
        assertEquals(0.5, sparseArc.getValue(), 1e-6);
        assertNotNull(findVariable(fractionalVariables, "qij", 4, 5));
    }

    @Test
    void xkijBranchingShouldUseRealDvrpNodeIdsForFractionalArcs() {
        Node<Route, LmSrc, Graph> node = buildNodeWithSparseCustomerIds();
        XkijBranching<Route, LmSrc, Graph> branchingRule = new XkijBranching<>();

        List<Variable> fractionalVariables = branchingRule.getFractionalVariables(1, 2, node);

        assertNotNull(findVariable(fractionalVariables, "xkij", 0, 0, 2));
        Variable sparseArc = findVariable(fractionalVariables, "xkij", 0, 2, 4);
        assertNotNull(sparseArc);
        assertEquals(0.5, sparseArc.getValue(), 1e-6);
        assertNotNull(findVariable(fractionalVariables, "xkij", 0, 4, 5));
    }

    @Test
    void xkijBranchingShouldTreatIntegralArcCountsAboveOneAsIntegral() {
        Node<Route, LmSrc, Graph> node = buildNodeWithRepeatedIntegralArcCounts();
        XkijBranching<Route, LmSrc, Graph> branchingRule = new XkijBranching<>();

        assertEquals(0, branchingRule.getFractionalVariables(1, 2, node).size());
    }

    @Test
    void qijBranchingShouldIgnoreDirectDepotReturnRoutesWithoutCustomers() {
        Node<Route, LmSrc, Graph> node = new Node<>();
        Route directReturn = new Route(4, 0, 8);
        directReturn.setVisitedVertices(new TIntArrayList(new int[]{4, 5}));
        node.setUsedScheduleSet(new ArrayList<>(Arrays.asList(Arrays.asList(directReturn))));

        Solution<Route> solution = new Solution<>(1, 0);
        solution.setYks(new ArrayList<>(Arrays.asList(new double[]{0.5})));
        node.setSolution(solution);

        QijBranching<Route, LmSrc, Graph> branchingRule = new QijBranching<>();

        assertEquals(0, branchingRule.getFractionalVariables(1, 0, node).size());
    }

    @Test
    void qijBranchingShouldTreatIntegralArcCountsAboveOneAsIntegral() {
        Node<Route, LmSrc, Graph> node = buildNodeWithRepeatedIntegralArcCounts();
        QijBranching<Route, LmSrc, Graph> branchingRule = new QijBranching<>();

        assertEquals(0, branchingRule.getFractionalVariables(1, 2, node).size());
    }

    @Test
    void vehicleCountThenArcBranchingShouldBranchOnFractionalRouteCountFirst() {
        Node<Route, LmSrc, Graph> node = buildNodeWithFractionalVehicleCount();
        VehicleCountThenArcBranching<Route, LmSrc, Graph> branchingRule =
                new VehicleCountThenArcBranching<>(new QijBranching<>());

        List<Variable> fractionalVariables = branchingRule.getFractionalVariables(1, 2, node);

        assertEquals(1, fractionalVariables.size());
        Variable vehicleCount = fractionalVariables.get(0);
        assertEquals(VehicleCountThenArcBranching.VEHICLE_COUNT_TYPE, vehicleCount.getType());
        assertEquals(1.5, vehicleCount.getValue(), 1e-6);
    }

    @Test
    void vehicleCountThenArcBranchingShouldDelegateToArcBranchingWhenRouteCountIsIntegral() {
        Node<Route, LmSrc, Graph> node = buildNodeWithIntegralVehicleCountAndFractionalArc();
        VehicleCountThenArcBranching<Route, LmSrc, Graph> branchingRule =
                new VehicleCountThenArcBranching<>(new QijBranching<>());

        List<Variable> fractionalVariables = branchingRule.getFractionalVariables(1, 2, node);

        Variable sparseArc = findVariable(fractionalVariables, "qij", 2, 4);
        assertNotNull(sparseArc);
        assertEquals(0.5, sparseArc.getValue(), 1e-6);
    }

    @Test
    void vehicleCountThenArcBranchingShouldTreatVehicleCountAboveOneAsIntegral() {
        Node<Route, LmSrc, Graph> node = buildNodeWithTwoIntegralRoutes();
        VehicleCountThenArcBranching<Route, LmSrc, Graph> branchingRule =
                new VehicleCountThenArcBranching<>(new QijBranching<>());

        List<Variable> fractionalVariables = branchingRule.getFractionalVariables(1, 2, node);

        assertEquals(0, fractionalVariables.size());
    }

    @Test
    void vehicleCountBranchingConstraintShouldUseOneCoefficientPerRoute() {
        Node<Route, LmSrc, Graph> node = buildNodeWithFractionalVehicleCount();
        VehicleCountThenArcBranching<Route, LmSrc, Graph> branchingRule =
                new VehicleCountThenArcBranching<>(new QijBranching<>());
        Variable vehicleCount = new Variable(VehicleCountThenArcBranching.VEHICLE_COUNT_TYPE, new int[0], 1.5);

        Constraint left = branchingRule.createBranchingConstraint(node, 1, vehicleCount, 0);
        Constraint right = branchingRule.createBranchingConstraint(node, 1, vehicleCount, 1);

        assertEquals("Left", left.getType());
        assertEquals(-1, left.getCoeff_1().get(0));
        assertEquals(-1, left.getRightSideValue());
        assertEquals(1, branchingRule.calculateVariableVisitedTimes(node.getUsedScheduleSet().get(0).get(0), vehicleCount));
        assertEquals("Right", right.getType());
        assertEquals(1, right.getCoeff_1().get(0));
        assertEquals(2, right.getRightSideValue());
    }

    @Test
    void vehicleCountBranchDualShouldBeAppliedOnceAtPricingInitialization() {
        VehicleCountThenArcBranching<Route, LmSrc, Graph> branchingRule =
                new VehicleCountThenArcBranching<>(new QijBranching<>());
        Constraint right = new Constraint();
        right.setVariable(new Variable(VehicleCountThenArcBranching.VEHICLE_COUNT_TYPE, new int[0], 1.5));
        right.setCoeff_1(new TIntArrayList(new int[]{1}));

        double reducedCost = branchingRule.updateInitialReducedCost(0, right, 2.5, 10.0);
        double unchangedAfterArc = branchingRule.updateReducedCost(0, 0, 1, right, 2.5, reducedCost);

        assertEquals(7.5, reducedCost, 1e-6);
        assertEquals(7.5, unchangedAfterArc, 1e-6);
    }

    @Test
    void branchRouteFilterShouldMatchLuckydragonNoHardFilteringSemantics() {
        Route route = new Route(0, 0, 6);
        route.setVisitedVertices(new TIntArrayList(new int[]{0, 3, 4, 5}));
        Constraint rightBranch = new Constraint();
        rightBranch.setType("Right");
        rightBranch.setVariable(new Variable("qij", new int[]{0, 2}, 0.5));
        rightBranch.setCoeff_1(new TIntArrayList(new int[]{1}));

        assertEquals(true, BranchingRouteFilter.isArcAllowed(0, 0, 2, Arrays.asList(buildLeftBranch(0, 2))));
        assertEquals(true, BranchingRouteFilter.isRouteAllowed(route, 0, Arrays.asList(buildLeftBranch(0, 3))));
        assertEquals(true, BranchingRouteFilter.isArcAllowed(0, 0, 3, Arrays.asList(rightBranch)));
        assertEquals(true, BranchingRouteFilter.isRouteAllowed(route, 0, Arrays.asList(rightBranch)));
    }

    @Test
    void strongBranchingFactoryShouldReturnSupportedStrategy() {
        Configuration config = new Configuration();
        Configuration.BranchingConfig branchingConfig = new Configuration.BranchingConfig();
        branchingConfig.setVariableSelectionRule("strong branching");
        config.setBranching(branchingConfig);
        VariableSelectionStrategy<DynamicInstance, Route, LmSrc, Graph> strategy =
                BranchingStrategyFactory.createVariableSelectionStrategy(config);

        assertEquals("strong branching", strategy.getStrategyName());
    }

    @Test
    void defaultBranchingFactoryShouldUseVehicleCountThenConfiguredArcBranching() {
        Configuration config = new Configuration();
        Configuration.BranchingConfig branchingConfig = new Configuration.BranchingConfig();
        branchingConfig.setBranchingSwitch("branch-and-bound");
        branchingConfig.setBranchVariable("xkij");
        config.setBranching(branchingConfig);
        BranchingRule<Route, LmSrc, Graph> branchingRule = BranchingStrategyFactory.createBranchingRule(config);

        assertEquals("vehicle-count then xkij branching", branchingRule.getStrategyName());
    }

    @Test
    void strongBranchingShouldSelectBestCandidateAfterHeuristicEvaluation() {
        Configuration config = buildStrongBranchingConfig(3, 2);
        StrongBranching<DynamicInstance, Route, LmSrc, Graph> strategy = new StrongBranching<>();
        FakeStrongBranchingEvaluator evaluator = new FakeStrongBranchingEvaluator();
        strategy.Initialize(null, config, null, null);
        strategy.setStrongBranchingEvaluator(evaluator);

        Node<Route, LmSrc, Graph> parentNode = new Node<>();
        Solution<Route> parentSolution = new Solution<>(1, 5);
        parentSolution.setObjValue(10.0);
        parentNode.setSolution(parentSolution);

        Variable lowAfterPhase3 = new Variable("qij", new int[]{1, 2}, 0.5);
        Variable highAfterPhase3 = new Variable("qij", new int[]{2, 3}, 0.5);
        Variable filteredAfterPhase2 = new Variable("qij", new int[]{3, 4}, 0.5);

        Variable selected = strategy.selectBranchingVariable(
                Arrays.asList(lowAfterPhase3, highAfterPhase3, filteredAfterPhase2),
                parentNode,
                null
        );

        assertEquals(highAfterPhase3, selected);
        assertEquals(6, evaluator.restrictedMasterSolves);
        assertEquals(4, evaluator.heuristicEvaluations);
    }

    @Test
    void zzcBpcYamlShouldStayOnSupportedDvrpStrategies() throws Exception {
        Configuration config = Configuration.loadFromFile("src/main/java/org/zzc/bpcsolver/algorithm/config/bpc_solver.yaml");

        assertEquals("none", config.getStabilization().getStabilizationSwitch());
        assertEquals("qij", config.getBranching().getBranchVariable());
        assertEquals("most fractional branching", config.getBranching().getVariableSelectionRule());
        assertEquals("forward labeling", config.getPricing().getPricingSwitch());
        assertFalse(config.getPricing().isHeuristicEnabled());
        assertEquals("none", config.getVariableFixing().getVariableFixingSwitch());
    }

    private Node<Route, LmSrc, Graph> buildNodeWithSparseCustomerIds() {
        Node<Route, LmSrc, Graph> node = new Node<>();
        Route route = new Route(0, 0, 8);
        route.setVisitedVertices(new TIntArrayList(new int[]{0, 2, 4, 5}));
        route.setValue(0.5);

        List<Route> routes = new ArrayList<>();
        routes.add(route);
        node.setUsedScheduleSet(new ArrayList<>(Arrays.asList(routes)));

        Solution<Route> solution = new Solution<>(1, 2);
        solution.setYks(new ArrayList<>(Arrays.asList(new double[]{0.5})));
        node.setSolution(solution);
        return node;
    }

    private Node<Route, LmSrc, Graph> buildNodeWithFractionalVehicleCount() {
        Node<Route, LmSrc, Graph> node = new Node<>();
        Route firstRoute = new Route(0, 0, 8);
        firstRoute.setVisitedVertices(new TIntArrayList(new int[]{0, 2, 5}));
        Route secondRoute = new Route(0, 1, 8);
        secondRoute.setVisitedVertices(new TIntArrayList(new int[]{0, 4, 5}));
        node.setUsedScheduleSet(new ArrayList<>(Arrays.asList(Arrays.asList(firstRoute, secondRoute))));

        Solution<Route> solution = new Solution<>(1, 2);
        solution.setYks(new ArrayList<>(Arrays.asList(new double[]{0.5, 1.0})));
        node.setSolution(solution);
        return node;
    }

    private Node<Route, LmSrc, Graph> buildNodeWithIntegralVehicleCountAndFractionalArc() {
        Node<Route, LmSrc, Graph> node = new Node<>();
        Route firstRoute = new Route(0, 0, 8);
        firstRoute.setVisitedVertices(new TIntArrayList(new int[]{0, 2, 4, 5}));
        Route secondRoute = new Route(0, 1, 8);
        secondRoute.setVisitedVertices(new TIntArrayList(new int[]{0, 4, 2, 5}));
        node.setUsedScheduleSet(new ArrayList<>(Arrays.asList(Arrays.asList(firstRoute, secondRoute))));

        Solution<Route> solution = new Solution<>(1, 2);
        solution.setYks(new ArrayList<>(Arrays.asList(new double[]{0.5, 0.5})));
        node.setSolution(solution);
        return node;
    }

    private Node<Route, LmSrc, Graph> buildNodeWithRepeatedIntegralArcCounts() {
        Node<Route, LmSrc, Graph> node = new Node<>();
        Route firstRoute = new Route(0, 0, 8);
        firstRoute.setVisitedVertices(new TIntArrayList(new int[]{0, 2, 4, 5}));
        Route secondRoute = new Route(0, 1, 8);
        secondRoute.setVisitedVertices(new TIntArrayList(new int[]{0, 2, 4, 5}));
        node.setUsedScheduleSet(new ArrayList<>(Arrays.asList(Arrays.asList(firstRoute, secondRoute))));

        Solution<Route> solution = new Solution<>(1, 2);
        solution.setYks(new ArrayList<>(Arrays.asList(new double[]{1.0, 1.0})));
        node.setSolution(solution);
        return node;
    }

    private Node<Route, LmSrc, Graph> buildNodeWithTwoIntegralRoutes() {
        Node<Route, LmSrc, Graph> node = new Node<>();
        Route firstRoute = new Route(0, 0, 8);
        firstRoute.setVisitedVertices(new TIntArrayList(new int[]{0, 2, 5}));
        Route secondRoute = new Route(0, 1, 8);
        secondRoute.setVisitedVertices(new TIntArrayList(new int[]{0, 4, 5}));
        node.setUsedScheduleSet(new ArrayList<>(Arrays.asList(Arrays.asList(firstRoute, secondRoute))));

        Solution<Route> solution = new Solution<>(1, 2);
        solution.setYks(new ArrayList<>(Arrays.asList(new double[]{1.0, 1.0})));
        node.setSolution(solution);
        return node;
    }

    private Variable findVariable(List<Variable> variables, String type, int... indexes) {
        for (Variable variable : variables) {
            if (type.equals(variable.getType()) && Arrays.equals(indexes, variable.getIndexes())) {
                return variable;
            }
        }
        return null;
    }

    private Constraint buildLeftBranch(int tail, int head) {
        Constraint leftBranch = new Constraint();
        leftBranch.setType("Left");
        leftBranch.setVariable(new Variable("qij", new int[]{tail, head}, 0.5));
        leftBranch.setCoeff_1(new TIntArrayList(new int[]{-1}));
        return leftBranch;
    }

    private Configuration buildStrongBranchingConfig(int limit1, int limit2) {
        Configuration config = new Configuration();
        Configuration.BranchingConfig branching = new Configuration.BranchingConfig();
        branching.setVariableSelectionRule("strong branching");
        branching.setNumCandidateVariables1(limit1);
        branching.setNumCandidateVariables2(limit2);
        config.setBranching(branching);
        return config;
    }

    private static final class FakeStrongBranchingEvaluator
            implements StrongBranchingEvaluator<Route, LmSrc, Graph> {

        private int restrictedMasterSolves;
        private int heuristicEvaluations;

        @Override
        public Node<Route, LmSrc, Graph> solveRestrictedMasterChild(Node<Route, LmSrc, Graph> parentNode,
                                                                    Variable branchingVar,
                                                                    int branchFlag) {
            restrictedMasterSolves++;
            int tail = branchingVar.getIndexes()[0];
            if (tail == 1) {
                return childWithObjective(13.0);
            }
            if (tail == 2) {
                return childWithObjective(12.0);
            }
            return childWithObjective(11.0);
        }

        @Override
        public Node<Route, LmSrc, Graph> runHeuristicColumnGeneration(Node<Route, LmSrc, Graph> childNode,
                                                                      BPCLogger logger) {
            heuristicEvaluations++;
            double objective = childNode.getSolution().getObjValue();
            childNode.getSolution().setObjValue(objective == 12.0 ? 17.0 : 14.0);
            return childNode;
        }

        private Node<Route, LmSrc, Graph> childWithObjective(double objective) {
            Node<Route, LmSrc, Graph> childNode = new Node<>();
            Solution<Route> solution = new Solution<>(1, 5);
            solution.setObjValue(objective);
            childNode.setSolution(solution);
            childNode.setFeasible(true);
            return childNode;
        }
    }
}

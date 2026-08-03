package org.zzc.bpcsolver.algorithm;

import gnu.trove.list.array.TIntArrayList;
import org.junit.jupiter.api.Test;
import org.zzc.bpcsolver.algorithm.acceleration.DivingHeuristic;
import org.zzc.bpcsolver.algorithm.acceleration.ScheduleEnumeration;
import org.zzc.bpcsolver.algorithm.acceleration.VariableFixing;
import org.zzc.bpcsolver.algorithm.branching.BranchingRule;
import org.zzc.bpcsolver.algorithm.factory.PricingStrategyFactory;
import org.zzc.bpcsolver.algorithm.config.Configuration;
import org.zzc.bpcsolver.algorithm.cutting.Cutting;
import org.zzc.bpcsolver.algorithm.pricing.ExactPricing;
import org.zzc.bpcsolver.algorithm.pricing.HeuristicPricing;
import org.zzc.bpcsolver.core.branch.ColumnGenerationStatus;
import org.zzc.bpcsolver.core.branch.StageSolution;
import org.zzc.bpcsolver.core.branch.Node;
import org.zzc.bpcsolver.algorithm.cutting.LmSrc;
import org.zzc.bpcsolver.core.branch.Solution;
import org.zzc.bpcsolver.core.graph.Graph;
import org.zzc.bpcsolver.core.graph.Route;
import org.zzc.bpcsolver.core.branch.Variable;
import org.zzc.bpcsolver.algorithm.logger.BPCLogger;
import org.zzc.bpcsolver.model.DVRPMasterProblem;
import org.zzc.bpcsolver.problem.config.ProblemConfig;
import org.zzc.bpcsolver.problem.config.ProblemConfigReader;
import org.zzc.bpcsolver.problem.entity.Customer;
import org.zzc.bpcsolver.problem.entity.Depot;
import org.zzc.bpcsolver.problem.entity.DynamicInstance;
import org.zzc.bpcsolver.problem.entity.ProblemData;
import org.zzc.bpcsolver.problem.loader.ProblemDataLoader;
import org.zzc.bpcsolver.problem.update.DynamicStateUpdater;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BPCSolverStageFlowTest {

    @Test
    void shouldGenerateOriginSingletonRoutesAndExternalDirectReturnRoutes() {
        DynamicInstance instance = buildInstanceWithExternalDepot();
        BPCSolver solver = new BPCSolver();

        List<Route> routes = solver.generateInitialRoutesForCurrentInstance(instance);

        assertEquals(3, routes.size());
        assertNotNull(findRoute(routes, new int[]{0, 1, 3}));
        assertNotNull(findRoute(routes, new int[]{0, 2, 3}));
        Route externalRoute = findRoute(routes, new int[]{4, 3});
        assertNotNull(externalRoute);
        assertEquals(13.0, externalRoute.getCost(), 1e-6);
        assertEquals(Arrays.asList(18.0, 31.0), externalRoute.getArrivalTimes());
    }

    @Test
    void shouldSkipInfeasibleOriginSingletonRoute() {
        DynamicInstance instance = buildOriginOnlyInstance();
        instance.getProblemData().getCustomers().get(1).setDemand(11);
        BPCSolver solver = new BPCSolver();

        List<Route> routes = solver.generateInitialRoutesForCurrentInstance(instance);

        assertNotNull(findRoute(routes, new int[]{0, 1, 3}));
        assertNull(findRoute(routes, new int[]{0, 2, 3}));
        assertEquals(1, routes.size());
    }

    @Test
    void shouldDriveBranchPriceSolveFromInitialRoutes() {
        DynamicInstance instance = buildInstanceWithExternalDepot();
        BPCSolver solver = new BPCSolver();

        BPCSolver.SolveResult result = solver.solveNode(instance, 1, 5);
        StageSolution stageSolution = result.getSolution();

        assertEquals(BPCSolver.SolveStatus.INTEGER_ROOT, result.getStatus());
        assertTrue(result.hasIntegerIncumbent());
        assertTrue(result.isCertifiedExact());
        assertFalse(result.isFallback());
        assertEquals(BPCSolverTemplate.BranchSearchTermination.ROOT_INTEGER,
                result.getBranchSearchStatistics().getTerminationReason());
        assertTrue(result.getBranchSearchStatistics().isSolvedToOptimality());
        assertEquals(1, result.getBranchSearchStatistics().getNodesExplored());
        assertTrue(result.getRunStatistics().getColumnGenerationRuns() > 0);
        assertTrue(result.getRunStatistics().getColumnGenerationIterations() > 0);
        assertTrue(result.getRunStatistics().getMasterSolves() >= result.getRunStatistics().getColumnGenerationIterations());
        assertTrue(result.getRunStatistics().getExactPricingCalls() > 0);
        assertEquals(0, result.getRunStatistics().getHeuristicPricingCalls());
        assertTrue(result.getRunStatistics().getColumnsAdded() > 0);
        assertTrue(stageSolution.isIntegerSolution());
        assertEquals(27.0, stageSolution.getObjectiveValue(), 1e-6);
        assertEquals(1, stageSolution.getSelectedRoutes().size());
        assertArrayEquals(new int[]{4, 2, 1, 3}, stageSolution.getSelectedRoutes().get(0).getVisitedVertices().toArray());
    }

    @Test
    void shouldKeepResultNodeSynchronizedWithFinalStageState() {
        DynamicInstance instance = buildInstanceWithExternalDepot();
        BPCSolver solver = new BPCSolver();

        BPCSolver.SolveResult result = solver.solveNode(instance, 1, 5);
        Node<Route, LmSrc, Graph> resultNode = result.getResultNode();
        StageSolution stageSolution = result.getSolution();

        assertEquals(stageSolution.getObjectiveValue(), resultNode.getSolution().getObjValue(), 1e-9);
        assertEquals(resultNode.getGraphs().size(), resultNode.getUsedScheduleSet().size());
        assertEquals(resultNode.getGraphs().size(), resultNode.getSolution().getYks().size());
        assertFalse(resultNode.getUsedScheduleSet().stream().allMatch(List::isEmpty));
    }

    @Test
    void shouldRepairRouteFractionalNodeWhenDirectedArcFlowIsIntegral() throws Exception {
        DynamicInstance instance = buildOriginOnlyInstance();
        Graph graph = new Graph();
        graph.buildFromInstance(instance, instance.getOriginDepot().getId());

        Route firstHalf = buildRoute(0, 0, 40.0, 0.5, 0, 1, 2, 3);
        Route secondHalf = buildRoute(0, 1, 40.0, 0.5, 0, 1, 2, 3);
        Node<Route, LmSrc, Graph> node = new Node<>();
        node.setGraphs(new ArrayList<>(Arrays.asList(graph)));
        node.setUsedScheduleSet(new ArrayList<>(Arrays.asList(new ArrayList<>(Arrays.asList(firstHalf, secondHalf)))));
        Solution<Route> solution = new Solution<>(1, instance.getNumJobs());
        solution.setYks(new ArrayList<>(Arrays.asList(new double[]{0.5, 0.5})));
        solution.setObjValue(40.0);
        node.setSolution(solution);
        node.setFeasible(true);
        node.setInteger(false);
        node.setColumnGenerationStatus(ColumnGenerationStatus.CONVERGED);

        RepairExposingSolver solver = new RepairExposingSolver();
        solver.initializeForRepair(instance);

        assertTrue(solver.repair(node));
        assertTrue(node.isInteger());
        assertEquals(40.0, node.getSolution().getObjValue(), 1e-6);
        assertEquals(1, node.getSolution().getBestSchedules().size());
        assertArrayEquals(new int[]{0, 1, 2, 3},
                node.getSolution().getBestSchedules().get(0).getVisitedVertices().toArray());
    }

    @Test
    void shouldRecordIntegerFlowRepairFailureReasonWhenArcFlowIsFractional() throws Exception {
        DynamicInstance instance = buildOriginOnlyInstance();
        Graph graph = new Graph();
        graph.buildFromInstance(instance, instance.getOriginDepot().getId());

        Route fractionalRoute = buildRoute(0, 0, 40.0, 0.5, 0, 1, 2, 3);
        Node<Route, LmSrc, Graph> node = new Node<>();
        node.setGraphs(new ArrayList<>(Arrays.asList(graph)));
        node.setUsedScheduleSet(new ArrayList<>(Arrays.asList(new ArrayList<>(Arrays.asList(fractionalRoute)))));
        Solution<Route> solution = new Solution<>(1, instance.getNumJobs());
        solution.setYks(new ArrayList<>(Arrays.asList(new double[]{0.5})));
        solution.setObjValue(20.0);
        node.setSolution(solution);
        node.setFeasible(true);
        node.setInteger(false);
        node.setColumnGenerationStatus(ColumnGenerationStatus.CONVERGED);

        RepairExposingSolver solver = new RepairExposingSolver();
        solver.initializeForRepair(instance);
        solver.startStatsForRepair();

        assertFalse(solver.repair(node));
        solver.finishStatsForRepair();
        assertEquals("arcFlowNotIntegral", solver.stats().getIntegerFlowRepairLastFailureReason());
        assertEquals(1, solver.stats().getIntegerFlowRepairFailuresArcFlowNotIntegral());
    }

    @Test
    void shouldExposeIterationLimitWhenColumnGenerationDoesNotClose() {
        DynamicInstance instance = buildInstanceWithExternalDepot();
        BPCSolver solver = new BPCSolver();

        BPCSolver.SolveResult result = solver.solveNode(instance, 1, 1);
        StageSolution stageSolution = result.getSolution();

        assertEquals(BPCSolver.SolveStatus.CG_ITERATION_LIMIT, result.getStatus());
        assertEquals(ColumnGenerationStatus.ITERATION_LIMIT, result.getResultNode().getColumnGenerationStatus());
        assertFalse(result.hasIntegerIncumbent());
        assertFalse(result.getResultNode().isInteger());
        assertFalse(stageSolution.isIntegerSolution());
        assertTrue(stageSolution.getObjectiveValue() > 0.0);
        assertFalse(stageSolution.getAllRoutes().isEmpty());
        assertFalse(stageSolution.getSelectedRoutes().isEmpty());
        assertTrue(stageSolution.getPricingCustomerDuals().length > 0);
    }

    @Test
    void shouldPreserveConfiguredSolveTimeLimitThroughInitialization() {
        DynamicInstance instance = buildInstanceWithExternalDepot();
        class DeadlineTrackingSolver extends BPCSolver {
            private final List<Long> observedLimits = java.util.Collections.synchronizedList(new ArrayList<>());
            private final List<Long> observedDeadlines = java.util.Collections.synchronizedList(new ArrayList<>());
            private final List<Long> observedTimes = java.util.Collections.synchronizedList(new ArrayList<>());

            @Override
            protected List<Route> pricingAlgorithm(String state, Graph graph, int index, List<LmSrc> cuts,
                                                   List<org.zzc.bpcsolver.core.branch.Constraint> branchConstraints,
                                                   double dualsOfMachines, double[] dualsOfJobs,
                                                   List<Double> dualsOfCuts, List<Double> dualsOfBranches, int iter) {
                observedLimits.add(getBranchingTimeLimitSeconds());
                observedDeadlines.add(getSolveDeadlineNanos());
                observedTimes.add(System.nanoTime());
                return new ArrayList<>();
            }
        }
        DeadlineTrackingSolver solver = new DeadlineTrackingSolver();
        solver.setBranchingTimeLimitSeconds(2);

        solver.solveRootOnlyNode(instance, 1, 1);

        assertFalse(solver.observedLimits.isEmpty());
        assertTrue(solver.observedLimits.stream().allMatch(limit -> limit == 2L));
        assertTrue(solver.observedDeadlines.stream().allMatch(deadline -> deadline != Long.MAX_VALUE));
        for (int i = 0; i < solver.observedDeadlines.size(); i++) {
            long remainingNanos = solver.observedDeadlines.get(i) - solver.observedTimes.get(i);
            assertTrue(remainingNanos > 0L);
            assertTrue(remainingNanos <= 2_000_000_000L);
        }
    }

    @Test
    void shouldTreatZeroMaxIterationsAsConvergenceDrivenColumnGeneration() {
        DynamicInstance instance = buildInstanceWithExternalDepot();
        BPCSolver solver = new BPCSolver();

        BPCSolver.SolveResult result = solver.solveNode(instance, 1, BPCSolver.UNLIMITED_MAX_ITERATIONS);

        assertEquals(BPCSolver.SolveStatus.INTEGER_ROOT, result.getStatus());
        assertEquals(ColumnGenerationStatus.CONVERGED, result.getResultNode().getColumnGenerationStatus());
        assertTrue(result.hasIntegerIncumbent());
        assertTrue(result.getSolution().isIntegerSolution());
        assertEquals(27.0, result.getSolution().getObjectiveValue(), 1e-6);
    }

    @Test
    void shouldWriteImprovedRouteIntoFinalStageSolution() {
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
        assertEquals(2, stageSolution.getSelectedRoutes().size());
    }

    @Test
    void shouldRouteMachineDualThroughPricingStrategyAdapter() {
        DynamicInstance instance = buildInstanceWithExternalDepot();
        Graph externalGraph = new Graph();
        externalGraph.buildFromInstance(instance, 4);
        Configuration configuration = new Configuration();

        List<Route> improvingRoutes = PricingStrategyFactory.createPricingAlgorithm(configuration).solve(
                externalGraph,
                1,
                new ArrayList<>(),
                new ArrayList<>(),
                40.0,
                new double[6],
                new ArrayList<>(),
                new ArrayList<>(),
                null,
                configuration,
                null,
                1
        );

        assertFalse(improvingRoutes.isEmpty());
        Route directReturnRoute = findRoute(improvingRoutes, new int[]{4, 3});
        assertEquals(-27.0, directReturnRoute.getReducedCost(), 1e-6);
    }

    @Test
    void shouldSelectNewColumnsDeterministicallyWhenReducedCostAndCostTie() {
        BPCSolver solver = new BPCSolver();
        solver.prepareRootSolveContext(buildOriginOnlyInstance(), new ArrayList<Route>(), 1);
        Route laterPath = buildRoute(0, 0, 10.0, -1.0, 0, 2, 3);
        Route earlierPath = buildRoute(0, 1, 10.0, -1.0, 0, 1, 3);

        List<Route> selectedRoutes = solver.selectNewColumnsForGraph(Arrays.asList(laterPath, earlierPath));

        assertEquals(1, selectedRoutes.size());
        assertArrayEquals(new int[]{0, 1, 3}, selectedRoutes.get(0).getVisitedVertices().toArray());
    }

    @Test
    void shouldSolveBranchedChildNodeWithBranchConstraintActive() {
        DynamicInstance instance = buildInstanceWithExternalDepot();
        BPCSolver solver = new BPCSolver();
        BPCSolver.SolveResult result = solver.solveRootOnlyNode(instance, 1, 5);
        Node<Route, LmSrc, Graph> rootNode = result.getResultNode();
        Variable forbidArcTwoToOne = new Variable("qij", new int[]{2, 1}, 0.5);

        Node<Route, LmSrc, Graph> leftChild = solver.solveChildNodeForCurrentInstance(rootNode, forbidArcTwoToOne, 0, 5);
        Node<Route, LmSrc, Graph> rightChild = solver.solveChildNodeForCurrentInstance(rootNode, forbidArcTwoToOne, 1, 5);

        assertTrue(leftChild.isFeasible());
        assertTrue(rightChild.isFeasible());
        assertEquals(1, leftChild.getBranchConstraints().size());
        assertEquals(1, rightChild.getBranchConstraints().size());
        assertEquals(0.0, calculateArcUsage(leftChild, 2, 1), 1e-6);
        assertTrue(containsArcInRoutePool(leftChild, 2, 1));
        assertTrue(calculateArcUsage(rightChild, 2, 1) >= 1.0 - 1e-6);
        assertTrue(leftChild.getSolution().getObjValue() >= rootNode.getSolution().getObjValue() - 1e-6);
    }

    @Test
    void shouldRunCopiedSearchTreeStepThroughRealChildSolves() {
        DynamicInstance instance = buildInstanceWithExternalDepot();
        BPCSolver solver = new BPCSolver();
        BPCSolver.SolveResult rootResult = solver.solveRootOnlyNode(instance, 1, 5);
        Node<Route, LmSrc, Graph> fractionalRoot = rootResult.getResultNode();
        makeArcFractional(fractionalRoot, 2, 1);
        fractionalRoot.getSolution().setObjValue(1.0);

        Node<Route, LmSrc, Graph> resultNode = solver.branchAndPriceFromCurrentRootNode(fractionalRoot, 5);

        assertNotNull(resultNode);
        assertTrue(resultNode.isFeasible());
        assertNotNull(resultNode.getSolution());
        assertTrue(resultNode.getSolution().getObjValue() > 0.0);
        assertTrue(solver.getNumNodesExplored() > 1);
        assertNotNull(solver.getSearchTree());
    }

    @Test
    void shouldRunStrongBranchingThroughRealDvrpChildRestrictedMasters() {
        DynamicInstance instance = buildInstanceWithExternalDepot();
        class StrongBranchingSolver extends BPCSolver {
            @Override
            protected void prepareRootSolveContext(DynamicInstance instance,
                                                   List<Route> initialSchedules,
                                                   int maxAddedColumnsPerIteration) {
                super.prepareRootSolveContext(instance, initialSchedules, maxAddedColumnsPerIteration);
                this.config.getBranching().setVariableSelectionRule("strong branching");
                this.config.getBranching().setNumCandidateVariables1(2);
                this.config.getBranching().setNumCandidateVariables2(1);
            }
        }
        StrongBranchingSolver solver = new StrongBranchingSolver();
        BPCSolver.SolveResult rootResult = solver.solveRootOnlyNode(instance, 1, 5);
        Node<Route, LmSrc, Graph> fractionalRoot = rootResult.getResultNode();
        makeArcFractional(fractionalRoot, 2, 1);
        fractionalRoot.getSolution().setObjValue(1.0);

        Node<Route, LmSrc, Graph> resultNode = solver.branchAndPriceFromCurrentRootNode(fractionalRoot, 5);

        assertNotNull(resultNode);
        assertTrue(resultNode.isFeasible());
        assertNotNull(resultNode.getSolution());
        assertTrue(solver.getNumNodesExplored() > 1);
        assertEquals("strong branching", solver.getBranchingStrategy().getVariableSelectionStrategy().getStrategyName());
    }

    @Test
    void shouldApplyProgrammaticVariableSelectionRuleToSolverConfiguration() {
        DynamicInstance instance = buildInstanceWithExternalDepot();
        BPCSolver solver = new BPCSolver();
        solver.setVariableSelectionRule("strong branching");

        solver.solveRootOnlyNode(instance, 1, 5);

        assertEquals("strong branching", solver.getVariableSelectionRule());
        assertEquals("strong branching", solver.getBranchingStrategy().getVariableSelectionStrategy().getStrategyName());
    }

    @Test
    void shouldUseCurrentColumnPoolToBuildIntegerIncumbentWhenRootIsFractional() {
        DynamicInstance instance = buildInstanceWithExternalDepot();
        BPCSolver solver = new BPCSolver();
        BPCSolver.SolveResult rootResult = solver.solveRootOnlyNode(instance, 1, 5);
        Node<Route, LmSrc, Graph> fractionalRoot = rootResult.getResultNode();
        makeArcFractional(fractionalRoot, 2, 1);
        fractionalRoot.getSolution().setObjValue(1.0);

        Node<Route, LmSrc, Graph> resultNode = solver.branchAndPriceFromCurrentRootNode(fractionalRoot, 5);

        assertTrue(resultNode.isInteger());
        assertEquals(rootResult.getSolution().getObjectiveValue(), resultNode.getSolution().getObjValue(), 1e-6);
        assertFalse(resultNode.getSolution().getBestSchedules().isEmpty());
    }

    @Test
    void shouldReuseRootUpperBoundWhenBranchingStartsAfterRootSolve() {
        DynamicInstance instance = buildInstanceWithExternalDepot();
        class RootUpperBoundTrackingSolver extends BPCSolver {
            private int upperBoundCalls;

            @Override
            protected ColumnGenerationStatus columnGeneration(DVRPMasterProblem masterProblem, Node<Route, LmSrc, Graph> node, int maxIterations) {
                node.setFeasible(true);
                node.setInteger(false);
                node.setColumnGenerationStatus(ColumnGenerationStatus.CONVERGED);
                node.getSolution().setObjValue(1.0);
                return ColumnGenerationStatus.CONVERGED;
            }

            @Override
            protected void calculateUpperBound(Node<Route, LmSrc, Graph> node) {
                upperBoundCalls++;
                Solution<Route> incumbent = new Solution<>(node.getGraphs().size(), instance.getNumJobs());
                incumbent.setObjValue(node.getSolution().getObjValue() + 1.0);
                setBestSolution(node, incumbent);
            }

            @Override
            protected Node<Route, LmSrc, Graph> solveBranchedChildNode(Node<Route, LmSrc, Graph> parentNode,
                                                                       Variable branchingVar,
                                                                       int branchFlag,
                                                                       int maxIterations) {
                return null;
            }
        }
        RootUpperBoundTrackingSolver solver = new RootUpperBoundTrackingSolver();
        BPCSolver.SolveResult rootResult = solver.solveRootOnlyNode(instance, 1, 5);
        Node<Route, LmSrc, Graph> fractionalRoot = rootResult.getResultNode();
        int[] arc = firstArcFromRoutePool(fractionalRoot);
        makeArcFractional(fractionalRoot, arc[0], arc[1]);

        solver.branchAndPriceFromCurrentRootNode(fractionalRoot, 5);

        assertEquals(1, solver.upperBoundCalls);
    }

    @Test
    void shouldExposeLpFallbackWhenBranchSearchFindsNoIntegerIncumbent() {
        DynamicInstance instance = buildInstanceWithExternalDepot();
        class NoIncumbentSolver extends BPCSolver {
            @Override
            protected void calculateUpperBound(Node<Route, LmSrc, Graph> node) {
            }

            @Override
            protected Node<Route, LmSrc, Graph> solveBranchedChildNode(Node<Route, LmSrc, Graph> parentNode,
                                                                       Variable branchingVar,
                                                                       int branchFlag,
                                                                       int maxIterations) {
                return null;
            }
        }
        NoIncumbentSolver solver = new NoIncumbentSolver();

        BPCSolver.SolveResult rootResult = solver.solveRootOnlyNode(instance, 1, 5);
        Node<Route, LmSrc, Graph> fractionalRoot = rootResult.getResultNode();
        makeArcFractional(fractionalRoot, 2, 1);
        solver.setOptimalNode(null);
        BPCSolver.SolveResult result = solver.branchAndPriceResultFromCurrentRootNode(fractionalRoot, 5);

        assertEquals(BPCSolver.SolveStatus.LP_FALLBACK, result.getStatus());
        assertEquals(BPCSolverTemplate.BranchSearchTermination.TREE_EXHAUSTED,
                result.getBranchSearchStatistics().getTerminationReason());
        assertFalse(result.getBranchSearchStatistics().isSolvedToOptimality());
        assertEquals(3, result.getBranchSearchStatistics().getNodesExplored());
        assertEquals(2, result.getBranchSearchStatistics().getChildrenSolved());
        assertEquals(2, result.getBranchSearchStatistics().getInfeasibleChildren());
        assertFalse(result.hasIntegerIncumbent());
        assertTrue(result.isFallback());
        assertFalse(result.getSolution().isIntegerSolution());
        assertFalse(result.getSolution().getSelectedRoutes().isEmpty());
    }

    @Test
    void shouldExposeBranchSearchStatisticsWhenSearchFindsIntegerIncumbent() {
        DynamicInstance instance = buildInstanceWithExternalDepot();
        BPCSolver solver = new BPCSolver();
        BPCSolver.SolveResult rootResult = solver.solveRootOnlyNode(instance, 1, 5);
        Node<Route, LmSrc, Graph> fractionalRoot = rootResult.getResultNode();
        makeArcFractional(fractionalRoot, 2, 1);
        fractionalRoot.getSolution().setObjValue(1.0);

        BPCSolver.SolveResult result = solver.branchAndPriceResultFromCurrentRootNode(fractionalRoot, 5);
        BPCSolverTemplate.BranchSearchStatistics statistics = result.getBranchSearchStatistics();

        assertEquals(BPCSolver.SolveStatus.INTEGER_INCUMBENT, result.getStatus());
        assertEquals(BPCSolverTemplate.BranchSearchTermination.TREE_EXHAUSTED, statistics.getTerminationReason());
        assertEquals(3, statistics.getNodesExplored());
        assertEquals(1, statistics.getNodesProcessed());
        assertEquals(2, statistics.getChildrenSolved());
        assertEquals(0, statistics.getOpenNodesAtEnd());
        assertTrue(statistics.isSolvedToOptimality());
        assertTrue(result.isCertifiedExact());
        assertEquals(result.getSolution().getObjectiveValue(), statistics.getResultObjectiveValue(), 1e-6);
        assertTrue(statistics.getResultColumnCount() > 0);
    }

    @Test
    void shouldKeepTreeExhaustionUncertifiedWhenAChildFailsColumnGenerationClosure() {
        DynamicInstance instance = buildInstanceWithExternalDepot();
        class BranchFailureSolver extends BPCSolver {
            @Override
            protected Node<Route, LmSrc, Graph> solveBranchedChildNode(Node<Route, LmSrc, Graph> parentNode,
                                                                       Variable branchingVar,
                                                                       int branchFlag,
                                                                       int maxIterations) {
                Node<Route, LmSrc, Graph> childNode = parentNode.copy();
                childNode.setFeasible(true);
                childNode.setInteger(false);
                childNode.setColumnGenerationStatus(ColumnGenerationStatus.ITERATION_LIMIT);
                return childNode;
            }
        }
        BranchFailureSolver solver = new BranchFailureSolver();
        BPCSolver.SolveResult rootResult = solver.solveRootOnlyNode(instance, 1, 5);
        Node<Route, LmSrc, Graph> fractionalRoot = rootResult.getResultNode();
        makeArcFractional(fractionalRoot, 2, 1);
        fractionalRoot.getSolution().setObjValue(1.0);

        BPCSolver.SolveResult result = solver.branchAndPriceResultFromCurrentRootNode(fractionalRoot, 5);

        assertEquals(BPCSolver.SolveStatus.INTEGER_INCUMBENT, result.getStatus());
        assertEquals(BPCSolverTemplate.BranchSearchTermination.TREE_EXHAUSTED,
                result.getBranchSearchStatistics().getTerminationReason());
        assertFalse(result.getBranchSearchStatistics().isSolvedToOptimality());
        assertFalse(result.isCertifiedExact());
    }

    @Test
    void shouldPruneSolvedChildNodesWhoseLowerBoundCannotImproveIncumbent() {
        DynamicInstance instance = buildInstanceWithExternalDepot();
        class PruningTrackingSolver extends BPCSolver {
            private int upperBoundCalls;
            private int solvedChildren;

            @Override
            protected void calculateUpperBound(Node<Route, LmSrc, Graph> node) {
                upperBoundCalls++;
                Solution<Route> incumbent = new Solution<>(node.getGraphs().size(), instance.getNumJobs());
                incumbent.setObjValue(node.getSolution().getObjValue() + 1.0);
                setBestSolution(node, incumbent);
            }

            @Override
            protected Node<Route, LmSrc, Graph> solveBranchedChildNode(Node<Route, LmSrc, Graph> parentNode,
                                                                       Variable branchingVar,
                                                                       int branchFlag,
                                                                       int maxIterations) {
                solvedChildren++;
                Node<Route, LmSrc, Graph> childNode = parentNode.copy();
                childNode.setFeasible(true);
                childNode.setInteger(false);
                childNode.getSolution().setObjValue(parentNode.getSolution().getObjValue() + 100.0 + branchFlag);
                return childNode;
            }

            void resetTracking() {
                upperBoundCalls = 0;
                solvedChildren = 0;
            }
        }
        PruningTrackingSolver solver = new PruningTrackingSolver();
        BPCSolver.SolveResult rootResult = solver.solveRootOnlyNode(instance, 1, 5);
        solver.resetTracking();
        Node<Route, LmSrc, Graph> fractionalRoot = rootResult.getResultNode();
        makeArcFractional(fractionalRoot, 2, 1);
        fractionalRoot.getSolution().setObjValue(1.0);

        Node<Route, LmSrc, Graph> resultNode = solver.branchAndPriceFromCurrentRootNode(fractionalRoot, 5);

        assertEquals(2, solver.solvedChildren);
        assertEquals(1, solver.upperBoundCalls);
        assertTrue(solver.getSearchTree().isEmpty());
        assertTrue(resultNode.isInteger());
        assertEquals(2.0, resultNode.getSolution().getObjValue(), 1e-6);
    }

    @Test
    void shouldIgnoreInvalidIntegerChildWhenUpdatingIncumbent() {
        DynamicInstance instance = buildInstanceWithExternalDepot();
        class InvalidIntegerChildSolver extends BPCSolver {
            private int solvedChildren;

            @Override
            protected void calculateUpperBound(Node<Route, LmSrc, Graph> node) {
                Solution<Route> incumbent = new Solution<>(node.getGraphs().size(), instance.getNumJobs());
                incumbent.setObjValue(10.0);
                setBestSolution(node, incumbent);
            }

            @Override
            protected Node<Route, LmSrc, Graph> solveBranchedChildNode(Node<Route, LmSrc, Graph> parentNode,
                                                                       Variable branchingVar,
                                                                       int branchFlag,
                                                                       int maxIterations) {
                solvedChildren++;
                Node<Route, LmSrc, Graph> childNode = parentNode.copy();
                childNode.setFeasible(true);
                childNode.setInteger(true);
                childNode.setSolution(null);
                return childNode;
            }
        }
        InvalidIntegerChildSolver solver = new InvalidIntegerChildSolver();
        BPCSolver.SolveResult rootResult = solver.solveRootOnlyNode(instance, 1, 5);
        Node<Route, LmSrc, Graph> fractionalRoot = rootResult.getResultNode();
        makeArcFractional(fractionalRoot, 2, 1);
        fractionalRoot.getSolution().setObjValue(1.0);

        Node<Route, LmSrc, Graph> resultNode = solver.branchAndPriceFromCurrentRootNode(fractionalRoot, 5);

        assertEquals(2, solver.solvedChildren);
        assertTrue(resultNode.isInteger());
        assertEquals(10.0, resultNode.getSolution().getObjValue(), 1e-6);
    }

    @Test
    void shouldKeepIncumbentStableForNumericallyTiedIntegerChild() {
        DynamicInstance instance = buildInstanceWithExternalDepot();
        class TiedIntegerChildSolver extends BPCSolver {
            @Override
            protected void calculateUpperBound(Node<Route, LmSrc, Graph> node) {
                Solution<Route> incumbent = new Solution<>(node.getGraphs().size(), instance.getNumJobs());
                incumbent.setObjValue(10.0);
                setBestSolution(node, incumbent);
            }

            @Override
            protected Node<Route, LmSrc, Graph> solveBranchedChildNode(Node<Route, LmSrc, Graph> parentNode,
                                                                       Variable branchingVar,
                                                                       int branchFlag,
                                                                       int maxIterations) {
                Node<Route, LmSrc, Graph> childNode = parentNode.copy();
                childNode.setFeasible(true);
                childNode.setInteger(true);
                childNode.getSolution().setObjValue(10.0 - 1e-7);
                return childNode;
            }
        }
        TiedIntegerChildSolver solver = new TiedIntegerChildSolver();
        BPCSolver.SolveResult rootResult = solver.solveRootOnlyNode(instance, 1, 5);
        Node<Route, LmSrc, Graph> fractionalRoot = rootResult.getResultNode();
        makeArcFractional(fractionalRoot, 2, 1);

        Node<Route, LmSrc, Graph> resultNode = solver.branchAndPriceFromCurrentRootNode(fractionalRoot, 5);

        assertTrue(resultNode.isInteger());
        assertEquals(10.0, resultNode.getSolution().getObjValue(), 1e-9);
    }

    @Test
    void shouldDelegateGenericAccelerationAndCutHooksToConfiguredStrategies() {
        DynamicInstance instance = buildInstanceWithExternalDepot();
        BPCSolver solver = new BPCSolver();
        Node<Route, LmSrc, Graph> rootNode = solver.solveRootOnlyNode(instance, 1, 5).getResultNode();
        int[] calls = new int[4];

        Solution<Route> divingSolution = new Solution<>(rootNode.getGraphs().size(), instance.getNumJobs());
        divingSolution.setObjValue(123.0);
        solver.getAccelerationStrategy().setDivingHeuristic(new DivingHeuristic<DynamicInstance, Route, LmSrc, Graph>() {
            @Override
            public void initialize(DynamicInstance instance, Configuration config,
                                   HeuristicPricing<Route, Graph, LmSrc> heuristicPricing,
                                   BranchingRule<Route, LmSrc, Graph> branchingRule) {
            }

            @Override
            public Solution<Route> solve(Node<Route, LmSrc, Graph> node, BPCLogger logger) {
                calls[0]++;
                return divingSolution;
            }

            @Override
            public String getStrategyName() {
                return "counting diving";
            }
        });
        solver.getCuttingStrategy().setCuttingStrategy(new Cutting<Route, LmSrc>() {
            @Override
            public void Initialize(int numJobs, Configuration config) {
            }

            @Override
            public List<LmSrc> separateCuts(int numMachines, List<List<Route>> scheduleSets, List<double[]> yks) {
                calls[1]++;
                return Arrays.asList(new LmSrc(1.0, 1.0));
            }

            @Override
            public String getStrategyName() {
                return "counting cutting";
            }
        });
        solver.getAccelerationStrategy().setVariableFixing(new VariableFixing<Route, LmSrc, Graph>() {
            @Override
            public void initialize(Configuration config) {
            }

            @Override
            public List<int[]> solve(Graph graph, Node<Route, LmSrc, Graph> node, double upperBound) {
                calls[2]++;
                return Arrays.asList(new int[]{1, 2});
            }

            @Override
            public String getStrategyName() {
                return "counting variable fixing";
            }
        });
        solver.getAccelerationStrategy().setScheduleEnumeration(new ScheduleEnumeration<Route, LmSrc, Graph>() {
            @Override
            public void initialize(Configuration config, ExactPricing<Route, Graph, LmSrc> pricingStrategy,
                                   BranchingRule<Route, LmSrc, Graph> branchingRule) {
            }

            @Override
            public List<Route> solve(int machineIndex, Graph graph, Node<Route, LmSrc, Graph> node,
                                     double upperBound, Configuration config, BPCLogger logger) {
                calls[3]++;
                return Arrays.asList(buildRoute(0, 99, 1.0, 0.0, 0, 1, 3));
            }

            @Override
            public String getStrategyName() {
                return "counting schedule enumeration";
            }
        });

        assertEquals(123.0, solver.divingHeuristic(rootNode).getObjValue(), 1e-6);
        assertEquals(1, solver.cutGeneration(rootNode).size());
        assertEquals(1, solver.obtainNonImprovingArcs(0, rootNode.getGraphs().get(0), rootNode, 100.0).size());
        assertEquals(1, solver.enumerateImprovingSchedules(0, rootNode.getGraphs().get(0), rootNode, 100.0).size());
        assertArrayEquals(new int[]{1, 1, 1, 1}, calls);
    }

    @Test
    void shouldSwitchFromHeuristicToExactPricingWhenHeuristicFindsNoColumns() {
        DynamicInstance instance = buildInstanceWithExternalDepot();
        class TrackingPricingSolver extends BPCSolver {
            private final List<String> pricingStates = java.util.Collections.synchronizedList(new ArrayList<>());

            @Override
            protected void prepareRootSolveContext(DynamicInstance instance,
                                                   List<Route> initialSchedules,
                                                   int maxAddedColumnsPerIteration) {
                super.prepareRootSolveContext(instance, initialSchedules, maxAddedColumnsPerIteration);
                this.config.getPricing().setHeuristicPricing("light labeling");
                this.pricingStrategy.setHeuristicPricingAlgorithm(
                        PricingStrategyFactory.createHeuristicPricingAlgorithm(this.config)
                );
            }

            @Override
            protected List<Route> pricingAlgorithm(String state, Graph graph, int index, List<LmSrc> cuts,
                                                   List<org.zzc.bpcsolver.core.branch.Constraint> branchConstraints,
                                                   double dualsOfMachines, double[] dualsOfJobs,
                                                   List<Double> dualsOfCuts, List<Double> dualsOfBranches, int iter) {
                pricingStates.add(state);
                return new ArrayList<>();
            }
        }
        TrackingPricingSolver solver = new TrackingPricingSolver();

        BPCSolver.SolveResult result = solver.solveRootOnlyNode(instance, 1, 2);

        assertFalse(solver.pricingStates.isEmpty());
        assertEquals("Heuristic Pricing", solver.pricingStates.get(0));
        assertTrue(result.getRunStatistics().getHeuristicPricingCalls() > 0);
        assertTrue(result.getRunStatistics().getExactPricingCalls() > 0);
    }

    @Test
    void shouldSwitchThroughMultiStageHeuristicBeforeExactPricingWhenNoColumns() {
        DynamicInstance instance = buildInstanceWithExternalDepot();
        class TrackingPricingSolver extends BPCSolver {
            private final List<String> pricingStates = java.util.Collections.synchronizedList(new ArrayList<>());
            private final List<String> heuristicStages = java.util.Collections.synchronizedList(new ArrayList<>());

            @Override
            protected void prepareRootSolveContext(DynamicInstance instance,
                                                   List<Route> initialSchedules,
                                                   int maxAddedColumnsPerIteration) {
                super.prepareRootSolveContext(instance, initialSchedules, maxAddedColumnsPerIteration);
                this.config.getPricing().setHeuristicPricing("multi-stage heuristic pricing");
                this.pricingStrategy.setHeuristicPricingAlgorithm(
                        PricingStrategyFactory.createHeuristicPricingAlgorithm(this.config)
                );
            }

            @Override
            protected List<Route> pricingAlgorithm(String state, Graph graph, int index, List<LmSrc> cuts,
                                                   List<org.zzc.bpcsolver.core.branch.Constraint> branchConstraints,
                                                   double dualsOfMachines, double[] dualsOfJobs,
                                                   List<Double> dualsOfCuts, List<Double> dualsOfBranches, int iter) {
                pricingStates.add(state);
                heuristicStages.add(this.config.getPricing().getCurrentHeuristic());
                return new ArrayList<>();
            }
        }
        TrackingPricingSolver solver = new TrackingPricingSolver();

        BPCSolver.SolveResult result = solver.solveRootOnlyNode(instance, 1, 2);

        assertTrue(solver.heuristicStages.contains("light labeling"));
        assertTrue(solver.heuristicStages.contains("relaxed labeling"));
        assertTrue(result.getRunStatistics().getHeuristicPricingIterations() > 1);
        assertTrue(result.getRunStatistics().getExactPricingIterations() > 0);
    }

    @Test
    void shouldDispatchDefaultExactPricingOncePerGraph() {
        DynamicInstance instance = buildInstanceWithExternalDepot();
        class TrackingPricingSolver extends BPCSolver {
            private final List<Integer> exactGraphIndexes = java.util.Collections.synchronizedList(new ArrayList<>());

            @Override
            protected List<Route> pricingAlgorithm(String state, Graph graph, int index, List<LmSrc> cuts,
                                                   List<org.zzc.bpcsolver.core.branch.Constraint> branchConstraints,
                                                   double dualsOfMachines, double[] dualsOfJobs,
                                                   List<Double> dualsOfCuts, List<Double> dualsOfBranches, int iter) {
                if ("Exact Pricing".equals(state)) {
                    exactGraphIndexes.add(index);
                }
                return new ArrayList<>();
            }
        }
        TrackingPricingSolver solver = new TrackingPricingSolver();

        BPCSolver.SolveResult result = solver.solveRootOnlyNode(instance, 1, 2);

        assertEquals(2, result.getResultNode().getGraphs().size());
        assertTrue(solver.exactGraphIndexes.contains(0));
        assertTrue(solver.exactGraphIndexes.contains(1));
        assertTrue(result.getRunStatistics().getExactPricingCalls()
                >= result.getRunStatistics().getExactPricingIterations() * result.getResultNode().getGraphs().size());
    }

    @Test
    void shouldAddLmSrcCutsAndExposeCutDualsWhenExactPricingConverges() {
        DynamicInstance instance = buildInstanceWithExternalDepot();
        class CutLoopTrackingSolver extends BPCSolver {
            private int cutGenerationCalls;
            private final List<Integer> pricingCutCounts = new ArrayList<>();
            private final List<Integer> pricingCutDualCounts = new ArrayList<>();

            @Override
            protected void prepareRootSolveContext(DynamicInstance instance,
                                                   List<Route> initialSchedules,
                                                   int maxAddedColumnsPerIteration) {
                super.prepareRootSolveContext(instance, initialSchedules, maxAddedColumnsPerIteration);
                this.config.getCutting().setCuttingSwitch("lm-src");
                this.config.getCutting().setNumAddedCut(1);
                this.config.getCutting().setMaxNumCut(1);
            }

            @Override
            protected List<Route> pricingAlgorithm(String state, Graph graph, int index, List<LmSrc> cuts,
                                                   List<org.zzc.bpcsolver.core.branch.Constraint> branchConstraints,
                                                   double dualsOfMachines, double[] dualsOfJobs,
                                                   List<Double> dualsOfCuts, List<Double> dualsOfBranches, int iter) {
                pricingCutCounts.add(cuts == null ? 0 : cuts.size());
                pricingCutDualCounts.add(dualsOfCuts == null ? 0 : dualsOfCuts.size());
                return new ArrayList<>();
            }

            @Override
            protected List<LmSrc> cutGeneration(Node<Route, LmSrc, Graph> node) {
                cutGenerationCalls++;
                if (cutGenerationCalls > 1) {
                    return new ArrayList<>();
                }
                LmSrc cut = new LmSrc(0.5, 3);
                cut.setId(101);
                cut.setSubSet(new gnu.trove.set.hash.TIntHashSet(new int[]{1, 2}));
                cut.setMemorySet(Arrays.asList(
                        new gnu.trove.set.hash.TIntHashSet(new int[]{1, 2}),
                        new gnu.trove.set.hash.TIntHashSet(new int[]{1, 2})
                ));
                cut.setConstant(1.0);
                return Arrays.asList(cut);
            }
        }
        CutLoopTrackingSolver solver = new CutLoopTrackingSolver();
        solver.setParallelPricing(false);

        BPCSolver.SolveResult result = solver.solveRootOnlyNode(instance, 1, 3);

        assertEquals(1, result.getResultNode().getUsedCutSet().size());
        assertEquals(1, result.getResultNode().getSolution().getDualsOfCuts().size());
        assertTrue(solver.cutGenerationCalls >= 1);
        assertTrue(solver.pricingCutCounts.contains(1));
        assertTrue(solver.pricingCutDualCounts.contains(1));
    }

    @Test
    void shouldStopLmSrcSeparationWhenExactPricingCallExceedsConfiguredLimit() {
        DynamicInstance instance = buildInstanceWithExternalDepot();
        class SlowPricingGuardSolver extends BPCSolver {
            private int cutGenerationCalls;

            @Override
            protected void prepareRootSolveContext(DynamicInstance instance,
                                                   List<Route> initialSchedules,
                                                   int maxAddedColumnsPerIteration) {
                super.prepareRootSolveContext(instance, initialSchedules, maxAddedColumnsPerIteration);
                this.config.getCutting().setCuttingSwitch("lm-src");
                this.config.getCutting().setNumAddedCut(1);
                this.config.getCutting().setMaxNumCut(10);
                this.config.getCutting().setStopAfterPricingSeconds(1e-12);
                this.config.getCutting().setTrailingRounds(0);
            }

            @Override
            protected List<Route> pricingAlgorithm(String state, Graph graph, int index, List<LmSrc> cuts,
                                                   List<org.zzc.bpcsolver.core.branch.Constraint> branchConstraints,
                                                   double dualsOfMachines, double[] dualsOfJobs,
                                                   List<Double> dualsOfCuts, List<Double> dualsOfBranches, int iter) {
                return new ArrayList<>();
            }

            @Override
            protected List<LmSrc> cutGeneration(Node<Route, LmSrc, Graph> node) {
                cutGenerationCalls++;
                return Arrays.asList(buildNonBindingCut(1000 + cutGenerationCalls));
            }
        }
        SlowPricingGuardSolver solver = new SlowPricingGuardSolver();
        solver.setParallelPricing(false);

        BPCSolver.SolveResult result = solver.solveRootOnlyNode(instance, 1, 3);

        assertEquals(0, solver.cutGenerationCalls);
        assertEquals(0, result.getResultNode().getUsedCutSet().size());
        assertTrue(result.getRunStatistics().isCutSeparationStoppedBySlowPricing());
        assertFalse(result.getRunStatistics().isCutSeparationStoppedByTrailing());
    }

    @Test
    void shouldStopLmSrcSeparationAfterTrailingGapImprovementIsTooSmall() {
        DynamicInstance instance = buildInstanceWithExternalDepot();
        class TrailingGuardSolver extends BPCSolver {
            private int cutGenerationCalls;

            @Override
            protected void prepareRootSolveContext(DynamicInstance instance,
                                                   List<Route> initialSchedules,
                                                   int maxAddedColumnsPerIteration) {
                super.prepareRootSolveContext(instance, initialSchedules, maxAddedColumnsPerIteration);
                this.config.getCutting().setCuttingSwitch("lm-src");
                this.config.getCutting().setNumAddedCut(1);
                this.config.getCutting().setMaxNumCut(10);
                this.config.getCutting().setStopAfterPricingSeconds(0.0);
                this.config.getCutting().setTrailingRounds(3);
                this.config.getCutting().setMinTrailingGapImprovement(0.02);
            }

            @Override
            protected List<Route> pricingAlgorithm(String state, Graph graph, int index, List<LmSrc> cuts,
                                                   List<org.zzc.bpcsolver.core.branch.Constraint> branchConstraints,
                                                   double dualsOfMachines, double[] dualsOfJobs,
                                                   List<Double> dualsOfCuts, List<Double> dualsOfBranches, int iter) {
                return new ArrayList<>();
            }

            @Override
            protected List<LmSrc> cutGeneration(Node<Route, LmSrc, Graph> node) {
                cutGenerationCalls++;
                if (this.optimalNode != null && this.optimalNode.getSolution() != null) {
                    this.optimalNode.getSolution().setObjValue(10_000.0);
                }
                return Arrays.asList(buildNonBindingCut(2000 + cutGenerationCalls));
            }
        }
        TrailingGuardSolver solver = new TrailingGuardSolver();
        solver.setParallelPricing(false);

        BPCSolver.SolveResult result = solver.solveRootOnlyNode(instance, 1, 10);

        assertEquals(4, solver.cutGenerationCalls);
        assertEquals(4, result.getResultNode().getUsedCutSet().size());
        assertFalse(result.getRunStatistics().isCutSeparationStoppedBySlowPricing());
        assertTrue(result.getRunStatistics().isCutSeparationStoppedByTrailing());
        assertEquals(4, result.getRunStatistics().getCutGuardRounds());
        assertTrue(result.getRunStatistics().getCutGuardLastGap() > 0.0);
    }

    @Test
    void shouldAllowSerialPricingDispatchThroughConfiguration() {
        DynamicInstance instance = buildInstanceWithExternalDepot();
        class TrackingPricingSolver extends BPCSolver {
            private final List<Integer> exactGraphIndexes = new ArrayList<>();
            private final List<String> pricingThreadNames = new ArrayList<>();

            @Override
            protected List<Route> pricingAlgorithm(String state, Graph graph, int index, List<LmSrc> cuts,
                                                   List<org.zzc.bpcsolver.core.branch.Constraint> branchConstraints,
                                                   double dualsOfMachines, double[] dualsOfJobs,
                                                   List<Double> dualsOfCuts, List<Double> dualsOfBranches, int iter) {
                if ("Exact Pricing".equals(state)) {
                    exactGraphIndexes.add(index);
                    pricingThreadNames.add(Thread.currentThread().getName());
                }
                return new ArrayList<>();
            }
        }
        TrackingPricingSolver solver = new TrackingPricingSolver();
        solver.setParallelPricing(false);

        BPCSolver.SolveResult result = solver.solveRootOnlyNode(instance, 1, 2);

        assertFalse(solver.isParallelPricing());
        assertEquals(2, result.getResultNode().getGraphs().size());
        assertEquals(Arrays.asList(0, 1), solver.exactGraphIndexes.subList(0, 2));
        assertTrue(solver.pricingThreadNames.stream().allMatch(name -> name.equals(Thread.currentThread().getName())));
    }

    @Test
    void shouldKeepStageObjectiveWhenMultiStageHeuristicPricingIsEnabled() {
        DynamicInstance instance = buildInstanceWithExternalDepot();
        BPCSolver exactOnlySolver = new BPCSolver();
        BPCSolver heuristicSolver = new BPCSolver();
        heuristicSolver.setHeuristicPricingRule("multi-stage heuristic pricing");

        StageSolution exactOnlySolution = exactOnlySolver.solve(instance, 1, 5);
        BPCSolver.SolveResult heuristicResult = heuristicSolver.solveNode(instance, 1, 5);
        StageSolution heuristicSolution = heuristicResult.getSolution();

        assertEquals("multi-stage heuristic pricing", heuristicSolver.getHeuristicPricingRule());
        assertTrue(heuristicResult.getRunStatistics().getHeuristicPricingCalls() > 0);
        assertTrue(heuristicResult.getRunStatistics().getExactPricingCalls() > 0);
        assertTrue(heuristicSolution.isIntegerSolution());
        assertEquals(exactOnlySolution.getObjectiveValue(), heuristicSolution.getObjectiveValue(), 1e-6);
        assertCoversEveryRemainingCustomerExactlyOnce(heuristicSolution.getSelectedRoutes(), instance);
    }

    @Test
    void shouldExposeWhetherStageSolveEndedWithIntegerNode() {
        DynamicInstance instance = buildInstanceWithExternalDepot();
        BPCSolver solver = new BPCSolver();

        BPCSolver.SolveResult result = solver.solveNode(instance, 1, 5);

        assertEquals(result.getResultNode().isInteger(), result.getSolution().isIntegerSolution());
    }

    @Test
    void shouldStayCloseToReferenceR101FirstStageBaseline() {
        ProblemConfig config = new ProblemConfigReader().read("dvrp_td.yaml");
        ProblemData problemData = new ProblemDataLoader().load(config);
        DynamicInstance instance = new DynamicStateUpdater().createInitialInstance(problemData, 0.0);
        BPCSolver solver = new BPCSolver();

        BPCSolver.SolveResult result = solver.solveNode(instance, 10, 50);
        StageSolution stageSolution = result.getSolution();

        assertEquals("r101_1.xlsx", config.getInstanceFileName());
        assertEquals(25, config.getNumCustomers());
        assertEquals(25, instance.getRemainingCustomerIds().size());
        assertEquals(26, instance.getReturnDepot().getId());
        assertTrue(stageSolution.isIntegerSolution());
        assertEquals(8, stageSolution.getSelectedRoutes().size());
        assertEquals(617.1, stageSolution.getObjectiveValue(), 1e-6);
        assertEquals(617.1, stageSolution.getObjectiveValue(), 2.0);

        Route singletonRoute = findRoute(stageSolution.getSelectedRoutes(), new int[]{0, 18, 26});
        assertNotNull(singletonRoute);
        assertEquals(31.6, singletonRoute.getCost(), 0.1);
        assertCoversEveryRemainingCustomerExactlyOnce(stageSolution.getSelectedRoutes(), instance);
    }

    @Test
    void shouldReturnExecutableIntegerSolutionForR101DynamicStageWithExternalDepots() {
        ProblemConfig config = new ProblemConfigReader().read("dvrp_td.yaml");
        config.setInstanceFileName("r101_D_25_E_25.xlsx");
        config.setInstanceBaseName("r101_D_25_E_25");
        ProblemData problemData = new ProblemDataLoader().load(config);
        DynamicStateUpdater updater = new DynamicStateUpdater();
        DynamicInstance firstStage = updater.createInitialInstance(problemData, 0.0);
        BPCSolver solver = new BPCSolver();
        StageSolution firstSolution = solver.solve(firstStage, 10, 50);

        DynamicInstance secondStage = updater.update(firstStage, firstSolution, 10.0);
        StageSolution secondSolution = solver.solve(secondStage, 10, 50);

        assertEquals(Arrays.asList(14, 21, 22, 23, 24, 25), secondStage.getRemainingCustomerIds());
        assertEquals(2, secondStage.getExternalDepots().size());
        assertTrue(secondSolution.isIntegerSolution());
        assertFalse(secondSolution.getSelectedRoutes().isEmpty());
        assertCoversEveryRemainingCustomerExactlyOnce(secondSolution.getSelectedRoutes(), secondStage);
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

    private static final class RepairExposingSolver extends BPCSolver {
        private void initializeForRepair(DynamicInstance instance) throws Exception {
            Configuration config = Configuration.loadFromFile(
                    "src/main/java/org/zzc/bpcsolver/algorithm/config/bpc_solver.yaml"
            );
            initialize(instance, config, new BPCLogger(config, "repair-test"));
        }

        private boolean repair(Node<Route, LmSrc, Graph> node) {
            return repairRouteFractionalIntegerFlow(node);
        }

        private void startStatsForRepair() {
            startRunStatistics();
        }

        private void finishStatsForRepair() {
            finishRunStatistics();
        }

        private BPCSolverTemplate.BPCRunStatistics stats() {
            return getLastRunStatistics();
        }
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

    private LmSrc buildNonBindingCut(int id) {
        LmSrc cut = new LmSrc(0.5, 3);
        cut.setId(id);
        cut.setSubSet(new gnu.trove.set.hash.TIntHashSet(new int[]{1, 2, 3}));
        cut.setMemorySet(Arrays.asList(
                new gnu.trove.set.hash.TIntHashSet(new int[]{1, 2, 3}),
                new gnu.trove.set.hash.TIntHashSet(new int[]{1, 2, 3})
        ));
        cut.setConstant(1_000.0);
        cut.setViolation(0.1);
        return cut;
    }

    private Route findRoute(List<Route> routes, int[] nodeIds) {
        for (Route route : routes) {
            if (Arrays.equals(route.getVisitedVertices().toArray(), nodeIds)) {
                return route;
            }
        }
        return null;
    }

    private double calculateArcUsage(Node<Route, LmSrc, Graph> node, int tail, int head) {
        double usage = 0.0;
        for (int k = 0; k < node.getUsedScheduleSet().size(); k++) {
            List<Route> routes = node.getUsedScheduleSet().get(k);
            double[] values = node.getSolution().getYks().get(k);
            for (int s = 0; s < routes.size(); s++) {
                if (containsArc(routes.get(s), tail, head)) {
                    usage += values[s];
                }
            }
        }
        return usage;
    }

    private boolean containsArc(Route route, int tail, int head) {
        int[] nodes = route.getVisitedVertices().toArray();
        for (int i = 0; i < nodes.length - 1; i++) {
            if (nodes[i] == tail && nodes[i + 1] == head) {
                return true;
            }
        }
        return false;
    }

    private boolean containsArcInRoutePool(Node<Route, LmSrc, Graph> node, int tail, int head) {
        for (List<Route> routes : node.getUsedScheduleSet()) {
            for (Route route : routes) {
                if (containsArc(route, tail, head)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean containsTailWithDifferentSuccessorInRoutePool(Node<Route, LmSrc, Graph> node, int tail, int head) {
        for (List<Route> routes : node.getUsedScheduleSet()) {
            for (Route route : routes) {
                int[] nodes = route.getVisitedVertices().toArray();
                for (int i = 0; i < nodes.length - 1; i++) {
                    if (nodes[i] == tail && nodes[i + 1] != head) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void makeArcFractional(Node<Route, LmSrc, Graph> node, int tail, int head) {
        for (int k = 0; k < node.getUsedScheduleSet().size(); k++) {
            List<Route> routes = node.getUsedScheduleSet().get(k);
            double[] values = node.getSolution().getYks().get(k);
            for (int s = 0; s < routes.size(); s++) {
                if (containsArc(routes.get(s), tail, head)) {
                    values[s] = 0.5;
                    routes.get(s).setValue(0.5);
                    node.setInteger(false);
                    return;
                }
            }
        }
        throw new AssertionError("No route contains the requested arc.");
    }

    private int[] firstArcFromRoutePool(Node<Route, LmSrc, Graph> node) {
        for (List<Route> routes : node.getUsedScheduleSet()) {
            for (Route route : routes) {
                int[] nodes = route.getVisitedVertices().toArray();
                if (nodes.length >= 2) {
                    return new int[]{nodes[0], nodes[1]};
                }
            }
        }
        throw new AssertionError("No route contains at least one arc.");
    }

    private void assertCoversEveryRemainingCustomerExactlyOnce(List<Route> selectedRoutes, DynamicInstance instance) {
        int maxCustomerId = instance.getRemainingCustomerIds().stream()
                .mapToInt(Integer::intValue)
                .max()
                .orElse(0);
        int[] coverCounts = new int[Math.max(instance.getReturnDepot().getId(), maxCustomerId) + 1];
        for (Route route : selectedRoutes) {
            for (int nodeId : route.getVisitedVertices().toArray()) {
                if (instance.getRemainingCustomerIds().contains(nodeId)) {
                    coverCounts[nodeId]++;
                }
            }
        }
        for (int customerId : instance.getRemainingCustomerIds()) {
            assertEquals(1, coverCounts[customerId]);
        }
    }
}

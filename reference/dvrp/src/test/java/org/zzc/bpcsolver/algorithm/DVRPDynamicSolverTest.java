package org.zzc.bpcsolver.algorithm;

import org.junit.jupiter.api.Test;
import org.zzc.bpcsolver.core.branch.Node;
import org.zzc.bpcsolver.core.branch.StageSolution;
import org.zzc.bpcsolver.core.graph.Graph;
import org.zzc.bpcsolver.core.graph.Route;
import org.zzc.bpcsolver.core.graph.RouteFeasibilityValidator;
import org.zzc.bpcsolver.algorithm.cutting.LmSrc;
import org.zzc.bpcsolver.problem.entity.Customer;
import org.zzc.bpcsolver.problem.entity.Depot;
import org.zzc.bpcsolver.problem.entity.DynamicInstance;
import org.zzc.bpcsolver.problem.entity.ProblemData;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DVRPDynamicSolverTest {

    @Test
    void shouldConnectProblemDataToInitialDynamicInstanceAndStaticBpcSolve() {
        ProblemData problemData = buildProblemData();
        DVRPDynamicSolver dynamicSolver = new DVRPDynamicSolver();

        DVRPDynamicSolver.Result result = dynamicSolver.solve(problemData, 0.0, 1, 5);

        DynamicInstance stageInstance = result.getStageInstance();
        StageSolution stageSolution = result.getStageSolution();

        assertNotNull(stageInstance);
        assertNotNull(stageSolution);
        assertEquals(0.0, stageInstance.getCurrentTime(), 1e-9);
        assertEquals(2, stageInstance.getRemainingCustomerIds().size());
        assertTrue(stageSolution.getObjectiveValue() > 0.0);
        assertFalse(stageSolution.getAllRoutes().isEmpty());
        assertFalse(stageSolution.getSelectedRoutes().isEmpty());
        assertTrue(stageSolution.isIntegerSolution());
        assertStageSolutionFeasible(stageInstance, stageSolution);
    }

    @Test
    void shouldMaterializeResultNodeShapeFromStaticInstanceSolve() {
        ProblemData problemData = buildProblemData();
        DynamicInstance instance = new org.zzc.bpcsolver.problem.update.DynamicStateUpdater()
                .createInitialInstance(problemData, 0.0);
        BPCSolver bpcSolver = new BPCSolver();

        BPCSolver.SolveResult result = bpcSolver.solveNode(instance, 1, 5);
        Node<Route, LmSrc, Graph> resultNode = result.getResultNode();

        assertNotNull(resultNode);
        assertTrue(resultNode.isFeasible());
        assertFalse(resultNode.getGraphs().isEmpty());
        assertEquals(resultNode.getGraphs().size(), resultNode.getUsedScheduleSet().size());
        assertEquals(resultNode.getGraphs().size(), resultNode.getSolution().getYks().size());
        assertEquals(result.getSolution().getObjectiveValue(), resultNode.getSolution().getObjValue(), 1e-9);
        assertEquals(resultNode.isInteger(), result.getSolution().isIntegerSolution());
        assertEquals(result.getSolution().getPricingCustomerDuals().length, resultNode.getSolution().getDualsOfJobs().length);
        assertEquals(resultNode.getGraphs().size(), resultNode.getSolution().getDualsOfMachines().length);
        assertFalse(result.getSolution().getSelectedRoutes().isEmpty());

        boolean hasPositiveLpValue = resultNode.getSolution().getYks().stream()
                .filter(values -> values != null)
                .flatMapToDouble(java.util.Arrays::stream)
                .anyMatch(value -> value > 1e-6);
        assertTrue(hasPositiveLpValue);

        if (resultNode.isInteger()) {
            assertFalse(resultNode.getSolution().getBestSchedules().isEmpty());
        }

        int totalColumns = resultNode.getUsedScheduleSet().stream()
                .filter(routes -> routes != null)
                .mapToInt(java.util.List::size)
                .sum();
        assertNotEquals(0, totalColumns);
    }

    @Test
    void shouldRunMultipleDynamicStagesFromEventDrivenDecisionTimes() {
        ProblemData problemData = buildProblemData();
        problemData.getCustomers().get(1).setCallInTime(40.0);
        DVRPDynamicSolver dynamicSolver = new DVRPDynamicSolver();

        List<DVRPDynamicSolver.Result> results = dynamicSolver.solveStages(
                problemData,
                1,
                5
        );

        assertEquals(2, results.size());
        assertEquals(0.0, results.get(0).getStageInstance().getCurrentTime(), 1e-9);
        assertEquals(40.0, results.get(1).getStageInstance().getCurrentTime(), 1e-9);
        assertTrue(results.get(0).getStageSolution().isIntegerSolution());
        assertStageSolutionFeasible(results.get(0).getStageInstance(), results.get(0).getStageSolution());
        assertTrue(results.get(1).getStageInstance().getServedCustomerIds().size() > 0);
        assertTrue(results.get(0).getActualDistanceToNextStage() > 0.0);
    }

    @Test
    void shouldExposeEventDrivenDecisionTimesForValidationRunners() {
        ProblemData problemData = buildProblemData();
        problemData.getCustomers().get(1).setCallInTime(15.0);

        List<Double> decisionTimes = DVRPDynamicSolver.buildEventDrivenDecisionTimes(problemData);

        assertEquals(Arrays.asList(0.0, 15.0), decisionTimes);
    }

    @Test
    void shouldSolveReferenceR101AsSingleEventDrivenStage() {
        org.zzc.bpcsolver.problem.config.ProblemConfig config =
                new org.zzc.bpcsolver.problem.config.ProblemConfigReader().read("dvrp_td.yaml");
        ProblemData problemData = new org.zzc.bpcsolver.problem.loader.ProblemDataLoader().load(config);
        DVRPDynamicSolver dynamicSolver = new DVRPDynamicSolver();

        List<DVRPDynamicSolver.Result> results = dynamicSolver.solveStages(
                problemData,
                BPCSolver.DEFAULT_MAX_ADDED_COLUMNS_PER_ITERATION,
                BPCSolver.DEFAULT_MAX_ITERATIONS
        );

        assertEquals(1, results.size());
        assertEquals(0.0, results.get(0).getStageInstance().getCurrentTime(), 1e-9);

        DynamicInstance initialStageInstance = results.get(0).getStageInstance();
        StageSolution initialStageSolution = results.get(0).getStageSolution();
        assertEquals(25, initialStageInstance.getRemainingCustomerIds().size());
        assertEquals(8, initialStageSolution.getSelectedRoutes().size());
        assertTrue(initialStageSolution.isIntegerSolution());
        assertStageSolutionFeasible(initialStageInstance, initialStageSolution);
        assertEquals(617.1, initialStageSolution.getObjectiveValue(), 2.0);
        assertTrue(results.get(0).getActualDistanceToNextStage() > 0.0);
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
        returnDepot.setId(3);
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
        problemData.getCustomers().add(buildCustomer(1, 10.0, 0.0, 2));
        problemData.getCustomers().add(buildCustomer(2, 20.0, 0.0, 3));
        problemData.buildStaticData();
        return problemData;
    }

    private void assertStageSolutionFeasible(DynamicInstance instance, StageSolution solution) {
        if (instance.getRemainingCustomerIds().isEmpty()) {
            assertTrue(solution.getSelectedRoutes().isEmpty());
            return;
        }
        assertTrue(solution.isIntegerSolution(), "Only integer executable stage solutions should be validated.");
        RouteFeasibilityValidator validator = new RouteFeasibilityValidator();
        List<String> violations = validator.findStageSolutionViolations(instance, solution);
        assertTrue(violations.isEmpty(), () -> "Stage solution should be feasible, violations=" + violations);
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
}

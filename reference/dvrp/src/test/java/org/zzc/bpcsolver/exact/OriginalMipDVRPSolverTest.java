package org.zzc.bpcsolver.exact;

import org.junit.jupiter.api.Test;
import org.zzc.bpcsolver.algorithm.BPCSolver;
import org.zzc.bpcsolver.core.branch.StageSolution;
import org.zzc.bpcsolver.core.graph.Route;
import org.zzc.bpcsolver.core.graph.RouteFeasibilityValidator;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OriginalMipDVRPSolverTest {

    @Test
    void shouldSolveSmallOriginOnlyStageToOptimalRoute() {
        DynamicInstance instance = new DynamicStateUpdater().createInitialInstance(buildLineProblemData(10), 0.0);
        OriginalMipDVRPSolver solver = new OriginalMipDVRPSolver();

        StageSolution solution = solver.solve(instance, fastOptions()).getStageSolution();

        new DVRPExactSolutionValidator().assertValid(instance, solution);
        assertEquals(40.0, solution.getObjectiveValue(), 1e-6);
        assertEquals(1, solution.getSelectedRoutes().size());
        assertEquals(new HashSet<>(Arrays.asList(1, 2)), visitedCustomers(solution.getSelectedRoutes().get(0)));
        assertTrue(solution.isIntegerSolution());
    }

    @Test
    void shouldSplitRoutesWhenCapacityRequiresIt() {
        DynamicInstance instance = new DynamicStateUpdater().createInitialInstance(buildLineProblemData(4), 0.0);
        OriginalMipDVRPSolver solver = new OriginalMipDVRPSolver();

        StageSolution solution = solver.solve(instance, fastOptions()).getStageSolution();

        new DVRPExactSolutionValidator().assertValid(instance, solution);
        assertEquals(60.0, solution.getObjectiveValue(), 1e-6);
        assertEquals(2, solution.getSelectedRoutes().size());
    }

    @Test
    void shouldUseExternalDepotExactlyOnceWhenItExists() {
        DynamicInstance instance = new DynamicStateUpdater().createInitialInstance(buildLineProblemData(10), 0.0);
        instance.setRemainingCustomerIds(new ArrayList<>(Arrays.asList(2)));
        Depot externalDepot = buildDepot(5, Depot.DepotType.EXTERNAL, 13.0, 0.0, 1, 8);
        instance.getExternalDepots().add(externalDepot);

        OriginalMipDVRPSolver solver = new OriginalMipDVRPSolver();
        StageSolution solution = solver.solve(instance, fastOptions()).getStageSolution();

        new DVRPExactSolutionValidator().assertValid(instance, solution);
        assertEquals(1, solution.getSelectedRoutes().size());
        assertEquals(5, solution.getSelectedRoutes().get(0).getStartDepotId());
        assertEquals(27.0, solution.getObjectiveValue(), 1e-6);
    }

    @Test
    void shouldAllowMathematicalModelStartTimeModeForExternalDepots() {
        DynamicInstance instance = new DynamicStateUpdater().createInitialInstance(buildLineProblemData(10), 0.0);
        instance.setRemainingCustomerIds(new ArrayList<>(Arrays.asList(2)));
        Depot externalDepot = buildDepot(5, Depot.DepotType.EXTERNAL, 13.0, 0.0, 1, 8);
        externalDepot.setEarliestTime(30.0);
        instance.getExternalDepots().add(externalDepot);

        OriginalMipDVRPSolver solver = new OriginalMipDVRPSolver();
        StageSolution solution = solver.solve(instance, fastOptions()
                .setStartTimeMode(OriginalMipDVRPSolver.StartTimeMode.START_DEPOT_EARLIEST)
        ).getStageSolution();

        new DVRPExactSolutionValidator(new RouteFeasibilityValidator(
                1e-6,
                RouteFeasibilityValidator.StartTimePolicy.START_DEPOT_EARLIEST
        )).assertValid(instance, solution);
        assertEquals(30.0, solution.getSelectedRoutes().get(0).getArrivalTimes().get(0), 1e-6);
    }

    @Test
    void shouldProvideLowerBoundComparatorAgainstCurrentBpcOnSmallStage() {
        DynamicInstance instance = new DynamicStateUpdater().createInitialInstance(buildLineProblemData(10), 0.0);
        OriginalMipDVRPSolver exactSolver = new OriginalMipDVRPSolver();
        BPCSolver bpcSolver = new BPCSolver();

        StageSolution exactSolution = exactSolver.solve(instance, fastOptions()).getStageSolution();
        StageSolution bpcSolution = bpcSolver.solve(instance, 5, 20);

        new DVRPExactSolutionValidator().assertValid(instance, exactSolution);
        new DVRPExactSolutionValidator().assertValid(instance, bpcSolution);
        assertTrue(exactSolution.getObjectiveValue() <= bpcSolution.getObjectiveValue() + 1e-6);
    }

    @Test
    void shouldCompareBpcAgainstExactMipOnSameDynamicState() {
        ProblemData problemData = buildLineProblemData(10);

        java.util.List<BPCExactComparisonMain.ComparisonRow> rows = BPCExactComparisonMain.compare(
                problemData,
                5,
                20,
                fastOptions()
        );

        assertEquals(1, rows.size());
        BPCExactComparisonMain.ComparisonRow row = rows.get(0);
        new DVRPExactSolutionValidator().assertValid(row.getStageInstance(), row.getBpcSolution());
        new DVRPExactSolutionValidator().assertValid(row.getStageInstance(), row.getExactSolution());
        assertTrue(row.getExactSolution().getObjectiveValue()
                <= row.getBpcSolution().getObjectiveValue() + 1e-6);
        assertTrue(row.getExactResult().getBestBound()
                <= row.getBpcSolution().getObjectiveValue() + 1e-6);
    }

    @Test
    void shouldKeepBpcAndExactClosedOnSparseDynamicReferenceInstance() {
        assertBpcAndExactClosedOnDynamicReferenceInstance("r101_D_75_E_75", 284.0528);
    }

    @Test
    void shouldKeepBpcAndExactClosedOnSecondSeriesCsharpGapInstance() {
        assertBpcAndExactClosedOnDynamicReferenceInstance("r202_D_25_E_25", 2408.0440);
    }

    @Test
    void shouldKeepBpcAndExactClosedOnLargeSecondSeriesReferenceInstance() {
        assertBpcAndExactClosedOnDynamicReferenceInstance("r205_D_25_E_25", 2384.6118);
    }

    private OriginalMipDVRPSolver.Options fastOptions() {
        return OriginalMipDVRPSolver.Options.defaults().setTimeLimitSeconds(10.0);
    }

    private void assertBpcAndExactClosedOnDynamicReferenceInstance(String instanceName,
                                                                  double expectedBpcActualDistance) {
        ProblemConfig config = new ProblemConfigReader().read("dvrp_td.yaml");
        config.setInstanceFileName(instanceName + ".xlsx");
        config.setInstanceBaseName(instanceName);
        ProblemData problemData = new ProblemDataLoader().load(config);

        List<BPCExactComparisonMain.ComparisonRow> rows = BPCExactComparisonMain.compare(
                problemData,
                BPCSolver.DEFAULT_MAX_ADDED_COLUMNS_PER_ITERATION,
                BPCSolver.DEFAULT_MAX_ITERATIONS,
                fastOptions()
        );

        double bpcActualDistance = 0.0;
        double exactActualDistance = 0.0;
        for (BPCExactComparisonMain.ComparisonRow row : rows) {
            if (!row.getStageInstance().getRemainingCustomerIds().isEmpty()) {
                new DVRPExactSolutionValidator().assertValid(row.getStageInstance(), row.getBpcSolution());
                new DVRPExactSolutionValidator().assertValid(row.getStageInstance(), row.getExactSolution());
            }
            assertEquals(
                    row.getExactSolution().getObjectiveValue(),
                    row.getBpcSolution().getObjectiveValue(),
                    1e-4
            );
            bpcActualDistance += row.getBpcActualDistanceToNextStage();
            exactActualDistance += row.getExactActualDistanceToNextStage();
        }

        assertEquals(expectedBpcActualDistance, bpcActualDistance, 1e-4);
        if (!"r101_D_75_E_75".equals(instanceName)) {
            assertEquals(bpcActualDistance, exactActualDistance, 1e-4);
        }
    }

    private Set<Integer> visitedCustomers(Route route) {
        Set<Integer> customers = new HashSet<>();
        int[] vertices = route.getVisitedVertices().toArray();
        for (int i = 1; i < vertices.length - 1; i++) {
            customers.add(vertices[i]);
        }
        return customers;
    }

    private ProblemData buildLineProblemData(int capacity) {
        ProblemData problemData = new ProblemData();

        Depot originDepot = buildDepot(0, Depot.DepotType.ORIGIN, 0.0, 0.0, 2, capacity);
        Depot returnDepot = buildDepot(3, Depot.DepotType.RETURN, 0.0, 0.0, 0, capacity);

        problemData.setOriginDepot(originDepot);
        problemData.setReturnDepot(returnDepot);
        problemData.setVehicleCapacity(capacity);
        problemData.setInitialVehicleCount(2);
        problemData.setSpeed(1.0);
        problemData.setDistanceScale(1.0);
        problemData.setCustomers(new ArrayList<>());
        problemData.getCustomers().add(buildCustomer(1, 10.0, 0.0, 3));
        problemData.getCustomers().add(buildCustomer(2, 20.0, 0.0, 3));
        problemData.buildStaticData();
        return problemData;
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

    private Customer buildCustomer(int id, double x, double y, int demand) {
        Customer customer = new Customer();
        customer.setId(id);
        customer.setX(x);
        customer.setY(y);
        customer.setDemand(demand);
        customer.setEarliestTime(0.0);
        customer.setLatestTime(100.0);
        customer.setServiceTime(0.0);
        customer.setCallInTime(0.0);
        return customer;
    }
}

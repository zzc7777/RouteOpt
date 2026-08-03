package org.zzc.bpcsolver.exact;

import org.junit.jupiter.api.Test;
import org.zzc.bpcsolver.algorithm.BPCSolver;
import org.zzc.bpcsolver.problem.entity.Customer;
import org.zzc.bpcsolver.problem.entity.Depot;
import org.zzc.bpcsolver.problem.entity.ProblemData;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BPCTimingBatchMainTest {

    @Test
    void shouldMeasureJavaBpcTimingWithoutExactMip() {
        ProblemData problemData = buildProblemData();

        List<BPCTimingBatchMain.TimingRow> rows = BPCTimingBatchMain.measureInstance(
                "tiny_case",
                problemData,
                BPCSolver.DEFAULT_MAX_ADDED_COLUMNS_PER_ITERATION,
                5,
                null
        );

        assertEquals(1, rows.size());
        BPCTimingBatchMain.TimingRow row = rows.get(0);
        assertEquals("tiny_case", row.getInstanceName());
        assertEquals(0, row.getStageIndex());
        assertNotNull(row.getStageSolution());
        assertNotNull(row.getRunStatistics());
        assertTrue(row.getRunStatistics().getColumnGenerationRuns() > 0);
        assertTrue(row.getRunStatistics().getColumnGenerationIterations() > 0);
        assertTrue(row.getRunStatistics().getMasterSolves() > 0);
        assertTrue(row.getRunStatistics().getExactPricingCalls() > 0);
        assertTrue(row.getRunStatistics().getElapsedMillis() >= 0);
        assertTrue(row.getBranchSearchStatistics().getElapsedMillis() >= 0);

        BPCTimingBatchMain.InstanceTimingSummary summary = BPCTimingBatchMain.InstanceTimingSummary.from(
                "tiny_case",
                rows
        );
        assertEquals(1, summary.getStageCount());
        assertTrue(summary.getTotalBpcElapsedMillis() >= 0);
        assertTrue(summary.getTotalMasterSolves() > 0);
        assertTrue(summary.getTotalExactPricingCalls() > 0);
        assertFalse(summary.getStageRows().isEmpty());
    }

    @Test
    void shouldMeasureTimingWithPrimalHeuristicDisabled() {
        ProblemData problemData = buildProblemData();

        List<BPCTimingBatchMain.TimingRow> rows = BPCTimingBatchMain.measureInstance(
                "tiny_case",
                problemData,
                BPCSolver.DEFAULT_MAX_ADDED_COLUMNS_PER_ITERATION,
                5,
                null,
                "none"
        );

        assertEquals(1, rows.size());
        assertTrue(rows.get(0).getRunStatistics().getPrimalHeuristicElapsedMillis() >= 0);
        assertNotNull(rows.get(0).getStageSolution());
    }

    @Test
    void shouldMeasureTimingWithParallelPricingDisabled() {
        ProblemData problemData = buildProblemData();

        List<BPCTimingBatchMain.TimingRow> rows = BPCTimingBatchMain.measureInstance(
                "tiny_case",
                problemData,
                BPCSolver.DEFAULT_MAX_ADDED_COLUMNS_PER_ITERATION,
                5,
                null,
                "none",
                false
        );

        assertEquals(1, rows.size());
        assertTrue(rows.get(0).getRunStatistics().getExactPricingCalls() > 0);
        assertNotNull(rows.get(0).getStageSolution());
    }

    @Test
    void shouldMeasureTimingWithLmSrcCuttingEnabled() {
        ProblemData problemData = buildProblemData();

        List<BPCTimingBatchMain.TimingRow> rows = BPCTimingBatchMain.measureInstance(
                "tiny_case",
                problemData,
                BPCSolver.DEFAULT_MAX_ADDED_COLUMNS_PER_ITERATION,
                5,
                null,
                "none",
                false,
                "lm-src"
        );

        assertEquals(1, rows.size());
        assertTrue(rows.get(0).getBranchSearchStatistics().getResultCutCount() >= 0);

        BPCTimingBatchMain.InstanceTimingSummary summary = BPCTimingBatchMain.InstanceTimingSummary.from(
                "tiny_case",
                rows
        );
        assertTrue(summary.getMaxResultCutCount() >= 0);
        assertNotNull(rows.get(0).getStageSolution());
    }

    @Test
    void shouldMeasureTimingWithRelaxationRuleRecorded() {
        ProblemData problemData = buildProblemData();

        List<BPCTimingBatchMain.TimingRow> rows = BPCTimingBatchMain.measureInstance(
                "tiny_case",
                problemData,
                BPCSolver.DEFAULT_MAX_ADDED_COLUMNS_PER_ITERATION,
                5,
                null,
                "none",
                false,
                "none",
                "ng-route"
        );

        assertEquals(1, rows.size());
        assertEquals("ng-route", rows.get(0).getRelaxationRule());

        BPCTimingBatchMain.InstanceTimingSummary summary = BPCTimingBatchMain.InstanceTimingSummary.from(
                "tiny_case",
                rows
        );
        assertEquals("ng-route", summary.getRelaxationRule());
        assertTrue(summary.toCsvLine().contains(",ng-route,"));
    }

    private ProblemData buildProblemData() {
        ProblemData problemData = new ProblemData();

        Depot originDepot = buildDepot(0, Depot.DepotType.ORIGIN);
        Depot returnDepot = buildDepot(3, Depot.DepotType.RETURN);
        problemData.setOriginDepot(originDepot);
        problemData.setReturnDepot(returnDepot);
        problemData.setVehicleCapacity(10);
        problemData.setInitialVehicleCount(1);
        problemData.setSpeed(1.0);
        problemData.setDistanceScale(1.0);

        Customer firstCustomer = buildCustomer(1, 10.0, 0.0, 2);
        Customer secondCustomer = buildCustomer(2, 20.0, 0.0, 3);
        problemData.setCustomers(Arrays.asList(firstCustomer, secondCustomer));
        problemData.buildStaticData();
        return problemData;
    }

    private Depot buildDepot(int id, Depot.DepotType type) {
        Depot depot = new Depot();
        depot.setId(id);
        depot.setType(type);
        depot.setX(0.0);
        depot.setY(0.0);
        depot.setEarliestTime(0.0);
        depot.setLatestTime(100.0);
        depot.setVehicleCount(1);
        depot.setRemainingCapacity(10);
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

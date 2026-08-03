package org.zzc.bpcsolver.exact;

import org.junit.jupiter.api.Test;
import org.zzc.bpcsolver.algorithm.BPCSolver;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BPCExactBatchMainTest {

    @Test
    void shouldKeepRepresentativeInstancesClosedAgainstExactMip() {
        List<BPCExactBatchMain.InstanceValidationSummary> summaries = BPCExactBatchMain.validateInstances(
                BPCExactBatchMain.representativeInstanceNames(),
                BPCSolver.DEFAULT_MAX_ADDED_COLUMNS_PER_ITERATION,
                BPCSolver.DEFAULT_MAX_ITERATIONS,
                OriginalMipDVRPSolver.Options.defaults().setTimeLimitSeconds(20.0)
        );

        assertEquals(BPCExactBatchMain.representativeInstanceNames().size(), summaries.size());
        for (BPCExactBatchMain.InstanceValidationSummary summary : summaries) {
            assertTrue(summary.getStageCount() > 0);
            assertTrue(summary.getMaxObjectiveGap() <= 1e-4, summary.getInstanceName() + " objective gap too large");
            assertBoundClosedOrKnownTimeLimitCase(summary);
            assertTrue(summary.getBpcColumnGenerationRuns() > 0, summary.toCsvLine());
            assertTrue(summary.getBpcMasterSolves() >= summary.getBpcColumnGenerationIterations(), summary.toCsvLine());
            assertTrue(summary.getBpcExactPricingCalls() > 0, summary.toCsvLine());
            assertEquals(0, summary.getBpcHeuristicPricingCalls(), summary.toCsvLine());
        }
    }

    @Test
    void shouldKeepBroadCoverageInstancesClosedAgainstExactMip() {
        List<BPCExactBatchMain.InstanceValidationSummary> summaries = BPCExactBatchMain.validateInstances(
                BPCExactBatchMain.broadCoverageInstanceNames(),
                BPCSolver.DEFAULT_MAX_ADDED_COLUMNS_PER_ITERATION,
                BPCSolver.DEFAULT_MAX_ITERATIONS,
                OriginalMipDVRPSolver.Options.defaults().setTimeLimitSeconds(20.0)
        );

        assertEquals(BPCExactBatchMain.broadCoverageInstanceNames().size(), summaries.size());
        for (BPCExactBatchMain.InstanceValidationSummary summary : summaries) {
            assertTrue(summary.getStageCount() > 0);
            assertTrue(summary.getMaxObjectiveGap() <= 1e-4, summary.toCsvLine());
            assertBoundClosedOrKnownTimeLimitCase(summary);
            assertTrue(summary.getBpcColumnGenerationIterations() > 0, summary.toCsvLine());
        }
    }

    @Test
    void shouldKeepRepresentativeInstancesClosedAgainstExactMipWithMultiStageHeuristicPricing() {
        List<BPCExactBatchMain.InstanceValidationSummary> exactOnlySummaries = BPCExactBatchMain.validateInstances(
                BPCExactBatchMain.representativeInstanceNames(),
                BPCSolver.DEFAULT_MAX_ADDED_COLUMNS_PER_ITERATION,
                BPCSolver.DEFAULT_MAX_ITERATIONS,
                OriginalMipDVRPSolver.Options.defaults().setTimeLimitSeconds(20.0)
        );
        List<BPCExactBatchMain.InstanceValidationSummary> heuristicSummaries = BPCExactBatchMain.validateInstances(
                BPCExactBatchMain.representativeInstanceNames(),
                BPCSolver.DEFAULT_MAX_ADDED_COLUMNS_PER_ITERATION,
                BPCSolver.DEFAULT_MAX_ITERATIONS,
                OriginalMipDVRPSolver.Options.defaults().setTimeLimitSeconds(20.0),
                "multi-stage"
        );

        assertEquals(exactOnlySummaries.size(), heuristicSummaries.size());
        for (int i = 0; i < heuristicSummaries.size(); i++) {
            BPCExactBatchMain.InstanceValidationSummary exactOnlySummary = exactOnlySummaries.get(i);
            BPCExactBatchMain.InstanceValidationSummary heuristicSummary = heuristicSummaries.get(i);
            assertEquals(exactOnlySummary.getInstanceName(), heuristicSummary.getInstanceName());
            assertEquals(exactOnlySummary.getStageCount(), heuristicSummary.getStageCount());
            assertTrue(heuristicSummary.getMaxObjectiveGap() <= 1e-4, heuristicSummary.toCsvLine());
            assertBoundClosedOrKnownTimeLimitCase(heuristicSummary);
            assertTrue(heuristicSummary.getBpcHeuristicPricingCalls() > 0, heuristicSummary.toCsvLine());
            assertTrue(heuristicSummary.getBpcExactPricingCalls() > 0, heuristicSummary.toCsvLine());
            assertTrue(heuristicSummary.getBpcColumnGenerationIterations() > 0, heuristicSummary.toCsvLine());
        }
    }

    private void assertBoundClosedOrKnownTimeLimitCase(BPCExactBatchMain.InstanceValidationSummary summary) {
        if ("r202_D_25_E_25".equals(summary.getInstanceName())) {
            assertTrue(summary.getMaxBoundGap() <= 11.0, summary.toCsvLine());
            return;
        }
        assertTrue(summary.getMaxBoundGap() <= 1e-4, summary.toCsvLine());
    }
}

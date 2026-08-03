package org.zzc.bpcsolver.diagnostic.r201;

import org.junit.jupiter.api.Test;

import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SolomonR201RestrictedMasterDiagnosticMainTest {

    @Test
    void witnessOnlyIntegerMasterShouldSelectAllPublicR201Routes() throws Exception {
        SolomonR201RestrictedMasterDiagnosticMain.DiagnosticRun run =
                SolomonR201RestrictedMasterDiagnosticMain.runDiagnostic(Paths.get("src/main/resources/r201.txt"));

        assertTrue(run.witnessFeasible);
        SolomonR201RestrictedMasterDiagnosticMain.ModelResult witnessIp = findResult(run, "witness_only_ip");

        assertTrue(witnessIp.solved);
        assertTrue(witnessIp.solutionInteger);
        assertTrue(witnessIp.stageFeasible, witnessIp.violations::toString);
        assertTrue(witnessIp.allWitnessFullySelected);
        assertEquals(8, witnessIp.fullySelectedWitnessRoutes);
        assertEquals(SolomonR201RestrictedMasterDiagnosticMain.WITNESS_JAVA_FLOOR_OBJECTIVE,
                witnessIp.objective, 1e-9);
        assertEquals(0, witnessIp.coverageStats.underCoveredCustomers);
        assertEquals(0, witnessIp.coverageStats.overCoveredCustomers);
    }

    private SolomonR201RestrictedMasterDiagnosticMain.ModelResult findResult(
            SolomonR201RestrictedMasterDiagnosticMain.DiagnosticRun run,
            String modelName) {
        for (SolomonR201RestrictedMasterDiagnosticMain.ModelResult result : run.results) {
            if (modelName.equals(result.modelName)) {
                return result;
            }
        }
        throw new AssertionError("Missing diagnostic result: " + modelName);
    }
}

package org.zzc.bpcsolver.benchmark;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StaticScaleLimitBenchmarkMainTest {

    @Test
    void shouldReadSolomon100CapacityForR201BenchmarkInstance() {
        assertEquals(1000, StaticScaleLimitBenchmarkMain.readSolomon100Capacity("r201"));
    }
}

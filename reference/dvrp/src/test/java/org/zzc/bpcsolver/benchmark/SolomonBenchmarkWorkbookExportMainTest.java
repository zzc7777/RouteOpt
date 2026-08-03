package org.zzc.bpcsolver.benchmark;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SolomonBenchmarkWorkbookExportMainTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldReadBpcBenchmarkWorkbookAndExportCsv() throws Exception {
        Path source = Paths.get("src/main/resources/solomon_100_customers_benchmark_bpc.xlsx");

        List<SolomonBenchmarkWorkbookExportMain.BenchmarkRow> rows =
                SolomonBenchmarkWorkbookExportMain.readWorkbook(source);

        assertEquals(56, rows.size());
        assertEquals("c101", rows.get(0).getInstance());
        assertEquals(10, rows.get(0).getVehicles());
        assertEquals(827.3, rows.get(0).getDistance(), 1e-9);

        Path output = tempDir.resolve("benchmark-reference.csv");
        SolomonBenchmarkWorkbookExportMain.writeCsv(rows, output);

        assertTrue(Files.exists(output));
        List<String> lines = Files.readAllLines(output);
        assertEquals("instance,benchmark_vehicles,benchmark_distance", lines.get(0));
        assertEquals("c101,10,827.3", lines.get(1));
    }
}

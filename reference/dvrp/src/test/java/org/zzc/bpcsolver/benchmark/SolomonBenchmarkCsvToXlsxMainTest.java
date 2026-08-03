package org.zzc.bpcsolver.benchmark;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SolomonBenchmarkCsvToXlsxMainTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldConvertExactBenchmarkCsvToCompactWorkbookShape() throws Exception {
        Path source = Paths.get("src/main/resources/solomon_vrptw_exact_single_objective_benchmark_100_customers.csv");

        List<SolomonBenchmarkCsvToXlsxMain.BenchmarkRow> rows =
                SolomonBenchmarkCsvToXlsxMain.readBenchmarkCsv(source);

        assertEquals(56, rows.size());
        assertEquals("c101", rows.get(0).getInstance());
        assertEquals(10, rows.get(0).getVehicles());
        assertEquals(827.3, rows.get(0).getDistance(), 1e-9);

        Path output = tempDir.resolve("benchmark.xlsx");
        SolomonBenchmarkCsvToXlsxMain.writeWorkbook(rows, output);

        assertTrue(Files.exists(output));
        try (InputStream inputStream = Files.newInputStream(output);
             Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheet("Sheet1");
            assertEquals("instance", sheet.getRow(0).getCell(0).getStringCellValue());
            assertEquals("vehicles", sheet.getRow(0).getCell(1).getStringCellValue());
            assertEquals("distance", sheet.getRow(0).getCell(2).getStringCellValue());
            assertEquals("c101", sheet.getRow(1).getCell(0).getStringCellValue());
            assertEquals(10, (int) sheet.getRow(1).getCell(1).getNumericCellValue());
            assertEquals(827.3, sheet.getRow(1).getCell(2).getNumericCellValue(), 1e-9);
        }
    }
}

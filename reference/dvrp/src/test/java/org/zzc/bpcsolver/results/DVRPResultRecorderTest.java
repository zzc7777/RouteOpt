package org.zzc.bpcsolver.results;

import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zzc.bpcsolver.algorithm.config.Configuration;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DVRPResultRecorderTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldReadResultRecordingSwitchFromSolverYaml() throws Exception {
        Path configPath = tempDir.resolve("bpc_solver.yaml");
        String yaml = ""
                + "name: test\n"
                + "version: '1.0'\n"
                + "resultRecording:\n"
                + "  RECORD_RESULTS: true\n"
                + "  RESULTS_DIR: \"" + tempDir.toString().replace("\\", "\\\\") + "\"\n"
                + "  RUN_DESCRIPTION: yaml-enabled\n";
        Files.write(configPath, yaml.getBytes(StandardCharsets.UTF_8));

        DVRPResultRecorder recorder = DVRPResultRecorder.fromSolverConfigPath(configPath.toString());

        assertTrue(recorder.isEnabled());
        assertEquals(tempDir.resolve("java"), recorder.getResultsDir());
    }

    @Test
    void shouldWriteFinalOnlyRunWorkbookUnderSolverFolder() throws Exception {
        Configuration.ResultRecordingConfig config = new Configuration.ResultRecordingConfig();
        config.setRecordResults(true);
        config.setResultsDir(tempDir.toString());
        config.setRunDescription("unit-test");
        DVRPResultRecorder recorder = new DVRPResultRecorder(config);

        String runId = recorder.nextRunId();
        DVRPResultRecord finalRecord = DVRPResultRecord.builder()
                .runId(runId)
                .solver("java-bpc")
                .instance("tiny")
                .numCustomers(2)
                .decisionTimes("0.0;10.0")
                .finalActualDistance(12.5)
                .solveTimeMillis(34L)
                .status("success")
                .notes("smoke")
                .build();

        recorder.write(runId, finalRecord, "smoke");

        Path finalWorkbook = tempDir.resolve("java").resolve(runId).resolve(DVRPResultWorkbookWriter.RESULT_FILE);
        assertTrue(Files.exists(finalWorkbook));

        try (InputStream inputStream = Files.newInputStream(finalWorkbook);
             Workbook workbook = WorkbookFactory.create(inputStream)) {
            assertEquals("final_results", workbook.getSheetAt(0).getSheetName());
            assertEquals("solver", workbook.getSheetAt(0).getRow(0).getCell(0).getStringCellValue());
            assertEquals("instance", workbook.getSheetAt(0).getRow(0).getCell(1).getStringCellValue());
            assertEquals("num_customers", workbook.getSheetAt(0).getRow(0).getCell(2).getStringCellValue());
            assertEquals("decision_times", workbook.getSheetAt(0).getRow(0).getCell(3).getStringCellValue());
            assertEquals("final_actual_distance", workbook.getSheetAt(0).getRow(0).getCell(4).getStringCellValue());
            assertEquals("java-bpc", workbook.getSheetAt(0).getRow(1).getCell(0).getStringCellValue());
            assertEquals("tiny", workbook.getSheetAt(0).getRow(1).getCell(1).getStringCellValue());
            assertEquals(2.0, workbook.getSheetAt(0).getRow(1).getCell(2).getNumericCellValue(), 1e-9);
            assertEquals("0.0;10.0", workbook.getSheetAt(0).getRow(1).getCell(3).getStringCellValue());
            assertEquals(12.5, workbook.getSheetAt(0).getRow(1).getCell(4).getNumericCellValue(), 1e-9);
            assertEquals("success", workbook.getSheetAt(0).getRow(1).getCell(6).getStringCellValue());
        }
    }

    @Test
    void shouldAllocateRunsInsideEachSolverFamilyFolder() {
        Configuration.ResultRecordingConfig config = new Configuration.ResultRecordingConfig();
        config.setRecordResults(true);
        config.setResultsDir(tempDir.toString());

        DVRPResultRecorder cplexRecorder = new DVRPResultRecorder(
                config,
                DVRPResultRecorder.SolverFamily.CPLEX,
                new DVRPResultWorkbookWriter()
        );
        String runId = cplexRecorder.nextRunId();
        DVRPResultRecord finalRecord = DVRPResultRecord.builder()
                .runId(runId)
                .solver("cplex-original-mip")
                .instance("tiny")
                .numCustomers(2)
                .decisionTimes("0.0")
                .finalActualDistance(1.0)
                .solveTimeMillis(2L)
                .status("success")
                .build();

        cplexRecorder.write(runId, Collections.singletonList(finalRecord), "", false);

        assertTrue(Files.exists(tempDir.resolve("cplex").resolve(runId).resolve("result.xlsx")));
    }
}

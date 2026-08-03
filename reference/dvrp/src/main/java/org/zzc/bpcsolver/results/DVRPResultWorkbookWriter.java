package org.zzc.bpcsolver.results;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class DVRPResultWorkbookWriter {

    public static final String RESULT_FILE = "result.xlsx";

    private static final String FINAL_SHEET = "final_results";
    private static final String[] FINAL_HEADERS = {
            "solver",
            "instance",
            "num_customers",
            "decision_times",
            "final_actual_distance",
            "solve_time_ms",
            "status",
            "notes"
    };

    public void write(Path runDir, List<DVRPResultRecord> finalRecords) {
        if (runDir == null) {
            throw new IllegalArgumentException("runDir must not be null.");
        }
        try {
            Files.createDirectories(runDir);
            writeFinalRecords(runDir.resolve(RESULT_FILE), finalRecords);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write DVRP result workbooks.", e);
        }
    }

    private void writeFinalRecords(Path workbookPath, List<DVRPResultRecord> records) throws IOException {
        if (records == null || records.isEmpty()) {
            return;
        }
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = getOrCreateSheet(workbook, FINAL_SHEET, FINAL_HEADERS);
            for (DVRPResultRecord record : records) {
                Row row = sheet.createRow(sheet.getLastRowNum() + 1);
                writeString(row, 0, record.getSolver());
                writeString(row, 1, record.getInstance());
                writeNullableInt(row, 2, record.getNumCustomers());
                writeString(row, 3, record.getDecisionTimes());
                writeDouble(row, 4, record.getFinalActualDistance());
                writeLong(row, 5, record.getSolveTimeMillis());
                writeString(row, 6, record.getStatus());
                writeString(row, 7, record.getNotes());
            }
            autosize(sheet, FINAL_HEADERS.length);
            save(workbook, workbookPath);
        }
    }

    private Sheet getOrCreateSheet(Workbook workbook, String sheetName, String[] headers) {
        Sheet sheet = workbook.getSheet(sheetName);
        if (sheet != null) {
            ensureHeader(sheet, headers);
            return sheet;
        }
        return createSheetWithHeader(workbook, sheetName, headers);
    }

    private Sheet createSheetWithHeader(Workbook workbook, String sheetName, String[] headers) {
        Sheet sheet = workbook.createSheet(sheetName);
        Row headerRow = sheet.createRow(0);
        CellStyle headerStyle = workbook.createCellStyle();
        org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
        return sheet;
    }

    private void ensureHeader(Sheet sheet, String[] headers) {
        Row headerRow = sheet.getRow(0);
        if (headerRow == null) {
            headerRow = sheet.createRow(0);
        }
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.getCell(i);
            if (cell == null) {
                cell = headerRow.createCell(i);
            }
            if (!headers[i].equals(cell.getStringCellValue())) {
                cell.setCellValue(headers[i]);
            }
        }
    }

    private void save(Workbook workbook, Path workbookPath) throws IOException {
        try (OutputStream outputStream = Files.newOutputStream(workbookPath)) {
            workbook.write(outputStream);
        }
    }

    private void autosize(Sheet sheet, int columnCount) {
        for (int i = 0; i < columnCount; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void writeString(Row row, int index, String value) {
        row.createCell(index).setCellValue(value == null ? "" : value);
    }

    private void writeNullableInt(Row row, int index, Integer value) {
        if (value == null) {
            row.createCell(index).setBlank();
            return;
        }
        writeInt(row, index, value);
    }

    private void writeInt(Row row, int index, int value) {
        row.createCell(index).setCellValue(value);
    }

    private void writeLong(Row row, int index, long value) {
        row.createCell(index).setCellValue(value);
    }

    private void writeDouble(Row row, int index, double value) {
        row.createCell(index).setCellValue(value);
    }
}

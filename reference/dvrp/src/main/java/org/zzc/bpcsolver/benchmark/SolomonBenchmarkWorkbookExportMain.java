package org.zzc.bpcsolver.benchmark;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Exports compact Solomon benchmark workbooks to CSV for batch scripts.
 */
public class SolomonBenchmarkWorkbookExportMain {

    private static final Path DEFAULT_INPUT = Paths.get(
            "src/main/resources/solomon_100_customers_benchmark_bpc.xlsx"
    );
    private static final Path DEFAULT_OUTPUT = Paths.get(
            "results/solomon100-benchmark/benchmark-reference.csv"
    );

    public static void main(String[] args) {
        Path input = Paths.get(args.length > 0 ? args[0] : DEFAULT_INPUT.toString());
        Path output = Paths.get(args.length > 1 ? args[1] : DEFAULT_OUTPUT.toString());
        try {
            List<BenchmarkRow> rows = readWorkbook(input);
            writeCsv(rows, output);
            System.out.println("Wrote " + rows.size() + " benchmark rows to " + output.toAbsolutePath());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to export Solomon benchmark workbook.", e);
        }
    }

    static List<BenchmarkRow> readWorkbook(Path input) throws IOException {
        try (InputStream inputStream = Files.newInputStream(input);
             Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheet("Sheet1");
            if (sheet == null) {
                throw new IllegalArgumentException("Sheet1 not found in benchmark workbook: " + input);
            }

            Map<String, Integer> headerMap = buildHeaderMap(sheet.getRow(0));
            List<BenchmarkRow> rows = new ArrayList<>();
            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null || isRowEmpty(row)) {
                    continue;
                }
                rows.add(new BenchmarkRow(
                        getString(row, headerMap, "instance").toLowerCase(Locale.ROOT),
                        (int) Math.round(getDouble(row, headerMap, "vehicles")),
                        getDouble(row, headerMap, "distance")
                ));
            }
            rows.sort(Comparator
                    .comparingInt((BenchmarkRow row) -> familyOrder(row.instance))
                    .thenComparingInt(row -> numericSuffix(row.instance))
                    .thenComparing(row -> row.instance));
            return rows;
        }
    }

    static void writeCsv(List<BenchmarkRow> rows, Path output) throws IOException {
        Path parent = output.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(output, StandardCharsets.UTF_8))) {
            writer.println("instance,benchmark_vehicles,benchmark_distance");
            for (BenchmarkRow row : rows) {
                writer.println(escapeCsv(row.instance)
                        + "," + row.vehicles
                        + "," + formatDouble(row.distance));
            }
        }
    }

    private static Map<String, Integer> buildHeaderMap(Row headerRow) {
        if (headerRow == null) {
            throw new IllegalArgumentException("Header row is missing in benchmark workbook.");
        }
        Map<String, Integer> headerMap = new HashMap<>();
        for (Cell cell : headerRow) {
            headerMap.put(cell.getStringCellValue().trim().toLowerCase(Locale.ROOT), cell.getColumnIndex());
        }
        for (String requiredColumn : new String[]{"instance", "vehicles", "distance"}) {
            if (!headerMap.containsKey(requiredColumn)) {
                throw new IllegalArgumentException("Missing benchmark column: " + requiredColumn);
            }
        }
        return headerMap;
    }

    private static boolean isRowEmpty(Row row) {
        for (Cell cell : row) {
            if (cell != null && cell.getCellType() != CellType.BLANK && !cell.toString().trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static String getString(Row row, Map<String, Integer> headerMap, String columnName) {
        Cell cell = row.getCell(headerMap.get(columnName));
        if (cell == null) {
            return "";
        }
        if (cell.getCellType() == CellType.STRING) {
            return cell.getStringCellValue().trim();
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            return formatDouble(cell.getNumericCellValue());
        }
        return cell.toString().trim();
    }

    private static double getDouble(Row row, Map<String, Integer> headerMap, String columnName) {
        Cell cell = row.getCell(headerMap.get(columnName));
        if (cell == null) {
            throw new IllegalArgumentException("Missing value for benchmark column: " + columnName);
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            return cell.getNumericCellValue();
        }
        if (cell.getCellType() == CellType.STRING) {
            return Double.parseDouble(cell.getStringCellValue().trim());
        }
        throw new IllegalArgumentException("Unsupported benchmark cell type for column: " + columnName);
    }

    private static int familyOrder(String instance) {
        if (instance.startsWith("c")) {
            return 0;
        }
        if (instance.startsWith("r") && !instance.startsWith("rc")) {
            return 1;
        }
        if (instance.startsWith("rc")) {
            return 2;
        }
        return 3;
    }

    private static int numericSuffix(String instance) {
        String digits = instance.replaceAll("\\D+", "");
        if (digits.isEmpty()) {
            return Integer.MAX_VALUE;
        }
        return Integer.parseInt(digits);
    }

    private static String formatDouble(double value) {
        if (Double.isNaN(value)) {
            return "";
        }
        if (Math.rint(value) == value) {
            return String.format(Locale.US, "%.0f", value);
        }
        return String.format(Locale.US, "%.4f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private static String escapeCsv(String value) {
        String sanitized = value == null ? "" : value.replace('\n', ' ').replace('\r', ' ');
        if (sanitized.contains(",") || sanitized.contains("\"")) {
            return "\"" + sanitized.replace("\"", "\"\"") + "\"";
        }
        return sanitized;
    }

    static final class BenchmarkRow {
        private final String instance;
        private final int vehicles;
        private final double distance;

        private BenchmarkRow(String instance, int vehicles, double distance) {
            this.instance = instance;
            this.vehicles = vehicles;
            this.distance = distance;
        }

        String getInstance() {
            return instance;
        }

        int getVehicles() {
            return vehicles;
        }

        double getDistance() {
            return distance;
        }
    }
}

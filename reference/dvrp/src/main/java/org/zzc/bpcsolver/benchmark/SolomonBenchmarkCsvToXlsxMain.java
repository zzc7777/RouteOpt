package org.zzc.bpcsolver.benchmark;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Converts the Solomon exact VRPTW benchmark CSV into the compact workbook shape
 * used by {@code solomon_100_customers_benchmark.xlsx}.
 */
public class SolomonBenchmarkCsvToXlsxMain {

    private static final Path DEFAULT_INPUT = Paths.get(
            "src/main/resources/solomon_vrptw_exact_single_objective_benchmark_100_customers.csv"
    );
    private static final Path DEFAULT_OUTPUT = Paths.get(
            "src/main/resources/solomon_100_customers_benchmark.xlsx"
    );
    private static final String[] HEADERS = {"instance", "vehicles", "distance"};

    public static void main(String[] args) {
        Path input = Paths.get(args.length > 0 ? args[0] : DEFAULT_INPUT.toString());
        Path output = Paths.get(args.length > 1 ? args[1] : DEFAULT_OUTPUT.toString());
        try {
            List<BenchmarkRow> rows = readBenchmarkCsv(input);
            writeWorkbook(rows, output);
            System.out.println("Wrote " + rows.size() + " benchmark rows to " + output.toAbsolutePath());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to convert Solomon benchmark CSV.", e);
        }
    }

    static List<BenchmarkRow> readBenchmarkCsv(Path input) throws IOException {
        List<BenchmarkRow> rows = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(input, StandardCharsets.UTF_8)) {
            String header = reader.readLine();
            if (header == null) {
                return rows;
            }
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                List<String> fields = parseCsvLine(line);
                if (fields.size() < 3) {
                    throw new IllegalArgumentException("Invalid benchmark CSV row: " + line);
                }
                rows.add(new BenchmarkRow(
                        fields.get(0).trim().toLowerCase(Locale.ROOT),
                        Integer.parseInt(fields.get(2).trim()),
                        Double.parseDouble(fields.get(1).trim())
                ));
            }
        }
        rows.sort(Comparator
                .comparingInt((BenchmarkRow row) -> familyOrder(row.instance))
                .thenComparingInt(row -> numericSuffix(row.instance))
                .thenComparing(row -> row.instance));
        return rows;
    }

    static void writeWorkbook(List<BenchmarkRow> rows, Path output) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Sheet1");
            writeHeader(sheet.createRow(0));
            for (int index = 0; index < rows.size(); index++) {
                writeRow(sheet.createRow(index + 1), rows.get(index));
            }
            for (int column = 0; column < HEADERS.length; column++) {
                sheet.autoSizeColumn(column);
            }
            try (OutputStream outputStream = Files.newOutputStream(output)) {
                workbook.write(outputStream);
            }
        }
    }

    private static void writeHeader(Row row) {
        for (int column = 0; column < HEADERS.length; column++) {
            Cell cell = row.createCell(column);
            cell.setCellValue(HEADERS[column]);
        }
    }

    private static void writeRow(Row row, BenchmarkRow benchmarkRow) {
        row.createCell(0).setCellValue(benchmarkRow.instance);
        row.createCell(1).setCellValue(benchmarkRow.vehicles);
        row.createCell(2).setCellValue(benchmarkRow.distance);
    }

    private static List<String> parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int index = 0; index < line.length(); index++) {
            char ch = line.charAt(index);
            if (ch == '"') {
                if (inQuotes && index + 1 < line.length() && line.charAt(index + 1) == '"') {
                    current.append('"');
                    index++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (ch == ',' && !inQuotes) {
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        fields.add(current.toString());
        return fields;
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

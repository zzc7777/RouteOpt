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
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Converts standard Solomon 100-customer VRPTW txt instances into the DVRP Excel
 * shape consumed by {@code ExcelReader}. Every customer disclosure time is fixed to 0.
 */
public class Solomon100ToDvrpExcelMain {

    private static final String DEFAULT_INPUT_DIR = "src/main/resources/solomon-100/In";
    private static final String DEFAULT_OUTPUT_DIR = "src/main/resources/Solomon100_Instance";
    private static final String[] HEADERS = {
            "ID",
            "Coord_X",
            "Coord_Y",
            "Demand",
            "Time_Earliest",
            "Time_Latest",
            "Time_Service",
            "Time_Disclosure"
    };

    public static void main(String[] args) {
        Path inputDir = Paths.get(args.length > 0 ? args[0] : DEFAULT_INPUT_DIR);
        Path outputDir = Paths.get(args.length > 1 ? args[1] : DEFAULT_OUTPUT_DIR);

        try {
            int converted = convertDirectory(inputDir, outputDir);
            System.out.println("Converted " + converted + " Solomon instances to " + outputDir.toAbsolutePath());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to convert Solomon instances.", e);
        }
    }

    public static int convertDirectory(Path inputDir, Path outputDir) throws IOException {
        if (!Files.isDirectory(inputDir)) {
            throw new IllegalArgumentException("Input directory does not exist: " + inputDir);
        }
        Files.createDirectories(outputDir);

        List<Path> txtFiles;
        try (Stream<Path> stream = Files.list(inputDir)) {
            txtFiles = stream
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".txt"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .collect(Collectors.toList());
        }

        int converted = 0;
        for (Path txtFile : txtFiles) {
            SolomonInstance instance = readSolomonInstance(txtFile);
            Path outputPath = outputDir.resolve(stripExtension(txtFile.getFileName().toString()) + ".xlsx");
            writeDvrpWorkbook(instance, outputPath);
            converted++;
        }
        return converted;
    }

    static SolomonInstance readSolomonInstance(Path txtFile) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(txtFile, StandardCharsets.UTF_8)) {
            List<String> lines = reader.lines().collect(Collectors.toList());
            int capacity = readCapacity(lines);
            List<SolomonRow> rows = readRows(lines);
            if (rows.size() < 2) {
                throw new IllegalArgumentException("Instance must contain depot and customers: " + txtFile);
            }
            SolomonRow depot = rows.get(0);
            List<SolomonRow> customers = rows.subList(1, rows.size());
            return new SolomonInstance(capacity, depot, new ArrayList<>(customers));
        }
    }

    private static int readCapacity(List<String> lines) {
        for (String line : lines) {
            String[] parts = splitNumbers(line);
            if (parts.length == 2) {
                return Integer.parseInt(parts[1]);
            }
        }
        throw new IllegalArgumentException("Vehicle capacity line not found.");
    }

    private static List<SolomonRow> readRows(List<String> lines) {
        List<SolomonRow> rows = new ArrayList<>();
        for (String line : lines) {
            String[] parts = splitNumbers(line);
            if (parts.length < 7) {
                continue;
            }
            rows.add(new SolomonRow(
                    Integer.parseInt(parts[0]),
                    Double.parseDouble(parts[1]),
                    Double.parseDouble(parts[2]),
                    Integer.parseInt(parts[3]),
                    Double.parseDouble(parts[4]),
                    Double.parseDouble(parts[5]),
                    Double.parseDouble(parts[6])
            ));
        }
        return rows;
    }

    private static String[] splitNumbers(String line) {
        if (line == null) {
            return new String[0];
        }
        String trimmed = line.trim();
        if (trimmed.isEmpty() || !Character.isDigit(trimmed.charAt(0))) {
            return new String[0];
        }
        return trimmed.split("\\s+");
    }

    static void writeDvrpWorkbook(SolomonInstance instance, Path outputPath) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Sheet1");
            writeHeader(sheet.createRow(0));
            writeRow(sheet.createRow(1), instance.depot, 0.0);

            int rowIndex = 2;
            for (SolomonRow customer : instance.customers) {
                writeRow(sheet.createRow(rowIndex), customer, 0.0);
                rowIndex++;
            }

            SolomonRow returnDepot = new SolomonRow(
                    instance.customers.size() + 1,
                    instance.depot.x,
                    instance.depot.y,
                    0,
                    instance.depot.readyTime,
                    instance.depot.dueDate,
                    0.0
            );
            writeRow(sheet.createRow(rowIndex), returnDepot, 0.0);

            for (int column = 0; column < HEADERS.length; column++) {
                sheet.autoSizeColumn(column);
            }

            try (OutputStream outputStream = Files.newOutputStream(outputPath)) {
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

    private static void writeRow(Row row, SolomonRow source, double disclosureTime) {
        row.createCell(0).setCellValue(source.id);
        row.createCell(1).setCellValue(source.x);
        row.createCell(2).setCellValue(source.y);
        row.createCell(3).setCellValue(source.demand);
        row.createCell(4).setCellValue(source.readyTime);
        row.createCell(5).setCellValue(source.dueDate);
        row.createCell(6).setCellValue(source.serviceTime);
        row.createCell(7).setCellValue(disclosureTime);
    }

    private static String stripExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex < 0 ? fileName : fileName.substring(0, dotIndex);
    }

    static final class SolomonInstance {
        private final int capacity;
        private final SolomonRow depot;
        private final List<SolomonRow> customers;

        private SolomonInstance(int capacity, SolomonRow depot, List<SolomonRow> customers) {
            this.capacity = capacity;
            this.depot = depot;
            this.customers = customers;
        }

        int getCapacity() {
            return capacity;
        }

        SolomonRow getDepot() {
            return depot;
        }

        List<SolomonRow> getCustomers() {
            return customers;
        }
    }

    static final class SolomonRow {
        private final int id;
        private final double x;
        private final double y;
        private final int demand;
        private final double readyTime;
        private final double dueDate;
        private final double serviceTime;

        private SolomonRow(int id,
                           double x,
                           double y,
                           int demand,
                           double readyTime,
                           double dueDate,
                           double serviceTime) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.demand = demand;
            this.readyTime = readyTime;
            this.dueDate = dueDate;
            this.serviceTime = serviceTime;
        }

        int getId() {
            return id;
        }

        double getX() {
            return x;
        }

        double getY() {
            return y;
        }

        int getDemand() {
            return demand;
        }

        double getReadyTime() {
            return readyTime;
        }

        double getDueDate() {
            return dueDate;
        }

        double getServiceTime() {
            return serviceTime;
        }
    }
}

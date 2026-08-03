package org.zzc.bpcsolver.problem.loader;

import org.apache.poi.ss.usermodel.*;
import org.zzc.bpcsolver.problem.config.ProblemConfig;
import org.zzc.bpcsolver.problem.entity.Customer;
import org.zzc.bpcsolver.problem.entity.Depot;
import org.zzc.bpcsolver.problem.entity.ProblemData;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class ExcelReader {

    public ProblemData read(ProblemConfig config) {
        try (InputStream inputStream = openInputStream(buildResourcePath(config));
             Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheet("Sheet1");
            if (sheet == null) {
                throw new IllegalArgumentException("Sheet1 not found in workbook.");
            }

            Map<String, Integer> headerMap = buildHeaderMap(sheet.getRow(0));
            validateRequiredColumns(headerMap);

            ProblemData problemData = new ProblemData();
            problemData.setVehicleCapacity(config.getVehicleCapacity());
            problemData.setInitialVehicleCount(config.getInitialVehicleCount());
            problemData.setSpeed(config.getSpeed());
            problemData.setDistanceScale(config.getDistanceScale());
            problemData.setDistanceDecimalPlaces(config.getDistanceDecimalPlaces());
            problemData.setTravelTimeDecimalPlaces(config.getTravelTimeDecimalPlaces());
            problemData.setDistanceRoundingMode(config.getDistanceRoundingMode());
            problemData.setTravelTimeRoundingMode(config.getTravelTimeRoundingMode());

            int firstDataRow = 1;
            int lastDataRow = sheet.getLastRowNum();
            problemData.setOriginDepot(readDepot(sheet.getRow(firstDataRow), headerMap, Depot.DepotType.ORIGIN, config));
            problemData.setReturnDepot(readReturnDepot(sheet.getRow(lastDataRow), headerMap, config));

            int firstCustomerRow = firstDataRow + 1;
            int customerEndRowExclusive = firstCustomerRow + config.getNumCustomers();
            if (customerEndRowExclusive > lastDataRow) {
                throw new IllegalArgumentException("Workbook does not contain enough customer rows for NUM_CUSTOMERS="
                        + config.getNumCustomers() + ".");
            }
            for (int rowIndex = firstCustomerRow; rowIndex < customerEndRowExclusive; rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null || isRowEmpty(row)) {
                    continue;
                }
                problemData.getCustomers().add(readCustomer(row, headerMap));
            }
            return problemData;
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read Excel instance.", e);
        }
    }

    private String buildResourcePath(ProblemConfig config) {
        return config.getResourcePath() + config.buildInstanceFileName();
    }

    private InputStream openInputStream(String resourcePath) {
        InputStream inputStream = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream(resourcePath);
        if (inputStream == null) {
            throw new IllegalArgumentException("Resource not found: " + resourcePath);
        }
        return inputStream;
    }

    private Map<String, Integer> buildHeaderMap(Row headerRow) {
        if (headerRow == null) {
            throw new IllegalArgumentException("Header row is missing.");
        }
        Map<String, Integer> headerMap = new HashMap<>();
        for (Cell cell : headerRow) {
            headerMap.put(cell.getStringCellValue().trim(), cell.getColumnIndex());
        }
        return headerMap;
    }

    private void validateRequiredColumns(Map<String, Integer> headerMap) {
        String[] requiredColumns = {
                "ID",
                "Coord_X",
                "Coord_Y",
                "Demand",
                "Time_Earliest",
                "Time_Latest",
                "Time_Service",
                "Time_Disclosure"
        };
        for (String column : requiredColumns) {
            if (!headerMap.containsKey(column)) {
                throw new IllegalArgumentException("Missing required column: " + column);
            }
        }
    }

    private Depot readDepot(Row row, Map<String, Integer> headerMap, Depot.DepotType depotType, ProblemConfig config) {
        Depot depot = new Depot();
        depot.setId(getInt(row, headerMap, "ID"));
        depot.setX(getDouble(row, headerMap, "Coord_X"));
        depot.setY(getDouble(row, headerMap, "Coord_Y"));
        depot.setEarliestTime(getDouble(row, headerMap, "Time_Earliest"));
        depot.setLatestTime(getDouble(row, headerMap, "Time_Latest"));
        depot.setType(depotType);
        depot.setVehicleCount(depotType == Depot.DepotType.ORIGIN ? config.getInitialVehicleCount() : 0);
        depot.setRemainingCapacity(config.getVehicleCapacity());
        return depot;
    }

    private Depot readReturnDepot(Row row, Map<String, Integer> headerMap, ProblemConfig config) {
        Depot depot = readDepot(row, headerMap, Depot.DepotType.RETURN, config);
        depot.setId(config.getNumCustomers() + 1);
        return depot;
    }

    private Customer readCustomer(Row row, Map<String, Integer> headerMap) {
        Customer customer = new Customer();
        customer.setId(getInt(row, headerMap, "ID"));
        customer.setX(getDouble(row, headerMap, "Coord_X"));
        customer.setY(getDouble(row, headerMap, "Coord_Y"));
        customer.setDemand(getInt(row, headerMap, "Demand"));
        customer.setEarliestTime(getDouble(row, headerMap, "Time_Earliest"));
        customer.setLatestTime(getDouble(row, headerMap, "Time_Latest"));
        customer.setServiceTime(getDouble(row, headerMap, "Time_Service"));
        customer.setCallInTime(getDouble(row, headerMap, "Time_Disclosure"));
        return customer;
    }

    private boolean isRowEmpty(Row row) {
        for (Cell cell : row) {
            if (cell != null && cell.getCellType() != CellType.BLANK && !cell.toString().trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private int getInt(Row row, Map<String, Integer> headerMap, String columnName) {
        return (int) Math.round(getDouble(row, headerMap, columnName));
    }

    private double getDouble(Row row, Map<String, Integer> headerMap, String columnName) {
        Cell cell = row.getCell(headerMap.get(columnName));
        if (cell == null) {
            throw new IllegalArgumentException("Cell is missing for column: " + columnName);
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            return cell.getNumericCellValue();
        }
        if (cell.getCellType() == CellType.STRING) {
            return Double.parseDouble(cell.getStringCellValue().trim());
        }
        throw new IllegalArgumentException("Unsupported cell type for column: " + columnName);
    }
}

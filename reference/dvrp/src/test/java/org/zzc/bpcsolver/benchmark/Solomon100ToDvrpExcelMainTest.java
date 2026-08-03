package org.zzc.bpcsolver.benchmark;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Solomon100ToDvrpExcelMainTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldParseSolomonTxtAndWriteDvrpExcelShape() throws Exception {
        Path source = Paths.get("src/main/resources/solomon-100/In/c101.txt");

        Solomon100ToDvrpExcelMain.SolomonInstance instance =
                Solomon100ToDvrpExcelMain.readSolomonInstance(source);

        assertEquals(200, instance.getCapacity());
        assertEquals(0, instance.getDepot().getId());
        assertEquals(40.0, instance.getDepot().getX(), 1e-9);
        assertEquals(50.0, instance.getDepot().getY(), 1e-9);
        assertEquals(100, instance.getCustomers().size());
        assertEquals(1, instance.getCustomers().get(0).getId());
        assertEquals(10, instance.getCustomers().get(0).getDemand());

        Path output = tempDir.resolve("c101.xlsx");
        Solomon100ToDvrpExcelMain.writeDvrpWorkbook(instance, output);

        assertTrue(Files.exists(output));
        try (InputStream inputStream = Files.newInputStream(output);
             Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheet("Sheet1");
            assertEquals("ID", sheet.getRow(0).getCell(0).getStringCellValue());
            assertEquals("Time_Disclosure", sheet.getRow(0).getCell(7).getStringCellValue());

            Row origin = sheet.getRow(1);
            assertEquals(0, (int) origin.getCell(0).getNumericCellValue());
            assertEquals(0.0, origin.getCell(7).getNumericCellValue(), 1e-9);

            Row firstCustomer = sheet.getRow(2);
            assertEquals(1, (int) firstCustomer.getCell(0).getNumericCellValue());
            assertEquals(0.0, firstCustomer.getCell(7).getNumericCellValue(), 1e-9);

            Row returnDepot = sheet.getRow(102);
            assertEquals(101, (int) returnDepot.getCell(0).getNumericCellValue());
            assertEquals(40.0, returnDepot.getCell(1).getNumericCellValue(), 1e-9);
            assertEquals(50.0, returnDepot.getCell(2).getNumericCellValue(), 1e-9);
            assertEquals(0.0, returnDepot.getCell(7).getNumericCellValue(), 1e-9);
        }
    }
}

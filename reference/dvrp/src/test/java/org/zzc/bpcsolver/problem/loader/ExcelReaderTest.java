package org.zzc.bpcsolver.problem.loader;

import org.junit.jupiter.api.Test;
import org.zzc.bpcsolver.problem.config.ProblemConfig;
import org.zzc.bpcsolver.problem.config.ProblemConfigReader;
import org.zzc.bpcsolver.problem.entity.Depot;
import org.zzc.bpcsolver.problem.entity.ProblemData;
import org.zzc.bpcsolver.problem.metric.DecimalRoundingMode;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExcelReaderTest {

    @Test
    void shouldReadProblemDataFromExcelResource() {
        ProblemConfig config = new ProblemConfigReader().read("dvrp_td.yaml");
        ExcelReader excelReader = new ExcelReader();

        ProblemData problemData = excelReader.read(config);

        assertEquals(0, problemData.getOriginDepot().getId());
        assertEquals(Depot.DepotType.ORIGIN, problemData.getOriginDepot().getType());
        assertEquals(25, problemData.getOriginDepot().getVehicleCount());
        assertEquals(200, problemData.getOriginDepot().getRemainingCapacity());

        assertEquals(26, problemData.getReturnDepot().getId());
        assertEquals(Depot.DepotType.RETURN, problemData.getReturnDepot().getType());

        assertEquals(25, problemData.getCustomers().size());
        assertEquals(1, problemData.getCustomers().get(0).getId());
        assertEquals(10, problemData.getCustomers().get(0).getDemand());
        assertEquals(0.0, problemData.getCustomers().get(0).getCallInTime());

        assertEquals(200, problemData.getVehicleCapacity());
        assertEquals(25, problemData.getInitialVehicleCount());
        assertEquals(1.0, problemData.getSpeed());
        assertEquals(1.0, problemData.getDistanceScale());
        assertEquals(1, problemData.getDistanceDecimalPlaces());
        assertEquals(1, problemData.getTravelTimeDecimalPlaces());
        assertEquals(DecimalRoundingMode.FLOOR, problemData.getDistanceRoundingMode());
        assertEquals(DecimalRoundingMode.FLOOR, problemData.getTravelTimeRoundingMode());
    }

    @Test
    void shouldReadConvertedSolomon100ExcelResource() {
        ProblemConfig config = new ProblemConfigReader().read("dvrp_td.yaml");
        config.setResourcePath("Solomon100_Instance/");
        config.setInstanceFileName("c101.xlsx");
        config.setInstanceBaseName("c101");
        config.setNumCustomers(100);
        config.setVehicleCapacity(200);
        config.setInitialVehicleCount(25);
        config.setDistanceRoundingMode(DecimalRoundingMode.FLOOR);
        config.setTravelTimeRoundingMode(DecimalRoundingMode.FLOOR);
        ExcelReader excelReader = new ExcelReader();

        ProblemData problemData = excelReader.read(config);
        problemData.buildStaticData();

        assertEquals(0, problemData.getOriginDepot().getId());
        assertEquals(101, problemData.getReturnDepot().getId());
        assertEquals(100, problemData.getCustomers().size());
        assertEquals(1, problemData.getCustomers().get(0).getId());
        assertEquals(10, problemData.getCustomers().get(0).getDemand());
        assertEquals(0.0, problemData.getCustomers().get(0).getCallInTime());
        assertEquals(100, problemData.getCustomers().get(99).getId());
        assertEquals(DecimalRoundingMode.FLOOR, problemData.getDistanceRoundingMode());
        assertEquals(DecimalRoundingMode.FLOOR, problemData.getTravelTimeRoundingMode());
        assertEquals(18.6, problemData.getStaticDistance(0, 1), 1e-9);
    }
}

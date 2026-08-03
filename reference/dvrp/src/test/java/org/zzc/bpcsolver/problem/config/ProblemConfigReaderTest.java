package org.zzc.bpcsolver.problem.config;

import org.junit.jupiter.api.Test;
import org.zzc.bpcsolver.problem.metric.DecimalRoundingMode;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProblemConfigReaderTest {

    @Test
    void shouldReadDvrpProblemConfigFromResources() {
        ProblemConfigReader reader = new ProblemConfigReader();

        ProblemConfig config = reader.read("dvrp_td.yaml");

        assertEquals("DVRP_Problem_Config", config.getName());
        assertEquals("1.0", config.getVersion());
        assertEquals("DYNAMIC_VEHICLE_ROUTING_PROBLEM", config.getProblemType());
        assertEquals("MINIMIZE_TOTAL_DISTANCE", config.getObjectiveFunction());
        assertEquals("DVRP_Instance/", config.getResourcePath());
        assertEquals(25, config.getNumCustomers());
        assertEquals(200, config.getVehicleCapacity());
        assertEquals(25, config.getInitialVehicleCount());
        assertEquals(1.0, config.getSpeed());
        assertEquals(1.0, config.getDistanceScale());
        assertEquals(1, config.getDistanceDecimalPlaces());
        assertEquals(1, config.getTravelTimeDecimalPlaces());
        assertEquals(DecimalRoundingMode.FLOOR, config.getDistanceRoundingMode());
        assertEquals(DecimalRoundingMode.FLOOR, config.getTravelTimeRoundingMode());
        assertEquals("r101_1", config.getInstanceBaseName());
        assertEquals("r101_1.xlsx", config.getInstanceFileName());
        assertEquals(25, config.getDegreeOfDynamism());
        assertEquals(25, config.getEffectiveDegreeOfDynamism());
        assertEquals("r101_1.xlsx", config.buildInstanceFileName());
    }
}

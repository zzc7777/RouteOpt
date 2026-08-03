package org.zzc.bpcsolver.problem.entity;

import org.junit.jupiter.api.Test;
import org.zzc.bpcsolver.problem.config.ProblemConfig;
import org.zzc.bpcsolver.problem.config.ProblemConfigReader;
import org.zzc.bpcsolver.problem.loader.ProblemDataLoader;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProblemDataTest {

    @Test
    void shouldValidateStandardProblemDataSuccessfully() {
        ProblemConfig config = new ProblemConfigReader().read("dvrp_td.yaml");
        ProblemData problemData = new ProblemDataLoader().load(config);

        assertDoesNotThrow(problemData::validate);
    }

    @Test
    void shouldRejectNonSequentialCustomerIds() {
        ProblemData problemData = buildValidProblemData();
        problemData.getCustomers().get(1).setId(3);

        assertThrows(IllegalArgumentException.class, problemData::validate);
    }

    @Test
    void shouldBuildStaticDistanceDataSuccessfully() {
        ProblemConfig config = new ProblemConfigReader().read("dvrp_td.yaml");
        ProblemData problemData = new ProblemDataLoader().load(config);

        assertDoesNotThrow(problemData::buildStaticData);
        assertEquals(27, problemData.getStaticNodeCount());
        assertEquals(0.0, problemData.getStaticDistance(0, 0), 1e-9);
        assertEquals(problemData.getStaticDistance(0, 1), problemData.getStaticDistance(1, 0), 1e-9);
        assertTrue(problemData.getStaticDistance(0, 1) > 0.0);
        assertEquals(problemData.getStaticDistance(0, 1), problemData.getStaticTravelTime(0, 1), 1e-9);
    }

    @Test
    void shouldUseConfiguredDistancePrecisionWhenBuildingStaticData() {
        ProblemData problemData = buildValidProblemData();
        problemData.getOriginDepot().setX(0.0);
        problemData.getOriginDepot().setY(0.0);
        problemData.getCustomers().get(0).setX(1.0);
        problemData.getCustomers().get(0).setY(1.0);
        problemData.setDistanceDecimalPlaces(2);
        problemData.setTravelTimeDecimalPlaces(2);

        problemData.buildStaticData();

        assertEquals(1.41, problemData.getStaticDistance(0, 1), 1e-9);
        assertEquals(1.41, problemData.getStaticTravelTime(0, 1), 1e-9);
    }

    private ProblemData buildValidProblemData() {
        ProblemData problemData = new ProblemData();

        Depot originDepot = new Depot();
        originDepot.setId(0);
        originDepot.setType(Depot.DepotType.ORIGIN);
        problemData.setOriginDepot(originDepot);

        Customer customer1 = new Customer();
        customer1.setId(1);
        Customer customer2 = new Customer();
        customer2.setId(2);
        problemData.getCustomers().add(customer1);
        problemData.getCustomers().add(customer2);

        Depot returnDepot = new Depot();
        returnDepot.setId(3);
        returnDepot.setType(Depot.DepotType.RETURN);
        problemData.setReturnDepot(returnDepot);

        problemData.setVehicleCapacity(200);
        problemData.setInitialVehicleCount(25);
        problemData.setSpeed(1.0);
        problemData.setDistanceScale(1.0);

        return problemData;
    }
}

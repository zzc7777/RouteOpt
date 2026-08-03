package org.zzc.bpcsolver.problem.metric;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DistanceCalculatorTest {

    @Test
    void shouldRoundDistanceUsingConfiguredDecimalPlaces() {
        DistanceCalculator calculator = new DistanceCalculator();

        assertEquals(1.4, calculator.calculateDistance(0.0, 0.0, 1.0, 1.0, 1.0, 1), 1e-9);
        assertEquals(1.41, calculator.calculateDistance(0.0, 0.0, 1.0, 1.0, 1.0, 2), 1e-9);
    }

    @Test
    void shouldFloorDistanceUsingConfiguredDecimalPlaces() {
        DistanceCalculator calculator = new DistanceCalculator();

        assertEquals(1.4, calculator.calculateDistance(
                0.0, 0.0, 1.0, 1.0, 1.0, 1, DecimalRoundingMode.FLOOR));
        assertEquals(1.41, calculator.calculateDistance(
                0.0, 0.0, 1.0, 1.0, 1.0, 2, DecimalRoundingMode.FLOOR));
    }

    @Test
    void shouldRoundTravelTimeUsingConfiguredDecimalPlaces() {
        DistanceCalculator calculator = new DistanceCalculator();

        assertEquals(1.5, calculator.calculateTravelTime(1.45, 1.0, 1), 1e-9);
        assertEquals(1.5, calculator.calculateTravelTime(1.499, 1.0, 2), 1e-9);
    }

    @Test
    void shouldFloorTravelTimeUsingConfiguredDecimalPlaces() {
        DistanceCalculator calculator = new DistanceCalculator();

        assertEquals(1.4, calculator.calculateTravelTime(1.49, 1.0, 1, DecimalRoundingMode.FLOOR), 1e-9);
        assertEquals(1.49, calculator.calculateTravelTime(1.499, 1.0, 2, DecimalRoundingMode.FLOOR), 1e-9);
    }

    @Test
    void shouldRejectNegativeDecimalPlaces() {
        DistanceCalculator calculator = new DistanceCalculator();

        assertThrows(IllegalArgumentException.class,
                () -> calculator.calculateDistance(0.0, 0.0, 1.0, 1.0, 1.0, -1));
        assertThrows(IllegalArgumentException.class,
                () -> calculator.calculateTravelTime(1.0, 1.0, -1));
    }
}

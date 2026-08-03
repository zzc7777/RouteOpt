package org.zzc.bpcsolver.problem.metric;

public class DistanceCalculator {

    private static final int DEFAULT_DECIMAL_PLACES = 1;

    public double calculateDistance(double x1, double y1, double x2, double y2, double distanceScale) {
        return calculateDistance(x1, y1, x2, y2, distanceScale, DEFAULT_DECIMAL_PLACES);
    }

    public double calculateDistance(double x1,
                                    double y1,
                                    double x2,
                                    double y2,
                                    double distanceScale,
                                    int decimalPlaces) {
        return calculateDistance(x1, y1, x2, y2, distanceScale, decimalPlaces, DecimalRoundingMode.ROUND);
    }

    public double calculateDistance(double x1,
                                    double y1,
                                    double x2,
                                    double y2,
                                    double distanceScale,
                                    int decimalPlaces,
                                    DecimalRoundingMode roundingMode) {
        double deltaX = x1 - x2;
        double deltaY = y1 - y2;
        double euclideanDistance = Math.sqrt(deltaX * deltaX + deltaY * deltaY);
        return applyRoundingMode(euclideanDistance * distanceScale, decimalPlaces, roundingMode);
    }

    public double calculateTravelTime(double distance, double speed) {
        return calculateTravelTime(distance, speed, DEFAULT_DECIMAL_PLACES);
    }

    public double calculateTravelTime(double distance, double speed, int decimalPlaces) {
        return calculateTravelTime(distance, speed, decimalPlaces, DecimalRoundingMode.ROUND);
    }

    public double calculateTravelTime(double distance,
                                      double speed,
                                      int decimalPlaces,
                                      DecimalRoundingMode roundingMode) {
        if (speed <= 0) {
            throw new IllegalArgumentException("speed must be positive.");
        }
        return applyRoundingMode(distance / speed, decimalPlaces, roundingMode);
    }

    public double roundToDecimalPlaces(double value, int decimalPlaces) {
        return applyRoundingMode(value, decimalPlaces, DecimalRoundingMode.ROUND);
    }

    public double floorToDecimalPlaces(double value, int decimalPlaces) {
        return applyRoundingMode(value, decimalPlaces, DecimalRoundingMode.FLOOR);
    }

    public double applyRoundingMode(double value, int decimalPlaces, DecimalRoundingMode roundingMode) {
        double factor = factor(decimalPlaces);
        DecimalRoundingMode mode = roundingMode == null ? DecimalRoundingMode.ROUND : roundingMode;
        if (mode == DecimalRoundingMode.FLOOR) {
            return Math.floor(value * factor) / factor;
        }
        return Math.round(value * factor) / factor;
    }

    private double factor(int decimalPlaces) {
        if (decimalPlaces < 0) {
            throw new IllegalArgumentException("decimalPlaces must be non-negative.");
        }
        return Math.pow(10.0, decimalPlaces);
    }
}

package org.zzc.bpcsolver.problem.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;
import org.zzc.bpcsolver.problem.metric.DecimalRoundingMode;

@Getter
@Setter
public class ProblemConfig {

    private String name;
    private String version;
    @JsonProperty("PROBLEM_TYPE")
    private String problemType;
    @JsonProperty("OBJECTIVE_FUNCTION")
    private String objectiveFunction;
    @JsonProperty("RESOURCE_PATH")
    private String resourcePath;
    @JsonProperty("NUM_CUSTOMERS")
    private int numCustomers;
    @JsonProperty("VEHICLE_CAPACITY")
    private int vehicleCapacity;
    @JsonProperty("INITIAL_VEHICLE_COUNT")
    private int initialVehicleCount;
    @JsonProperty("SPEED")
    private double speed;
    @JsonProperty("DISTANCE_SCALE")
    private double distanceScale;
    @JsonProperty("DISTANCE_DECIMAL_PLACES")
    private int distanceDecimalPlaces = 1;
    @JsonProperty("TRAVEL_TIME_DECIMAL_PLACES")
    private int travelTimeDecimalPlaces = 1;
    @JsonProperty("DISTANCE_ROUNDING_MODE")
    private DecimalRoundingMode distanceRoundingMode = DecimalRoundingMode.ROUND;
    @JsonProperty("TRAVEL_TIME_ROUNDING_MODE")
    private DecimalRoundingMode travelTimeRoundingMode = DecimalRoundingMode.ROUND;
    @JsonProperty("INSTANCE_BASE_NAME")
    private String instanceBaseName;
    @JsonProperty("INSTANCE_FILE_NAME")
    private String instanceFileName;
    @JsonProperty("DEGREE_OF_DYNAMISM")
    private int degreeOfDynamism;
    @JsonProperty("EFFECTIVE_DEGREE_OF_DYNAMISM")
    private int effectiveDegreeOfDynamism;

    public void validate() {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("name must not be empty.");
        }
        if (version == null || version.trim().isEmpty()) {
            throw new IllegalArgumentException("version must not be empty.");
        }
        if (problemType == null || problemType.trim().isEmpty()) {
            throw new IllegalArgumentException("PROBLEM_TYPE must not be empty.");
        }
        if (objectiveFunction == null || objectiveFunction.trim().isEmpty()) {
            throw new IllegalArgumentException("OBJECTIVE_FUNCTION must not be empty.");
        }
        if (resourcePath == null || resourcePath.trim().isEmpty()) {
            throw new IllegalArgumentException("RESOURCE_PATH must not be empty.");
        }
        if (numCustomers <= 0) {
            throw new IllegalArgumentException("NUM_CUSTOMERS must be positive.");
        }
        if (vehicleCapacity <= 0) {
            throw new IllegalArgumentException("VEHICLE_CAPACITY must be positive.");
        }
        if (initialVehicleCount <= 0) {
            throw new IllegalArgumentException("INITIAL_VEHICLE_COUNT must be positive.");
        }
        if (speed <= 0) {
            throw new IllegalArgumentException("SPEED must be positive.");
        }
        if (distanceScale <= 0) {
            throw new IllegalArgumentException("DISTANCE_SCALE must be positive.");
        }
        if (distanceDecimalPlaces < 0) {
            throw new IllegalArgumentException("DISTANCE_DECIMAL_PLACES must be non-negative.");
        }
        if (travelTimeDecimalPlaces < 0) {
            throw new IllegalArgumentException("TRAVEL_TIME_DECIMAL_PLACES must be non-negative.");
        }
        if (distanceRoundingMode == null) {
            throw new IllegalArgumentException("DISTANCE_ROUNDING_MODE must not be null.");
        }
        if (travelTimeRoundingMode == null) {
            throw new IllegalArgumentException("TRAVEL_TIME_ROUNDING_MODE must not be null.");
        }
        if (instanceBaseName == null || instanceBaseName.trim().isEmpty()) {
            throw new IllegalArgumentException("INSTANCE_BASE_NAME must not be empty.");
        }
        if (degreeOfDynamism <= 0) {
            throw new IllegalArgumentException("DEGREE_OF_DYNAMISM must be positive.");
        }
        if (effectiveDegreeOfDynamism <= 0) {
            throw new IllegalArgumentException("EFFECTIVE_DEGREE_OF_DYNAMISM must be positive.");
        }
    }

    public String buildInstanceFileName() {
        if (instanceFileName != null && !instanceFileName.trim().isEmpty()) {
            return instanceFileName;
        }
        return instanceBaseName + "_D_" + degreeOfDynamism + "_E_" + effectiveDegreeOfDynamism + ".xlsx";
    }
}

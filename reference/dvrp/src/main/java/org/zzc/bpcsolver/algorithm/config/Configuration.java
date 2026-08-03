package org.zzc.bpcsolver.algorithm.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.Getter;
import lombok.Setter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

@Getter
@Setter
public class Configuration {
    private String name;
    private String version;

    private LoggingConfig logging;
    private RelaxationConfig relaxation;
    private StabilizationConfig stabilization;
    private DivingConfig diving;
    private PrimalHeuristicConfig primalHeuristic;
    private ColumnGenerationConfig columnGeneration;
    private CuttingConfig cutting;
    private BranchingConfig branching;
    private PricingConfig pricing;
    private VariableFixingConfig variableFixing;
    private ScheduleEnumerationConfig scheduleEnumeration;
    private ResultRecordingConfig resultRecording;

    public Configuration() {}

    public void createConfig(String configPath) {
        try {
            Configuration config = Configuration.loadFromFile(configPath);
            this.name = config.name;
            this.version = config.version;
            this.logging = config.logging;
            this.relaxation = config.relaxation;
            this.stabilization = config.stabilization;
            this.diving = config.diving;
            this.primalHeuristic = config.primalHeuristic;
            this.columnGeneration = config.columnGeneration;
            this.cutting = config.cutting;
            this.branching = config.branching;
            this.pricing = config.pricing;
            this.variableFixing = config.variableFixing;
            this.scheduleEnumeration = config.scheduleEnumeration;
            this.resultRecording = config.resultRecording;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load configuration from YAML file: " + configPath, e);
        }
    }

    public static Configuration loadFromFile(String configPath) throws IOException {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        return mapper.readValue(Files.newInputStream(Paths.get(configPath)), Configuration.class);
    }

    @Getter
    @Setter
    public static class LoggingConfig {
        @JsonProperty("LOGGING_SWITCH")
        private String LOGGING_SWITCH;
        @JsonProperty("OUTPUT_DIR")
        private String OUTPUT_DIR;
        @JsonProperty("FILE_NAME_PATTERN")
        private String FILE_NAME_PATTERN;
        @JsonProperty("components")
        private ComponentsConfig components;

        @Getter
        @Setter
        public static class ComponentsConfig {
            @JsonProperty("PRIMAL_HEURISTIC")
            private boolean primalHeuristic;
            @JsonProperty("COLUMN_GENERATION")
            private boolean columnGeneration;
            @JsonProperty("PRICING")
            private boolean pricing;
            @JsonProperty("CUTTING")
            private boolean cutting;
            @JsonProperty("RELAXATION")
            private boolean relaxation;
            @JsonProperty("VARIABLE_FIXING")
            private boolean variableFixing;
            @JsonProperty("SCHEDULE_ENUMERATION")
            private boolean scheduleEnumeration;
            @JsonProperty("DIVING")
            private boolean diving;
            @JsonProperty("BRANCHING")
            private boolean branching;
            @JsonProperty("SOLUTION")
            private boolean solution;

        }

        public boolean isEnabled() {
            return "on".equalsIgnoreCase(LOGGING_SWITCH);
        }
    }

    @Getter
    @Setter
    public static class RelaxationConfig {
        @JsonProperty("RELAXATION_SWITCH")
        private String relaxationSwitch;

        @JsonProperty("NG_SIZE")
        private int ngSize;

        @JsonProperty("MAX_NG_SIZE")
        private int maxNgSize;

        public RelaxationConfig() {}

        public boolean isEnabled() {
            return relaxationSwitch != null
                    && !relaxationSwitch.trim().isEmpty()
                    && !"none".equalsIgnoreCase(relaxationSwitch.trim())
                    && !"off".equalsIgnoreCase(relaxationSwitch.trim())
                    && !"false".equalsIgnoreCase(relaxationSwitch.trim());
        }

        @Override
        public String toString() {
            return String.format("RelaxationConfig{switch='%s', ngSize=%d, maxNgSize=%d}",
                    relaxationSwitch, ngSize, maxNgSize);
        }
    }

    @Getter
    @Setter
    public static class StabilizationConfig {
        @JsonProperty("STABILIZATION_SWITCH")
        private String stabilizationSwitch;
        @JsonProperty("ALPHA")
        private double alpha;
        @JsonProperty("ADJUST_STEP_SIZE")
        private double adjustStepSize;

        public StabilizationConfig() {}

        public boolean isEnabled() {
            return !"none".equalsIgnoreCase(stabilizationSwitch);
        }

        @Override
        public String toString() {
            return String.format("StabilizationConfig{switch='%s', alpha=%.3f, adjustStepSize=%.3f}",
                    stabilizationSwitch, alpha, adjustStepSize);
        }
    }

    @Getter
    @Setter
    public static class DivingConfig{
        @JsonProperty("DIVING_SWITCH")
        private String divingSwitch;

        public DivingConfig() {}

        public boolean isEnabled() {
            return !"none".equalsIgnoreCase(divingSwitch);
        }

        @Override
        public String toString() {
            return String.format("DivingConfig{switch='%s'}",
                    divingSwitch);
        }
    }

    @Getter
    @Setter
    public static class PrimalHeuristicConfig {
        @JsonProperty("PRIMAL_HEURISTIC_SWITCH")
        private String primalHeuristicSwitch;

        public PrimalHeuristicConfig() {}

        public boolean isEnabled() {
            return !"none".equalsIgnoreCase(primalHeuristicSwitch);
        }

        @Override
        public String toString() {
            return String.format("PrimalHeuristicConfig{switch='%s'}", primalHeuristicSwitch);
        }
    }

    @Getter
    @Setter
    public static class ColumnGenerationConfig {
        @JsonProperty("COLUMN_GENERATION_SWITCH")
        private String columnGenerationSwitch;

        @JsonProperty("NUM_ADDED_COLUMN")
        private int numAddedColumn;

        @JsonProperty("MAX_NUM_COLUMN")
        private int maxNumColumn;

        @JsonProperty("PARALLEL_PRICING")
        private boolean parallelPricing = true;

        public ColumnGenerationConfig() {}

        public boolean isEnabled() {
            return !"none".equalsIgnoreCase(columnGenerationSwitch);
        }

        public boolean isParallelPricingEnabled() {
            return parallelPricing;
        }

        @Override
        public String toString() {
            return String.format("ColumnGenerationConfig{switch='%s', numAdded=%d, maxNum=%d, parallelPricing=%s}",
                    columnGenerationSwitch, numAddedColumn, maxNumColumn, parallelPricing);
        }
    }

    @Getter
    @Setter
    public static class CuttingConfig {
        @JsonProperty("CUTTING_SWITCH")
        private String cuttingSwitch;

        @JsonProperty("NUM_ADDED_CUT")
        private int numAddedCut;

        @JsonProperty("MAX_NUM_CUT")
        private int maxNumCut;

        @JsonProperty("BASE_SET_SIZE")
        private int baseSetSize;

        @JsonProperty("MULTIPLIER")
        private double multiplier;

        @JsonProperty("STOP_AFTER_PRICING_SECONDS")
        private double stopAfterPricingSeconds = 6.0;

        @JsonProperty("TRAILING_ROUNDS")
        private int trailingRounds = 3;

        @JsonProperty("MIN_TRAILING_GAP_IMPROVEMENT")
        private double minTrailingGapImprovement = 0.02;

        public CuttingConfig() {}

        public boolean isEnabled() {
            return !"none".equalsIgnoreCase(cuttingSwitch);
        }

        public boolean isLmSrc() {
            if (cuttingSwitch == null) {
                return false;
            }
            String normalized = cuttingSwitch.trim().toLowerCase();
            return "lm-src".equals(normalized)
                    || "lmsrc".equals(normalized)
                    || "lm src".equals(normalized)
                    || "lm-src cutting".equals(normalized)
                    || "limited-memory subset row cut".equals(normalized);
        }

        public boolean isCutSeparationGuardEnabled() {
            return isLmSrc()
                    && (stopAfterPricingSeconds > 0.0
                    || (trailingRounds > 0 && minTrailingGapImprovement > 0.0));
        }

        @Override
        public String toString() {
            return String.format("CuttingConfig{switch='%s', numAdded=%d, maxNum=%d, baseSetSize=%d, multiplier=%.2f, stopAfterPricingSeconds=%.2f, trailingRounds=%d, minTrailingGapImprovement=%.4f}",
                    cuttingSwitch, numAddedCut, maxNumCut, baseSetSize, multiplier,
                    stopAfterPricingSeconds, trailingRounds, minTrailingGapImprovement);
        }
    }

    @Getter
    @Setter
    public static class BranchingConfig {
        @JsonProperty("BRANCHING_SWITCH")
        private String branchingSwitch;

        @JsonProperty("BRANCH_VARIABLE")
        private String branchVariable;

        @JsonProperty("VARIABLE_SELECTION_RULE")
        private String variableSelectionRule;

        @JsonProperty("NODE_SELECTION_RULE")
        private String nodeSelectionRule;

        @JsonProperty("NUM_CANDIDATE_VARIABLES_1")
        private int numCandidateVariables1;

        @JsonProperty("NUM_CANDIDATE_VARIABLES_2")
        private int numCandidateVariables2;

        public BranchingConfig() {}

        public boolean isEnabled() {
            return !"none".equalsIgnoreCase(branchingSwitch);
        }

        @Override
        public String toString() {
            return String.format("BranchingConfig{switch='%s', variable='%s', varRule='%s', nodeRule='%s'}",
                    branchingSwitch, branchVariable, variableSelectionRule, nodeSelectionRule);
        }
    }

    @Getter
    @Setter
    public static class PricingConfig {
        @JsonProperty("PRICING_SWITCH")
        private String pricingSwitch;

        @JsonProperty("HEURISTIC_PRICING")
        private String heuristicPricing;

        @JsonProperty("DIMENSIONS")
        private int dimension;

        @JsonProperty("NUM_BUCKETS_1")
        private int numBuckets_1;

        @JsonProperty("NUM_BUCKETS_2")
        private int numBuckets_2;

        @JsonProperty("THRESHOLD_BIDIRECTIONAL_SEARCH")
        private double thresholdBidirectionalSearch;

        private String currentHeuristic;

        public PricingConfig() {}

        public boolean isHeuristicEnabled() {
            return !"none".equalsIgnoreCase(heuristicPricing);
        }

        public boolean isBucketGraphEnabled() {
            return "bucket graph labeling algorithm".equalsIgnoreCase(pricingSwitch);
        }

        @Override
        public String toString() {
            return String.format("PricingConfig{switch='%s', dimension = %d, numBuckets_1=%d, numBuckets_2=%d, threshold=%.2f}",
                    pricingSwitch, dimension, numBuckets_1, numBuckets_2, thresholdBidirectionalSearch);
        }
    }

    @Getter
    @Setter
    public static class VariableFixingConfig {
        @JsonProperty("VARIABLE_FIXING_SWITCH")
        private String variableFixingSwitch;

        @JsonProperty("THRESHOLD_VARIABLE_FIXING")
        private double thresholdVariableFixing;

        public VariableFixingConfig() {}

        public boolean isEnabled() {
            return !"none".equalsIgnoreCase(variableFixingSwitch);
        }

        @Override
        public String toString() {
            return String.format("VariableFixingConfig{switch='%s', threshold=%.3f}",
                    variableFixingSwitch, thresholdVariableFixing);
        }
    }

    @Getter
    @Setter
    public static class ScheduleEnumerationConfig {
        @JsonProperty("SCHEDULE_ENUMERATION_SWITCH")
        private String scheduleEnumerationSwitch;

        @JsonProperty("THRESHOLD_SCHEDULE_ENUMERATION")
        private double thresholdScheduleEnumeration;

        @JsonProperty("SCHEDULE_POOL_SIZE")
        private int schedulePoolSize;

        public ScheduleEnumerationConfig() {}

        public boolean isEnabled() {
            return !"none".equalsIgnoreCase(scheduleEnumerationSwitch);
        }

        @Override
        public String toString() {
            return String.format("ScheduleEnumerationConfig{switch='%s', threshold=%.3f, poolSize=%d}",
                    scheduleEnumerationSwitch, thresholdScheduleEnumeration, schedulePoolSize);
        }
    }

    @Getter
    @Setter
    public static class ResultRecordingConfig {
        @JsonProperty("RECORD_RESULTS")
        private boolean recordResults;

        @JsonProperty("RESULTS_DIR")
        private String resultsDir = "results";

        @JsonProperty("RUN_DESCRIPTION")
        private String runDescription = "";

        public ResultRecordingConfig() {}

        public boolean isEnabled() {
            return recordResults;
        }
    }
}

package org.zzc.bpcsolver.algorithm.logger;

import lombok.Getter;
import lombok.Setter;
import org.zzc.bpcsolver.algorithm.config.Configuration;
import org.zzc.bpcsolver.algorithm.cutting.LmSrc;
import org.zzc.bpcsolver.algorithm.logger.model.*;
import org.zzc.bpcsolver.core.branch.Node;
import org.zzc.bpcsolver.core.graph.Graph;
import org.zzc.bpcsolver.core.graph.Route;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
public class BPCLogger {

    public static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private Configuration config;
    private LogData logData;
    private String instanceName;
    private final Map<String, Long> timers = new HashMap<>();

    public BPCLogger() {
        this.logData = new LogData();
    }

    public BPCLogger(Configuration config, String instanceName) {
        this.config = config;
        this.instanceName = instanceName;
        this.logData = new LogData();
        this.logData.setInstanceName(instanceName);
        this.logData.setStartTime(LocalDateTime.now().format(TIMESTAMP_FORMAT));
    }

    public void logPrimalHeuristic(String heuristicType, double objectiveValue) {
        PrimalHeuristicLog log = new PrimalHeuristicLog();
        log.setHeuristicType(heuristicType);
        log.setObjectiveValue(objectiveValue);
        log.setRunningTime(stopTimer("PRIMAL_HEURISTIC"));
        log.setTimestamp(getCurrentTimestamp());
        logData.getPrimalHeuristics().add(log);
    }

    public void logPricing(String pricingType, int iter, int machineIndex, int numDominatedLabels, int numExtendedLabels) {
        PricingLog log = new PricingLog();
        log.setPricingType(pricingType);
        log.setIter(iter);
        log.setMachineIndex(machineIndex);
        log.setNumDominatedLabels(numDominatedLabels);
        log.setNumExtendedLabels(numExtendedLabels);
        log.setRunningTime(stopTimer("PRICING_" + iter + "_" + machineIndex));
        log.setTimestamp(getCurrentTimestamp());
        logData.getPricingLogs().add(log);
    }

    public void logBranching(String branchingType, String nodeSelectionRule, String variableSelectionRule,
                             boolean solvedToOptimality, int nodesExplored, int columnsAdded,
                             int cutsAdded, double objectiveValue) {
        BranchingLog log = new BranchingLog();
        log.setBranchingType(branchingType);
        log.setNodeSelectionRule(nodeSelectionRule);
        log.setVariableSelectionRule(variableSelectionRule);
        log.setSolvedToOptimality(solvedToOptimality);
        log.setNodesExplored(nodesExplored);
        log.setColumnsAdded(columnsAdded);
        log.setCutsAdded(cutsAdded);
        log.setObjectiveValue(objectiveValue);
        log.setRunningTime(stopTimer("BRANCHING"));
        log.setTimestamp(getCurrentTimestamp());
        logData.getBranchingLogs().add(log);
    }

    public void logCutting(CuttingLog log, String cuttingType, boolean solvedToOptimality, int cutsAdded, double objectiveValue) {
        log.setCutType(cuttingType);
        log.setSolvedToOptimality(solvedToOptimality);
        log.setCutsAdded(cutsAdded);
        log.setObjectiveValue(objectiveValue);
        log.setRunningTime(stopTimer("CUTTING"));
        log.setTimestamp(getCurrentTimestamp());
        logData.getCuttingLogs().add(log);
    }

    public void logColumnGeneration(int iter, boolean solvedToOptimality, int columnsAdded, double objectiveValue) {
        ColumnGenerationLog log = new ColumnGenerationLog();
        log.setIter(iter);
        log.setSolvedToOptimality(solvedToOptimality);
        log.setColumnsAdded(columnsAdded);
        log.setObjectiveValue(objectiveValue);
        log.setRunningTime(stopTimer("COLUMN_GENERATION"));
        log.setTimestamp(getCurrentTimestamp());
        logData.getColumnGenerationLogs().add(log);
    }

    public void logRelaxation(String relaxationType, boolean solvedToOptimality, int columnsAdded, int cutsAdded, double objectiveValue) {
        RelaxationLog log = new RelaxationLog();
        log.setRelaxationType(relaxationType);
        log.setSolvedToOptimality(solvedToOptimality);
        log.setColumnsAdded(columnsAdded);
        log.setCutsAdded(cutsAdded);
        log.setObjectiveValue(objectiveValue);
        log.setRunningTime(stopTimer("RELAXATION"));
        log.setTimestamp(getCurrentTimestamp());
        logData.getRelaxationLogs().add(log);
    }

    public void logDiving(String divingType, double objectiveValue) {
        DivingLog log = new DivingLog();
        log.setDivingType(divingType);
        log.setObjectiveValue(objectiveValue);
        log.setRunningTime(stopTimer("DIVING"));
        log.setTimestamp(getCurrentTimestamp());
        logData.getDivingLogs().add(log);
    }

    public void logVariableFixing(String fixingType, int variablesFixed) {
        VariableFixingLog log = new VariableFixingLog();
        log.setFixingType(fixingType);
        log.setNumArcsRemoved(variablesFixed);
        log.setRunningTime(stopTimer("VARIABLE_FIXING"));
        log.setTimestamp(getCurrentTimestamp());
        logData.getVariableFixingLogs().add(log);
    }

    public void logScheduleEnumeration(String enumerationType, boolean solvedToOptimality, int columnsAdded, double objectiveValue) {
        ScheduleEnumerationLog log = new ScheduleEnumerationLog();
        log.setEnumerationType(enumerationType);
        log.setSolvedToOptimality(solvedToOptimality);
        log.setColumnsAdded(columnsAdded);
        log.setObjectiveValue(objectiveValue);
        log.setRunningTime(stopTimer("SCHEDULE_ENUMERATION"));
        log.setTimestamp(getCurrentTimestamp());
        logData.getScheduleEnumerationLogs().add(log);
    }

    public void logSolution(Node<Route, LmSrc, Graph> node) {
        SolutionLog log = new SolutionLog();
        if (node != null && node.getSolution() != null) {
            log.setObjectiveValue(node.getSolution().getObjValue());
        }
        logData.setSolutionLog(log);
    }

    public void startTimer(String component) {
        timers.put(component, System.currentTimeMillis());
    }

    public double stopTimer(String component) {
        Long startTime = timers.get(component);
        if (startTime == null) {
            return 0.0;
        }
        double elapsedTime = (System.currentTimeMillis() - startTime) / 1000.0;
        timers.remove(component);
        return elapsedTime;
    }

    private String getCurrentTimestamp() {
        return LocalDateTime.now().format(TIMESTAMP_FORMAT);
    }

    public void export() {
        if (logData.getEndTime() == null) {
            logData.setEndTime(LocalDateTime.now().format(TIMESTAMP_FORMAT));
        }
    }
}

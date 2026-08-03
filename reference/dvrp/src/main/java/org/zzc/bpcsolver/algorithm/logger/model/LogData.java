package org.zzc.bpcsolver.algorithm.logger.model;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class LogData {

    private String instanceName;
    private String startTime;
    private String endTime;
    private double totalRunningTime;
    private List<PrimalHeuristicLog> primalHeuristics = new ArrayList<>();
    private List<PricingLog> pricingLogs = new ArrayList<>();
    private List<BranchingLog> branchingLogs = new ArrayList<>();
    private List<CuttingLog> cuttingLogs = new ArrayList<>();
    private List<ColumnGenerationLog> columnGenerationLogs = new ArrayList<>();
    private List<RelaxationLog> relaxationLogs = new ArrayList<>();
    private List<DivingLog> divingLogs = new ArrayList<>();
    private List<VariableFixingLog> variableFixingLogs = new ArrayList<>();
    private List<ScheduleEnumerationLog> scheduleEnumerationLogs = new ArrayList<>();
    private SolutionLog solutionLog;
}

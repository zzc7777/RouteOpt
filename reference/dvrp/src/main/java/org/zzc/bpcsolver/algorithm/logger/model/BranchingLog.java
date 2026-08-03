package org.zzc.bpcsolver.algorithm.logger.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BranchingLog {
    private String branchingType;
    private String nodeSelectionRule;
    private String variableSelectionRule;
    private boolean solvedToOptimality;
    private int nodesExplored;
    private int columnsAdded;
    private int cutsAdded;
    private double objectiveValue;
    private double runningTime;
    private String timestamp;
}

package org.zzc.bpcsolver.algorithm.logger.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RelaxationLog {
    private String relaxationType;
    private boolean solvedToOptimality;
    private int columnsAdded;
    private int cutsAdded;
    private double objectiveValue;
    private double runningTime;
    private String timestamp;
}

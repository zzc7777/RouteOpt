package org.zzc.bpcsolver.algorithm.logger.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CuttingLog {
    private String cutType;
    private boolean solvedToOptimality;
    private int cutsAdded;
    private double objectiveValue;
    private double runningTime;
    private double separationTime;
    private String timestamp;
}

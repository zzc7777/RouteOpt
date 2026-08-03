package org.zzc.bpcsolver.algorithm.logger.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ColumnGenerationLog {
    private int iter;
    private boolean solvedToOptimality;
    private int columnsAdded;
    private double objectiveValue;
    private double runningTime;
    private String timestamp;
}

package org.zzc.bpcsolver.algorithm.logger.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VariableFixingLog {
    private String fixingType;
    private int numArcsRemoved;
    private double runningTime;
    private String timestamp;
}

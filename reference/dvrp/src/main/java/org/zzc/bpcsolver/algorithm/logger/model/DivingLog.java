package org.zzc.bpcsolver.algorithm.logger.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DivingLog {
    private String divingType;
    private double objectiveValue;
    private double runningTime;
    private String timestamp;
}

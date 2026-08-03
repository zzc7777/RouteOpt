package org.zzc.bpcsolver.algorithm.logger.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PricingLog {
    private String pricingType;
    private int iter;
    private int machineIndex;
    private int numDominatedLabels;
    private int numExtendedLabels;
    private double runningTime;
    private String timestamp;
}

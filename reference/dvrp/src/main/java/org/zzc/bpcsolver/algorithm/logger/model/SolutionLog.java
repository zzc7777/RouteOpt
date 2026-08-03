package org.zzc.bpcsolver.algorithm.logger.model;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class SolutionLog {
    private double objectiveValue;
    private Map<Integer, java.util.List<Integer>> schedules;
    private Map<Integer, Double> completionTimes;
    private Map<Integer, Double> costs;
}

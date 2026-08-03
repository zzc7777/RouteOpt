package org.zzc.bpcsolver.algorithm.strategy;

import lombok.Getter;
import lombok.Setter;
import org.zzc.bpcsolver.algorithm.acceleration.DivingHeuristic;
import org.zzc.bpcsolver.algorithm.acceleration.PrimalHeuristic;
import org.zzc.bpcsolver.algorithm.acceleration.Relaxation;
import org.zzc.bpcsolver.algorithm.acceleration.ScheduleEnumeration;
import org.zzc.bpcsolver.algorithm.acceleration.Stabilization;
import org.zzc.bpcsolver.algorithm.acceleration.VariableFixing;
import org.zzc.bpcsolver.algorithm.cutting.CutTemplate;
import org.zzc.bpcsolver.core.template.GraphTemplate;
import org.zzc.bpcsolver.core.template.PathTemplate;
import org.zzc.bpcsolver.problem.template.DynamicInstanceTemplate;

@Getter
@Setter
public class AccelerationStrategy<T extends DynamicInstanceTemplate, S extends PathTemplate,
        C extends CutTemplate, G extends GraphTemplate> {

    private Relaxation<S, C, G> relaxation;
    private Stabilization<T, S, C, G> stabilization;
    private PrimalHeuristic<T, S, G> primalHeuristic;
    private DivingHeuristic<T, S, C, G> divingHeuristic;
    private VariableFixing<S, C, G> variableFixing;
    private ScheduleEnumeration<S, C, G> scheduleEnumeration;
}

package org.zzc.bpcsolver.algorithm.strategy;

import lombok.Getter;
import lombok.Setter;
import org.zzc.bpcsolver.algorithm.cutting.CutTemplate;
import org.zzc.bpcsolver.algorithm.cutting.Cutting;
import org.zzc.bpcsolver.core.template.GraphTemplate;
import org.zzc.bpcsolver.core.template.PathTemplate;
import org.zzc.bpcsolver.problem.template.DynamicInstanceTemplate;

@Getter
@Setter
public class CuttingStrategy<T extends DynamicInstanceTemplate, S extends PathTemplate,
        C extends CutTemplate, G extends GraphTemplate> {

    private Cutting<S, C> cuttingStrategy;
}

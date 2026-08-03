package org.zzc.bpcsolver.algorithm.strategy;

import lombok.Getter;
import lombok.Setter;
import org.zzc.bpcsolver.algorithm.cutting.CutTemplate;
import org.zzc.bpcsolver.algorithm.pricing.ExactPricing;
import org.zzc.bpcsolver.algorithm.pricing.HeuristicPricing;
import org.zzc.bpcsolver.core.template.GraphTemplate;
import org.zzc.bpcsolver.core.template.PathTemplate;
import org.zzc.bpcsolver.problem.template.DynamicInstanceTemplate;

@Getter
@Setter
public class PricingStrategy<T extends DynamicInstanceTemplate, S extends PathTemplate,
        C extends CutTemplate, G extends GraphTemplate> {

    private HeuristicPricing<S, G, C> heuristicPricingAlgorithm;
    private ExactPricing<S, G, C> exactPricingAlgorithm;
}

package org.zzc.bpcsolver.algorithm.acceleration;

import org.zzc.bpcsolver.algorithm.branching.BranchingRule;
import org.zzc.bpcsolver.algorithm.config.Configuration;
import org.zzc.bpcsolver.algorithm.cutting.CutTemplate;
import org.zzc.bpcsolver.algorithm.logger.BPCLogger;
import org.zzc.bpcsolver.algorithm.strategy.PricingStrategy;
import org.zzc.bpcsolver.core.branch.Node;
import org.zzc.bpcsolver.core.template.GraphTemplate;
import org.zzc.bpcsolver.core.template.PathTemplate;
import org.zzc.bpcsolver.problem.template.DynamicInstanceTemplate;

public interface Stabilization<T extends DynamicInstanceTemplate, S extends PathTemplate,
        C extends CutTemplate, G extends GraphTemplate> {

    void Initialize(Configuration config, T instance, Node<S, C, G> node,
                    PricingStrategy<T, S, C, G> pricingStrategy, BranchingRule<S, C, G> branchingRule);

    void solve(T instance, Configuration config, Node<S, C, G> node,
               PricingStrategy<T, S, C, G> pricingStrategy, BranchingRule<S, C, G> branchingRule,
               BPCLogger logger);

    String getStrategyName();
}

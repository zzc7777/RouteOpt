package org.zzc.bpcsolver.algorithm.acceleration;

import org.zzc.bpcsolver.algorithm.branching.BranchingRule;
import org.zzc.bpcsolver.algorithm.config.Configuration;
import org.zzc.bpcsolver.algorithm.cutting.CutTemplate;
import org.zzc.bpcsolver.algorithm.logger.BPCLogger;
import org.zzc.bpcsolver.algorithm.pricing.HeuristicPricing;
import org.zzc.bpcsolver.core.branch.Node;
import org.zzc.bpcsolver.core.branch.Solution;
import org.zzc.bpcsolver.core.template.GraphTemplate;
import org.zzc.bpcsolver.core.template.PathTemplate;
import org.zzc.bpcsolver.problem.template.DynamicInstanceTemplate;

public interface DivingHeuristic<T extends DynamicInstanceTemplate, S extends PathTemplate,
        C extends CutTemplate, G extends GraphTemplate> {

    void initialize(T instance, Configuration config, HeuristicPricing<S, G, C> heuristicPricing,
                    BranchingRule<S, C, G> branchingRule);

    Solution<S> solve(Node<S, C, G> node, BPCLogger logger);

    String getStrategyName();
}

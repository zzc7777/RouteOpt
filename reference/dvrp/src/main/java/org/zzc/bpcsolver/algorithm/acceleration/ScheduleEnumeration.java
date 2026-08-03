package org.zzc.bpcsolver.algorithm.acceleration;

import org.zzc.bpcsolver.algorithm.branching.BranchingRule;
import org.zzc.bpcsolver.algorithm.config.Configuration;
import org.zzc.bpcsolver.algorithm.cutting.CutTemplate;
import org.zzc.bpcsolver.algorithm.logger.BPCLogger;
import org.zzc.bpcsolver.algorithm.pricing.ExactPricing;
import org.zzc.bpcsolver.core.branch.Node;
import org.zzc.bpcsolver.core.template.GraphTemplate;
import org.zzc.bpcsolver.core.template.PathTemplate;

import java.util.List;

public interface ScheduleEnumeration<S extends PathTemplate, C extends CutTemplate, G extends GraphTemplate> {

    void initialize(Configuration config, ExactPricing<S, G, C> pricingStrategy,
                    BranchingRule<S, C, G> branchingRule);

    List<S> solve(int machineIndex, G graph, Node<S, C, G> node, double upperBound,
                  Configuration config, BPCLogger logger);

    String getStrategyName();
}

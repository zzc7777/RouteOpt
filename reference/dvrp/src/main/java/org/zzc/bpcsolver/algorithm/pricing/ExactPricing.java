package org.zzc.bpcsolver.algorithm.pricing;

import org.zzc.bpcsolver.algorithm.branching.BranchingRule;
import org.zzc.bpcsolver.algorithm.config.Configuration;
import org.zzc.bpcsolver.algorithm.cutting.CutTemplate;
import org.zzc.bpcsolver.algorithm.logger.BPCLogger;
import org.zzc.bpcsolver.core.branch.Constraint;
import org.zzc.bpcsolver.core.template.GraphTemplate;
import org.zzc.bpcsolver.core.template.PathTemplate;

import java.util.List;

public interface ExactPricing<S extends PathTemplate, G extends GraphTemplate, C extends CutTemplate> {

    List<S> solve(G graph, int index, List<C> cuts, List<Constraint> branchConstraints,
                  double dualsOfMachines, double[] dualsOfJobs, List<Double> dualsOfCuts,
                  List<Double> dualsOfBranches, BranchingRule<S, C, G> branchingRule,
                  Configuration config, BPCLogger logger, int iter);

    List<S> convertLabelsToSchedules(List<Label> labels);

    String getStrategyName();
}

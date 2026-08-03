package org.zzc.bpcsolver.algorithm.strategy;

import lombok.Getter;
import lombok.Setter;
import org.zzc.bpcsolver.algorithm.branching.BranchingRule;
import org.zzc.bpcsolver.algorithm.branching.nodeSelection.NodeSelectionStrategy;
import org.zzc.bpcsolver.algorithm.branching.variableSelection.VariableSelectionStrategy;
import org.zzc.bpcsolver.algorithm.cutting.CutTemplate;
import org.zzc.bpcsolver.core.template.GraphTemplate;
import org.zzc.bpcsolver.core.template.PathTemplate;
import org.zzc.bpcsolver.problem.template.DynamicInstanceTemplate;

@Getter
@Setter
public class BranchingStrategy<T extends DynamicInstanceTemplate, S extends PathTemplate,
        C extends CutTemplate, G extends GraphTemplate> {

    private BranchingRule<S, C, G> branchingRule;
    private VariableSelectionStrategy<T, S, C, G> variableSelectionStrategy;
    private NodeSelectionStrategy<S, C, G> nodeSelectionStrategy;
}

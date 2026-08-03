package org.zzc.bpcsolver.algorithm.branching.variableSelection;

import org.zzc.bpcsolver.algorithm.DeterministicOrdering;
import org.zzc.bpcsolver.algorithm.branching.BranchingRule;
import org.zzc.bpcsolver.algorithm.config.Configuration;
import org.zzc.bpcsolver.algorithm.cutting.CutTemplate;
import org.zzc.bpcsolver.algorithm.logger.BPCLogger;
import org.zzc.bpcsolver.algorithm.pricing.HeuristicPricing;
import org.zzc.bpcsolver.core.branch.Node;
import org.zzc.bpcsolver.core.branch.Variable;
import org.zzc.bpcsolver.core.template.GraphTemplate;
import org.zzc.bpcsolver.core.template.PathTemplate;
import org.zzc.bpcsolver.problem.template.DynamicInstanceTemplate;

import java.util.List;
import java.util.Objects;

public class MostFractionalBranching<T extends DynamicInstanceTemplate, S extends PathTemplate, C extends CutTemplate, G extends GraphTemplate>
        implements VariableSelectionStrategy<T, S, C, G> {

    @Override
    public void Initialize(T instance, Configuration config, HeuristicPricing<S, G, C> heuristicPricing,
                           BranchingRule<S, C, G> branchingRule) {
    }

    @Override
    public Variable selectBranchingVariable(List<Variable> fractionalVars, Node<S, C, G> node, BPCLogger logger) {
        if (fractionalVars == null || fractionalVars.isEmpty()) {
            return null;
        }

        return fractionalVars.stream()
                .filter(Objects::nonNull)
                .sorted(DeterministicOrdering.variableComparator())
                .findFirst()
                .orElse(null);
    }

    @Override
    public String getStrategyName() {
        return "most fractional branching";
    }
}

package org.zzc.bpcsolver.algorithm.factory;

import org.zzc.bpcsolver.algorithm.branching.BranchingRule;
import org.zzc.bpcsolver.algorithm.branching.QijBranching;
import org.zzc.bpcsolver.algorithm.branching.VehicleCountThenArcBranching;
import org.zzc.bpcsolver.algorithm.branching.XkijBranching;
import org.zzc.bpcsolver.algorithm.branching.nodeSelection.BestFirstStrategy;
import org.zzc.bpcsolver.algorithm.branching.nodeSelection.DepthFirstStrategy;
import org.zzc.bpcsolver.algorithm.branching.nodeSelection.NodeSelectionStrategy;
import org.zzc.bpcsolver.algorithm.branching.variableSelection.MostFractionalBranching;
import org.zzc.bpcsolver.algorithm.branching.variableSelection.StrongBranching;
import org.zzc.bpcsolver.algorithm.branching.variableSelection.VariableSelectionStrategy;
import org.zzc.bpcsolver.algorithm.config.Configuration;
import org.zzc.bpcsolver.algorithm.cutting.CutTemplate;
import org.zzc.bpcsolver.core.template.GraphTemplate;
import org.zzc.bpcsolver.core.template.PathTemplate;
import org.zzc.bpcsolver.problem.template.DynamicInstanceTemplate;

public final class BranchingStrategyFactory {

    private BranchingStrategyFactory() {
    }

    public static <S extends PathTemplate, C extends CutTemplate, G extends GraphTemplate>
    BranchingRule<S, C, G> createBranchingRule(Configuration config) {
        if (config == null || config.getBranching() == null) {
            return new VehicleCountThenArcBranching<>(new QijBranching<>());
        }

        String branchingSwitch = config.getBranching().getBranchingSwitch();
        String branchVariable = config.getBranching().getBranchVariable();
        BranchingRule<S, C, G> arcBranchingRule = createArcBranchingRule(branchVariable);
        if (branchingSwitch == null || branchingSwitch.trim().isEmpty()) {
            return new VehicleCountThenArcBranching<>(arcBranchingRule);
        }

        switch (branchingSwitch.toLowerCase().trim()) {
            case "xkij":
                return new XkijBranching<>();
            case "qij":
                return new QijBranching<>();
            case "branch-and-bound":
            case "vehicle-count-first":
            case "vehicle-count then arc":
            default:
                return new VehicleCountThenArcBranching<>(arcBranchingRule);
        }
    }

    private static <S extends PathTemplate, C extends CutTemplate, G extends GraphTemplate>
    BranchingRule<S, C, G> createArcBranchingRule(String branchVariable) {
        if (branchVariable == null) {
            return new QijBranching<>();
        }
        switch (branchVariable.toLowerCase().trim()) {
            case "xkij":
                return new XkijBranching<>();
            case "qij":
            default:
                return new QijBranching<>();
        }
    }

    public static <T extends DynamicInstanceTemplate, S extends PathTemplate, C extends CutTemplate, G extends GraphTemplate>
    VariableSelectionStrategy<T, S, C, G> createVariableSelectionStrategy(Configuration config) {
        if (config == null || config.getBranching() == null) {
            return new MostFractionalBranching<>();
        }

        String variableSelectionRule = config.getBranching().getVariableSelectionRule();
        if (variableSelectionRule == null) {
            return new MostFractionalBranching<>();
        }

        switch (variableSelectionRule.toLowerCase().trim()) {
            case "strong branching":
                return new StrongBranching<>();
            case "most fractional branching":
            default:
                return new MostFractionalBranching<>();
        }
    }

    public static <S extends PathTemplate, C extends CutTemplate, G extends GraphTemplate>
    NodeSelectionStrategy<S, C, G> createNodeSelectionStrategy(Configuration config) {
        if (config == null || config.getBranching() == null) {
            return new BestFirstStrategy<>();
        }

        String nodeSelectionRule = config.getBranching().getNodeSelectionRule();
        if (nodeSelectionRule == null) {
            return new BestFirstStrategy<>();
        }

        switch (nodeSelectionRule.toLowerCase().trim()) {
            case "depth-first selection":
                return new DepthFirstStrategy<>();
            case "best-first selection":
            default:
                return new BestFirstStrategy<>();
        }
    }
}

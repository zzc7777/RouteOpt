package org.zzc.bpcsolver.algorithm.pricing;

import org.zzc.bpcsolver.algorithm.branching.BranchingRule;
import org.zzc.bpcsolver.algorithm.config.Configuration;
import org.zzc.bpcsolver.algorithm.cutting.LmSrc;
import org.zzc.bpcsolver.algorithm.logger.BPCLogger;
import org.zzc.bpcsolver.core.branch.Constraint;
import org.zzc.bpcsolver.core.graph.Graph;
import org.zzc.bpcsolver.core.graph.Route;

import java.util.List;

public class MultiStageHeuristicPricing implements HeuristicPricing<Route, Graph, LmSrc> {

    public static final String STRATEGY_NAME = "multi-stage heuristic pricing";

    private final LightLabeling lightLabeling = new LightLabeling();
    private final RelaxedLabeling relaxedLabeling = new RelaxedLabeling();

    @Override
    public List<Route> solve(Graph graph, int index, List<LmSrc> cuts, List<Constraint> branchConstraints,
                             double dualsOfMachines, double[] dualsOfJobs, List<Double> dualsOfCuts,
                             List<Double> dualsOfBranches, BranchingRule<Route, LmSrc, Graph> branchingRule,
                             Configuration config, BPCLogger logger, int iter) {
        String currentHeuristic = getCurrentHeuristic(config);
        switch (currentHeuristic) {
            case LightLabeling.STRATEGY_NAME:
                return lightLabeling.solve(graph, index, cuts, branchConstraints, dualsOfMachines,
                        dualsOfJobs, dualsOfCuts, dualsOfBranches, branchingRule, config, logger, iter);
            case RelaxedLabeling.STRATEGY_NAME:
                return relaxedLabeling.solve(graph, index, cuts, branchConstraints, dualsOfMachines,
                        dualsOfJobs, dualsOfCuts, dualsOfBranches, branchingRule, config, logger, iter);
            default:
                throw new IllegalStateException("Unknown heuristic pricing: " + currentHeuristic);
        }
    }

    @Override
    public void reset(Configuration config) {
        setCurrentHeuristic(config, LightLabeling.STRATEGY_NAME);
    }

    @Override
    public String switchHeuristic(Configuration config, String type) {
        String currentHeuristic = getCurrentHeuristic(config);
        if (LightLabeling.STRATEGY_NAME.equals(currentHeuristic)) {
            setCurrentHeuristic(config, RelaxedLabeling.STRATEGY_NAME);
            return type;
        }
        if (RelaxedLabeling.STRATEGY_NAME.equals(currentHeuristic)) {
            return "Exact Pricing";
        }
        return "Exact Pricing";
    }

    @Override
    public String getCurrentHeuristic(Configuration config) {
        if (config == null || config.getPricing() == null) {
            return LightLabeling.STRATEGY_NAME;
        }
        String currentHeuristic = config.getPricing().getCurrentHeuristic();
        if (currentHeuristic == null || currentHeuristic.trim().isEmpty()) {
            return LightLabeling.STRATEGY_NAME;
        }
        return currentHeuristic;
    }

    @Override
    public void setCurrentHeuristic(Configuration config, String heuristic) {
        if (config != null && config.getPricing() != null) {
            config.getPricing().setCurrentHeuristic(heuristic);
        }
    }

    @Override
    public String getStrategyName() {
        return STRATEGY_NAME;
    }
}

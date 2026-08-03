package org.zzc.bpcsolver.algorithm.factory;

import org.zzc.bpcsolver.algorithm.acceleration.*;
import org.zzc.bpcsolver.algorithm.config.Configuration;
import org.zzc.bpcsolver.algorithm.cutting.LmSrc;
import org.zzc.bpcsolver.core.graph.Graph;
import org.zzc.bpcsolver.core.graph.Route;
import org.zzc.bpcsolver.problem.entity.DynamicInstance;

import java.util.ArrayList;
import java.util.List;

public final class AccelerationStrategyFactory {

    private AccelerationStrategyFactory() {
    }

    public static Relaxation<Route, LmSrc, Graph> createRelaxation(Configuration config) {
        String rule = config == null || config.getRelaxation() == null
                ? "none"
                : config.getRelaxation().getRelaxationSwitch();
        if (rule == null || rule.trim().isEmpty()) {
            return new NoOpRelaxation<>();
        }
        String normalizedRule = rule.trim().toLowerCase();
        switch (normalizedRule) {
            case "ng-route":
            case "ng route":
            case "ng-schedule":
            case "ng schedule":
                return new NgRouteRelaxation();
            case "dynamic ng-route":
            case "dynamic ng route":
            case "dynamic ng-schedule":
            case "dynamic ng schedule":
            case "dyna ng-route":
            case "dyna ng route":
                return new DynaNgRouteRelaxation();
            case "none":
            case "off":
            case "false":
                return new NoOpRelaxation<>();
            default:
                throw new IllegalArgumentException("Unknown relaxation strategy: " + rule);
        }
    }

    public static Relaxation<Route, LmSrc, Graph> createNoOpRelaxation() {
        return new NoOpRelaxation<>();
    }

    public static Stabilization<DynamicInstance, Route, LmSrc, Graph> createStabilization(Configuration config) {
        return new NoOpStabilization<>();
    }

    public static PrimalHeuristic<DynamicInstance, Route, Graph> createPrimalHeuristic(Configuration config) {
        if (config != null && config.getPrimalHeuristic() != null && !config.getPrimalHeuristic().isEnabled()) {
            return new NoOpPrimalHeuristic();
        }
        return new DVRPColumnPoolPrimalHeuristic();
    }

    public static DivingHeuristic<DynamicInstance, Route, LmSrc, Graph> createDivingHeuristic(Configuration config) {
        return new NoOpDivingHeuristic<>();
    }

    public static VariableFixing<Route, LmSrc, Graph> createVariableFixing(Configuration config) {
        return new NoOpVariableFixing<>();
    }

    public static ScheduleEnumeration<Route, LmSrc, Graph> createScheduleEnumeration(Configuration config) {
        return new NoOpScheduleEnumeration<>();
    }

    private static final class NoOpRelaxation<S extends org.zzc.bpcsolver.core.template.PathTemplate<?>,
            C extends org.zzc.bpcsolver.algorithm.cutting.CutTemplate,
            G extends org.zzc.bpcsolver.core.template.GraphTemplate<?, ?>>
            implements Relaxation<S, C, G> {
        @Override
        public void initialize(Configuration config, List<G> graphs) {
        }

        @Override
        public boolean update(org.zzc.bpcsolver.core.branch.Node<S, C, G> node) {
            return false;
        }

        @Override
        public String getStrategyName() {
            return "noop relaxation";
        }
    }

    private static final class NoOpStabilization<T extends org.zzc.bpcsolver.problem.template.DynamicInstanceTemplate,
            S extends org.zzc.bpcsolver.core.template.PathTemplate,
            C extends org.zzc.bpcsolver.algorithm.cutting.CutTemplate,
            G extends org.zzc.bpcsolver.core.template.GraphTemplate>
            implements Stabilization<T, S, C, G> {
        @Override
        public void Initialize(Configuration config, T instance, org.zzc.bpcsolver.core.branch.Node<S, C, G> node,
                               org.zzc.bpcsolver.algorithm.strategy.PricingStrategy<T, S, C, G> pricingStrategy,
                               org.zzc.bpcsolver.algorithm.branching.BranchingRule<S, C, G> branchingRule) {
        }

        @Override
        public void solve(T instance, Configuration config, org.zzc.bpcsolver.core.branch.Node<S, C, G> node,
                          org.zzc.bpcsolver.algorithm.strategy.PricingStrategy<T, S, C, G> pricingStrategy,
                          org.zzc.bpcsolver.algorithm.branching.BranchingRule<S, C, G> branchingRule,
                          org.zzc.bpcsolver.algorithm.logger.BPCLogger logger) {
        }

        @Override
        public String getStrategyName() {
            return "noop stabilization";
        }
    }

    private static final class NoOpPrimalHeuristic implements PrimalHeuristic<DynamicInstance, Route, Graph> {
        @Override
        public org.zzc.bpcsolver.core.branch.Solution<Route> obtainInitialSolution(DynamicInstance instance, List<Graph> graphs) {
            org.zzc.bpcsolver.core.branch.Solution<Route> solution =
                    new org.zzc.bpcsolver.core.branch.Solution<>(instance.getNumMachines(), instance.getNumJobs());
            solution.setObjValue(Double.POSITIVE_INFINITY);
            return solution;
        }

        @Override
        public org.zzc.bpcsolver.core.branch.Solution<Route> obtainFeasibleSolution(DynamicInstance instance, List<Graph> graphs,
                                                                                    List<List<Route>> schedules, List<double[]> ysk) {
            org.zzc.bpcsolver.core.branch.Solution<Route> solution =
                    new org.zzc.bpcsolver.core.branch.Solution<>(instance.getNumMachines(), instance.getNumJobs());
            solution.setObjValue(Double.POSITIVE_INFINITY);
            return solution;
        }

        @Override
        public String getStrategyName() {
            return "noop primal heuristic";
        }
    }

    private static final class NoOpDivingHeuristic<T extends org.zzc.bpcsolver.problem.template.DynamicInstanceTemplate,
            S extends org.zzc.bpcsolver.core.template.PathTemplate,
            C extends org.zzc.bpcsolver.algorithm.cutting.CutTemplate,
            G extends org.zzc.bpcsolver.core.template.GraphTemplate>
            implements DivingHeuristic<T, S, C, G> {
        @Override
        public void initialize(T instance, Configuration config, org.zzc.bpcsolver.algorithm.pricing.HeuristicPricing<S, G, C> heuristicPricing,
                               org.zzc.bpcsolver.algorithm.branching.BranchingRule<S, C, G> branchingRule) {
        }

        @Override
        public org.zzc.bpcsolver.core.branch.Solution<S> solve(org.zzc.bpcsolver.core.branch.Node<S, C, G> node,
                                                               org.zzc.bpcsolver.algorithm.logger.BPCLogger logger) {
            return node.getSolution();
        }

        @Override
        public String getStrategyName() {
            return "noop diving";
        }
    }

    private static final class NoOpVariableFixing<S extends org.zzc.bpcsolver.core.template.PathTemplate,
            C extends org.zzc.bpcsolver.algorithm.cutting.CutTemplate,
            G extends org.zzc.bpcsolver.core.template.GraphTemplate>
            implements VariableFixing<S, C, G> {
        @Override
        public void initialize(Configuration config) {
        }

        @Override
        public List<int[]> solve(G graph, org.zzc.bpcsolver.core.branch.Node<S, C, G> node, double upperBound) {
            return new ArrayList<>();
        }

        @Override
        public String getStrategyName() {
            return "noop variable fixing";
        }
    }

    private static final class NoOpScheduleEnumeration<S extends org.zzc.bpcsolver.core.template.PathTemplate,
            C extends org.zzc.bpcsolver.algorithm.cutting.CutTemplate,
            G extends org.zzc.bpcsolver.core.template.GraphTemplate>
            implements ScheduleEnumeration<S, C, G> {
        @Override
        public void initialize(Configuration config, org.zzc.bpcsolver.algorithm.pricing.ExactPricing<S, G, C> pricingStrategy,
                               org.zzc.bpcsolver.algorithm.branching.BranchingRule<S, C, G> branchingRule) {
        }

        @Override
        public List<S> solve(int machineIndex, G graph, org.zzc.bpcsolver.core.branch.Node<S, C, G> node,
                             double upperBound, Configuration config, org.zzc.bpcsolver.algorithm.logger.BPCLogger logger) {
            return new ArrayList<>();
        }

        @Override
        public String getStrategyName() {
            return "noop schedule enumeration";
        }
    }
}

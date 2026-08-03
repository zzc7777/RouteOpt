package org.zzc.bpcsolver.algorithm.factory;

import org.zzc.bpcsolver.algorithm.config.Configuration;
import org.zzc.bpcsolver.algorithm.cutting.Cutting;
import org.zzc.bpcsolver.algorithm.cutting.LmSrc;
import org.zzc.bpcsolver.algorithm.cutting.LmSrcGeneration;
import org.zzc.bpcsolver.core.graph.Route;

import java.util.ArrayList;
import java.util.List;

public final class CuttingStrategyFactory {

    private CuttingStrategyFactory() {
    }

    public static Cutting<Route, LmSrc> createCuttingMethod(Configuration config) {
        if (config == null || config.getCutting() == null || !config.getCutting().isEnabled()) {
            return new NoOpCutting();
        }
        String rule = config.getCutting().getCuttingSwitch();
        if (rule == null) {
            return new NoOpCutting();
        }
        switch (rule.toLowerCase().trim()) {
            case "lm-src":
            case "lmsrc":
            case "limited-memory subset row cut":
            case "limited-memory subset row cuts":
                return new LmSrcGeneration();
            default:
                return new NoOpCutting();
        }
    }

    public static Cutting<Route, LmSrc> createCuttingMethodFromRule(String rule) {
        Configuration config = new Configuration();
        Configuration.CuttingConfig cutting = new Configuration.CuttingConfig();
        cutting.setCuttingSwitch(rule == null ? "none" : rule);
        cutting.setBaseSetSize(3);
        cutting.setMultiplier(0.5);
        config.setCutting(cutting);
        return createCuttingMethod(config);
    }

    public static Cutting<Route, LmSrc> createNoOpCutting() {
        return new NoOpCutting();
    }

    private static final class NoOpCutting implements Cutting<Route, LmSrc> {
        @Override
        public void Initialize(int numJobs, Configuration config) {
        }

        @Override
        public List<LmSrc> separateCuts(int numMachines, List<List<Route>> scheduleSets, List<double[]> yks) {
            return new ArrayList<>();
        }

        @Override
        public String getStrategyName() {
            return "noop cutting";
        }
    }
}

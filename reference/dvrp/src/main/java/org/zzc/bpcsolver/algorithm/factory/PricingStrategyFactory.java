package org.zzc.bpcsolver.algorithm.factory;

import org.zzc.bpcsolver.algorithm.config.Configuration;
import org.zzc.bpcsolver.algorithm.cutting.LmSrc;
import org.zzc.bpcsolver.algorithm.pricing.ExactPricing;
import org.zzc.bpcsolver.algorithm.pricing.ForwardLabeling;
import org.zzc.bpcsolver.algorithm.pricing.HeuristicPricing;
import org.zzc.bpcsolver.algorithm.pricing.LightLabeling;
import org.zzc.bpcsolver.algorithm.pricing.MultiStageHeuristicPricing;
import org.zzc.bpcsolver.algorithm.pricing.RelaxedLabeling;
import org.zzc.bpcsolver.core.graph.Graph;
import org.zzc.bpcsolver.core.graph.Route;

public final class PricingStrategyFactory {

    private PricingStrategyFactory() {
    }

    public static ExactPricing<Route, Graph, LmSrc> createPricingAlgorithm(Configuration config) {
        return new ForwardLabeling();
    }

    public static HeuristicPricing<Route, Graph, LmSrc> createHeuristicPricingAlgorithm(Configuration config) {
        if (config == null || config.getPricing() == null) {
            return new LightLabeling();
        }
        return createHeuristicPricingAlgorithmFromRule(config.getPricing().getHeuristicPricing());
    }

    private static HeuristicPricing<Route, Graph, LmSrc> createHeuristicPricingAlgorithmFromRule(String rule) {
        if (rule == null) {
            return new LightLabeling();
        }
        switch (rule.toLowerCase().trim()) {
            case "light":
            case "light labeling":
                return new LightLabeling();
            case "relaxed":
            case "relaxed labeling":
                return new RelaxedLabeling();
            case "multi-stage":
            case "multi-stage-heuristic":
            case "multi-stage-heuristic-pricing":
            case "multi-stage heuristic pricing":
                return new MultiStageHeuristicPricing();
            default:
                return new LightLabeling();
        }
    }
}

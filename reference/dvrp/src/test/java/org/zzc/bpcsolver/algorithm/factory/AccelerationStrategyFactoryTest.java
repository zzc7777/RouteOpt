package org.zzc.bpcsolver.algorithm.factory;

import org.junit.jupiter.api.Test;
import org.zzc.bpcsolver.algorithm.acceleration.Relaxation;
import org.zzc.bpcsolver.algorithm.acceleration.PrimalHeuristic;
import org.zzc.bpcsolver.algorithm.config.Configuration;
import org.zzc.bpcsolver.algorithm.cutting.LmSrc;
import org.zzc.bpcsolver.core.branch.Solution;
import org.zzc.bpcsolver.core.graph.Graph;
import org.zzc.bpcsolver.core.graph.Route;
import org.zzc.bpcsolver.problem.entity.DynamicInstance;

import java.io.IOException;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccelerationStrategyFactoryTest {

    @Test
    void shouldUseColumnPoolPrimalHeuristicByDefault() {
        PrimalHeuristic<DynamicInstance, Route, Graph> heuristic =
                AccelerationStrategyFactory.createPrimalHeuristic(new Configuration());

        assertEquals("dvrp column-pool integer master", heuristic.getStrategyName());
    }

    @Test
    void shouldDisablePrimalHeuristicWhenConfiguredAsNone() {
        Configuration config = new Configuration();
        Configuration.PrimalHeuristicConfig primalHeuristicConfig = new Configuration.PrimalHeuristicConfig();
        primalHeuristicConfig.setPrimalHeuristicSwitch("none");
        config.setPrimalHeuristic(primalHeuristicConfig);
        DynamicInstance instance = new DynamicInstance();

        PrimalHeuristic<DynamicInstance, Route, Graph> heuristic =
                AccelerationStrategyFactory.createPrimalHeuristic(config);
        Solution<Route> solution = heuristic.obtainFeasibleSolution(
                instance,
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>()
        );

        assertEquals("noop primal heuristic", heuristic.getStrategyName());
        assertTrue(Double.isInfinite(solution.getObjValue()));
    }

    @Test
    void shouldReadPrimalHeuristicSwitchFromYaml() throws IOException {
        Configuration config = Configuration.loadFromFile(
                "src/main/java/org/zzc/bpcsolver/algorithm/config/bpc_solver.yaml"
        );

        assertEquals("column-pool-integer-master",
                config.getPrimalHeuristic().getPrimalHeuristicSwitch());
    }

    @Test
    void shouldReadParallelPricingSwitchFromYaml() throws IOException {
        Configuration config = Configuration.loadFromFile(
                "src/main/java/org/zzc/bpcsolver/algorithm/config/bpc_solver.yaml"
        );

        assertTrue(config.getColumnGeneration().isParallelPricingEnabled());
    }

    @Test
    void shouldCreateNgRouteRelaxationFromConfig() {
        Configuration config = new Configuration();
        Configuration.RelaxationConfig relaxationConfig = new Configuration.RelaxationConfig();
        relaxationConfig.setRelaxationSwitch("ng-route");
        relaxationConfig.setNgSize(4);
        config.setRelaxation(relaxationConfig);

        Relaxation<Route, LmSrc, Graph> relaxation = AccelerationStrategyFactory.createRelaxation(config);

        assertEquals("ng-route", relaxation.getStrategyName());
    }

    @Test
    void shouldCreateDynamicNgRouteRelaxationFromConfig() {
        Configuration config = new Configuration();
        Configuration.RelaxationConfig relaxationConfig = new Configuration.RelaxationConfig();
        relaxationConfig.setRelaxationSwitch("dynamic ng-route");
        relaxationConfig.setNgSize(4);
        relaxationConfig.setMaxNgSize(10);
        config.setRelaxation(relaxationConfig);

        Relaxation<Route, LmSrc, Graph> relaxation = AccelerationStrategyFactory.createRelaxation(config);

        assertEquals("dynamic ng-route", relaxation.getStrategyName());
    }

    @Test
    void shouldTreatBlankRelaxationSwitchAsDisabled() {
        Configuration config = new Configuration();
        Configuration.RelaxationConfig relaxationConfig = new Configuration.RelaxationConfig();
        config.setRelaxation(relaxationConfig);

        Relaxation<Route, LmSrc, Graph> relaxation = AccelerationStrategyFactory.createRelaxation(config);

        assertEquals("noop relaxation", relaxation.getStrategyName());
        assertTrue(!relaxationConfig.isEnabled());
    }
}

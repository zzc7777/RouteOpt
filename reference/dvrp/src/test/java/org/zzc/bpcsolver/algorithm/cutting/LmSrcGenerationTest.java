package org.zzc.bpcsolver.algorithm.cutting;

import gnu.trove.list.array.TIntArrayList;
import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;
import org.junit.jupiter.api.Test;
import org.zzc.bpcsolver.algorithm.config.Configuration;
import org.zzc.bpcsolver.core.graph.Route;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LmSrcGenerationTest {

    @Test
    void shouldCalculateCoefficientWithMemoryStateReset() {
        LmSrc cut = new LmSrc(0.5, 3);
        cut.setSubSet(new TIntHashSet(new int[]{1, 2, 3}));
        cut.setMemorySet(Arrays.asList(new TIntHashSet(new int[]{1, 2, 3, 4, 5, 7})));

        double coefficient = cut.calculateCoefficient(new TIntArrayList(new int[]{0, 1, 4, 5, 3, 6, 2, 7, 1, 0}), 0);

        assertEquals(2.0, coefficient, 1e-6);
    }

    @Test
    void shouldProduceMemorySetThatPreservesPositiveRouteViolation() {
        LmSrcGeneration generation = new LmSrcGeneration();
        generation.Initialize(5, buildCuttingConfig());
        TIntSet subSet = new TIntHashSet(new int[]{1, 2, 3});
        List<Route> routes = Arrays.asList(
                buildRoute(0, 0, 0, 1, 4, 5, 3, 6, 2, 7, 1, 0),
                buildRoute(0, 1, 0, 7, 2, 8, 3, 0)
        );

        TIntSet memorySet = generation.produceMemorySet(routes, subSet, new double[]{0.2, 0.3});

        assertTrue(memorySet.contains(1));
        assertTrue(memorySet.contains(2));
        assertTrue(memorySet.contains(3));
        assertTrue(memorySet.contains(4));
        assertTrue(memorySet.contains(5));
        assertTrue(memorySet.contains(7));
        assertTrue(memorySet.contains(8));
    }

    @Test
    void shouldSeparateViolatedLmSrcFromFractionalColumnPool() {
        LmSrcGeneration generation = new LmSrcGeneration();
        generation.Initialize(3, buildCuttingConfig());
        List<List<Route>> routes = Arrays.asList(Arrays.asList(
                buildRoute(0, 0, 0, 1, 2, 4),
                buildRoute(0, 1, 0, 2, 3, 4)
        ));

        List<LmSrc> cuts = generation.separateCuts(1, routes, Arrays.asList(new double[]{0.6, 0.6}));

        assertEquals(1, cuts.size());
        assertEquals(1.0, cuts.get(0).getConstant(), 1e-6);
        assertEquals(0.2, cuts.get(0).getViolation(), 1e-6);
    }

    @Test
    void shouldUseVisitCountsWhenSeparatingRelaxedRoutes() {
        LmSrcGeneration generation = new LmSrcGeneration();
        generation.Initialize(3, buildCuttingConfig());
        List<List<Route>> routes = Arrays.asList(Arrays.asList(
                buildRoute(0, 0, 0, 1, 2, 1, 3, 4)
        ));

        List<LmSrc> cuts = generation.separateCuts(1, routes, Arrays.asList(new double[]{0.6}));

        assertEquals(1, cuts.size());
        assertEquals(0.2, cuts.get(0).getViolation(), 1e-6);
    }

    @Test
    void shouldProduceMemorySetUsingRelaxedRouteVisitedVector() {
        LmSrcGeneration generation = new LmSrcGeneration();
        generation.Initialize(3, buildCuttingConfig());
        Route route = buildRoute(0, 0, 0, 1, 4, 2, 1, 3, 5);
        for (int nodeId : route.getVisitedVertices().toArray()) {
            route.getVisitedVector()[nodeId]++;
        }
        TIntSet memorySet = generation.produceMemorySet(Arrays.asList(route), new TIntHashSet(new int[]{1, 2, 3}), new double[]{0.6});

        assertTrue(memorySet.contains(4));
    }

    @Test
    void shouldProduceCandidateSubSetsFromObservedCustomerIds() {
        LmSrcGeneration generation = new LmSrcGeneration();
        List<TIntSet> subSets = generation.produceSubSets(Arrays.asList(14, 21, 22, 23), 3);

        assertEquals(4, subSets.size());
        assertTrue(subSets.stream().anyMatch(set -> set.contains(14) && set.contains(21) && set.contains(22)));
        assertTrue(subSets.stream().anyMatch(set -> set.contains(21) && set.contains(22) && set.contains(23)));
    }

    @Test
    void shouldReuseObservedBaseSetsWithinSameStaticCustomerSet() {
        LmSrcGeneration generation = new LmSrcGeneration();
        generation.Initialize(100, buildCuttingConfig());
        List<List<Route>> routes = Arrays.asList(Arrays.asList(
                buildRoute(0, 0, 0, 14, 21, 22, 101),
                buildRoute(0, 1, 0, 14, 22, 23, 101)
        ));

        generation.separateCuts(1, routes, Arrays.asList(new double[]{0.6, 0.6}));
        generation.separateCuts(1, routes, Arrays.asList(new double[]{0.6, 0.6}));

        assertEquals(1, generation.getObservedBaseSetBuildCount());
    }

    @Test
    void shouldKeepPreGeneratedStaticBaseSetsAcrossRoutePoolChanges() {
        LmSrcGeneration generation = new LmSrcGeneration();
        generation.Initialize(100, buildCuttingConfig());
        generation.initializeBaseSetsFromCustomerIds(Arrays.asList(14, 21, 22, 23));
        List<List<Route>> firstRoutes = Arrays.asList(Arrays.asList(
                buildRoute(0, 0, 0, 14, 21, 22, 101),
                buildRoute(0, 1, 0, 14, 22, 23, 101)
        ));
        List<List<Route>> secondRoutes = Arrays.asList(Arrays.asList(
                buildRoute(0, 0, 0, 14, 21, 22, 101),
                buildRoute(0, 1, 0, 14, 22, 24, 101)
        ));

        generation.separateCuts(1, firstRoutes, Arrays.asList(new double[]{0.6, 0.6}));
        generation.separateCuts(1, secondRoutes, Arrays.asList(new double[]{0.6, 0.6}));

        assertEquals(1, generation.getObservedBaseSetBuildCount());
    }

    @Test
    void shouldRebuildObservedBaseSetsWhenCustomerSetChanges() {
        LmSrcGeneration generation = new LmSrcGeneration();
        generation.Initialize(100, buildCuttingConfig());
        List<List<Route>> firstRoutes = Arrays.asList(Arrays.asList(
                buildRoute(0, 0, 0, 14, 21, 22, 101),
                buildRoute(0, 1, 0, 14, 22, 23, 101)
        ));
        List<List<Route>> secondRoutes = Arrays.asList(Arrays.asList(
                buildRoute(0, 0, 0, 14, 21, 22, 101),
                buildRoute(0, 1, 0, 14, 22, 24, 101)
        ));

        generation.separateCuts(1, firstRoutes, Arrays.asList(new double[]{0.6, 0.6}));
        generation.separateCuts(1, secondRoutes, Arrays.asList(new double[]{0.6, 0.6}));

        assertEquals(2, generation.getObservedBaseSetBuildCount());
    }

    private Configuration buildCuttingConfig() {
        Configuration config = new Configuration();
        Configuration.CuttingConfig cutting = new Configuration.CuttingConfig();
        cutting.setCuttingSwitch("lm-src");
        cutting.setBaseSetSize(3);
        cutting.setMultiplier(0.5);
        config.setCutting(cutting);
        return config;
    }

    private Route buildRoute(int startDepotId, int index, int... nodes) {
        Route route = new Route(startDepotId, index, 16);
        route.setVisitedVertices(new TIntArrayList(nodes));
        return route;
    }
}

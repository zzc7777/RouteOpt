package org.zzc.bpcsolver.algorithm.cutting;

import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;
import org.zzc.bpcsolver.algorithm.config.Configuration;
import org.zzc.bpcsolver.core.graph.Route;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

public class LmSrcGeneration implements Cutting<Route, LmSrc> {

    static final double DEFAULT_TOLERANCE = 1e-6;

    private List<TIntSet> baseSets = new ArrayList<>();
    private List<Integer> cachedObservedCustomerIds = new ArrayList<>();
    private List<TIntSet> cachedObservedBaseSets = new ArrayList<>();
    private boolean staticCustomerBaseSetsInitialized = false;
    private int observedBaseSetBuildCount = 0;
    private double p = 0.5;
    private int baseSetSize = 3;
    private double stopAfterPricingSeconds = 6.0;
    private int trailingRounds = 3;
    private double minTrailingGapImprovement = 0.02;

    @Override
    public void Initialize(int numJobs, Configuration config) {
        if (config != null && config.getCutting() != null) {
            this.p = config.getCutting().getMultiplier();
            this.baseSetSize = config.getCutting().getBaseSetSize();
            this.stopAfterPricingSeconds = config.getCutting().getStopAfterPricingSeconds();
            this.trailingRounds = config.getCutting().getTrailingRounds();
            this.minTrailingGapImprovement = config.getCutting().getMinTrailingGapImprovement();
        }
        this.baseSets = new ArrayList<>();
        this.cachedObservedCustomerIds = new ArrayList<>();
        this.cachedObservedBaseSets = new ArrayList<>();
        this.staticCustomerBaseSetsInitialized = false;
        this.observedBaseSetBuildCount = 0;
    }

    public void initializeBaseSetsFromCustomerIds(List<Integer> customerIds) {
        List<Integer> normalizedCustomerIds = normalizeCustomerIds(customerIds);
        if (this.staticCustomerBaseSetsInitialized
                && normalizedCustomerIds.equals(this.cachedObservedCustomerIds)) {
            return;
        }
        this.cachedObservedCustomerIds = new ArrayList<>(normalizedCustomerIds);
        this.cachedObservedBaseSets = produceSubSets(this.cachedObservedCustomerIds, this.baseSetSize);
        this.baseSets = this.cachedObservedBaseSets;
        this.staticCustomerBaseSetsInitialized = true;
        if (!this.cachedObservedBaseSets.isEmpty()) {
            this.observedBaseSetBuildCount++;
        }
    }

    public List<TIntSet> produceSubSets(int numJobs) {
        return produceSubSets(numJobs, this.baseSetSize);
    }

    public List<TIntSet> produceSubSets(int numJobs, int subsetSize) {
        List<TIntSet> subSets = new ArrayList<>();
        if (numJobs <= 0 || subsetSize <= 0 || subsetSize > numJobs) {
            return subSets;
        }
        TIntSet current = new TIntHashSet();
        backtrack(subSets, current, numJobs, subsetSize, 1);
        return subSets;
    }

    public List<TIntSet> produceSubSets(List<Integer> nodeIds, int subsetSize) {
        List<TIntSet> subSets = new ArrayList<>();
        if (nodeIds == null || nodeIds.isEmpty() || subsetSize <= 0 || subsetSize > nodeIds.size()) {
            return subSets;
        }
        TIntSet current = new TIntHashSet();
        backtrack(subSets, current, nodeIds, subsetSize, 0);
        return subSets;
    }

    private void backtrack(List<TIntSet> subSets, TIntSet current, int numJobs, int subsetSize, int start) {
        if (current.size() == subsetSize) {
            subSets.add(new TIntHashSet(current));
            return;
        }
        for (int i = start; i <= numJobs; i++) {
            current.add(i);
            backtrack(subSets, current, numJobs, subsetSize, i + 1);
            current.remove(i);
        }
    }

    private void backtrack(List<TIntSet> subSets, TIntSet current, List<Integer> nodeIds, int subsetSize, int start) {
        if (current.size() == subsetSize) {
            subSets.add(new TIntHashSet(current));
            return;
        }
        for (int i = start; i < nodeIds.size(); i++) {
            int nodeId = nodeIds.get(i);
            current.add(nodeId);
            backtrack(subSets, current, nodeIds, subsetSize, i + 1);
            current.remove(nodeId);
        }
    }

    @Override
    public List<LmSrc> separateCuts(int numMachines, List<List<Route>> scheduleSets, List<double[]> yks) {
        if (scheduleSets == null || yks == null || scheduleSets.isEmpty()) {
            return new ArrayList<>();
        }
        List<TIntSet> candidateSets = getCandidateBaseSets(scheduleSets);
        if (candidateSets.isEmpty()) {
            candidateSets = this.baseSets;
        }
        List<LmSrc> lmSrcs = getViolatedCuts(numMachines, candidateSets, scheduleSets, yks);
        for (LmSrc lmSrc : lmSrcs) {
            List<TIntSet> memorySet = new ArrayList<>();
            for (int k = 0; k < numMachines; k++) {
                List<Route> routes = k < scheduleSets.size() ? scheduleSets.get(k) : new ArrayList<>();
                double[] values = k < yks.size() ? yks.get(k) : new double[0];
                memorySet.add(produceMemorySet(routes, lmSrc.getSubSet(), values));
            }
            lmSrc.setMemorySet(memorySet);

            List<List<Double>> coefficientSet = new ArrayList<>();
            for (int k = 0; k < numMachines; k++) {
                List<Route> routes = k < scheduleSets.size() ? scheduleSets.get(k) : new ArrayList<>();
                List<Double> coefficients = new ArrayList<>();
                for (Route route : routes) {
                    coefficients.add(lmSrc.calculateCoefficient(route.getVisitedVertices(), k));
                }
                coefficientSet.add(coefficients);
            }
            lmSrc.setCoefficient(coefficientSet);
            lmSrc.setConstant(Math.floor(this.baseSetSize * this.p));
        }
        return lmSrcs;
    }

    public List<LmSrc> getViolatedCuts(int numMachines,
                                       List<TIntSet> subSets,
                                       List<List<Route>> scheduleSets,
                                       List<double[]> yks) {
        List<LmSrc> lmSrcs = new ArrayList<>();
        if (subSets == null) {
            return lmSrcs;
        }
        List<CandidateBaseSet> candidateBaseSets = buildCandidateBaseSets(subSets);
        List<PositiveRouteColumn> positiveRoutes = collectPositiveRouteColumns(numMachines, scheduleSets, yks);
        if (candidateBaseSets.isEmpty() || positiveRoutes.isEmpty()) {
            return lmSrcs;
        }

        for (CandidateBaseSet candidate : candidateBaseSets) {
            double leftHandSide = 0.0;
            for (PositiveRouteColumn column : positiveRoutes) {
                int coeff = countVisitsInSubset(candidate.nodeIds, column);
                if (coeff == 0) {
                    continue;
                }
                leftHandSide += Math.floor(this.p * coeff) * column.value;
            }

            LmSrc lmSrc = new LmSrc(this.p, this.baseSetSize);
            lmSrc.setId(candidate.id);
            lmSrc.setSubSet(new TIntHashSet(candidate.subSet));
            double violation = leftHandSide - Math.floor(this.baseSetSize * this.p);
            lmSrc.setViolation(violation > DEFAULT_TOLERANCE ? violation : 0.0);
            if (lmSrc.getViolation() > DEFAULT_TOLERANCE) {
                lmSrcs.add(lmSrc);
            }
        }

        lmSrcs.sort(Comparator
                .comparingDouble(LmSrc::getViolation)
                .reversed()
                .thenComparingInt(LmSrc::getId));
        return lmSrcs;
    }

    private List<CandidateBaseSet> buildCandidateBaseSets(List<TIntSet> subSets) {
        List<CandidateBaseSet> candidateBaseSets = new ArrayList<>();
        if (subSets == null) {
            return candidateBaseSets;
        }
        for (int i = 0; i < subSets.size(); i++) {
            TIntSet subSet = subSets.get(i);
            if (subSet == null || subSet.isEmpty()) {
                continue;
            }
            candidateBaseSets.add(new CandidateBaseSet(i + 1, subSet));
        }
        return candidateBaseSets;
    }

    private List<PositiveRouteColumn> collectPositiveRouteColumns(int numMachines,
                                                                  List<List<Route>> scheduleSets,
                                                                  List<double[]> yks) {
        List<PositiveRouteColumn> positiveRoutes = new ArrayList<>();
        if (scheduleSets == null || yks == null) {
            return positiveRoutes;
        }
        for (int k = 0; k < numMachines; k++) {
            List<Route> routes = k < scheduleSets.size() ? scheduleSets.get(k) : new ArrayList<>();
            double[] values = k < yks.size() ? yks.get(k) : new double[0];
            for (int s = 0; s < routes.size() && s < values.length; s++) {
                double value = values[s];
                Route route = routes.get(s);
                if (Math.abs(value) <= DEFAULT_TOLERANCE || route == null) {
                    continue;
                }
                positiveRoutes.add(new PositiveRouteColumn(route, value));
            }
        }
        return positiveRoutes;
    }

    public TIntSet produceMemorySet(List<Route> routes, TIntSet subSet, double[] values) {
        TIntSet memorySet = new TIntHashSet(subSet);
        if (routes == null || subSet == null || values == null) {
            return memorySet;
        }
        for (int s = 0; s < routes.size() && s < values.length; s++) {
            Route route = routes.get(s);
            if (values[s] <= DEFAULT_TOLERANCE
                    || route == null
                    || route.getVisitedVertices() == null
                    || countVisitsInSubset(subSet, route) == 0) {
                continue;
            }
            double state = 0.0;
            TIntSet aux = new TIntHashSet();
            for (int i = 0; i < route.getVisitedVertices().size(); i++) {
                int node = route.getVisitedVertices().get(i);
                if (subSet.contains(node)) {
                    state += this.p;
                    if (state >= 1.0 - DEFAULT_TOLERANCE) {
                        memorySet.addAll(aux);
                        aux.clear();
                        state -= 1.0;
                    }
                } else if (state > DEFAULT_TOLERANCE) {
                    aux.add(node);
                }
            }
        }
        return memorySet;
    }

    private int countVisitsInSubset(TIntSet subSet, Route route) {
        if (subSet == null || route == null || route.getVisitedVertices() == null) {
            return 0;
        }
        return countVisitsInSubset(subSet.toArray(), new PositiveRouteColumn(route, 0.0));
    }

    private int countVisitsInSubset(int[] subsetNodes, PositiveRouteColumn column) {
        if (subsetNodes == null || column == null || column.route == null || column.route.getVisitedVertices() == null) {
            return 0;
        }
        if (column.hasVisitedVectorCounts) {
            int visits = 0;
            for (int nodeId : subsetNodes) {
                if (nodeId >= 0 && nodeId < column.visitedVector.length) {
                    visits += column.visitedVector[nodeId];
                }
            }
            return visits;
        }
        int visits = 0;
        for (int i = 0; i < column.route.getVisitedVertices().size(); i++) {
            if (contains(subsetNodes, column.route.getVisitedVertices().get(i))) {
                visits++;
            }
        }
        return visits;
    }

    private boolean hasAnyVisitedCount(int[] visitedVector) {
        if (visitedVector == null) {
            return false;
        }
        for (int count : visitedVector) {
            if (count > 0) {
                return true;
            }
        }
        return false;
    }

    private boolean contains(int[] values, int candidate) {
        if (values == null) {
            return false;
        }
        for (int value : values) {
            if (value == candidate) {
                return true;
            }
        }
        return false;
    }

    private static final class CandidateBaseSet {
        private final int id;
        private final TIntSet subSet;
        private final int[] nodeIds;

        private CandidateBaseSet(int id, TIntSet subSet) {
            this.id = id;
            this.subSet = subSet;
            this.nodeIds = subSet == null ? new int[0] : subSet.toArray();
        }
    }

    private final class PositiveRouteColumn {
        private final Route route;
        private final double value;
        private final int[] visitedVector;
        private final boolean hasVisitedVectorCounts;

        private PositiveRouteColumn(Route route, double value) {
            this.route = route;
            this.value = value;
            this.visitedVector = route == null ? null : route.getVisitedVector();
            this.hasVisitedVectorCounts = hasAnyVisitedCount(this.visitedVector);
        }
    }

    private List<TIntSet> getCandidateBaseSets(List<List<Route>> scheduleSets) {
        if (this.staticCustomerBaseSetsInitialized) {
            return this.cachedObservedBaseSets;
        }
        List<Integer> observedCustomerIds = extractCustomerIds(scheduleSets);
        if (observedCustomerIds.isEmpty() || observedCustomerIds.size() < this.baseSetSize) {
            return new ArrayList<>();
        }
        if (observedCustomerIds.equals(this.cachedObservedCustomerIds)) {
            return this.cachedObservedBaseSets;
        }
        this.cachedObservedCustomerIds = new ArrayList<>(observedCustomerIds);
        this.cachedObservedBaseSets = produceSubSets(this.cachedObservedCustomerIds, this.baseSetSize);
        this.observedBaseSetBuildCount++;
        return this.cachedObservedBaseSets;
    }

    int getObservedBaseSetBuildCount() {
        return observedBaseSetBuildCount;
    }

    private List<Integer> normalizeCustomerIds(List<Integer> customerIds) {
        TreeSet<Integer> normalizedIds = new TreeSet<>();
        if (customerIds == null) {
            return new ArrayList<>();
        }
        for (Integer customerId : customerIds) {
            if (customerId != null && customerId > 0) {
                normalizedIds.add(customerId);
            }
        }
        return new ArrayList<>(normalizedIds);
    }

    private List<Integer> extractCustomerIds(List<List<Route>> scheduleSets) {
        TreeSet<Integer> customerIds = new TreeSet<>();
        for (List<Route> routes : scheduleSets) {
            if (routes == null) {
                continue;
            }
            for (Route route : routes) {
                if (route == null || route.getVisitedVertices() == null) {
                    continue;
                }
                for (int i = 1; i < route.getVisitedVertices().size() - 1; i++) {
                    int nodeId = route.getVisitedVertices().get(i);
                    if (nodeId > 0) {
                        customerIds.add(nodeId);
                    }
                }
            }
        }
        return new ArrayList<>(customerIds);
    }

    @Override
    public String getStrategyName() {
        return "lm-SRC";
    }

    public double getStopAfterPricingSeconds() {
        return stopAfterPricingSeconds;
    }

    public int getTrailingRounds() {
        return trailingRounds;
    }

    public double getMinTrailingGapImprovement() {
        return minTrailingGapImprovement;
    }
}

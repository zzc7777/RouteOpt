package org.zzc.bpcsolver.core.graph;

import lombok.Getter;
import gnu.trove.iterator.TIntIterator;
import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;
import org.zzc.bpcsolver.algorithm.pricing.Label;
import org.zzc.bpcsolver.algorithm.pricing.PricingDiagnostics;
import org.zzc.bpcsolver.core.template.VertexTemplate;
import org.zzc.bpcsolver.problem.entity.Depot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
public class Vertex extends VertexTemplate {

    private static final double EPSILON = 1e-6;

    private double x;
    private double y;
    private double earliestTime;
    private double latestTime;
    private double serviceTime;
    private int demand;
    private Depot.DepotType depotType;
    private TIntSet ngSet;
    private List<Label> labelSet;
    private Map<String, List<Label>> dominanceBuckets;

    public Vertex() {
        super(0, 0);
        this.ngSet = new TIntHashSet();
        this.labelSet = new ArrayList<>();
        this.dominanceBuckets = new HashMap<>();
    }

    public Vertex(int id, int index, double x, double y, double earliestTime, double latestTime,
                  double serviceTime, int demand, Depot.DepotType depotType) {
        super(id, index);
        this.x = x;
        this.y = y;
        this.earliestTime = earliestTime;
        this.latestTime = latestTime;
        this.serviceTime = serviceTime;
        this.demand = demand;
        this.depotType = depotType;
        this.ngSet = new TIntHashSet();
        this.labelSet = new ArrayList<>();
        this.dominanceBuckets = new HashMap<>();
    }

    public Vertex(Vertex other) {
        super(other);
        this.x = other.x;
        this.y = other.y;
        this.earliestTime = other.earliestTime;
        this.latestTime = other.latestTime;
        this.serviceTime = other.serviceTime;
        this.demand = other.demand;
        this.depotType = other.depotType;
        this.ngSet = new TIntHashSet(other.ngSet);
        this.labelSet = new ArrayList<>();
        this.dominanceBuckets = new HashMap<>();
        for (Label label : other.labelSet) {
            Label copiedLabel = label == null ? null : label.copy();
            this.labelSet.add(copiedLabel);
            addToDominanceBucket(copiedLabel);
        }
    }

    @Override
    public Vertex copy() {
        return new Vertex(this);
    }

    public boolean addNonDominatedLabel(Label label) {
        return addNonDominatedLabel(label, new ArrayList<>());
    }

    public boolean addNonDominatedLabel(Label label, List<Double> dualsOfCuts) {
        if (label == null) {
            throw new IllegalArgumentException("label must not be null.");
        }
        if (isReturnDepot()) {
            labelSet.add(label);
            PricingDiagnostics.recordMaxLabelsPerVertex(labelSet.size());
            return true;
        }
        long startNanos = System.nanoTime();
        DominanceScanResult dominanceScan = scanDominance(label, dualsOfCuts, true);
        boolean dominated = dominanceScan.dominatingLabel != null;
        PricingDiagnostics.recordDominanceElapsed(System.nanoTime() - startNanos);
        if (PricingDiagnostics.isTimeLimitReached()) {
            return false;
        }
        if (dominated) {
            PricingDiagnostics.recordDominatedLabel();
            return false;
        }
        for (Label removedLabel : dominanceScan.removedLabels) {
            removeLabelFromIndexes(removedLabel);
            PricingDiagnostics.recordRemovedDominatedLabel();
        }
        labelSet.add(label);
        addToDominanceBucket(label);
        PricingDiagnostics.recordMaxLabelsPerVertex(labelSet.size());
        return true;
    }

    public boolean isDominatedByExistingLabel(Label label) {
        return isDominatedByExistingLabel(label, new ArrayList<>());
    }

    public boolean isDominatedByExistingLabel(Label label, List<Double> dualsOfCuts) {
        return scanDominance(label, dualsOfCuts, false).dominatingLabel != null;
    }

    public Label findDominatingLabel(Label label) {
        return findDominatingLabel(label, new ArrayList<>());
    }

    public Label findDominatingLabel(Label label, List<Double> dualsOfCuts) {
        Label dominatingLabel = findDominatingLabelReference(label, dualsOfCuts);
        return dominatingLabel == null ? null : dominatingLabel.copy();
    }

    private Label findDominatingLabelReference(Label label, List<Double> dualsOfCuts) {
        return scanDominance(label, dualsOfCuts, false).dominatingLabel;
    }

    public void removeDominatedLabels(Label label) {
        removeDominatedLabels(label, new ArrayList<>());
    }

    public void removeDominatedLabels(Label label, List<Double> dualsOfCuts) {
        if (label == null) {
            throw new IllegalArgumentException("label must not be null.");
        }
        if (isReturnDepot()) {
            return;
        }
        DominanceScanResult dominanceScan = scanDominance(label, dualsOfCuts, true);
        if (dominanceScan.dominatingLabel != null) {
            return;
        }
        for (Label removedLabel : dominanceScan.removedLabels) {
            removeLabelFromIndexes(removedLabel);
            PricingDiagnostics.recordRemovedDominatedLabel();
        }
    }

    public void clearLabels() {
        labelSet.clear();
        dominanceBuckets.clear();
    }

    public void replaceLabelsWith(Label label) {
        clearLabels();
        if (label == null) {
            return;
        }
        labelSet.add(label);
        addToDominanceBucket(label);
        PricingDiagnostics.recordMaxLabelsPerVertex(labelSet.size());
    }

    public void removeLabel(Label label) {
        removeLabelFromIndexes(label);
    }

    private boolean dominatesReducedCost(Label dominant, Label dominated, List<Double> dualsOfCuts) {
        double adjustedReducedCost = dominant.getReducedCost();
        if (dominant.getState() != null && dominated.getState() != null && dualsOfCuts != null) {
            int limit = Math.min(Math.min(dominant.getState().size(), dominated.getState().size()), dualsOfCuts.size());
            for (int i = 0; i < limit; i++) {
                if (dominant.getState().get(i) > dominated.getState().get(i)) {
                    adjustedReducedCost -= dualsOfCuts.get(i);
                }
            }
        }
        return adjustedReducedCost <= dominated.getReducedCost() + EPSILON;
    }

    private List<Label> getDominanceCandidates(Label label) {
        if (label == null) {
            return new ArrayList<>();
        }
        String key = dominanceKey(label);
        List<Label> candidates = dominanceBuckets.get(key);
        if ((candidates == null || candidates.isEmpty()) && !labelSet.isEmpty()) {
            rebuildDominanceBuckets();
            candidates = dominanceBuckets.get(key);
        }
        return candidates == null ? Collections.emptyList() : candidates;
    }

    private DominanceScanResult scanDominance(Label label, List<Double> dualsOfCuts, boolean collectRemovedLabels) {
        if (label == null) {
            throw new IllegalArgumentException("label must not be null.");
        }
        DominanceScanResult result = new DominanceScanResult();
        if (isReturnDepot()) {
            return result;
        }
        List<Label> candidates = getDominanceCandidates(label);
        for (int candidateIndex = 0; candidateIndex < candidates.size(); candidateIndex++) {
            if (PricingDiagnostics.isTimeLimitReached()) {
                return result;
            }
            Label currentLabel = candidates.get(candidateIndex);
            if (dominatesLabel(currentLabel, label, dualsOfCuts)) {
                result.dominatingLabel = currentLabel;
                return result;
            }
            if (collectRemovedLabels && dominatesLabel(label, currentLabel, dualsOfCuts)) {
                result.removedLabels.add(currentLabel);
            }
        }
        return result;
    }

    private boolean dominatesLabel(Label dominant, Label dominated, List<Double> dualsOfCuts) {
        if (dominant == null || dominated == null) {
            return false;
        }
        if (dominant.getDominanceGroupId() != dominated.getDominanceGroupId()) {
            return false;
        }
        if (dominant.getLastNodeId() != dominated.getLastNodeId()) {
            return false;
        }
        if (dominant.getStartServiceTime() > dominated.getStartServiceTime() + EPSILON) {
            return false;
        }
        if (dominant.getCapacityUsed() > dominated.getCapacityUsed() + EPSILON) {
            return false;
        }
        if (dominant.getReducedCost() > dominated.getReducedCost() + EPSILON) {
            return false;
        }
        if (!dominatesReducedCost(dominant, dominated, dualsOfCuts)) {
            return false;
        }
        return dominated.containsAllNgNodes(dominant);
    }

    private void addToDominanceBucket(Label label) {
        if (label == null || isReturnDepot()) {
            return;
        }
        dominanceBuckets.computeIfAbsent(dominanceKey(label), ignored -> new ArrayList<>()).add(label);
    }

    private void rebuildDominanceBuckets() {
        dominanceBuckets.clear();
        if (isReturnDepot()) {
            return;
        }
        for (Label label : labelSet) {
            addToDominanceBucket(label);
        }
    }

    private void removeLabelFromIndexes(Label label) {
        if (label != null) {
            label.setExtended(true);
        }
        labelSet.remove(label);
        if (label == null || isReturnDepot()) {
            return;
        }
        String dominanceKey = dominanceKey(label);
        List<Label> bucket = dominanceBuckets.get(dominanceKey);
        if (bucket == null) {
            return;
        }
        bucket.remove(label);
        if (bucket.isEmpty()) {
            dominanceBuckets.remove(dominanceKey);
        }
    }

    private static final class DominanceScanResult {
        private Label dominatingLabel;
        private final List<Label> removedLabels = new ArrayList<>();
    }

    private String dominanceKey(Label label) {
        return label.getDominanceGroupId() + ":" + label.getLastNodeId();
    }

    public boolean isCustomer() {
        return depotType == null;
    }

    public boolean isOriginDepot() {
        return depotType == Depot.DepotType.ORIGIN;
    }

    public boolean isReturnDepot() {
        return depotType == Depot.DepotType.RETURN;
    }

    public boolean isExternalDepot() {
        return depotType == Depot.DepotType.EXTERNAL;
    }
}

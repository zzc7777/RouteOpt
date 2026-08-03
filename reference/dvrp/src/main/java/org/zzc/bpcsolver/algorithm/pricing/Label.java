package org.zzc.bpcsolver.algorithm.pricing;

import gnu.trove.list.array.TIntArrayList;
import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.zzc.bpcsolver.core.graph.Cycle;
import org.zzc.bpcsolver.core.template.PathTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.BitSet;

@Getter
@Setter
public class Label extends PathTemplate<Cycle> {

    private int lastNodeId;
    private double startServiceTime;
    private double load;
    private double capacityUsed;
    private double reducedCost;
    private int dominanceGroupId;
    private List<Double> state;
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private TIntSet ngSet;
    private BitSet ngMask;
    private boolean extended;

    public Label(int id, int index, int numVertices) {
        super(id, index, numVertices);
        this.lastNodeId = 0;
        this.startServiceTime = 0.0;
        this.load = 0.0;
        this.capacityUsed = 0.0;
        this.reducedCost = 0.0;
        this.dominanceGroupId = id;
        this.state = new ArrayList<>();
        this.ngSet = new TIntHashSet();
        this.ngMask = new BitSet();
        this.extended = false;
    }

    public Label(Label other) {
        super(other);
        this.lastNodeId = other.getLastNodeId();
        this.startServiceTime = other.getStartServiceTime();
        this.load = other.getLoad();
        this.capacityUsed = other.getCapacityUsed();
        this.reducedCost = other.getReducedCost();
        this.dominanceGroupId = other.getDominanceGroupId();
        this.state = new ArrayList<>(other.getState());
        this.ngSet = new TIntHashSet(other.ngSet);
        this.ngMask = (BitSet) other.ngMask.clone();
        this.extended = other.isExtended();
    }

    @Override
    public Label copy() {
        return new Label(this);
    }

    public TIntSet getNgSet() {
        return new TIntHashSet(this.ngSet);
    }

    public void clearNgNodes() {
        this.ngSet.clear();
        this.ngMask.clear();
    }

    public boolean addNgNode(int nodeId) {
        boolean added = this.ngSet.add(nodeId);
        if (nodeId >= 0) {
            this.ngMask.set(nodeId);
        }
        return added;
    }

    public boolean containsNgNode(int nodeId) {
        return nodeId >= 0 && this.ngMask.get(nodeId);
    }

    public void retainNgNodes(TIntSet allowedNodes) {
        if (allowedNodes == null) {
            clearNgNodes();
            return;
        }
        TIntArrayList retainedNodes = new TIntArrayList();
        for (int nodeId : this.ngSet.toArray()) {
            if (allowedNodes.contains(nodeId)) {
                retainedNodes.add(nodeId);
            }
        }
        this.ngSet.clear();
        this.ngMask.clear();
        for (int i = 0; i < retainedNodes.size(); i++) {
            addNgNode(retainedNodes.get(i));
        }
    }

    public boolean containsAllNgNodes(Label other) {
        if (other == null) {
            return false;
        }
        BitSet otherMask = other.ngMask;
        for (int bit = otherMask.nextSetBit(0); bit >= 0; bit = otherMask.nextSetBit(bit + 1)) {
            if (!this.ngMask.get(bit)) {
                return false;
            }
        }
        return true;
    }

    public int[] snapshotNgNodes() {
        return this.ngSet.toArray();
    }

    @Override
    public Cycle createCycle(int index, TIntArrayList vertices, int startVertex) {
        return new Cycle(index, vertices, startVertex);
    }

    @Override
    public boolean containsCycle(Cycle cycle) {
        if (cycle == null || cycle.getVertices() == null || visitedVertices == null) {
            return false;
        }

        TIntArrayList cycleVertices = cycle.getVertices();
        if (cycleVertices.size() > visitedVertices.size()) {
            return false;
        }

        for (int i = 0; i <= visitedVertices.size() - cycleVertices.size(); i++) {
            boolean found = true;
            for (int j = 0; j < cycleVertices.size(); j++) {
                if (visitedVertices.get(i + j) != cycleVertices.get(j)) {
                    found = false;
                    break;
                }
            }
            if (found) return true;
        }
        return false;
    }
}

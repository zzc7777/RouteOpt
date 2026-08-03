package org.zzc.bpcsolver.algorithm.cutting;

import gnu.trove.list.array.TIntArrayList;
import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

import static org.zzc.bpcsolver.util.ArrayOperation.intersection;

@Getter
@Setter
public class LmSrc extends CutTemplate {

    private TIntSet subSet;
    private List<TIntSet> memorySet;
    private double p;
    private double baseSetSize;

    public LmSrc(double p, double baseSetSize) {
        super();
        this.subSet = new TIntHashSet();
        this.memorySet = new ArrayList<>();
        this.p = p;
        this.baseSetSize = baseSetSize;
    }

    public LmSrc(LmSrc other) {
        super(other);
        this.subSet = new TIntHashSet(other.subSet);
        this.memorySet = new ArrayList<>();
        for (TIntSet set : other.memorySet) {
            this.memorySet.add(new TIntHashSet(set));
        }
        this.p = other.p;
        this.baseSetSize = other.baseSetSize;
    }

    @Override
    public double calculateCoefficient(TIntArrayList visitedVertices, int machineIndex) {
        double coeff = 0.0;
        double state = 0.0;
        if (intersection(subSet, visitedVertices).isEmpty()) {
            return coeff;
        }
        for (int i = 0; i < visitedVertices.size(); i++) {
            int job = visitedVertices.get(i);
            if (!this.memorySet.get(machineIndex).contains(job)) {
                state = 0.0;
            } else if (subSet.contains(job)) {
                state += p;
                if (state >= 1.0) {
                    coeff += 1.0;
                    state -= 1.0;
                }
            }
        }
        return coeff;
    }

    @Override
    public LmSrc copy() {
        return new LmSrc(this);
    }
}

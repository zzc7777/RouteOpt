package org.zzc.bpcsolver.algorithm.cutting;

import gnu.trove.list.array.TIntArrayList;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Getter
@Setter
public abstract class CutTemplate {

    protected int id;
    protected List<List<Double>> coefficient;
    protected double constant;
    protected double violation;

    protected CutTemplate() {
        this.id = -1;
        this.coefficient = new ArrayList<>();
        this.constant = 0.0;
        this.violation = 0.0;
    }

    protected CutTemplate(CutTemplate other) {
        this.id = other.id;
        this.constant = other.constant;
        this.violation = other.violation;
        this.coefficient = new ArrayList<>();
        if (other.coefficient != null) {
            for (List<Double> inner : other.coefficient) {
                this.coefficient.add(inner == null ? null : new ArrayList<>(inner));
            }
        }
    }

    public abstract double calculateCoefficient(TIntArrayList visitedVertices, int machineIndex);

    public abstract CutTemplate copy();

    @Override
    public boolean equals(Object obj) {
        if (obj == null || getClass() != obj.getClass()) return false;
        CutTemplate that = (CutTemplate) obj;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

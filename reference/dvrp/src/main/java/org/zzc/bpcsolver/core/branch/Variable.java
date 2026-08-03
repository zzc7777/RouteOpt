package org.zzc.bpcsolver.core.branch;

import lombok.Getter;
import lombok.Setter;

import java.util.Arrays;
import java.util.Objects;

@Getter
@Setter
public class Variable {

    private String type;
    private int[] indexes;
    private double value;
    private double pseudoCost;
    private double nonIntegrability;

    public Variable(String type, int[] indexes, double value) {
        this.type = type;
        this.indexes = indexes.clone();
        this.value = value;
        this.pseudoCost = 0.0;
        this.nonIntegrability = Math.abs(this.value - Math.round(this.value));
    }

    public Variable(Variable other) {
        this.type = other.type;
        this.indexes = other.indexes.clone();
        this.value = other.value;
        this.pseudoCost = other.pseudoCost;
        this.nonIntegrability = other.nonIntegrability;
    }

    @Override
    public String toString() {
        return String.format("%s[%s] = %.6f", type, Arrays.toString(indexes), value);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Variable variable = (Variable) obj;
        return Objects.equals(type, variable.type) && Arrays.equals(indexes, variable.indexes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, Arrays.hashCode(indexes));
    }
}

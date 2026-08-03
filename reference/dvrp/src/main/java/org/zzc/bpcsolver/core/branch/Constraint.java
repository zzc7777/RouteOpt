package org.zzc.bpcsolver.core.branch;

import gnu.trove.list.array.TIntArrayList;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class Constraint {

    private String type;
    private Variable variable;
    private TIntArrayList coeff_1;
    private List<TIntArrayList> coeff_2;
    private int rightSideValue;

    public Constraint() {
    }

    public Constraint(Constraint constraint) {
        this.type = constraint.type;
        this.rightSideValue = constraint.rightSideValue;
        this.variable = constraint.variable == null ? null : new Variable(constraint.variable);
        this.coeff_1 = constraint.coeff_1 == null ? new TIntArrayList() : new TIntArrayList(constraint.coeff_1);
        this.coeff_2 = new ArrayList<>();
        if (constraint.coeff_2 != null) {
            for (TIntArrayList list : constraint.coeff_2) {
                this.coeff_2.add(list == null ? new TIntArrayList() : new TIntArrayList(list));
            }
        }
    }
}

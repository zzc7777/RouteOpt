package org.zzc.bpcsolver.core.graph;

import gnu.trove.list.array.TIntArrayList;
import org.zzc.bpcsolver.core.template.CycleTemplate;

public class Cycle extends CycleTemplate {

    public Cycle(int index, TIntArrayList vertices, int startVertex) {
        super(index, vertices, startVertex);
    }

    public Cycle(CycleTemplate other) {
        super(other);
    }

    @Override
    public Cycle copy() {
        return new Cycle(this);
    }
}

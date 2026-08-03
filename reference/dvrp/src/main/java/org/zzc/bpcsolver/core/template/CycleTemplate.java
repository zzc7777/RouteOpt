package org.zzc.bpcsolver.core.template;

import gnu.trove.list.array.TIntArrayList;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public abstract class CycleTemplate {

    protected int index;
    protected TIntArrayList vertices;
    protected int startVertex;


    public CycleTemplate(int index, TIntArrayList vertices, int startVertex) {
        this.index = index;
        this.vertices = vertices;
        this.startVertex = startVertex;
    }

    public CycleTemplate(CycleTemplate other) {
        this.index = other.index;
        this.vertices = new TIntArrayList(other.vertices);
        this.startVertex = other.startVertex;
    }

    public int getLength() {
        return vertices.size() - 1;
    }

    public boolean containsVertex(int vertex) {
        return vertices.subList(0, vertices.size() - 1).contains(vertex);
    }


    public abstract CycleTemplate copy();
}

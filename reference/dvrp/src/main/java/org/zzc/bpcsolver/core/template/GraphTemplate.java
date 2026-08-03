package org.zzc.bpcsolver.core.template;

import lombok.Getter;
import org.zzc.bpcsolver.algorithm.config.Configuration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Getter
public abstract class GraphTemplate<V extends VertexTemplate, A extends ArcTemplate> {

    protected int id;
    protected String type;
    protected int numVertices;
    protected List<V> vertices;
    protected List<A>[] outgoingArcs;

    protected GraphTemplate() {
    }

    @SuppressWarnings("unchecked")
    protected GraphTemplate(int id, String type, int numVertices) {
        this.id = id;
        this.type = type;
        this.numVertices = numVertices;
        this.vertices = new ArrayList<>(Collections.nCopies(numVertices, null));
        this.outgoingArcs = new List[numVertices];
        for (int i = 0; i < numVertices; i++) {
            this.outgoingArcs[i] = new ArrayList<>();
        }
    }

    @SuppressWarnings("unchecked")
    protected GraphTemplate(GraphTemplate<V, A> other) {
        this.id = other.id;
        this.type = other.type;
        this.numVertices = other.numVertices;
        this.vertices = new ArrayList<>(Collections.nCopies(numVertices, null));
        for (int i = 0; i < other.numVertices; i++) {
            if (other.vertices.get(i) != null) {
                V newVertex = (V) other.vertices.get(i).copy();
                addVertex(newVertex);
            }
        }
        this.outgoingArcs = new List[numVertices];
        for (int i = 0; i < numVertices; i++) {
            this.outgoingArcs[i] = new ArrayList<>();
        }
        for (int i = 0; i < other.numVertices; i++) {
            if (other.outgoingArcs[i] != null) {
                for (A arc : other.outgoingArcs[i]) {
                    A newArc = (A) arc.copy();
                    addArc(newArc);
                }
            }
        }
    }

    public abstract GraphTemplate<V, A> copy();

    public final void addVertex(V vertex) {
        vertices.set(vertex.getIndex(), vertex);
    }

    public void removeVertex(int vertexIndex) {
        vertices.set(vertexIndex, null);
        outgoingArcs[vertexIndex].clear();
        for (int i = 0; i < numVertices; i++) {
            for (int a = 0; a < outgoingArcs[i].size(); a++) {
                A arc = outgoingArcs[i].get(a);
                if (arc.getHeadIndex() == vertexIndex) {
                    outgoingArcs[i].remove(a);
                    a--;
                }
            }
        }
    }

    public final void addArc(A arc) {
        outgoingArcs[arc.getTailIndex()].add(arc);
    }

    public final V getVertex(int index) {
        return vertices.get(index);
    }

    public final List<A> getOutgoingArcs(int index) {
        return Collections.unmodifiableList(outgoingArcs[index]);
    }

    public void updateState4Arc(List<int[]> nonImprovingArcs, Configuration config) {
        // Placeholder hook copied from luckydragon trunk expectations.
    }

    public abstract void clearLabels();

    public abstract boolean isFeasibleArc(int tailIndex, int headIndex);
}

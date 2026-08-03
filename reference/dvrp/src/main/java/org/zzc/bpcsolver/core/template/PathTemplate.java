package org.zzc.bpcsolver.core.template;

import lombok.Getter;
import lombok.Setter;
import gnu.trove.list.array.TIntArrayList;
import org.zzc.bpcsolver.util.ArrayOperation;

import java.util.*;

@Getter
@Setter
public abstract class PathTemplate<R extends CycleTemplate> {
    protected int id;
    protected int index;
    protected TIntArrayList visitedVertices;
    protected int[] visitedVector;
    protected double cost;

    protected PathTemplate(int id, int index, int numVertices) {
        this.id = id;
        this.index = index;
        this.visitedVertices = new TIntArrayList();
        this.visitedVector = new int[numVertices];
        this.cost = 0.0;
    }

    protected PathTemplate(int id, int index, TIntArrayList visitedVertices, int[] visitedVector, double cost) {
        this.id = id;
        this.index = index;
        this.visitedVertices = new TIntArrayList(visitedVertices);
        this.visitedVector = ArrayOperation.deepCopyIntArray(visitedVector);
        this.cost = cost;
    }

    protected PathTemplate(PathTemplate<R> other) {
        this.id = other.id;
        this.index = other.index;
        this.visitedVertices = new TIntArrayList(other.visitedVertices);
        this.visitedVector = ArrayOperation.deepCopyIntArray(other.visitedVector);
        this.cost = other.cost;
    }

    public List<int[]> getArcs() {
        List<int[]> arcs = new java.util.ArrayList<>();
        for (int i = 0; i < visitedVertices.size() - 1; i++) {
            int[] arc = new int[2];
            arc[0] = visitedVertices.get(i);
            arc[1] = visitedVertices.get(i + 1);
            arcs.add(arc);
        }
        return arcs;
    }

    public boolean hasCycle() {
        Set<Integer> visited = new HashSet<>();
        for (int i = 0; i < visitedVertices.size(); i++) {
            int vertex = visitedVertices.get(i);
            if (visited.contains(vertex)) {
                return true;
            }
            visited.add(vertex);
        }
        return false;
    }

    public void removeCycles() {
        TIntArrayList vertices = new TIntArrayList();
        for (int i = 0; i < visitedVertices.size(); i++) {
            int vertex = visitedVertices.get(i);
            if (!vertices.contains(vertex)) {
                vertices.add(vertex);
            }
        }
        visitedVertices = new TIntArrayList(vertices);
    }

    public TIntArrayList obtainVertices(TIntArrayList visitedVertices, int start, int end) {
        TIntArrayList vertices = new TIntArrayList();
        for (int i = start; i <= end; i++) {
            vertices.add(visitedVertices.get(i));
        }
        return vertices;
    }

    public List<R> findAllCycles(){
        List<R> cycles = new ArrayList<>();
        Map<Integer, Integer> firstOccurrence = new HashMap<>();
        for (int i = 0; i < this.visitedVertices.size(); i++) {
            int vertex = this.visitedVertices.get(i);
            if (firstOccurrence.containsKey(vertex)) {
                int startIndex = firstOccurrence.get(vertex);
                TIntArrayList cycleVertices = obtainVertices(this.visitedVertices, startIndex, i);
                cycles.add(createCycle(this.index, cycleVertices, this.visitedVertices.get(startIndex)));
            } else {
                firstOccurrence.put(vertex, i);
            }
        }
        return cycles;
    }

    public abstract PathTemplate<R> copy();

    protected abstract R createCycle(int index, TIntArrayList vertices, int startVertex);

    protected abstract boolean containsCycle(R cycle);

    @Override
    public boolean equals(Object obj) {
        if (obj == null || getClass() != obj.getClass()) return false;
        PathTemplate<?> that = (PathTemplate<?>) obj;
        return Objects.equals(visitedVertices, that.visitedVertices);
    }

    @Override
    public int hashCode() {
        return Objects.hash(visitedVertices);
    }

    @Override
    public String toString() {
        return String.format("%s{id=%d, vertices=%s}",
                getClass().getSimpleName(), id, visitedVertices);
    }
}

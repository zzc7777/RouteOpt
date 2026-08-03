package org.zzc.bpcsolver.core.template;

import lombok.Getter;

import java.util.Objects;

@Getter
public abstract class VertexTemplate {

    protected int id;
    protected int index;

    protected VertexTemplate(int id, int index) {
        this.id = id;
        this.index = index;
    }

    protected VertexTemplate(VertexTemplate other) {
        this.id = other.id;
        this.index = other.index;
    }

    public abstract VertexTemplate copy();

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        VertexTemplate that = (VertexTemplate) obj;
        return id == that.id && index == that.index;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, index);
    }
}

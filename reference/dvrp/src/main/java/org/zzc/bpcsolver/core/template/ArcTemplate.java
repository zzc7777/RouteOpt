package org.zzc.bpcsolver.core.template;

import lombok.Getter;

import java.util.Objects;

@Getter
public abstract class ArcTemplate {

    protected int tailIndex;
    protected int headIndex;
    protected String state;

    protected ArcTemplate(int tailIndex, int headIndex, String state) {
        this.tailIndex = tailIndex;
        this.headIndex = headIndex;
        this.state = state;
    }

    protected ArcTemplate(ArcTemplate other) {
        this.tailIndex = other.tailIndex;
        this.headIndex = other.headIndex;
        this.state = other.state;
    }

    public abstract ArcTemplate copy();

    public abstract boolean isFixed();

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        ArcTemplate that = (ArcTemplate) obj;
        return tailIndex == that.tailIndex && headIndex == that.headIndex;
    }

    @Override
    public int hashCode() {
        return Objects.hash(tailIndex, headIndex);
    }
}

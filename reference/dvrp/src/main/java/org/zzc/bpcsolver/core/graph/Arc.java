package org.zzc.bpcsolver.core.graph;

import lombok.Getter;
import org.zzc.bpcsolver.core.template.ArcTemplate;

@Getter
public class Arc extends ArcTemplate {

    private double distance;
    private double travelTime;

    public Arc() {
        super(0, 0, "UNFIXED");
    }

    public Arc(int tailIndex, int headIndex, double distance, double travelTime, boolean fixed) {
        super(tailIndex, headIndex, fixed ? "FIXED" : "UNFIXED");
        this.distance = distance;
        this.travelTime = travelTime;
    }

    public Arc(Arc other) {
        super(other);
        this.distance = other.distance;
        this.travelTime = other.travelTime;
    }

    @Override
    public Arc copy() {
        return new Arc(this);
    }

    @Override
    public boolean isFixed() {
        return "FIXED".equalsIgnoreCase(state);
    }

    public void setFixed(boolean fixed) {
        this.state = fixed ? "FIXED" : "UNFIXED";
    }
}

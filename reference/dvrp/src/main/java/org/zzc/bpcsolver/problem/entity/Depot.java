package org.zzc.bpcsolver.problem.entity;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Depot {

    public enum DepotType {
        ORIGIN,
        RETURN,
        EXTERNAL
    }

    private int id;
    private double x;
    private double y;
    private double earliestTime;
    private double latestTime;
    private DepotType type;
    private int vehicleCount;
    private int remainingCapacity;

    public Depot() {
    }

    public Depot(Depot other) {
        this.id = other.id;
        this.x = other.x;
        this.y = other.y;
        this.earliestTime = other.earliestTime;
        this.latestTime = other.latestTime;
        this.type = other.type;
        this.vehicleCount = other.vehicleCount;
        this.remainingCapacity = other.remainingCapacity;
    }

    public Depot copy() {
        return new Depot(this);
    }
}

package org.zzc.bpcsolver.problem.entity;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Customer {

    private int id;
    private double x;
    private double y;
    private int demand;
    private double earliestTime;
    private double latestTime;
    private double serviceTime;
    private double callInTime;

    public Customer() {
    }

    public Customer(Customer other) {
        this.id = other.id;
        this.x = other.x;
        this.y = other.y;
        this.demand = other.demand;
        this.earliestTime = other.earliestTime;
        this.latestTime = other.latestTime;
        this.serviceTime = other.serviceTime;
        this.callInTime = other.callInTime;
    }

    public Customer copy() {
        return new Customer(this);
    }
}

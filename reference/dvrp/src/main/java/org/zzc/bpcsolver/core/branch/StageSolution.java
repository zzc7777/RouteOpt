package org.zzc.bpcsolver.core.branch;

import lombok.Getter;
import lombok.Setter;
import org.zzc.bpcsolver.core.graph.Route;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class StageSolution {

    private double objectiveValue;
    private List<Route> selectedRoutes;
    private List<Route> allRoutes;
    private double[] pricingCustomerDuals;
    private double[] startDepotConstraintDuals;
    private List<Double> cutConstraintDuals;
    private boolean integerSolution;

    public StageSolution() {
        this.selectedRoutes = new ArrayList<>();
        this.allRoutes = new ArrayList<>();
        this.pricingCustomerDuals = new double[0];
        this.startDepotConstraintDuals = new double[0];
        this.cutConstraintDuals = new ArrayList<>();
        this.integerSolution = false;
    }

    public StageSolution(StageSolution other) {
        this.objectiveValue = other.objectiveValue;
        this.selectedRoutes = new ArrayList<>();
        for (Route route : other.selectedRoutes) {
            this.selectedRoutes.add(route == null ? null : route.copy());
        }
        this.allRoutes = new ArrayList<>();
        for (Route route : other.allRoutes) {
            this.allRoutes.add(route == null ? null : route.copy());
        }
        this.pricingCustomerDuals = other.pricingCustomerDuals == null ? null : other.pricingCustomerDuals.clone();
        this.startDepotConstraintDuals = other.startDepotConstraintDuals == null ? null : other.startDepotConstraintDuals.clone();
        this.cutConstraintDuals = other.cutConstraintDuals == null ? null : new ArrayList<>(other.cutConstraintDuals);
        this.integerSolution = other.integerSolution;
    }

    public StageSolution copy() {
        return new StageSolution(this);
    }
}

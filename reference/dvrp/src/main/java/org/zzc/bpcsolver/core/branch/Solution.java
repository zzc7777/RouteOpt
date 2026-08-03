package org.zzc.bpcsolver.core.branch;

import lombok.Getter;
import lombok.Setter;
import org.zzc.bpcsolver.core.template.PathTemplate;

import java.util.*;

@Getter
@Setter
public class Solution<S extends PathTemplate> {

    private double objValue;
    private List<double[]> yks;
    private double[] dualsOfJobs;
    private double[] dualsOfMachines;
    private List<Double> dualsOfCuts;
    private List<Double> dualsOfBranches;
    private List<S> bestSchedules;

    public Solution (int numMachines, int numJobs) {
        this.objValue = 0.0;
        this.yks = new ArrayList<>();
        this.dualsOfJobs = new double[numJobs];
        this.dualsOfMachines = new double[numMachines];
        this.dualsOfCuts = new ArrayList<>();
        this.dualsOfBranches = new ArrayList<>();
        this.bestSchedules = new ArrayList<>();
    }


    public Solution(Solution<S> other) {
        Objects.requireNonNull(other, "Source solution cannot be null");
        this.objValue = other.objValue;
        this.yks = new ArrayList<>(other.yks.size());
        for (double[] yk : other.yks) {
            this.yks.add(yk != null ? yk.clone() : null);
        }
        this.dualsOfJobs = other.dualsOfJobs.clone();
        this.dualsOfMachines = other.dualsOfMachines.clone();
        this.dualsOfCuts = new ArrayList<>(other.dualsOfCuts);
        this.dualsOfBranches = new ArrayList<>(other.dualsOfBranches);
        this.bestSchedules = new ArrayList<>(other.bestSchedules.size());
        for (S schedule : other.bestSchedules) {
            @SuppressWarnings("unchecked")
            S copiedSchedule = (S) schedule.copy();
            this.bestSchedules.add(copiedSchedule);
        }
    }

    public Solution copy(){
        return new Solution<>(this);
    }

    public void calculateObjectiveValue() {
        for(S schedule : this.bestSchedules){
            this.objValue += schedule.getCost();
        }
    }
}

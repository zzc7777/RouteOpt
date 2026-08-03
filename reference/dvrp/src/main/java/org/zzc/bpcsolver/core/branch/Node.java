package org.zzc.bpcsolver.core.branch;

import lombok.Getter;
import lombok.Setter;
import org.zzc.bpcsolver.algorithm.cutting.CutTemplate;
import org.zzc.bpcsolver.core.template.GraphTemplate;
import org.zzc.bpcsolver.core.template.PathTemplate;
import org.zzc.bpcsolver.util.ArrayOperation;

import java.util.*;

import static org.zzc.bpcsolver.util.ArrayOperation.equalOne;

@Getter
@Setter

public class Node<S extends PathTemplate, C extends CutTemplate, G extends GraphTemplate> {

    private Solution<S> solution;
    private List<List<S>> usedScheduleSet;
    private List<C> usedCutSet;
    private List<Constraint> branchConstraints;
    private List<G> graphs;
    private boolean isInteger;
    private boolean isFeasible;
    private ColumnGenerationStatus columnGenerationStatus;
    private double DEFAULT_TOLERANCE  = 1e-6;

    public Node(){
        this.solution = null;
        this.usedScheduleSet = new ArrayList<>();
        this.usedCutSet = new ArrayList<>();
        this.branchConstraints = new ArrayList<>();
        this.graphs = new ArrayList<>();
        this.isInteger = false;
        this.isFeasible = true;
        this.columnGenerationStatus = ColumnGenerationStatus.NOT_STARTED;
    }

    public Node(Node<S,C,G> other){
        this.isInteger = other.isInteger;
        this.isFeasible = other.isFeasible;
        this.columnGenerationStatus = other.columnGenerationStatus == null
                ? ColumnGenerationStatus.NOT_STARTED
                : other.columnGenerationStatus;
        this.DEFAULT_TOLERANCE = other.DEFAULT_TOLERANCE;
        if (other.solution != null) {
            this.solution = new Solution<>(other.solution);
        } else {
            this.solution = null;
        }

        if (other.usedScheduleSet != null) {
            this.usedScheduleSet = new ArrayList<>();
            for (List<S> scheduleList : other.usedScheduleSet) {
                if (scheduleList != null) {
                    List<S> newScheduleList = new ArrayList<>();
                    for (S schedule : scheduleList) {
                        @SuppressWarnings("unchecked")
                        S copiedSchedule = (S) schedule.copy();
                        newScheduleList.add(copiedSchedule);
                    }
                    this.usedScheduleSet.add(newScheduleList);
                } else {
                    this.usedScheduleSet.add(null);
                }
            }
        } else {
            this.usedScheduleSet = new ArrayList<>();
        }

        if (other.usedCutSet != null) {
            this.usedCutSet = new ArrayList<>();
            for (C cut : other.usedCutSet) {
                @SuppressWarnings("unchecked")
                C copiedCut = (C) cut.copy();
                this.usedCutSet.add(copiedCut);
            }
        } else {
            this.usedCutSet = new ArrayList<>();
        }

        if (other.branchConstraints != null) {
            this.branchConstraints = new ArrayList<>();
            for (Constraint constraint : other.branchConstraints) {
                this.branchConstraints.add(new Constraint(constraint));
            }
        } else {
            this.branchConstraints = new ArrayList<>();
        }

        if (other.graphs != null) {
            this.graphs = new ArrayList<>();
            for (G graph : other.graphs) {
                @SuppressWarnings("unchecked")
                G copiedGraph = (G) graph.copy();
                this.graphs.add(copiedGraph);
            }
        } else {
            this.graphs = new ArrayList<>();
        }
    }

    public void checkIntegrality() {
        isIntegerSolution();
        if(this.isInteger){
            this.solution.setBestSchedules(new ArrayList<>());
            for(int k = 0; k < this.solution.getYks().size(); k++){
                for(int s = 0; s < this.solution.getYks().get(k).length; s++){
                    if(equalOne(this.solution.getYks().get(k)[s])){
                        this.solution.getBestSchedules().add(this.usedScheduleSet.get(k).get(s));
                    }
                }
            }

        }
    }

    public void isIntegerSolution() {
        List<double[]> yks = this.solution.getYks();
        this.isInteger =  yks.stream()
                .filter(Objects::nonNull)
                .flatMapToDouble(Arrays::stream)
                .allMatch(ArrayOperation::isIntegerValue);
    }

    public Node<S,C,G> copy(){return new Node<>(this);}
}

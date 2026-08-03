package org.zzc.bpcsolver.algorithm.branching;

import gnu.trove.list.array.TIntArrayList;
import org.zzc.bpcsolver.algorithm.cutting.CutTemplate;
import org.zzc.bpcsolver.core.branch.Constraint;
import org.zzc.bpcsolver.core.branch.Node;
import org.zzc.bpcsolver.core.branch.Solution;
import org.zzc.bpcsolver.core.branch.Variable;
import org.zzc.bpcsolver.core.template.GraphTemplate;
import org.zzc.bpcsolver.core.template.PathTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class XkijBranching<S extends PathTemplate, C extends CutTemplate, G extends GraphTemplate> implements BranchingRule<S, C, G> {

    private static final double EPSILON = 1e-6;
    private List<Map<ArcKey, Double>> xkij;

    private void calculateXkij(int numMachines, int numJobs, Node<S, C, G> node) {
        this.xkij = new ArrayList<>();
        for (int k = 0; k < numMachines; k++) {
            this.xkij.add(new TreeMap<>());
        }
        Solution<S> solution = node.getSolution();
        List<List<S>> usedScheduleSet = node.getUsedScheduleSet();
        for (int k = 0; k < numMachines; k++) {
            List<S> schedules = usedScheduleSet.get(k);
            double[] ys = solution.getYks().get(k);
            for (int s = 0; s < schedules.size(); s++) {
                S schedule = schedules.get(s);
                TIntArrayList visitedVertices = schedule.getVisitedVertices();
                double ysValue = ys[s];
                for (int i = 0; i < visitedVertices.size() - 1; i++) {
                    if (!isCustomerIncidentArcPosition(i, visitedVertices.size())) {
                        continue;
                    }
                    ArcKey arcKey = new ArcKey(visitedVertices.get(i), visitedVertices.get(i + 1));
                    Map<ArcKey, Double> machineArcs = this.xkij.get(k);
                    machineArcs.put(arcKey, machineArcs.getOrDefault(arcKey, 0.0) + ysValue);
                }
            }
        }
    }

    @Override
    public List<Variable> getFractionalVariables(int numMachines, int numJobs, Node<S, C, G> node) {
        calculateXkij(numMachines, numJobs, node);
        List<Variable> fractionalVariables = new ArrayList<>();
        for (int k = 0; k < numMachines; k++) {
            for (Map.Entry<ArcKey, Double> entry : this.xkij.get(k).entrySet()) {
                ArcKey arcKey = entry.getKey();
                double value = entry.getValue();
                if (arcKey.tail != arcKey.head && !isIntegerCount(value)) {
                    int[] indexes = {k, arcKey.tail, arcKey.head};
                    fractionalVariables.add(new Variable("xkij", indexes, value));
                }
            }
        }
        return fractionalVariables;
    }

    @Override
    public Constraint createBranchingConstraint(Node<S, C, G> node, int numMachines, Variable branchingVar, int flag) {
        Constraint constraint = new Constraint();
        constraint.setVariable(branchingVar);
        TIntArrayList coeff_1 = new TIntArrayList();
        for (int k = 0; k < numMachines; k++) {
            coeff_1.add(k == branchingVar.getIndexes()[0] ? 1 : 0);
        }
        constraint.setCoeff_1(coeff_1);
        List<TIntArrayList> coeff_2 = new ArrayList<>();
        for (int k = 0; k < numMachines; k++) {
            TIntArrayList coeff_2_k = new TIntArrayList();
            List<S> schedules = node.getUsedScheduleSet().get(k);
            for (S schedule : schedules) {
                int visitedTimes = 0;
                if (constraint.getCoeff_1().get(k) == 1) {
                    visitedTimes = calculateVariableVisitedTimes(schedule, branchingVar);
                }
                coeff_2_k.add(visitedTimes);
            }
            coeff_2.add(coeff_2_k);
        }
        constraint.setCoeff_2(coeff_2);
        int rightSideValue = flag == 0 ? -1 : 1;
        switch (flag) {
            case 0:
                constraint.setType("Left");
                for (int k = 0; k < numMachines; k++) {
                    constraint.getCoeff_1().set(k, -constraint.getCoeff_1().get(k));
                }
                rightSideValue = (int) Math.floor(branchingVar.getValue()) * rightSideValue;
                constraint.setRightSideValue(rightSideValue);
                break;
            case 1:
                constraint.setType("Right");
                rightSideValue = (int) Math.ceil(branchingVar.getValue()) * rightSideValue;
                constraint.setRightSideValue(rightSideValue);
                break;
            default:
                throw new IllegalArgumentException("Unknown branching flag: " + flag);
        }
        return constraint;
    }

    @Override
    public int calculateVariableVisitedTimes(S path, Variable branchingVar) {
        int visitedTimes = 0;
        for (int i = 0; i < path.getVisitedVertices().size() - 1; i++) {
            if (path.getVisitedVertices().get(i) == branchingVar.getIndexes()[1]
                    && path.getVisitedVertices().get(i + 1) == branchingVar.getIndexes()[2]) {
                visitedTimes++;
            }
        }
        return visitedTimes;
    }

    @Override
    public double updateReducedCost(int machineIndex, int tail, int head, Constraint branchConstraint, double dualOfBranch, double reducedCost) {
        int branchTail = branchConstraint.getVariable().getIndexes()[1];
        int branchHead = branchConstraint.getVariable().getIndexes()[2];
        if (branchTail == tail && branchHead == head) {
            reducedCost = reducedCost - branchConstraint.getCoeff_1().get(machineIndex) * dualOfBranch;
        }
        return reducedCost;
    }

    @Override
    public String getStrategyName() {
        return "xkij branching";
    }

    private static final class ArcKey implements Comparable<ArcKey> {

        private final int tail;
        private final int head;

        private ArcKey(int tail, int head) {
            this.tail = tail;
            this.head = head;
        }

        @Override
        public int compareTo(ArcKey other) {
            int tailCompare = Integer.compare(this.tail, other.tail);
            if (tailCompare != 0) {
                return tailCompare;
            }
            return Integer.compare(this.head, other.head);
        }
    }

    private boolean isCustomerIncidentArcPosition(int tailPosition, int pathSize) {
        int headPosition = tailPosition + 1;
        return (tailPosition > 0 && tailPosition < pathSize - 1)
                || (headPosition > 0 && headPosition < pathSize - 1);
    }

    private boolean isIntegerCount(double value) {
        return Math.abs(value - Math.rint(value)) <= EPSILON;
    }
}

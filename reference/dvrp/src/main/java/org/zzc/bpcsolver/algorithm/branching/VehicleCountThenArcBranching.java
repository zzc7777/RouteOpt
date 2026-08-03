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

public class VehicleCountThenArcBranching<S extends PathTemplate, C extends CutTemplate, G extends GraphTemplate>
        implements BranchingRule<S, C, G> {

    private static final double EPSILON = 1e-6;
    public static final String VEHICLE_COUNT_TYPE = "vehicleCount";

    private final BranchingRule<S, C, G> arcBranchingRule;

    public VehicleCountThenArcBranching(BranchingRule<S, C, G> arcBranchingRule) {
        if (arcBranchingRule == null) {
            throw new IllegalArgumentException("arcBranchingRule must not be null.");
        }
        this.arcBranchingRule = arcBranchingRule;
    }

    @Override
    public List<Variable> getFractionalVariables(int numMachines, int numJobs, Node<S, C, G> node) {
        double vehicleCount = calculateVehicleCount(node);
        if (!isIntegerCount(vehicleCount)) {
            List<Variable> variables = new ArrayList<>();
            variables.add(new Variable(VEHICLE_COUNT_TYPE, new int[0], vehicleCount));
            return variables;
        }
        return arcBranchingRule.getFractionalVariables(numMachines, numJobs, node);
    }

    @Override
    public Constraint createBranchingConstraint(Node<S, C, G> node, int numMachines, Variable branchingVar, int flag) {
        if (!isVehicleCountVariable(branchingVar)) {
            return arcBranchingRule.createBranchingConstraint(node, numMachines, branchingVar, flag);
        }

        Constraint constraint = new Constraint();
        constraint.setVariable(branchingVar);
        TIntArrayList coeff1 = new TIntArrayList();
        for (int k = 0; k < numMachines; k++) {
            coeff1.add(1);
        }
        constraint.setCoeff_1(coeff1);

        List<TIntArrayList> coeff2 = new ArrayList<>();
        for (int k = 0; k < numMachines; k++) {
            TIntArrayList coeff2K = new TIntArrayList();
            List<S> schedules = node.getUsedScheduleSet().get(k);
            for (int s = 0; s < schedules.size(); s++) {
                coeff2K.add(1);
            }
            coeff2.add(coeff2K);
        }
        constraint.setCoeff_2(coeff2);

        switch (flag) {
            case 0:
                constraint.setType("Left");
                for (int k = 0; k < numMachines; k++) {
                    constraint.getCoeff_1().set(k, -constraint.getCoeff_1().get(k));
                }
                constraint.setRightSideValue(-(int) Math.floor(branchingVar.getValue()));
                break;
            case 1:
                constraint.setType("Right");
                constraint.setRightSideValue((int) Math.ceil(branchingVar.getValue()));
                break;
            default:
                throw new IllegalArgumentException("Unknown branching flag: " + flag);
        }
        return constraint;
    }

    @Override
    public int calculateVariableVisitedTimes(S path, Variable branchingVar) {
        if (isVehicleCountVariable(branchingVar)) {
            return 1;
        }
        return arcBranchingRule.calculateVariableVisitedTimes(path, branchingVar);
    }

    @Override
    public double updateInitialReducedCost(int machineIndex, Constraint branchConstraint,
                                           double dualOfBranch, double reducedCost) {
        if (branchConstraint == null || !isVehicleCountVariable(branchConstraint.getVariable())) {
            return arcBranchingRule.updateInitialReducedCost(machineIndex, branchConstraint, dualOfBranch, reducedCost);
        }
        if (branchConstraint.getCoeff_1() == null
                || machineIndex < 0
                || machineIndex >= branchConstraint.getCoeff_1().size()) {
            return reducedCost;
        }
        return reducedCost - branchConstraint.getCoeff_1().get(machineIndex) * dualOfBranch;
    }

    @Override
    public double updateReducedCost(int machineIndex, int tail, int head, Constraint branchConstraint,
                                    double dualOfBranch, double reducedCost) {
        if (branchConstraint != null && isVehicleCountVariable(branchConstraint.getVariable())) {
            return reducedCost;
        }
        return arcBranchingRule.updateReducedCost(machineIndex, tail, head, branchConstraint, dualOfBranch, reducedCost);
    }

    @Override
    public String getStrategyName() {
        return "vehicle-count then " + arcBranchingRule.getStrategyName();
    }

    private double calculateVehicleCount(Node<S, C, G> node) {
        if (node == null || node.getSolution() == null) {
            return 0.0;
        }
        Solution<S> solution = node.getSolution();
        if (solution.getYks() == null) {
            return 0.0;
        }
        double vehicleCount = 0.0;
        for (double[] machineValues : solution.getYks()) {
            if (machineValues == null) {
                continue;
            }
            for (double value : machineValues) {
                vehicleCount += value;
            }
        }
        return vehicleCount;
    }

    private boolean isIntegerCount(double value) {
        return Math.abs(value - Math.rint(value)) <= EPSILON;
    }

    private boolean isVehicleCountVariable(Variable variable) {
        return variable != null && VEHICLE_COUNT_TYPE.equals(variable.getType());
    }
}

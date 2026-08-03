package org.zzc.bpcsolver.algorithm.branching;

import org.zzc.bpcsolver.algorithm.cutting.CutTemplate;
import org.zzc.bpcsolver.core.branch.Constraint;
import org.zzc.bpcsolver.core.branch.Node;
import org.zzc.bpcsolver.core.branch.Variable;
import org.zzc.bpcsolver.core.template.GraphTemplate;
import org.zzc.bpcsolver.core.template.PathTemplate;

import java.util.List;

public interface BranchingRule<S extends PathTemplate, C extends CutTemplate, G extends GraphTemplate> {

    List<Variable> getFractionalVariables(int numMachines, int numJobs, Node<S, C, G> node);

    Constraint createBranchingConstraint(Node<S, C, G> node, int numMachines, Variable branchingVar, int flag);

    int calculateVariableVisitedTimes(S path, Variable branchingVar);

    default double updateInitialReducedCost(int machineIndex, Constraint branchConstraint,
                                            double dualOfBranch, double reducedCost) {
        return reducedCost;
    }

    double updateReducedCost(int machineIndex, int tail, int head, Constraint branchConstraint,
                             double dualOfBranch, double reducedCost);

    String getStrategyName();
}

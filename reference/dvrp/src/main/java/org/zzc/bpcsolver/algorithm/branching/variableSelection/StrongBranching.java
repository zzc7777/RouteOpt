package org.zzc.bpcsolver.algorithm.branching.variableSelection;

import org.zzc.bpcsolver.algorithm.DeterministicOrdering;
import org.zzc.bpcsolver.algorithm.branching.BranchingRule;
import org.zzc.bpcsolver.algorithm.config.Configuration;
import org.zzc.bpcsolver.algorithm.cutting.CutTemplate;
import org.zzc.bpcsolver.algorithm.logger.BPCLogger;
import org.zzc.bpcsolver.algorithm.pricing.HeuristicPricing;
import org.zzc.bpcsolver.core.branch.Node;
import org.zzc.bpcsolver.core.branch.Variable;
import org.zzc.bpcsolver.core.template.GraphTemplate;
import org.zzc.bpcsolver.core.template.PathTemplate;
import org.zzc.bpcsolver.problem.template.DynamicInstanceTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class StrongBranching<T extends DynamicInstanceTemplate, S extends PathTemplate, C extends CutTemplate, G extends GraphTemplate>
        implements VariableSelectionStrategy<T, S, C, G> {

    private static final double EPSILON = 1e-6;
    private static final double INFEASIBLE_CHILD_SCORE = 1e9;

    private Configuration config;
    private final List<Variable> historyCandidateVariables = new ArrayList<>();
    private final Map<Variable, List<Node<S, C, G>>> candidateChildNodes = new HashMap<>();
    private StrongBranchingEvaluator<S, C, G> evaluator;

    @Override
    public void Initialize(T instance, Configuration config, HeuristicPricing<S, G, C> heuristicPricing,
                           BranchingRule<S, C, G> branchingRule) {
        this.config = config;
        this.historyCandidateVariables.clear();
        this.candidateChildNodes.clear();
    }

    @Override
    public void setStrongBranchingEvaluator(StrongBranchingEvaluator<S, C, G> evaluator) {
        this.evaluator = evaluator;
    }

    @Override
    public Variable selectBranchingVariable(List<Variable> fractionalVars, Node<S, C, G> node, BPCLogger logger) {
        if (fractionalVars == null || fractionalVars.isEmpty()) {
            return null;
        }
        List<Variable> sortedFractionalVars = fractionalVars.stream()
                .filter(Objects::nonNull)
                .sorted(DeterministicOrdering.variableComparator())
                .collect(Collectors.toList());
        if (sortedFractionalVars.isEmpty()) {
            return null;
        }
        if (this.evaluator == null || node == null || node.getSolution() == null) {
            return sortedFractionalVars.get(0);
        }

        List<Variable> branchingVars = branchingPhase1(sortedFractionalVars);
        branchingVars = branchingPhase2(branchingVars, node);
        branchingVars = branchingPhase3(branchingVars, node, logger);
        return branchingVars.isEmpty() ? sortedFractionalVars.get(0) : branchingVars.get(0);
    }

    private List<Variable> branchingPhase1(List<Variable> fractionalVars) {
        int limit1 = getNumCandidateVariables1();
        List<Variable> branchingVars = new ArrayList<>();
        if (this.historyCandidateVariables.isEmpty()) {
            branchingVars.addAll(fractionalVars.stream().limit(limit1).collect(Collectors.toList()));
            return branchingVars;
        }

        int currentLimit = Math.max(1, limit1 / 2);
        branchingVars.addAll(fractionalVars.stream().limit(currentLimit).collect(Collectors.toList()));

        int historyLimit = Math.max(0, limit1 - branchingVars.size());
        int usedHistory = 0;
        List<Variable> retainedHistory = new ArrayList<>();
        for (Variable historicalVariable : this.historyCandidateVariables) {
            Variable currentVariable = findCurrentFractionalVariable(fractionalVars, historicalVariable);
            if (currentVariable == null) {
                continue;
            }
            if (usedHistory < historyLimit && !branchingVars.contains(currentVariable)) {
                branchingVars.add(currentVariable);
                usedHistory++;
            } else {
                retainedHistory.add(currentVariable);
            }
        }
        this.historyCandidateVariables.clear();
        this.historyCandidateVariables.addAll(retainedHistory);

        if (branchingVars.isEmpty()) {
            branchingVars.addAll(fractionalVars.stream().limit(limit1).collect(Collectors.toList()));
        }
        return branchingVars;
    }

    private List<Variable> branchingPhase2(List<Variable> candidateVars, Node<S, C, G> node) {
        this.candidateChildNodes.clear();
        double parentObjective = getObjectiveValue(node);
        for (Variable candidateVar : candidateVars) {
            List<Node<S, C, G>> childNodes = new ArrayList<>();
            double pseudoCost = 1.0;
            for (int flag = 0; flag < 2; flag++) {
                Node<S, C, G> childNode = this.evaluator.solveRestrictedMasterChild(node, candidateVar, flag);
                if (childNode != null) {
                    childNodes.add(childNode);
                }
                pseudoCost *= calculateBoundImprovement(parentObjective, childNode);
            }
            candidateVar.setPseudoCost(pseudoCost);
            this.candidateChildNodes.put(new Variable(candidateVar), childNodes);
        }

        List<Variable> sortedCandidates = sortByPseudoCost(candidateVars);
        rememberHistoryCandidates(sortedCandidates);
        int limit2 = getNumCandidateVariables2();
        if (sortedCandidates.size() > limit2) {
            return new ArrayList<>(sortedCandidates.subList(0, limit2));
        }
        return sortedCandidates;
    }

    private List<Variable> branchingPhase3(List<Variable> candidateVars, Node<S, C, G> node, BPCLogger logger) {
        double parentObjective = getObjectiveValue(node);
        List<Variable> branchingVars = new ArrayList<>();
        try {
            for (Variable candidateVar : candidateVars) {
                List<Node<S, C, G>> childNodes = getCandidateChildNodes(candidateVar);
                if (childNodes.isEmpty()) {
                    branchingVars.add(candidateVar);
                    continue;
                }

                double pseudoCost = 1.0;
                for (Node<S, C, G> childNode : childNodes) {
                    Node<S, C, G> pricedChildNode = childNode != null && childNode.isFeasible()
                            ? this.evaluator.runHeuristicColumnGeneration(childNode, logger)
                            : childNode;
                    pseudoCost *= calculateBoundImprovement(parentObjective, pricedChildNode);
                }
                candidateVar.setPseudoCost(pseudoCost);
                branchingVars.add(candidateVar);
            }
        } finally {
            this.candidateChildNodes.clear();
        }
        return sortByPseudoCost(branchingVars);
    }

    private List<Node<S, C, G>> getCandidateChildNodes(Variable candidateVar) {
        for (Map.Entry<Variable, List<Node<S, C, G>>> entry : this.candidateChildNodes.entrySet()) {
            if (entry.getKey().equals(candidateVar)) {
                return entry.getValue();
            }
        }
        return new ArrayList<>();
    }

    private double calculateBoundImprovement(double parentObjective, Node<S, C, G> childNode) {
        if (childNode == null || !childNode.isFeasible()
                || childNode.getSolution() == null
                || !Double.isFinite(childNode.getSolution().getObjValue())) {
            return INFEASIBLE_CHILD_SCORE;
        }
        double improvement = childNode.getSolution().getObjValue() - parentObjective;
        return Math.max(EPSILON, improvement);
    }

    private double getObjectiveValue(Node<S, C, G> node) {
        if (node == null || node.getSolution() == null) {
            return 0.0;
        }
        return node.getSolution().getObjValue();
    }

    private Variable findCurrentFractionalVariable(List<Variable> fractionalVars, Variable target) {
        if (target == null) {
            return null;
        }
        for (Variable variable : fractionalVars) {
            if (target.equals(variable)) {
                return variable;
            }
        }
        return null;
    }

    private void rememberHistoryCandidates(List<Variable> sortedCandidates) {
        int limit1 = getNumCandidateVariables1();
        int limit2 = getNumCandidateVariables2();
        for (int i = limit2; i < sortedCandidates.size() && this.historyCandidateVariables.size() < limit1; i++) {
            Variable variable = sortedCandidates.get(i);
            if (!this.historyCandidateVariables.contains(variable)) {
                this.historyCandidateVariables.add(new Variable(variable));
            }
        }
    }

    private List<Variable> sortByPseudoCost(List<Variable> variables) {
        return variables.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingDouble(Variable::getPseudoCost).reversed()
                        .thenComparing(DeterministicOrdering.variableComparator()))
                .collect(Collectors.toList());
    }

    private int getNumCandidateVariables1() {
        if (this.config == null || this.config.getBranching() == null
                || this.config.getBranching().getNumCandidateVariables1() <= 0) {
            return Integer.MAX_VALUE;
        }
        return this.config.getBranching().getNumCandidateVariables1();
    }

    private int getNumCandidateVariables2() {
        if (this.config == null || this.config.getBranching() == null
                || this.config.getBranching().getNumCandidateVariables2() <= 0) {
            return Integer.MAX_VALUE;
        }
        return this.config.getBranching().getNumCandidateVariables2();
    }

    @Override
    public String getStrategyName() {
        return "strong branching";
    }
}

package org.zzc.bpcsolver.algorithm;

import org.zzc.bpcsolver.algorithm.cutting.CutTemplate;
import org.zzc.bpcsolver.algorithm.pricing.Label;
import org.zzc.bpcsolver.core.branch.Constraint;
import org.zzc.bpcsolver.core.branch.Node;
import org.zzc.bpcsolver.core.branch.Variable;
import org.zzc.bpcsolver.core.graph.Route;
import org.zzc.bpcsolver.core.template.GraphTemplate;
import org.zzc.bpcsolver.core.template.PathTemplate;

import java.util.Comparator;
import java.util.List;

public final class DeterministicOrdering {

    private DeterministicOrdering() {
    }

    public static Comparator<Route> routeComparator() {
        return Comparator.comparingDouble(Route::getReducedCost)
                .thenComparingDouble(Route::getCost)
                .thenComparingInt(Route::getStartDepotId)
                .thenComparing(DeterministicOrdering::pathSignature)
                .thenComparingInt(Route::getIndex);
    }

    public static Comparator<Label> labelComparator() {
        return Comparator.comparingDouble(Label::getReducedCost)
                .thenComparingDouble(Label::getCost)
                .thenComparingInt(Label::getId)
                .thenComparing(DeterministicOrdering::pathSignature)
                .thenComparingDouble(Label::getStartServiceTime)
                .thenComparingInt(Label::getIndex);
    }

    public static Comparator<Variable> variableComparator() {
        return Comparator.comparingDouble(Variable::getNonIntegrability).reversed()
                .thenComparing(Variable::getType, Comparator.nullsLast(String::compareTo))
                .thenComparing(DeterministicOrdering::variableIndexSignature)
                .thenComparingDouble(Variable::getValue);
    }

    public static <S extends PathTemplate, C extends CutTemplate, G extends GraphTemplate> Comparator<Node<S, C, G>> nodeComparator() {
        return Comparator.<Node<S, C, G>>comparingDouble(node -> node.getSolution().getObjValue())
                .thenComparingInt(DeterministicOrdering::countBranchConstraints)
                .thenComparing(DeterministicOrdering::branchConstraintSignature)
                .thenComparingInt(DeterministicOrdering::countColumns);
    }

    public static String pathSignature(PathTemplate<?> path) {
        if (path == null || path.getVisitedVertices() == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        int[] nodes = path.getVisitedVertices().toArray();
        for (int i = 0; i < nodes.length; i++) {
            if (i > 0) {
                builder.append('-');
            }
            builder.append(nodes[i]);
        }
        return builder.toString();
    }

    private static String variableIndexSignature(Variable variable) {
        if (variable == null || variable.getIndexes() == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        int[] indexes = variable.getIndexes();
        for (int i = 0; i < indexes.length; i++) {
            if (i > 0) {
                builder.append('-');
            }
            builder.append(indexes[i]);
        }
        return builder.toString();
    }

    private static int countBranchConstraints(Node<?, ?, ?> node) {
        return node.getBranchConstraints() == null ? 0 : node.getBranchConstraints().size();
    }

    private static String branchConstraintSignature(Node<?, ?, ?> node) {
        if (node.getBranchConstraints() == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (Constraint constraint : node.getBranchConstraints()) {
            if (constraint == null) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('|');
            }
            builder.append(constraint.getType()).append(':');
            builder.append(variableIndexSignature(constraint.getVariable())).append(':');
            builder.append(constraint.getRightSideValue());
        }
        return builder.toString();
    }

    private static int countColumns(Node<?, ?, ?> node) {
        if (node.getUsedScheduleSet() == null) {
            return 0;
        }
        int count = 0;
        for (List<?> schedules : node.getUsedScheduleSet()) {
            count += schedules == null ? 0 : schedules.size();
        }
        return count;
    }
}

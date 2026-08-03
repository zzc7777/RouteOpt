package org.zzc.bpcsolver.diagnostic.r201;

import gnu.trove.list.array.TIntArrayList;
import org.zzc.bpcsolver.algorithm.BPCSolver;
import org.zzc.bpcsolver.algorithm.branching.VehicleCountThenArcBranching;
import org.zzc.bpcsolver.algorithm.cutting.LmSrc;
import org.zzc.bpcsolver.core.branch.ColumnGenerationStatus;
import org.zzc.bpcsolver.core.branch.Constraint;
import org.zzc.bpcsolver.core.branch.Node;
import org.zzc.bpcsolver.core.branch.Variable;
import org.zzc.bpcsolver.core.graph.Graph;
import org.zzc.bpcsolver.core.graph.Route;
import org.zzc.bpcsolver.problem.entity.DynamicInstance;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class SolomonR201WitnessReplayMain {

    private static final double EPSILON = 1e-6;
    private static final double BENCHMARK_OBJECTIVE = R201DiagnosticSupport.SOLOMON_BPC_BENCHMARK_OBJECTIVE;

    public static void main(String[] args) throws IOException {
        ReplayConfig config = ReplayConfig.parse(args);
        DynamicInstance instance = R201DiagnosticSupport.loadR201Instance();
        List<Route> witnessRoutes = R201DiagnosticSupport.loadWitnessRoutes(instance, config.witnessPath);
        ReplaySolver solver = new ReplaySolver();
        solver.setHeuristicPricingRule(config.heuristicPricing);
        solver.setCuttingRule(config.cutting);
        solver.setRelaxationRule(config.relaxation);
        solver.setVariableSelectionRule(config.variableSelection);
        solver.setPrimalHeuristicRule(config.primalHeuristic);
        solver.setParallelPricing(config.parallelPricing);

        double witnessObjective = witnessRoutes.stream().mapToDouble(Route::getCost).sum();
        boolean witnessFeasible = R201DiagnosticSupport.isStageSolutionFeasible(instance, witnessRoutes);
        System.out.println("R201_WITNESS_REPLAY"
                + ",witnessPath=" + csv(config.witnessPath.toString())
                + ",witnessFeasible=" + witnessFeasible
                + ",witnessObjective=" + format(witnessObjective)
                + ",benchmarkObjective=" + format(BENCHMARK_OBJECTIVE)
                + ",witnessRoutes=" + witnessRoutes.size()
                + ",maxDepth=" + config.maxDepth
                + ",maxAddedColumns=" + config.maxAddedColumns
                + ",maxIterations=" + config.maxIterations
                + ",heuristicPricing=" + csv(config.heuristicPricing)
                + ",cutting=" + csv(config.cutting)
                + ",relaxation=" + csv(config.relaxation)
                + ",variableSelection=" + csv(config.variableSelection));

        ReplaySolver.RootReplayState root = solver.solveRootForReplay(
                instance,
                config.maxAddedColumns,
                config.maxIterations
        );
        Node<Route, LmSrc, Graph> currentNode = root.rootNode;
        System.out.println("depth,nodeStatus,nodeObj,nodeInteger,nodeFeasible,cgStatus,columns,cuts,"
                + "witnessRoutesInPool,witnessBranchFeasible,witnessBranchMinSlack,witnessBranchViolation,"
                + "witnessCutFeasible,witnessCutMaxViolation,witnessCutViolation,"
                + "lowerBoundExceedsWitness,branchVar,witnessUsage,chosenFlag,stopReason");
        printReplayRow(0, currentNode, root.columnGenerationStatus, witnessRoutes, null, 0, -1, "ROOT");

        for (int depth = 1; depth <= config.maxDepth; depth++) {
            if (currentNode == null) {
                printStop(depth, null, witnessRoutes, "NULL_NODE");
                break;
            }
            if (!currentNode.isFeasible()) {
                printStop(depth, currentNode, witnessRoutes, "NODE_INFEASIBLE");
                break;
            }
            if (currentNode.getColumnGenerationStatus() != ColumnGenerationStatus.CONVERGED) {
                printStop(depth, currentNode, witnessRoutes, "CG_NOT_CONVERGED");
                break;
            }
            if (currentNode.isInteger()) {
                printStop(depth, currentNode, witnessRoutes, "NODE_INTEGER");
                break;
            }

            List<Variable> fractionalVars = solver.fractionalVariables(currentNode);
            if (fractionalVars == null || fractionalVars.isEmpty()) {
                printStop(depth, currentNode, witnessRoutes, "NO_FRACTIONAL_VARIABLE");
                break;
            }
            Variable branchingVar = solver.selectBranchingVariableForReplay(fractionalVars, currentNode);
            if (branchingVar == null) {
                printStop(depth, currentNode, witnessRoutes, "NO_BRANCHING_VARIABLE");
                break;
            }

            int witnessUsage = witnessUsage(witnessRoutes, branchingVar);
            int chosenFlag = chooseWitnessBranch(branchingVar, witnessUsage);
            if (chosenFlag < 0) {
                printReplayRow(depth, currentNode, currentNode.getColumnGenerationStatus(), witnessRoutes,
                        branchingVar, witnessUsage, chosenFlag, "WITNESS_SATISFIES_NEITHER_CHILD");
                break;
            }

            currentNode = solver.solveChildForReplay(currentNode, branchingVar, chosenFlag, config.maxIterations);
            printReplayRow(depth, currentNode,
                    currentNode == null ? ColumnGenerationStatus.NOT_STARTED : currentNode.getColumnGenerationStatus(),
                    witnessRoutes, branchingVar, witnessUsage, chosenFlag, "CONTINUE");
        }
    }

    private static void printStop(int depth, Node<Route, LmSrc, Graph> node, List<Route> witnessRoutes, String reason) {
        printReplayRow(depth, node,
                node == null ? ColumnGenerationStatus.NOT_STARTED : node.getColumnGenerationStatus(),
                witnessRoutes, null, 0, -1, reason);
    }

    private static void printReplayRow(int depth,
                                       Node<Route, LmSrc, Graph> node,
                                       ColumnGenerationStatus status,
                                       List<Route> witnessRoutes,
                                       Variable branchingVar,
                                       int witnessUsage,
                                       int chosenFlag,
                                       String stopReason) {
        BranchAudit branchAudit = auditBranchConstraints(node, witnessRoutes);
        CutAudit cutAudit = auditCuts(node, witnessRoutes);
        double nodeObj = node == null || node.getSolution() == null
                ? Double.POSITIVE_INFINITY
                : node.getSolution().getObjValue();
        double witnessObjective = witnessRoutes.stream().mapToDouble(Route::getCost).sum();
        boolean lowerBoundExceedsWitness = status == ColumnGenerationStatus.CONVERGED
                && branchAudit.feasible
                && cutAudit.feasible
                && nodeObj > witnessObjective + EPSILON;
        System.out.println(depth
                + "," + csv(node == null ? "NULL" : "NODE")
                + "," + format(nodeObj)
                + "," + (node != null && node.isInteger())
                + "," + (node != null && node.isFeasible())
                + "," + csv(status == null ? "null" : status.name())
                + "," + countColumns(node)
                + "," + countCuts(node)
                + "," + countWitnessRoutesInPool(node, witnessRoutes)
                + "," + branchAudit.feasible
                + "," + format(branchAudit.minSlack)
                + "," + csv(branchAudit.firstViolation)
                + "," + cutAudit.feasible
                + "," + format(cutAudit.maxViolation)
                + "," + csv(cutAudit.firstViolation)
                + "," + lowerBoundExceedsWitness
                + "," + csv(branchingVar == null ? "" : branchingVar.toString())
                + "," + witnessUsage
                + "," + chosenFlag
                + "," + csv(stopReason));
    }

    private static BranchAudit auditBranchConstraints(Node<Route, LmSrc, Graph> node, List<Route> witnessRoutes) {
        BranchAudit audit = new BranchAudit();
        if (node == null || node.getBranchConstraints() == null || node.getBranchConstraints().isEmpty()) {
            return audit;
        }
        for (int i = 0; i < node.getBranchConstraints().size(); i++) {
            Constraint constraint = node.getBranchConstraints().get(i);
            double lhs = 0.0;
            for (Route route : witnessRoutes) {
                int machineIndex = 0;
                int coeff = coefficientForMachine(constraint, machineIndex);
                lhs += coeff * variableVisitedTimes(route, constraint.getVariable());
            }
            double slack = lhs - constraint.getRightSideValue();
            audit.minSlack = Math.min(audit.minSlack, slack);
            if (slack < -EPSILON && audit.firstViolation.isEmpty()) {
                audit.feasible = false;
                audit.firstViolation = "branch#" + i
                        + ":" + constraint.getType()
                        + ":" + variableToString(constraint.getVariable())
                        + ":lhs=" + format(lhs)
                        + ":rhs=" + constraint.getRightSideValue();
            }
        }
        return audit;
    }

    private static CutAudit auditCuts(Node<Route, LmSrc, Graph> node, List<Route> witnessRoutes) {
        CutAudit audit = new CutAudit();
        if (node == null || node.getUsedCutSet() == null || node.getUsedCutSet().isEmpty()) {
            return audit;
        }
        for (int i = 0; i < node.getUsedCutSet().size(); i++) {
            LmSrc cut = node.getUsedCutSet().get(i);
            double lhs = 0.0;
            for (Route route : witnessRoutes) {
                lhs += cut.calculateCoefficient(route.getVisitedVertices(), 0);
            }
            double violation = lhs - cut.getConstant();
            audit.maxViolation = Math.max(audit.maxViolation, violation);
            if (violation > EPSILON && audit.firstViolation.isEmpty()) {
                audit.feasible = false;
                audit.firstViolation = "cut#" + i
                        + ":id=" + cut.getId()
                        + ":subset=" + Arrays.toString(cut.getSubSet().toArray())
                        + ":lhs=" + format(lhs)
                        + ":rhs=" + format(cut.getConstant());
            }
        }
        return audit;
    }

    private static int coefficientForMachine(Constraint constraint, int machineIndex) {
        if (constraint == null || constraint.getCoeff_1() == null || constraint.getCoeff_1().isEmpty()) {
            return 0;
        }
        if (machineIndex < 0 || machineIndex >= constraint.getCoeff_1().size()) {
            return 0;
        }
        return constraint.getCoeff_1().get(machineIndex);
    }

    static int witnessUsage(List<Route> witnessRoutes, Variable variable) {
        int usage = 0;
        for (Route route : witnessRoutes) {
            usage += variableVisitedTimes(route, variable);
        }
        return usage;
    }

    static int chooseWitnessBranch(Variable variable, int usage) {
        if (variable == null) {
            return -1;
        }
        int floor = (int) Math.floor(variable.getValue());
        int ceil = (int) Math.ceil(variable.getValue());
        if (usage <= floor) {
            return 0;
        }
        if (usage >= ceil) {
            return 1;
        }
        return -1;
    }

    static int variableVisitedTimes(Route route, Variable variable) {
        if (route == null || route.getVisitedVertices() == null || variable == null || variable.getIndexes() == null) {
            return 0;
        }
        if (VehicleCountThenArcBranching.VEHICLE_COUNT_TYPE.equals(variable.getType())) {
            return 1;
        }
        int[] indexes = variable.getIndexes();
        int tailIndex = "xkij".equalsIgnoreCase(variable.getType()) ? 1 : 0;
        int headIndex = "xkij".equalsIgnoreCase(variable.getType()) ? 2 : 1;
        if (indexes.length <= headIndex) {
            return 0;
        }
        int tail = indexes[tailIndex];
        int head = indexes[headIndex];
        int count = 0;
        TIntArrayList visited = route.getVisitedVertices();
        for (int i = 0; i < visited.size() - 1; i++) {
            if (visited.get(i) == tail && visited.get(i + 1) == head) {
                count++;
            }
        }
        return count;
    }

    private static int countColumns(Node<Route, LmSrc, Graph> node) {
        if (node == null || node.getUsedScheduleSet() == null) {
            return 0;
        }
        int count = 0;
        for (List<Route> routes : node.getUsedScheduleSet()) {
            count += routes == null ? 0 : routes.size();
        }
        return count;
    }

    private static int countCuts(Node<Route, LmSrc, Graph> node) {
        return node == null || node.getUsedCutSet() == null ? 0 : node.getUsedCutSet().size();
    }

    private static int countWitnessRoutesInPool(Node<Route, LmSrc, Graph> node, List<Route> witnessRoutes) {
        if (node == null || node.getUsedScheduleSet() == null) {
            return 0;
        }
        List<Route> pool = node.getUsedScheduleSet().stream()
                .filter(routes -> routes != null)
                .flatMap(List::stream)
                .collect(Collectors.toList());
        int matched = 0;
        for (Route witness : witnessRoutes) {
            if (R201DiagnosticSupport.containsRoute(pool, witness)) {
                matched++;
            }
        }
        return matched;
    }

    private static String variableToString(Variable variable) {
        return variable == null ? "" : variable.toString();
    }

    private static String format(double value) {
        if (Double.isInfinite(value)) {
            return value > 0 ? "Infinity" : "-Infinity";
        }
        if (Double.isNaN(value)) {
            return "NaN";
        }
        return String.format(Locale.ROOT, "%.6f", value);
    }

    private static String csv(String value) {
        String text = value == null ? "" : value;
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    private static final class BranchAudit {
        private boolean feasible = true;
        private double minSlack = Double.POSITIVE_INFINITY;
        private String firstViolation = "";
    }

    private static final class CutAudit {
        private boolean feasible = true;
        private double maxViolation = 0.0;
        private String firstViolation = "";
    }

    private static final class ReplaySolver extends BPCSolver {

        private RootReplayState solveRootForReplay(DynamicInstance instance,
                                                   int maxAddedColumns,
                                                   int maxIterations) {
            RootSolveState state = solveRootNode(instance, maxAddedColumns, maxIterations);
            return new RootReplayState(state.getRootNode(), state.getColumnGenerationStatus());
        }

        private List<Variable> fractionalVariables(Node<Route, LmSrc, Graph> node) {
            return getFractionalVariables(node);
        }

        private Variable selectBranchingVariableForReplay(List<Variable> fractionalVars,
                                                          Node<Route, LmSrc, Graph> node) {
            return selectBranchingVariable(fractionalVars, node);
        }

        private Node<Route, LmSrc, Graph> solveChildForReplay(Node<Route, LmSrc, Graph> parentNode,
                                                              Variable branchingVar,
                                                              int branchFlag,
                                                              int maxIterations) {
            return solveBranchedChildNode(parentNode, branchingVar, branchFlag, maxIterations);
        }

        private static final class RootReplayState {
            private final Node<Route, LmSrc, Graph> rootNode;
            private final ColumnGenerationStatus columnGenerationStatus;

            private RootReplayState(Node<Route, LmSrc, Graph> rootNode,
                                    ColumnGenerationStatus columnGenerationStatus) {
                this.rootNode = rootNode;
                this.columnGenerationStatus = columnGenerationStatus;
            }
        }
    }

    private static final class ReplayConfig {
        private final int maxAddedColumns;
        private final int maxIterations;
        private final int maxDepth;
        private final String heuristicPricing;
        private final String cutting;
        private final String relaxation;
        private final String variableSelection;
        private final String primalHeuristic;
        private final boolean parallelPricing;
        private final Path witnessPath;

        private ReplayConfig(int maxAddedColumns,
                             int maxIterations,
                             int maxDepth,
                             String heuristicPricing,
                             String cutting,
                             String relaxation,
                             String variableSelection,
                             String primalHeuristic,
                             boolean parallelPricing,
                             Path witnessPath) {
            this.maxAddedColumns = maxAddedColumns;
            this.maxIterations = maxIterations;
            this.maxDepth = maxDepth;
            this.heuristicPricing = heuristicPricing;
            this.cutting = cutting;
            this.relaxation = relaxation;
            this.variableSelection = variableSelection;
            this.primalHeuristic = primalHeuristic;
            this.parallelPricing = parallelPricing;
            this.witnessPath = witnessPath;
        }

        private static ReplayConfig parse(String[] args) {
            return new ReplayConfig(
                    parseOptionalInt(args, 0, 20),
                    parseOptionalInt(args, 1, 0),
                    parseOptionalInt(args, 2, 20),
                    parseOptionalString(args, 3, "multi-stage"),
                    parseOptionalString(args, 4, "none"),
                    parseOptionalString(args, 5, "ng-route"),
                    parseOptionalString(args, 6, "most fractional branching"),
                    parseOptionalString(args, 7, "column-pool-integer-master"),
                    parseOptionalBoolean(args, 8, true),
                    Paths.get(parseOptionalString(args, 9,
                            R201DiagnosticSupport.DEFAULT_WITNESS_PATH.toString()))
            );
        }

        private static int parseOptionalInt(String[] args, int index, int defaultValue) {
            if (args == null || args.length <= index || args[index] == null || args[index].trim().isEmpty()) {
                return defaultValue;
            }
            return Integer.parseInt(args[index].trim());
        }

        private static String parseOptionalString(String[] args, int index, String defaultValue) {
            if (args == null || args.length <= index || args[index] == null || args[index].trim().isEmpty()) {
                return defaultValue;
            }
            return args[index].trim();
        }

        private static boolean parseOptionalBoolean(String[] args, int index, boolean defaultValue) {
            if (args == null || args.length <= index || args[index] == null || args[index].trim().isEmpty()) {
                return defaultValue;
            }
            return Boolean.parseBoolean(args[index].trim());
        }
    }
}

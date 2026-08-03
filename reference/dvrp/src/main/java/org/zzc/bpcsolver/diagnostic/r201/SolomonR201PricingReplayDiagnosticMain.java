package org.zzc.bpcsolver.diagnostic.r201;

import gnu.trove.list.array.TIntArrayList;
import org.zzc.bpcsolver.algorithm.BPCSolver;
import org.zzc.bpcsolver.algorithm.branching.BranchingRule;
import org.zzc.bpcsolver.algorithm.config.Configuration;
import org.zzc.bpcsolver.algorithm.cutting.LmSrc;
import org.zzc.bpcsolver.algorithm.pricing.ForwardLabeling;
import org.zzc.bpcsolver.algorithm.pricing.Label;
import org.zzc.bpcsolver.core.branch.ColumnGenerationStatus;
import org.zzc.bpcsolver.core.branch.Constraint;
import org.zzc.bpcsolver.core.branch.Node;
import org.zzc.bpcsolver.core.branch.Variable;
import org.zzc.bpcsolver.core.graph.Graph;
import org.zzc.bpcsolver.core.graph.Route;
import org.zzc.bpcsolver.core.graph.Vertex;
import org.zzc.bpcsolver.problem.entity.DynamicInstance;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class SolomonR201PricingReplayDiagnosticMain {

    private static final double EPSILON = 1e-6;
    private static final double BENCHMARK_OBJECTIVE = R201DiagnosticSupport.SOLOMON_BPC_BENCHMARK_OBJECTIVE;

    public static void main(String[] args) throws IOException {
        DiagnosticConfig config = DiagnosticConfig.parse(args);
        DynamicInstance instance = R201DiagnosticSupport.loadR201Instance();
        List<Route> witnessRoutes = R201DiagnosticSupport.loadWitnessRoutes(instance, config.witnessPath);

        ReplaySolver solver = new ReplaySolver();
        solver.setHeuristicPricingRule(config.heuristicPricing);
        solver.setCuttingRule(config.cutting);
        solver.setRelaxationRule(config.relaxation);
        solver.setVariableSelectionRule(config.variableSelection);
        solver.setPrimalHeuristicRule(config.primalHeuristic);
        solver.setParallelPricing(config.parallelPricing);

        ReplaySolver.RootReplayState root = solver.solveRootForReplay(
                instance,
                config.maxAddedColumns,
                config.maxIterations
        );

        double witnessObjective = witnessRoutes.stream().mapToDouble(Route::getCost).sum();
        boolean witnessFeasible = R201DiagnosticSupport.isStageSolutionFeasible(instance, witnessRoutes);
        System.out.println("R201_PRICING_REPLAY_DIAGNOSTIC"
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
                + ",variableSelection=" + csv(config.variableSelection)
                + ",rootCgStatus=" + csv(root.columnGenerationStatus.name()));

        System.out.println("PRICING_REPLAY");
        System.out.println("depth,routeIndex,prefixLength,prefix,tailNode,nextNode,rawArrival,startServiceTime,load,"
                + "capacityUsed,cost,reducedCost,ngSet,cutState,extended,accepted,removedReason,"
                + "dominatingPrefix,dominatingStartServiceTime,dominatingLoad,dominatingCapacityUsed,"
                + "dominatingReducedCost,dominatingNgSet,dominatingCutState");

        Node<Route, LmSrc, Graph> currentNode = root.rootNode;
        PrefixReplayLabeling prefixReplay = new PrefixReplayLabeling();
        for (int depth = 0; depth <= config.maxDepth && currentNode != null; depth++) {
            replayPricingAtNode(prefixReplay, solver, currentNode, witnessRoutes, depth);
            if (depth == config.maxDepth || shouldStopBranchReplay(currentNode)) {
                break;
            }
            Variable branchingVar = solver.selectBranchingVariableForReplay(
                    solver.fractionalVariables(currentNode),
                    currentNode
            );
            if (branchingVar == null) {
                break;
            }
            int usage = witnessUsage(witnessRoutes, branchingVar);
            int chosenFlag = chooseWitnessBranch(branchingVar, usage);
            if (chosenFlag < 0) {
                break;
            }
            currentNode = solver.solveChildForReplay(currentNode, branchingVar, chosenFlag, config.maxIterations);
        }

        System.out.println("BRANCH_REPLAY");
        System.out.println("depth,nodeObj,nodeInteger,nodeFeasible,cgStatus,branchVar,witnessUsage,"
                + "parentCompatible,leftCompatible,rightCompatible,atLeastOneChildCompatible,"
                + "leftConstraint,rightConstraint,firstParentViolation,stopReason");
        replayBranchPath(solver, root.rootNode, witnessRoutes, config.maxDepth, config.maxIterations);
    }

    private static void replayPricingAtNode(PrefixReplayLabeling replay,
                                            ReplaySolver solver,
                                            Node<Route, LmSrc, Graph> node,
                                            List<Route> witnessRoutes,
                                            int depth) {
        if (node == null || node.getGraphs() == null || node.getGraphs().isEmpty() || node.getSolution() == null) {
            return;
        }
        Graph sourceGraph = node.getGraphs().get(0);
        if (sourceGraph == null) {
            return;
        }
        Graph dominanceGraph = sourceGraph.copy();
        Graph graph = sourceGraph.copy();
        graph.clearLabels();

        double[] startDepotDuals = replay.exposeStartDepotDuals(
                graph,
                node.getSolution().getDualsOfMachines() == null || node.getSolution().getDualsOfMachines().length == 0
                        ? 0.0
                        : node.getSolution().getDualsOfMachines()[0]
        );
        double[] pricingNodeDuals = replay.exposePricingNodeDuals(
                graph,
                node.getSolution().getDualsOfJobs(),
                startDepotDuals
        );

        for (int routeIndex = 0; routeIndex < witnessRoutes.size(); routeIndex++) {
            replayOneRoute(
                    replay,
                    graph.copy(),
                    dominanceGraph,
                    witnessRoutes.get(routeIndex),
                    routeIndex + 1,
                    depth,
                    startDepotDuals,
                    pricingNodeDuals,
                    node.getUsedCutSet(),
                    node.getSolution().getDualsOfCuts(),
                    node.getBranchConstraints(),
                    node.getSolution().getDualsOfBranches(),
                    solver.branchingRuleForReplay(),
                    solver.configForReplay()
            );
        }
    }

    private static void replayOneRoute(PrefixReplayLabeling replay,
                                       Graph graph,
                                       Graph dominanceGraph,
                                       Route witnessRoute,
                                       int routeIndex,
                                       int depth,
                                       double[] startDepotDuals,
                                       double[] pricingNodeDuals,
                                       List<LmSrc> cuts,
                                       List<Double> dualsOfCuts,
                                       List<Constraint> branchConstraints,
                                       List<Double> dualsOfBranches,
                                       BranchingRule<Route, LmSrc, Graph> branchingRule,
                                       Configuration replayConfig) {
        if (witnessRoute == null || witnessRoute.getVisitedVertices() == null) {
            return;
        }
        graph.clearLabels();
        Label label = replay.exposeInitialLabel(graph, pricingNodeDuals, startDepotDuals);
        replay.exposeInitializeCutState(label, cuts);
        graph.getVertex(0).addNonDominatedLabel(label, activeCutDuals(dualsOfCuts));
        printPricingRow(depth, routeIndex, 0, label.getVisitedVertices(), label.getLastNodeId(), label.getLastNodeId(),
                label.getStartServiceTime(), label, true, true, "INITIAL", null);

        TIntArrayList path = witnessRoute.getVisitedVertices();
        for (int prefixLength = 1; prefixLength < path.size(); prefixLength++) {
            int nextNodeId = path.get(prefixLength);
            Vertex head = graph.findVertexById(nextNodeId);
            if (head == null) {
                printMissingVertexRow(depth, routeIndex, prefixLength, label, nextNodeId);
                break;
            }

            Label candidate = new Label(label);
            double rawArrival = rawArrival(graph.getInstance(), candidate.getLastNodeId(), nextNodeId,
                    candidate.getStartServiceTime());
            boolean extended = replay.exposeLabelExtend(
                    candidate,
                    head,
                    graph,
                    pricingNodeDuals,
                    cuts,
                    dualsOfCuts,
                    branchConstraints,
                    dualsOfBranches,
                    branchingRule,
                    replayConfig
            );
            if (!extended) {
                printPricingRow(depth, routeIndex, prefixLength, buildPrefix(path, prefixLength),
                        label.getLastNodeId(), nextNodeId, rawArrival, candidate, false, false,
                        inferExtensionFailure(graph, label, head, candidate), null);
                break;
            }

            Label fullPricingDominator = findFullPricingDominator(
                    dominanceGraph,
                    nextNodeId,
                    candidate,
                    activeCutDuals(dualsOfCuts)
            );
            if (fullPricingDominator != null) {
                printPricingRow(depth, routeIndex, prefixLength, candidate.getVisitedVertices(),
                        label.getLastNodeId(), nextNodeId, rawArrival, candidate, true, false,
                        "DOMINATED_BY_FULL_PRICING", fullPricingDominator);
                break;
            }

            Label dominatingLabel = head.findDominatingLabel(candidate, activeCutDuals(dualsOfCuts));
            boolean accepted = head.addNonDominatedLabel(candidate, activeCutDuals(dualsOfCuts));
            printPricingRow(depth, routeIndex, prefixLength, candidate.getVisitedVertices(),
                    label.getLastNodeId(), nextNodeId, rawArrival, candidate, true, accepted,
                    accepted ? "ACCEPTED" : "DOMINATED_IN_FORCED_REPLAY", dominatingLabel);
            if (!accepted) {
                break;
            }
            label = candidate;
        }
    }

    private static Label findFullPricingDominator(Graph dominanceGraph,
                                                  int nextNodeId,
                                                  Label candidate,
                                                  List<Double> dualsOfCuts) {
        if (dominanceGraph == null || candidate == null) {
            return null;
        }
        Vertex fullContextVertex = dominanceGraph.findVertexById(nextNodeId);
        if (fullContextVertex == null) {
            return null;
        }
        Label dominatingLabel = fullContextVertex.findDominatingLabel(candidate, dualsOfCuts);
        if (dominatingLabel == null || samePrefix(dominatingLabel, candidate)) {
            return null;
        }
        return dominatingLabel;
    }

    private static boolean samePrefix(Label left, Label right) {
        return left != null
                && right != null
                && left.getVisitedVertices() != null
                && left.getVisitedVertices().equals(right.getVisitedVertices());
    }

    private static void replayBranchPath(ReplaySolver solver,
                                         Node<Route, LmSrc, Graph> rootNode,
                                         List<Route> witnessRoutes,
                                         int maxDepth,
                                         int maxIterations) {
        Node<Route, LmSrc, Graph> currentNode = rootNode;
        for (int depth = 0; depth <= maxDepth; depth++) {
            if (currentNode == null) {
                printBranchRow(depth, null, null, 0, false, false, false,
                        null, null, "", "NULL_NODE");
                break;
            }
            BranchAudit parentAudit = auditBranchConstraints(currentNode.getBranchConstraints(), witnessRoutes);
            if (shouldStopBranchReplay(currentNode)) {
                printBranchRow(depth, currentNode, null, 0, parentAudit.feasible, false, false,
                        null, null, parentAudit.firstViolation, branchStopReason(currentNode));
                break;
            }

            List<Variable> fractionalVariables = solver.fractionalVariables(currentNode);
            Variable branchingVar = solver.selectBranchingVariableForReplay(fractionalVariables, currentNode);
            if (branchingVar == null) {
                printBranchRow(depth, currentNode, null, 0, parentAudit.feasible, false, false,
                        null, null, parentAudit.firstViolation, "NO_BRANCHING_VARIABLE");
                break;
            }

            Constraint leftConstraint = solver.createBranchConstraintForReplay(currentNode, branchingVar, 0);
            Constraint rightConstraint = solver.createBranchConstraintForReplay(currentNode, branchingVar, 1);
            boolean leftCompatible = parentAudit.feasible
                    && auditBranchConstraintsWithExtra(currentNode, leftConstraint, witnessRoutes).feasible;
            boolean rightCompatible = parentAudit.feasible
                    && auditBranchConstraintsWithExtra(currentNode, rightConstraint, witnessRoutes).feasible;
            int usage = witnessUsage(witnessRoutes, branchingVar);
            printBranchRow(depth, currentNode, branchingVar, usage, parentAudit.feasible,
                    leftCompatible, rightCompatible, leftConstraint, rightConstraint,
                    parentAudit.firstViolation, "CONTINUE");

            int chosenFlag = chooseWitnessBranch(branchingVar, usage);
            if (chosenFlag < 0) {
                break;
            }
            currentNode = solver.solveChildForReplay(currentNode, branchingVar, chosenFlag, maxIterations);
        }
    }

    private static boolean shouldStopBranchReplay(Node<Route, LmSrc, Graph> node) {
        return node == null
                || !node.isFeasible()
                || node.getColumnGenerationStatus() != ColumnGenerationStatus.CONVERGED
                || node.isInteger();
    }

    private static String branchStopReason(Node<Route, LmSrc, Graph> node) {
        if (node == null) {
            return "NULL_NODE";
        }
        if (!node.isFeasible()) {
            return "NODE_INFEASIBLE";
        }
        if (node.getColumnGenerationStatus() != ColumnGenerationStatus.CONVERGED) {
            return "CG_NOT_CONVERGED";
        }
        if (node.isInteger()) {
            return "NODE_INTEGER";
        }
        return "";
    }

    private static BranchAudit auditBranchConstraintsWithExtra(Node<Route, LmSrc, Graph> node,
                                                               Constraint extra,
                                                               List<Route> witnessRoutes) {
        List<Constraint> constraints = new ArrayList<>();
        if (node != null && node.getBranchConstraints() != null) {
            constraints.addAll(node.getBranchConstraints());
        }
        if (extra != null) {
            constraints.add(extra);
        }
        return auditBranchConstraints(constraints, witnessRoutes);
    }

    private static BranchAudit auditBranchConstraints(List<Constraint> constraints, List<Route> witnessRoutes) {
        BranchAudit audit = new BranchAudit();
        if (constraints == null || constraints.isEmpty()) {
            return audit;
        }
        for (int i = 0; i < constraints.size(); i++) {
            Constraint constraint = constraints.get(i);
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

    private static int coefficientForMachine(Constraint constraint, int machineIndex) {
        if (constraint == null || constraint.getCoeff_1() == null || constraint.getCoeff_1().isEmpty()) {
            return 0;
        }
        if (machineIndex < 0 || machineIndex >= constraint.getCoeff_1().size()) {
            return 0;
        }
        return constraint.getCoeff_1().get(machineIndex);
    }

    private static int witnessUsage(List<Route> witnessRoutes, Variable variable) {
        int usage = 0;
        for (Route route : witnessRoutes) {
            usage += variableVisitedTimes(route, variable);
        }
        return usage;
    }

    private static int chooseWitnessBranch(Variable variable, int usage) {
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

    private static int variableVisitedTimes(Route route, Variable variable) {
        if (route == null || route.getVisitedVertices() == null || variable == null || variable.getIndexes() == null) {
            return 0;
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

    private static String inferExtensionFailure(Graph graph, Label currentLabel, Vertex headVertex, Label candidate) {
        if (graph == null || currentLabel == null || headVertex == null || candidate == null) {
            return "EXTENSION_REJECTED";
        }
        DynamicInstance instance = graph.getInstance();
        int headNodeId = headVertex.getId();
        if (!headVertex.isReturnDepot()) {
            if (candidate.getVisitedVector() != null
                    && headNodeId >= 0
                    && headNodeId < candidate.getVisitedVector().length
                    && candidate.getVisitedVector()[headNodeId] > 0) {
                return "REPEATED_CUSTOMER_OR_NG_MEMORY";
            }
            if (candidate.containsNgNode(headNodeId)) {
                return "NG_MEMORY_CONTAINS_HEAD";
            }
            double nextLoad = currentLabel.getLoad() + instance.getDemand(headNodeId);
            if (nextLoad > instance.getProblemData().getVehicleCapacity() + EPSILON) {
                return "CAPACITY";
            }
        }
        double arrival = rawArrival(instance, currentLabel.getLastNodeId(), headNodeId,
                currentLabel.getStartServiceTime());
        double nextStartService = Math.max(arrival, instance.getEarliestTime(headNodeId));
        if (nextStartService > headVertex.getLatestTime() + EPSILON) {
            return "TIME_WINDOW";
        }
        return "EXTENSION_REJECTED";
    }

    private static double rawArrival(DynamicInstance instance,
                                     int tailNodeId,
                                     int headNodeId,
                                     double tailStartServiceTime) {
        return tailStartServiceTime
                + instance.getServiceTime(tailNodeId)
                + instance.getTravelTime(tailNodeId, headNodeId);
    }

    private static TIntArrayList buildPrefix(TIntArrayList path, int prefixLength) {
        TIntArrayList prefix = new TIntArrayList();
        for (int i = 0; i <= prefixLength && i < path.size(); i++) {
            prefix.add(path.get(i));
        }
        return prefix;
    }

    private static void printMissingVertexRow(int depth,
                                              int routeIndex,
                                              int prefixLength,
                                              Label label,
                                              int nextNodeId) {
        printPricingRow(depth, routeIndex, prefixLength,
                label == null ? new TIntArrayList() : label.getVisitedVertices(),
                label == null ? -1 : label.getLastNodeId(),
                nextNodeId,
                Double.NaN,
                label,
                false,
                false,
                "MISSING_VERTEX",
                null);
    }

    private static void printPricingRow(int depth,
                                        int routeIndex,
                                        int prefixLength,
                                        TIntArrayList prefix,
                                        int tailNode,
                                        int nextNode,
                                        double rawArrival,
                                        Label label,
                                        boolean extended,
                                        boolean accepted,
                                        String removedReason,
                                        Label dominatingLabel) {
        System.out.println(depth
                + "," + routeIndex
                + "," + prefixLength
                + "," + csv(prefix == null ? "" : prefix.toString())
                + "," + tailNode
                + "," + nextNode
                + "," + format(rawArrival)
                + "," + format(label == null ? Double.NaN : label.getStartServiceTime())
                + "," + format(label == null ? Double.NaN : label.getLoad())
                + "," + format(label == null ? Double.NaN : label.getCapacityUsed())
                + "," + format(label == null ? Double.NaN : label.getCost())
                + "," + format(label == null ? Double.NaN : label.getReducedCost())
                + "," + csv(label == null ? "" : sortedSet(label.snapshotNgNodes()))
                + "," + csv(label == null ? "" : listToString(label.getState()))
                + "," + extended
                + "," + accepted
                + "," + csv(removedReason)
                + "," + csv(dominatingLabel == null ? "" : dominatingLabel.getVisitedVertices().toString())
                + "," + format(dominatingLabel == null ? Double.NaN : dominatingLabel.getStartServiceTime())
                + "," + format(dominatingLabel == null ? Double.NaN : dominatingLabel.getLoad())
                + "," + format(dominatingLabel == null ? Double.NaN : dominatingLabel.getCapacityUsed())
                + "," + format(dominatingLabel == null ? Double.NaN : dominatingLabel.getReducedCost())
                + "," + csv(dominatingLabel == null ? "" : sortedSet(dominatingLabel.snapshotNgNodes()))
                + "," + csv(dominatingLabel == null ? "" : listToString(dominatingLabel.getState())));
    }

    private static void printBranchRow(int depth,
                                       Node<Route, LmSrc, Graph> node,
                                       Variable branchVar,
                                       int usage,
                                       boolean parentCompatible,
                                       boolean leftCompatible,
                                       boolean rightCompatible,
                                       Constraint leftConstraint,
                                       Constraint rightConstraint,
                                       String firstParentViolation,
                                       String stopReason) {
        double nodeObj = node == null || node.getSolution() == null
                ? Double.POSITIVE_INFINITY
                : node.getSolution().getObjValue();
        ColumnGenerationStatus cgStatus = node == null || node.getColumnGenerationStatus() == null
                ? ColumnGenerationStatus.NOT_STARTED
                : node.getColumnGenerationStatus();
        System.out.println(depth
                + "," + format(nodeObj)
                + "," + (node != null && node.isInteger())
                + "," + (node != null && node.isFeasible())
                + "," + csv(cgStatus.name())
                + "," + csv(branchVar == null ? "" : branchVar.toString())
                + "," + usage
                + "," + parentCompatible
                + "," + leftCompatible
                + "," + rightCompatible
                + "," + (leftCompatible || rightCompatible)
                + "," + csv(constraintToString(leftConstraint))
                + "," + csv(constraintToString(rightConstraint))
                + "," + csv(firstParentViolation)
                + "," + csv(stopReason));
    }

    private static String constraintToString(Constraint constraint) {
        if (constraint == null) {
            return "";
        }
        return constraint.getType()
                + ":" + variableToString(constraint.getVariable())
                + ":rhs=" + constraint.getRightSideValue()
                + ":coeff1=" + constraint.getCoeff_1();
    }

    private static String variableToString(Variable variable) {
        return variable == null ? "" : variable.toString();
    }

    private static List<Double> activeCutDuals(List<Double> dualsOfCuts) {
        return dualsOfCuts == null ? new ArrayList<>() : dualsOfCuts;
    }

    private static String sortedSet(int[] values) {
        if (values == null || values.length == 0) {
            return "[]";
        }
        int[] copy = values.clone();
        Arrays.sort(copy);
        return Arrays.toString(copy);
    }

    private static String listToString(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return "[]";
        }
        return values.stream()
                .map(SolomonR201PricingReplayDiagnosticMain::format)
                .collect(Collectors.joining(";", "[", "]"));
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

    private static String parseOptionalString(String[] args, int index, String defaultValue) {
        if (args == null || args.length <= index || args[index] == null || args[index].trim().isEmpty()) {
            return defaultValue;
        }
        return args[index].trim();
    }

    private static int parseOptionalInt(String[] args, int index, int defaultValue) {
        if (args == null || args.length <= index || args[index] == null || args[index].trim().isEmpty()) {
            return defaultValue;
        }
        return Integer.parseInt(args[index].trim());
    }

    private static boolean parseOptionalBoolean(String[] args, int index, boolean defaultValue) {
        if (args == null || args.length <= index || args[index] == null || args[index].trim().isEmpty()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(args[index].trim());
    }

    private static final class BranchAudit {
        private boolean feasible = true;
        private double minSlack = Double.POSITIVE_INFINITY;
        private String firstViolation = "";
    }

    private static final class PrefixReplayLabeling extends ForwardLabeling {

        private double[] exposeStartDepotDuals(Graph graph, double dualOfMachine) {
            return createStartDepotDuals(graph, dualOfMachine);
        }

        private double[] exposePricingNodeDuals(Graph graph, double[] customerDuals, double[] startDepotDuals) {
            return createPricingNodeDuals(graph, customerDuals, startDepotDuals);
        }

        private Label exposeInitialLabel(Graph graph, double[] customerDuals, double[] startDepotDuals) {
            return createInitialLabel(graph, customerDuals, startDepotDuals);
        }

        private void exposeInitializeCutState(Label label, List<LmSrc> cuts) {
            initializeCutState(label, cuts == null ? new ArrayList<>() : cuts);
        }

        private boolean exposeLabelExtend(Label label,
                                          Vertex headVertex,
                                          Graph graph,
                                          double[] customerDuals,
                                          List<LmSrc> cuts,
                                          List<Double> dualsOfCuts,
                                          List<Constraint> branchConstraints,
                                          List<Double> dualsOfBranches,
                                          BranchingRule<Route, LmSrc, Graph> branchingRule,
                                          Configuration replayConfig) {
            return labelExtend(
                    label,
                    headVertex,
                    graph,
                    customerDuals,
                    cuts == null ? new ArrayList<>() : cuts,
                    dualsOfCuts == null ? new ArrayList<>() : dualsOfCuts,
                    branchConstraints,
                    dualsOfBranches,
                    branchingRule,
                    replayConfig
            );
        }
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
            if (fractionalVars == null || fractionalVars.isEmpty()) {
                return null;
            }
            return selectBranchingVariable(fractionalVars, node);
        }

        private Node<Route, LmSrc, Graph> solveChildForReplay(Node<Route, LmSrc, Graph> parentNode,
                                                              Variable branchingVar,
                                                              int branchFlag,
                                                              int maxIterations) {
            return solveBranchedChildNode(parentNode, branchingVar, branchFlag, maxIterations);
        }

        private Constraint createBranchConstraintForReplay(Node<Route, LmSrc, Graph> parentNode,
                                                           Variable branchingVar,
                                                           int branchFlag) {
            return createBranchingConstraint(parentNode, branchingVar, branchFlag);
        }

        private BranchingRule<Route, LmSrc, Graph> branchingRuleForReplay() {
            return this.branchingStrategy == null ? null : this.branchingStrategy.getBranchingRule();
        }

        private Configuration configForReplay() {
            return this.config;
        }

        private static final class RootReplayState {
            private final Node<Route, LmSrc, Graph> rootNode;
            private final ColumnGenerationStatus columnGenerationStatus;

            private RootReplayState(Node<Route, LmSrc, Graph> rootNode,
                                    ColumnGenerationStatus columnGenerationStatus) {
                this.rootNode = rootNode;
                this.columnGenerationStatus = columnGenerationStatus == null
                        ? ColumnGenerationStatus.NOT_STARTED
                        : columnGenerationStatus;
            }
        }
    }

    private static final class DiagnosticConfig {
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

        private DiagnosticConfig(int maxAddedColumns,
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

        private static DiagnosticConfig parse(String[] args) {
            return new DiagnosticConfig(
                    parseOptionalInt(args, 0, 20),
                    parseOptionalInt(args, 1, 0),
                    parseOptionalInt(args, 2, 2),
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
    }
}

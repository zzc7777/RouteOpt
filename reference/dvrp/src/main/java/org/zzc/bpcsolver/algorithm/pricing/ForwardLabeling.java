package org.zzc.bpcsolver.algorithm.pricing;

import gnu.trove.list.array.TIntArrayList;
import gnu.trove.set.hash.TIntHashSet;
import org.zzc.bpcsolver.algorithm.branching.BranchingRule;
import org.zzc.bpcsolver.algorithm.cutting.LmSrc;
import org.zzc.bpcsolver.algorithm.DeterministicOrdering;
import org.zzc.bpcsolver.algorithm.config.Configuration;
import org.zzc.bpcsolver.algorithm.logger.BPCLogger;
import org.zzc.bpcsolver.core.branch.Constraint;
import org.zzc.bpcsolver.core.graph.Arc;
import org.zzc.bpcsolver.core.graph.Graph;
import org.zzc.bpcsolver.core.graph.Route;
import org.zzc.bpcsolver.core.graph.Vertex;
import org.zzc.bpcsolver.problem.entity.Depot;
import org.zzc.bpcsolver.problem.entity.DynamicInstance;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

public class ForwardLabeling implements ExactPricing<Route, Graph, LmSrc> {

    protected static final double EPSILON = 1e-6;

    public List<Route> solve(DynamicInstance instance) {
        return solve(instance, null);
    }

    public List<Route> solveImprovingRoutes(DynamicInstance instance, double[] customerDuals, double[] startDepotDuals) {
        return convertLabelsToRoutes(solve(buildGraphs(instance), customerDuals, startDepotDuals), instance);
    }

    public List<Route> solveImprovingRoutes(DynamicInstance instance, double[] nodeDuals) {
        return solveImprovingRoutes(instance, nodeDuals, null);
    }

    public List<Route> solve(DynamicInstance instance, double[] customerDuals, double[] startDepotDuals) {
        if (instance == null) {
            throw new IllegalArgumentException("instance must not be null.");
        }
        instance.validate();
        return convertLabelsToRoutes(solveAll(buildGraphs(instance), customerDuals, startDepotDuals), instance);
    }

    public List<Route> solve(DynamicInstance instance, double[] nodeDuals) {
        return solve(instance, nodeDuals, null);
    }

    public List<Route> solve(DynamicInstance instance, int startDepotId) {
        return solve(instance, startDepotId, null);
    }

    public List<Route> solveImprovingRoutes(DynamicInstance instance, int startDepotId, double[] nodeDuals) {
        return solveImprovingRoutes(instance, startDepotId, nodeDuals, null);
    }

    public List<Route> solveImprovingRoutes(DynamicInstance instance, int startDepotId,
                                            double[] customerDuals, double[] startDepotDuals) {
        return convertLabelsToRoutes(solveLabels(buildGraph(instance, startDepotId), customerDuals, startDepotDuals), instance);
    }

    public List<Route> solve(DynamicInstance instance, int startDepotId,
                             double[] customerDuals, double[] startDepotDuals) {
        return convertLabelsToRoutes(solveAllLabels(buildGraph(instance, startDepotId), customerDuals, startDepotDuals), instance);
    }

    public List<Route> solve(DynamicInstance instance, int startDepotId, double[] nodeDuals) {
        return solve(instance, startDepotId, nodeDuals, null);
    }

    public List<Route> solve(Graph graph) {
        return solve(graph, null);
    }

    public List<Route> solve(Graph graph, double[] customerDuals, double[] startDepotDuals) {
        return convertLabelsToRoutes(solveAllLabels(graph, customerDuals, startDepotDuals), graph.getInstance());
    }

    @Override
    public List<Route> solve(Graph graph,
                             int index,
                             List<LmSrc> cuts,
                             List<Constraint> branchConstraints,
                             double dualsOfMachines,
                             double[] dualsOfJobs,
                             List<Double> dualsOfCuts,
                             List<Double> dualsOfBranches,
                             BranchingRule<Route, LmSrc, Graph> branchingRule,
                             Configuration config,
                             BPCLogger logger,
                             int iter) {
        return solveImprovingRoutes(
                graph,
                dualsOfJobs,
                createStartDepotDuals(graph, dualsOfMachines),
                cuts,
                branchConstraints,
                dualsOfCuts,
                dualsOfBranches,
                branchingRule,
                config
        );
    }

    public List<Route> solveImprovingRoutes(Graph graph,
                                            double[] customerDuals,
                                            double[] startDepotDuals,
                                            List<Constraint> branchConstraints,
                                            List<Double> dualsOfBranches,
                                            BranchingRule<Route, LmSrc, Graph> branchingRule) {
        return solveImprovingRoutes(graph, customerDuals, startDepotDuals,
                new ArrayList<>(), branchConstraints, new ArrayList<>(), dualsOfBranches, branchingRule);
    }

    public List<Route> solveImprovingRoutes(Graph graph,
                                            double[] customerDuals,
                                            double[] startDepotDuals,
                                            List<LmSrc> cuts,
                                            List<Constraint> branchConstraints,
                                            List<Double> dualsOfCuts,
                                            List<Double> dualsOfBranches,
                                            BranchingRule<Route, LmSrc, Graph> branchingRule) {
        return solveImprovingRoutes(graph, customerDuals, startDepotDuals, cuts, branchConstraints,
                dualsOfCuts, dualsOfBranches, branchingRule, null);
    }

    public List<Route> solveImprovingRoutes(Graph graph,
                                            double[] customerDuals,
                                            double[] startDepotDuals,
                                            List<LmSrc> cuts,
                                            List<Constraint> branchConstraints,
                                            List<Double> dualsOfCuts,
                                            List<Double> dualsOfBranches,
                                            BranchingRule<Route, LmSrc, Graph> branchingRule,
                                            Configuration config) {
        return convertLabelsToRoutes(
                solveLabels(graph, customerDuals, startDepotDuals, cuts, branchConstraints, dualsOfCuts,
                        dualsOfBranches, branchingRule, config),
                graph.getInstance()
        );
    }

    public List<Route> solve(Graph graph, double[] nodeDuals) {
        return solve(graph, nodeDuals, null);
    }

    public Route convertLabelToRoute(Label label, DynamicInstance instance) {
        if (label == null) {
            throw new IllegalArgumentException("label must not be null.");
        }
        if (instance == null) {
            throw new IllegalArgumentException("instance must not be null.");
        }

        Route route = new Route(label.getId(), label.getIndex(),
                instance.getProblemData().getStaticNodeCount() + instance.getExternalDepots().size());
        route.setVisitedVertices(new TIntArrayList(label.getVisitedVertices()));
        route.update(instance);
        route.setReducedCost(label.getReducedCost());
        route.setLoad(label.getLoad());
        return route;
    }

    public List<Label> solveLabels(Graph graph, double[] nodeDuals) {
        return solveLabels(graph, nodeDuals, null);
    }

    public List<Label> solveLabels(Graph graph, double[] customerDuals, double[] startDepotDuals) {
        return filterImprovingLabels(solveAllLabels(graph, customerDuals, startDepotDuals));
    }

    public List<Label> solveLabels(Graph graph,
                                   double[] customerDuals,
                                   double[] startDepotDuals,
                                   List<Constraint> branchConstraints,
                                   List<Double> dualsOfBranches,
                                   BranchingRule<Route, LmSrc, Graph> branchingRule) {
        return solveLabels(graph, customerDuals, startDepotDuals, new ArrayList<>(), branchConstraints,
                new ArrayList<>(), dualsOfBranches, branchingRule);
    }

    public List<Label> solveLabels(Graph graph,
                                   double[] customerDuals,
                                   double[] startDepotDuals,
                                   List<LmSrc> cuts,
                                   List<Constraint> branchConstraints,
                                   List<Double> dualsOfCuts,
                                   List<Double> dualsOfBranches,
                                   BranchingRule<Route, LmSrc, Graph> branchingRule) {
        return solveLabels(graph, customerDuals, startDepotDuals, cuts, branchConstraints,
                dualsOfCuts, dualsOfBranches, branchingRule, null);
    }

    public List<Label> solveLabels(Graph graph,
                                   double[] customerDuals,
                                   double[] startDepotDuals,
                                   List<LmSrc> cuts,
                                   List<Constraint> branchConstraints,
                                   List<Double> dualsOfCuts,
                                   List<Double> dualsOfBranches,
                                   BranchingRule<Route, LmSrc, Graph> branchingRule,
                                   Configuration config) {
        return filterImprovingLabels(solveAllLabels(graph, customerDuals, startDepotDuals, cuts,
                branchConstraints, dualsOfCuts, dualsOfBranches, branchingRule, config));
    }

    public List<Label> solveAllLabels(Graph graph, double[] nodeDuals) {
        return solveAllLabels(graph, nodeDuals, null);
    }

    public List<Label> solveAllLabels(Graph graph, double[] customerDuals, double[] startDepotDuals) {
        return solveAllLabels(graph, customerDuals, startDepotDuals, new ArrayList<>(), new ArrayList<>(), null);
    }

    public List<Label> solveAllLabels(Graph graph,
                                      double[] customerDuals,
                                      double[] startDepotDuals,
                                      List<Constraint> branchConstraints,
                                      List<Double> dualsOfBranches,
                                      BranchingRule<Route, LmSrc, Graph> branchingRule) {
        return solveAllLabels(graph, customerDuals, startDepotDuals, new ArrayList<>(),
                branchConstraints, new ArrayList<>(), dualsOfBranches, branchingRule);
    }

    public List<Label> solveAllLabels(Graph graph,
                                      double[] customerDuals,
                                      double[] startDepotDuals,
                                      List<LmSrc> cuts,
                                      List<Constraint> branchConstraints,
                                      List<Double> dualsOfCuts,
                                      List<Double> dualsOfBranches,
                                      BranchingRule<Route, LmSrc, Graph> branchingRule) {
        return solveAllLabels(graph, customerDuals, startDepotDuals, cuts, branchConstraints,
                dualsOfCuts, dualsOfBranches, branchingRule, null);
    }

    public List<Label> solveAllLabels(Graph graph,
                                      double[] customerDuals,
                                      double[] startDepotDuals,
                                      List<LmSrc> cuts,
                                      List<Constraint> branchConstraints,
                                      List<Double> dualsOfCuts,
                                      List<Double> dualsOfBranches,
                                      BranchingRule<Route, LmSrc, Graph> branchingRule,
                                      Configuration config) {
        if (graph == null) {
            throw new IllegalArgumentException("graph must not be null.");
        }

        List<LmSrc> activeCuts = cuts == null ? new ArrayList<>() : cuts;
        List<Double> activeCutDuals = dualsOfCuts == null ? new ArrayList<>() : dualsOfCuts;
        clearLabels(graph);
        double[] pricingNodeDuals = createPricingNodeDuals(graph, customerDuals, startDepotDuals);
        Label initialLabel = createInitialLabel(graph, customerDuals, startDepotDuals);
        initializeCutState(initialLabel, activeCuts);
        applyInitialBranchReducedCost(initialLabel, graph, branchConstraints, dualsOfBranches, branchingRule);
        graph.getVertices().get(0).addNonDominatedLabel(initialLabel, activeCutDuals);
        Queue<Label> unextendedLabels = new ArrayDeque<>();
        unextendedLabels.add(initialLabel);

        while (!unextendedLabels.isEmpty()) {
            if (PricingDiagnostics.isTimeLimitReached()) {
                break;
            }
            Label label = unextendedLabels.poll();
            if (label == null || label.isExtended()) {
                continue;
            }
            Vertex tailVertex = graph.findVertexById(label.getLastNodeId());
            if (tailVertex == null) {
                label.setExtended(true);
                continue;
            }
            for (Arc arc : graph.getOutgoingArcs(tailVertex.getIndex())) {
                if (arc.isFixed()) {
                    continue;
                }
                Label newLabel = new Label(label);
                PricingDiagnostics.recordLabelCreated();
                long extensionStartNanos = System.nanoTime();
                boolean extended = labelExtend(newLabel, graph.getVertex(arc.getHeadIndex()), graph, pricingNodeDuals,
                        activeCuts, activeCutDuals, branchConstraints, dualsOfBranches, branchingRule, config);
                PricingDiagnostics.recordExtensionElapsed(System.nanoTime() - extensionStartNanos);
                if (extended) {
                    PricingDiagnostics.recordLabelExtended();
                    Vertex headVertex = graph.getVertex(arc.getHeadIndex());
                    if (headVertex.addNonDominatedLabel(newLabel, activeCutDuals) && !headVertex.isReturnDepot()) {
                        unextendedLabels.add(newLabel);
                    }
                }
            }
            label.setExtended(true);
        }

        List<Label> labelsAtSink = new ArrayList<>(graph.getVertices().get(graph.getNumVertices() - 1).getLabelSet());
        labelsAtSink.sort(DeterministicOrdering.labelComparator());
        PricingDiagnostics.recordSinkLabels(labelsAtSink.size());
        return labelsAtSink;
    }

    protected void applyInitialBranchReducedCost(Label label,
                                                 Graph graph,
                                                 List<Constraint> branchConstraints,
                                                 List<Double> dualsOfBranches,
                                                 BranchingRule<Route, LmSrc, Graph> branchingRule) {
        if (label == null || branchingRule == null || branchConstraints == null || dualsOfBranches == null) {
            return;
        }
        int machineIndex = resolveMachineIndex(graph);
        for (int i = 0; i < Math.min(branchConstraints.size(), dualsOfBranches.size()); i++) {
            label.setReducedCost(branchingRule.updateInitialReducedCost(
                    machineIndex,
                    branchConstraints.get(i),
                    dualsOfBranches.get(i),
                    label.getReducedCost()
            ));
        }
    }

    protected List<Label> solve(List<Graph> graphs, double[] customerDuals, double[] startDepotDuals) {
        List<Label> labels = new ArrayList<>();
        for (Graph graph : graphs) {
            labels.addAll(solveLabels(graph, customerDuals, startDepotDuals));
        }
        return labels;
    }

    protected List<Label> solve(List<Graph> graphs, double[] nodeDuals) {
        return solve(graphs, nodeDuals, null);
    }

    protected List<Label> solveAll(List<Graph> graphs, double[] customerDuals, double[] startDepotDuals) {
        List<Label> labels = new ArrayList<>();
        for (Graph graph : graphs) {
            labels.addAll(solveAllLabels(graph, customerDuals, startDepotDuals));
        }
        return labels;
    }

    protected List<Label> solveAll(List<Graph> graphs, double[] nodeDuals) {
        return solveAll(graphs, nodeDuals, null);
    }

    protected List<Graph> buildGraphs(DynamicInstance instance) {
        if (instance == null) {
            throw new IllegalArgumentException("instance must not be null.");
        }
        instance.validate();

        List<Graph> graphs = new ArrayList<>();
        graphs.add(buildGraph(instance, instance.getOriginDepot().getId()));
        for (Depot externalDepot : instance.getExternalDepots()) {
            graphs.add(buildGraph(instance, externalDepot.getId()));
        }
        return graphs;
    }

    protected Graph buildGraph(DynamicInstance instance, int startDepotId) {
        if (instance == null) {
            throw new IllegalArgumentException("instance must not be null.");
        }
        Depot startDepot = instance.getStartDepotById(startDepotId);
        if (startDepot == null) {
            throw new IllegalArgumentException("Unknown start depot id: " + startDepotId);
        }
        Graph graph = new Graph();
        graph.buildFromInstance(instance, startDepotId);
        return graph;
    }

    protected boolean checkNonExtendedLabels(Graph graph) {
        for (Vertex vertex : graph.getVertices()) {
            if (vertex == null) {
                continue;
            }
            for (Label label : vertex.getLabelSet()) {
                if (!label.isExtended()) {
                    return true;
                }
            }
        }
        return false;
    }

    protected void clearLabels(Graph graph) {
        graph.clearLabels();
    }

    protected List<Label> filterImprovingLabels(List<Label> labels) {
        List<Label> improvingLabels = new ArrayList<>();
        for (Label label : labels) {
            if (label.getReducedCost() < -EPSILON) {
                improvingLabels.add(label);
            }
        }
        return improvingLabels;
    }

    protected void initializeCutState(Label label, List<LmSrc> cuts) {
        if (label == null) {
            throw new IllegalArgumentException("label must not be null.");
        }
        label.setState(new ArrayList<>());
        if (cuts == null || cuts.isEmpty()) {
            return;
        }
        for (int i = 0; i < cuts.size(); i++) {
            label.getState().add(0.0);
        }
    }

    protected Label createInitialLabel(Graph graph, double[] customerDuals) {
        return createInitialLabel(graph, customerDuals, null);
    }

    protected Label createInitialLabel(Graph graph, double[] customerDuals, double[] startDepotDuals) {
        DynamicInstance instance = graph.getInstance();
        Label initialLabel = new Label(graph.getId(), graph.getIndex(), getVisitedVectorSize(instance));
        initialLabel.setLastNodeId(graph.getVertex(0).getId());
        initialLabel.setStartServiceTime(instance.getRouteStartTime());
        initialLabel.setCapacityUsed(getInitialCapacityUsed(instance, graph.getStartDepotId()));
        initialLabel.getVisitedVertices().add(graph.getVertex(0).getId());
        initialLabel.getVisitedVector()[graph.getVertex(0).getId()] = 1;
        initialLabel.setReducedCost(-getNodeDual(startDepotDuals, graph.getStartDepotId()));
        return initialLabel;
    }

    protected boolean labelExtend(Label label, Vertex headVertex, Graph graph, double[] customerDuals) {
        return labelExtend(label, headVertex, graph, customerDuals, new ArrayList<>(), new ArrayList<>(), null);
    }

    protected boolean labelExtend(Label label,
                                  Vertex headVertex,
                                  Graph graph,
                                  double[] customerDuals,
                                  List<LmSrc> cuts,
                                  List<Double> dualsOfCuts,
                                  List<Constraint> branchConstraints,
                                  List<Double> dualsOfBranches,
                                  BranchingRule<Route, LmSrc, Graph> branchingRule) {
        return labelExtend(label, headVertex, graph, customerDuals, cuts, dualsOfCuts,
                branchConstraints, dualsOfBranches, branchingRule, null);
    }

    protected boolean labelExtend(Label label,
                                  Vertex headVertex,
                                  Graph graph,
                                  double[] customerDuals,
                                  List<LmSrc> cuts,
                                  List<Double> dualsOfCuts,
                                  List<Constraint> branchConstraints,
                                  List<Double> dualsOfBranches,
                                  BranchingRule<Route, LmSrc, Graph> branchingRule,
                                  Configuration config) {
        DynamicInstance instance = graph.getInstance();
        int tailNodeId = label.getLastNodeId();
        int headNodeId = headVertex.getId();
        int machineIndex = resolveMachineIndex(graph);
        boolean ngRouteEnabled = isNgRouteEnabled(config);

        if (isForbiddenByLeftBranch(machineIndex, tailNodeId, headNodeId, branchConstraints)) {
            return false;
        }

        if (!headVertex.isReturnDepot()) {
            if (!ngRouteEnabled && label.getVisitedVector()[headNodeId] > 0) {
                return false;
            }
            if (label.containsNgNode(headNodeId)) {
                return false;
            }
            double nextLoad = label.getLoad() + instance.getDemand(headNodeId);
            if (nextLoad > getCapacityLimit(instance, label.getId()) + EPSILON) {
                return false;
            }
        }

        double departureTime = label.getStartServiceTime() + instance.getServiceTime(tailNodeId);
        double arrivalTime = departureTime + instance.getTravelTime(tailNodeId, headNodeId);
        double nextStartServiceTime = Math.max(arrivalTime, instance.getEarliestTime(headNodeId));
        if (nextStartServiceTime > headVertex.getLatestTime() + EPSILON) {
            return false;
        }

        double arcDistance = instance.getDistance(tailNodeId, headNodeId);
        label.setLastNodeId(headNodeId);
        label.setStartServiceTime(nextStartServiceTime);
        label.getVisitedVertices().add(headNodeId);
        label.getVisitedVector()[headNodeId] += 1;
        label.setCost(label.getCost() + arcDistance);
        label.setReducedCost(label.getReducedCost()
                + arcDistance
                - getNodeDual(customerDuals, tailNodeId) / 2.0
                - getNodeDual(customerDuals, headNodeId) / 2.0);

        if (!headVertex.isReturnDepot()) {
            label.setLoad(label.getLoad() + instance.getDemand(headNodeId));
            label.setCapacityUsed(label.getCapacityUsed() + instance.getDemand(headNodeId));
            if (!updateNgMemory(label, headVertex, ngRouteEnabled)) {
                return false;
            }
        }

        updateCutState(label, machineIndex, headNodeId, cuts, dualsOfCuts);

        if (branchingRule != null && branchConstraints != null && dualsOfBranches != null) {
            for (int i = 0; i < Math.min(branchConstraints.size(), dualsOfBranches.size()); i++) {
                label.setReducedCost(
                        branchingRule.updateReducedCost(
                                machineIndex,
                                tailNodeId,
                                headNodeId,
                                branchConstraints.get(i),
                                dualsOfBranches.get(i),
                                label.getReducedCost()
                        )
                );
            }
        }
        return true;
    }

    private boolean isForbiddenByLeftBranch(int machineIndex,
                                            int tailNodeId,
                                            int headNodeId,
                                            List<Constraint> branchConstraints) {
        if (branchConstraints == null || branchConstraints.isEmpty()) {
            return false;
        }
        for (Constraint branchConstraint : branchConstraints) {
            if (branchConstraint == null || !"Left".equalsIgnoreCase(branchConstraint.getType())) {
                continue;
            }
            if (branchConstraint.getRightSideValue() != 0) {
                continue;
            }
            if (matchesBranchArc(machineIndex, tailNodeId, headNodeId, branchConstraint)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesBranchArc(int machineIndex,
                                     int tailNodeId,
                                     int headNodeId,
                                     Constraint branchConstraint) {
        if (branchConstraint.getVariable() == null || branchConstraint.getVariable().getIndexes() == null) {
            return false;
        }
        String variableType = branchConstraint.getVariable().getType();
        int[] indexes = branchConstraint.getVariable().getIndexes();
        if ("qij".equalsIgnoreCase(variableType) && indexes.length >= 2) {
            return indexes[0] == tailNodeId && indexes[1] == headNodeId;
        }
        if ("xkij".equalsIgnoreCase(variableType) && indexes.length >= 3) {
            return indexes[0] == machineIndex && indexes[1] == tailNodeId && indexes[2] == headNodeId;
        }
        return false;
    }

    private boolean isNgRouteEnabled(Configuration config) {
        return config != null
                && config.getRelaxation() != null
                && config.getRelaxation().isEnabled();
    }

    private boolean updateNgMemory(Label label, Vertex headVertex, boolean ngRouteEnabled) {
        if (ngRouteEnabled) {
            TIntHashSet retainedNodes = new TIntHashSet();
            for (int nodeId : label.snapshotNgNodes()) {
                if (PricingDiagnostics.isTimeLimitReached()) {
                    return false;
                }
                if (headVertex.getNgSet().contains(nodeId)) {
                    retainedNodes.add(nodeId);
                }
            }
            label.retainNgNodes(retainedNodes);
        }
        label.addNgNode(headVertex.getId());
        return true;
    }

    protected void updateCutState(Label label,
                                  int machineIndex,
                                  int headNodeId,
                                  List<LmSrc> cuts,
                                  List<Double> dualsOfCuts) {
        if (label == null || cuts == null || cuts.isEmpty()) {
            return;
        }
        if (label.getState() == null) {
            label.setState(new ArrayList<>());
        }
        while (label.getState().size() < cuts.size()) {
            label.getState().add(0.0);
        }
        for (int o = 0; o < cuts.size(); o++) {
            LmSrc cut = cuts.get(o);
            if (cut == null) {
                continue;
            }
            if (dualsOfCuts == null || o >= dualsOfCuts.size()) {
                continue;
            }
            double dualOfCut = dualsOfCuts.get(o);
            if (dualOfCut >= 0) {
                continue;
            }
            if (!containsInMemorySet(cut, machineIndex, headNodeId)) {
                label.getState().set(o, 0.0);
                continue;
            }
            if (cut.getSubSet() != null && cut.getSubSet().contains(headNodeId)) {
                double updatedState = label.getState().get(o) + cut.getP();
                if (updatedState >= 1.0 - EPSILON) {
                    label.setReducedCost(label.getReducedCost() - dualOfCut);
                    updatedState -= 1.0;
                }
                label.getState().set(o, updatedState);
            }
        }
    }

    private boolean containsInMemorySet(LmSrc cut, int machineIndex, int nodeId) {
        if (cut == null || cut.getMemorySet() == null || cut.getMemorySet().isEmpty()) {
            return cut != null && cut.getSubSet() != null && cut.getSubSet().contains(nodeId);
        }
        int index = Math.max(0, Math.min(machineIndex, cut.getMemorySet().size() - 1));
        return cut.getMemorySet().get(index) != null && cut.getMemorySet().get(index).contains(nodeId);
    }

    protected boolean labelExtend(Label label,
                                  Vertex headVertex,
                                  Graph graph,
                                  double[] customerDuals,
                                  List<Constraint> branchConstraints,
                                  List<Double> dualsOfBranches,
                                  BranchingRule<Route, LmSrc, Graph> branchingRule) {
        return labelExtend(label, headVertex, graph, customerDuals,
                new ArrayList<>(), new ArrayList<>(), branchConstraints, dualsOfBranches, branchingRule);
    }

    protected double getCapacityLimit(DynamicInstance instance, int startDepotId) {
        if (startDepotId == instance.getOriginDepot().getId()) {
            return instance.getProblemData().getVehicleCapacity();
        }
        Depot externalDepot = instance.getExternalDepotById(startDepotId);
        if (externalDepot != null) {
            return externalDepot.getRemainingCapacity();
        }
        throw new IllegalArgumentException("Unknown start depot id: " + startDepotId);
    }

    protected double getInitialCapacityUsed(DynamicInstance instance, int startDepotId) {
        if (startDepotId == instance.getOriginDepot().getId()) {
            return 0.0;
        }
        Depot externalDepot = instance.getExternalDepotById(startDepotId);
        if (externalDepot != null) {
            return instance.getProblemData().getVehicleCapacity() - externalDepot.getRemainingCapacity();
        }
        throw new IllegalArgumentException("Unknown start depot id: " + startDepotId);
    }

    protected List<Route> convertLabelsToRoutes(List<Label> labels, DynamicInstance instance) {
        List<Route> routes = new ArrayList<>();
        for (Label label : labels) {
            routes.add(convertLabelToRoute(label, instance));
        }
        return routes;
    }

    @Override
    public List<Route> convertLabelsToSchedules(List<Label> labels) {
        List<Route> routes = new ArrayList<>();
        if (labels == null) {
            return routes;
        }
        for (Label label : labels) {
            if (label == null) {
                continue;
            }
            routes.add(new Route(
                    label.getId(),
                    label.getIndex(),
                    label.getVisitedVertices(),
                    label.getVisitedVector(),
                    label.getCost(),
                    label.getLoad(),
                    new ArrayList<>(),
                    label.getReducedCost(),
                    0.0
            ));
        }
        return routes;
    }

    protected double getNodeDual(double[] nodeDuals, int nodeId) {
        if (nodeDuals == null || nodeId < 0 || nodeId >= nodeDuals.length) {
            return 0.0;
        }
        return nodeDuals[nodeId];
    }

    protected int getVisitedVectorSize(DynamicInstance instance) {
        int maxNodeId = Math.max(instance.getOriginDepot().getId(), instance.getReturnDepot().getId());
        for (Depot externalDepot : instance.getExternalDepots()) {
            if (externalDepot != null) {
                maxNodeId = Math.max(maxNodeId, externalDepot.getId());
            }
        }
        return maxNodeId + 1;
    }

    protected double[] createPricingNodeDuals(Graph graph, double[] customerDuals, double[] startDepotDuals) {
        int maxNodeId = graph.getInstance().getReturnDepot().getId();
        maxNodeId = Math.max(maxNodeId, graph.getStartDepotId());
        if (customerDuals != null) {
            maxNodeId = Math.max(maxNodeId, customerDuals.length - 1);
        }
        if (startDepotDuals != null) {
            maxNodeId = Math.max(maxNodeId, startDepotDuals.length - 1);
        }
        double[] pricingNodeDuals = new double[maxNodeId + 1];
        addDuals(pricingNodeDuals, customerDuals);
        return pricingNodeDuals;
    }

    protected double[] createStartDepotDuals(Graph graph, double dualOfMachine) {
        int maxNodeId = graph.getInstance().getReturnDepot().getId();
        maxNodeId = Math.max(maxNodeId, graph.getInstance().getOriginDepot().getId());
        maxNodeId = Math.max(maxNodeId, graph.getStartDepotId());
        for (Depot externalDepot : graph.getInstance().getExternalDepots()) {
            maxNodeId = Math.max(maxNodeId, externalDepot.getId());
        }
        double[] startDepotDuals = new double[maxNodeId + 1];
        startDepotDuals[graph.getStartDepotId()] = dualOfMachine;
        return startDepotDuals;
    }

    private void addDuals(double[] target, double[] source) {
        if (source == null) {
            return;
        }
        for (int i = 0; i < source.length && i < target.length; i++) {
            target[i] += source[i];
        }
    }

    protected int resolveMachineIndex(Graph graph) {
        if (graph.getStartDepotId() == graph.getInstance().getOriginDepot().getId()) {
            return 0;
        }
        for (int i = 0; i < graph.getInstance().getExternalDepots().size(); i++) {
            if (graph.getInstance().getExternalDepots().get(i).getId() == graph.getStartDepotId()) {
                return i + 1;
            }
        }
        throw new IllegalArgumentException("Unknown start depot id: " + graph.getStartDepotId());
    }

    @Override
    public String getStrategyName() {
        return "forward labeling";
    }

}

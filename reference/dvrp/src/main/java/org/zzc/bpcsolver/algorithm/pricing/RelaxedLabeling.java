package org.zzc.bpcsolver.algorithm.pricing;

import org.zzc.bpcsolver.algorithm.DeterministicOrdering;
import org.zzc.bpcsolver.algorithm.branching.BranchingRule;
import org.zzc.bpcsolver.algorithm.config.Configuration;
import org.zzc.bpcsolver.algorithm.cutting.LmSrc;
import org.zzc.bpcsolver.algorithm.logger.BPCLogger;
import org.zzc.bpcsolver.core.branch.Constraint;
import org.zzc.bpcsolver.core.graph.Arc;
import org.zzc.bpcsolver.core.graph.Graph;
import org.zzc.bpcsolver.core.graph.Route;
import org.zzc.bpcsolver.core.graph.Vertex;

import java.util.ArrayList;
import java.util.List;

public class RelaxedLabeling implements HeuristicPricing<Route, Graph, LmSrc> {

    public static final String STRATEGY_NAME = "relaxed labeling";
    private static final int DEFAULT_MAX_LABELS_PER_VERTEX = 2;

    private final ForwardLabeling forwardLabeling = new ForwardLabeling();

    @Override
    public List<Route> solve(Graph graph, int index, List<LmSrc> cuts, List<Constraint> branchConstraints,
                             double dualsOfMachines, double[] dualsOfJobs, List<Double> dualsOfCuts,
                             List<Double> dualsOfBranches, BranchingRule<Route, LmSrc, Graph> branchingRule,
                             Configuration config, BPCLogger logger, int iter) {
        if (graph == null) {
            throw new IllegalArgumentException("graph must not be null.");
        }

        forwardLabeling.clearLabels(graph);
        double[] startDepotDuals = forwardLabeling.createStartDepotDuals(graph, dualsOfMachines);
        double[] pricingNodeDuals = forwardLabeling.createPricingNodeDuals(graph, dualsOfJobs, startDepotDuals);
        Label initialLabel = forwardLabeling.createInitialLabel(graph, dualsOfJobs, startDepotDuals);
        forwardLabeling.initializeCutState(initialLabel, cuts);
        forwardLabeling.applyInitialBranchReducedCost(initialLabel, graph, branchConstraints, dualsOfBranches, branchingRule);
        graph.getVertex(0).addNonDominatedLabel(initialLabel, dualsOfCuts);

        while (forwardLabeling.checkNonExtendedLabels(graph)) {
            if (PricingDiagnostics.isTimeLimitReached()) {
                break;
            }
            for (Vertex tailVertex : graph.getVertices()) {
                if (PricingDiagnostics.isTimeLimitReached()) {
                    break;
                }
                if (tailVertex == null) {
                    continue;
                }
                for (Label label : new ArrayList<>(tailVertex.getLabelSet())) {
                    if (PricingDiagnostics.isTimeLimitReached()) {
                        break;
                    }
                    if (label.isExtended()) {
                        continue;
                    }
                    for (Arc arc : graph.getOutgoingArcs(tailVertex.getIndex())) {
                        if (PricingDiagnostics.isTimeLimitReached()) {
                            break;
                        }
                        if (arc.isFixed()) {
                            continue;
                        }
                        Label newLabel = new Label(label);
                        PricingDiagnostics.recordLabelCreated();
                        long extensionStartNanos = System.nanoTime();
                        boolean extended = forwardLabeling.labelExtend(newLabel, graph.getVertex(arc.getHeadIndex()), graph,
                                pricingNodeDuals, cuts, dualsOfCuts, branchConstraints, dualsOfBranches,
                                branchingRule, config);
                        PricingDiagnostics.recordExtensionElapsed(System.nanoTime() - extensionStartNanos);
                        if (extended) {
                            PricingDiagnostics.recordLabelExtended();
                            acceptLabel(graph.getVertex(arc.getHeadIndex()), newLabel, dualsOfCuts, config);
                        }
                    }
                    label.setExtended(true);
                }
            }
        }

        List<Label> labelsAtSink = new ArrayList<>(graph.getVertex(graph.getNumVertices() - 1).getLabelSet());
        labelsAtSink.sort(DeterministicOrdering.labelComparator());
        PricingDiagnostics.recordSinkLabels(labelsAtSink.size());
        List<Route> routes = forwardLabeling.convertLabelsToRoutes(
                forwardLabeling.filterImprovingLabels(labelsAtSink),
                graph.getInstance()
        );
        routes.sort(DeterministicOrdering.routeComparator());
        return routes;
    }

    private void acceptLabel(Vertex vertex, Label label, List<Double> dualsOfCuts, Configuration config) {
        if (vertex.addNonDominatedLabel(label, dualsOfCuts)) {
            trimLabels(vertex, maxLabelsPerVertex(config));
        }
    }

    private int maxLabelsPerVertex(Configuration config) {
        if (config != null
                && config.getRelaxation() != null
                && config.getRelaxation().getMaxNgSize() > 0) {
            return config.getRelaxation().getMaxNgSize();
        }
        return DEFAULT_MAX_LABELS_PER_VERTEX;
    }

    private void trimLabels(Vertex vertex, int maxLabels) {
        if (maxLabels <= 0 || vertex.getLabelSet().size() <= maxLabels) {
            return;
        }
        vertex.getLabelSet().sort(DeterministicOrdering.labelComparator());
        while (vertex.getLabelSet().size() > maxLabels) {
            vertex.removeLabel(vertex.getLabelSet().get(vertex.getLabelSet().size() - 1));
        }
    }

    @Override
    public void reset(Configuration config) {
        setCurrentHeuristic(config, STRATEGY_NAME);
    }

    @Override
    public String switchHeuristic(Configuration config, String type) {
        return "Exact Pricing";
    }

    @Override
    public String getCurrentHeuristic(Configuration config) {
        if (config == null || config.getPricing() == null) {
            return STRATEGY_NAME;
        }
        String currentHeuristic = config.getPricing().getCurrentHeuristic();
        if (currentHeuristic == null || currentHeuristic.trim().isEmpty()) {
            return STRATEGY_NAME;
        }
        return currentHeuristic;
    }

    @Override
    public void setCurrentHeuristic(Configuration config, String heuristic) {
        if (config != null && config.getPricing() != null) {
            config.getPricing().setCurrentHeuristic(heuristic);
        }
    }

    @Override
    public String getStrategyName() {
        return STRATEGY_NAME;
    }
}

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

public class LightLabeling implements HeuristicPricing<Route, Graph, LmSrc> {

    public static final String STRATEGY_NAME = "light labeling";

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
                            acceptLabel(graph.getVertex(arc.getHeadIndex()), newLabel);
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

    private void acceptLabel(Vertex vertex, Label label) {
        if (vertex.getLabelSet().isEmpty()) {
            vertex.replaceLabelsWith(label);
            return;
        }
        Label incumbent = vertex.getLabelSet().get(0);
        if (DeterministicOrdering.labelComparator().compare(label, incumbent) < 0) {
            vertex.replaceLabelsWith(label);
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

package org.zzc.bpcsolver.algorithm;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.zzc.bpcsolver.algorithm.config.Configuration;
import org.zzc.bpcsolver.algorithm.cutting.CutTemplate;
import org.zzc.bpcsolver.algorithm.branching.variableSelection.StrongBranchingEvaluator;
import org.zzc.bpcsolver.algorithm.factory.BranchingStrategyFactory;
import org.zzc.bpcsolver.algorithm.logger.BPCLogger;
import org.zzc.bpcsolver.algorithm.strategy.AccelerationStrategy;
import org.zzc.bpcsolver.algorithm.strategy.BranchingStrategy;
import org.zzc.bpcsolver.algorithm.strategy.CuttingStrategy;
import org.zzc.bpcsolver.algorithm.strategy.PricingStrategy;
import org.zzc.bpcsolver.algorithm.pricing.PricingDiagnostics;
import org.zzc.bpcsolver.core.branch.ColumnGenerationStatus;
import org.zzc.bpcsolver.core.branch.Constraint;
import org.zzc.bpcsolver.core.branch.Node;
import org.zzc.bpcsolver.core.branch.Solution;
import org.zzc.bpcsolver.core.branch.Variable;
import org.zzc.bpcsolver.core.template.ArcTemplate;
import org.zzc.bpcsolver.core.template.CycleTemplate;
import org.zzc.bpcsolver.core.template.GraphTemplate;
import org.zzc.bpcsolver.core.template.PathTemplate;
import org.zzc.bpcsolver.core.template.VertexTemplate;
import org.zzc.bpcsolver.problem.template.DynamicInstanceTemplate;
import java.util.*;
import java.util.stream.Collectors;

@Getter
@Setter
public abstract class BPCSolverTemplate<T extends DynamicInstanceTemplate, S extends PathTemplate, C extends CutTemplate, G extends GraphTemplate<V, A>, V extends VertexTemplate, A extends ArcTemplate, R extends CycleTemplate, M> {

    protected static final double INCUMBENT_TOLERANCE = 1e-6;

    protected Queue<Node<S,C,G>> searchTree;
    protected Node<S, C, G> optimalNode;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    protected BranchSearchStatistics lastBranchSearchStatistics = BranchSearchStatistics.notStarted();
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private BranchSearchStatistics activeBranchSearchStatistics;
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private BPCRunStatistics activeRunStatistics;
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    protected BPCRunStatistics lastRunStatistics = BPCRunStatistics.empty();
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private boolean rootUpperBoundAvailableForBranching;
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private CutSeparationGuardState cutSeparationGuardState = new CutSeparationGuardState();

    protected Configuration config;
    protected T instance;
    protected BPCLogger logger;

    protected long startTime;
    protected long solveStartTime;
    protected long branchingTimeLimitSeconds;
    protected int numNodesExplored;

    protected AccelerationStrategy<T, S, C, G> accelerationStrategy;
    protected BranchingStrategy<T, S, C, G> branchingStrategy;
    protected PricingStrategy<T, S, C, G> pricingStrategy;
    protected CuttingStrategy<T, S, C, G> cuttingStrategy;

    protected void initialize (T instance, Configuration config, BPCLogger logger) {

        this.instance = instance;
        this.config = config;
        this.logger = logger;

        this.accelerationStrategy = new AccelerationStrategy<>();
        this.branchingStrategy = new BranchingStrategy<>();

        this.pricingStrategy = new PricingStrategy<>();
        this.cuttingStrategy = new CuttingStrategy<>();

        this.optimalNode = new Node<>();
        this.lastBranchSearchStatistics = BranchSearchStatistics.notStarted();
        this.activeBranchSearchStatistics = null;
        this.lastRunStatistics = BPCRunStatistics.empty();
        this.activeRunStatistics = null;
        this.rootUpperBoundAvailableForBranching = false;
        this.cutSeparationGuardState = new CutSeparationGuardState();
        if (this.branchingTimeLimitSeconds <= 0L) {
            this.branchingTimeLimitSeconds = 36_000L;
        }
        this.solveStartTime = 0L;

        this.numNodesExplored = 1;
    }

    protected BPCRunStatistics getLastRunStatistics() {
        return this.lastRunStatistics == null
                ? BPCRunStatistics.empty()
                : this.lastRunStatistics.copy();
    }

    protected void startRunStatistics() {
        this.activeRunStatistics = BPCRunStatistics.started();
        this.lastRunStatistics = this.activeRunStatistics.copy();
    }

    protected void finishRunStatistics() {
        if (this.activeRunStatistics == null) {
            return;
        }
        this.activeRunStatistics.finish();
        this.lastRunStatistics = this.activeRunStatistics.copy();
        this.activeRunStatistics = null;
    }

    private void recordColumnGenerationStart() {
        if (this.activeRunStatistics != null) {
            this.activeRunStatistics.recordColumnGenerationStart();
        }
    }

    private void recordColumnGenerationIteration(String type) {
        if (this.activeRunStatistics != null) {
            this.activeRunStatistics.recordColumnGenerationIteration(type);
        }
    }

    protected void recordMasterSolve() {
        if (this.activeRunStatistics != null) {
            this.activeRunStatistics.recordMasterSolve();
        }
    }

    protected void recordMasterSolveElapsed(long elapsedNanos) {
        if (this.activeRunStatistics != null) {
            this.activeRunStatistics.recordMasterSolveElapsed(elapsedNanos);
        }
    }

    protected void recordPricingCall(String type) {
        if (this.activeRunStatistics != null) {
            this.activeRunStatistics.recordPricingCall(type);
        }
    }

    protected void recordPricingElapsed(String type, long elapsedNanos) {
        if (this.activeRunStatistics != null) {
            this.activeRunStatistics.recordPricingElapsed(type, elapsedNanos);
        }
        recordCutGuardPricingElapsed(type, elapsedNanos);
    }

    protected void recordPricingDiagnostics(PricingDiagnostics.Snapshot snapshot) {
        if (this.activeRunStatistics != null && snapshot != null) {
            this.activeRunStatistics.recordPricingDiagnostics(snapshot);
        }
    }

    private void recordPrimalHeuristicStart() {
        if (this.activeRunStatistics != null) {
            this.activeRunStatistics.recordPrimalHeuristicStart();
        }
    }

    private void recordPrimalHeuristicElapsed(long elapsedNanos) {
        if (this.activeRunStatistics != null) {
            this.activeRunStatistics.recordPrimalHeuristicElapsed(elapsedNanos);
        }
    }

    private void recordIntegerFlowRepairAttempt(boolean success) {
        if (this.activeRunStatistics != null) {
            this.activeRunStatistics.recordIntegerFlowRepairAttempt(success);
        }
    }

    protected final void recordIntegerFlowRepairFailure(String reason) {
        if (this.activeRunStatistics != null) {
            this.activeRunStatistics.recordIntegerFlowRepairFailure(reason);
        }
    }

    private void recordColumnsAdded(int count) {
        if (this.activeRunStatistics != null) {
            this.activeRunStatistics.recordColumnsAdded(count);
        }
    }

    private void recordCutSeparationResult(int generatedCount, int addedCount, long elapsedNanos) {
        if (this.activeRunStatistics != null) {
            this.activeRunStatistics.recordCutSeparationResult(generatedCount, addedCount, elapsedNanos);
        }
    }

    protected Queue<Node<S, C, G>> createSearchTree() {
        return branchingStrategy.getNodeSelectionStrategy().createSearchTree();
    }

    protected void initializeBranchingComponents() {
        this.branchingStrategy.setBranchingRule(BranchingStrategyFactory.createBranchingRule(this.config));
        this.branchingStrategy.setVariableSelectionStrategy(BranchingStrategyFactory.createVariableSelectionStrategy(this.config));
        this.branchingStrategy.getVariableSelectionStrategy().Initialize(
                this.instance,
                this.config,
                this.pricingStrategy.getHeuristicPricingAlgorithm(),
                this.branchingStrategy.getBranchingRule()
        );
        this.branchingStrategy.getVariableSelectionStrategy().setStrongBranchingEvaluator(
                createStrongBranchingEvaluator()
        );
        this.branchingStrategy.setNodeSelectionStrategy(BranchingStrategyFactory.createNodeSelectionStrategy(this.config));
    }

    protected class RootSolveState {
        private final Node<S, C, G> rootNode;
        private final ColumnGenerationStatus columnGenerationStatus;

        protected RootSolveState(Node<S, C, G> rootNode, ColumnGenerationStatus columnGenerationStatus) {
            this.rootNode = rootNode;
            this.columnGenerationStatus = columnGenerationStatus == null
                    ? ColumnGenerationStatus.NOT_STARTED
                    : columnGenerationStatus;
        }

        public Node<S, C, G> getRootNode() {
            return rootNode;
        }

        public ColumnGenerationStatus getColumnGenerationStatus() {
            return columnGenerationStatus;
        }
    }

    protected RootSolveState solveRootNode(T instance,
                                           int maxAddedColumnsPerIteration,
                                           int maxIterations) {
        List<S> initialSchedules = generateInitialColumns(instance);
        if (initialSchedules.isEmpty()) {
            throw new IllegalStateException("No feasible initial columns were generated for the root solve.");
        }
        prepareRootSolveContext(instance, initialSchedules, maxAddedColumnsPerIteration);
        this.solveStartTime = System.nanoTime();
        startRunStatistics();
        initializeBranchingComponents();
        M masterProblem = initializeRootMasterProblem(instance, initialSchedules);
        try {
            Node<S, C, G> rootNode = createRootNode();
            ColumnGenerationStatus columnGenerationStatus = columnGeneration(masterProblem, rootNode, maxIterations);
            if (isColumnGenerationClosed(rootNode) && rootNode.isInteger()) {
                this.optimalNode = rootNode.copy();
                this.rootUpperBoundAvailableForBranching = false;
            } else {
                this.optimalNode = null;
                if (isColumnGenerationClosed(rootNode)) {
                    calculateUpperBound(rootNode);
                    this.rootUpperBoundAvailableForBranching = hasIntegerIncumbent();
                } else {
                    this.rootUpperBoundAvailableForBranching = false;
                }
            }
            return new RootSolveState(rootNode, columnGenerationStatus);
        } finally {
            closeMasterProblem(masterProblem);
        }
    }

    protected Node<S, C, G> branchAndPriceFromRootNode(Node<S, C, G> rootNode, int maxIterations) {
        if (rootNode == null) {
            throw new IllegalArgumentException("rootNode must not be null.");
        }
        if (maxIterations < 0) {
            throw new IllegalArgumentException("maxIterations must be non-negative; use 0 for convergence-driven CG.");
        }

        if (this.solveStartTime <= 0L) {
            this.solveStartTime = System.nanoTime();
        }
        initializeBranchingComponents();
        this.searchTree = createSearchTree();
        this.startTime = System.nanoTime();
        this.numNodesExplored = 1;
        this.activeBranchSearchStatistics = BranchSearchStatistics.started(this.numNodesExplored);
        this.lastBranchSearchStatistics = this.activeBranchSearchStatistics.copy();
        if (this.logger != null) {
            this.logger.startTimer("BRANCHING");
        }

        if (isColumnGenerationClosed(rootNode) && rootNode.isInteger()) {
            this.optimalNode = rootNode.copy();
            finishBranchSearch(BranchSearchTermination.ROOT_INTEGER, rootNode);
            return this.optimalNode.copy();
        }

        if (!isColumnGenerationClosed(rootNode)) {
            finishBranchSearch(BranchSearchTermination.CG_ITERATION_LIMIT, rootNode);
            return rootNode.copy();
        }

        if (this.rootUpperBoundAvailableForBranching && hasIntegerIncumbent()) {
            this.rootUpperBoundAvailableForBranching = false;
        } else {
            calculateUpperBound(rootNode);
            this.rootUpperBoundAvailableForBranching = false;
        }
        rootNode.getGraphs().forEach(GraphTemplate::clearLabels);
        this.searchTree.offer(rootNode);
        recordQueuedSearchNode();

        while (!this.searchTree.isEmpty() && !isBranchingTerminated()) {
            Node<S, C, G> currentNode = this.searchTree.poll();
            if (currentNode == null) {
                continue;
            }
            recordProcessedSearchNode();
            if (shouldPruneByCurrentIncumbent(currentNode)) {
                recordPrunedSearchNode();
                continue;
            }

            List<Variable> fractionalVars = getFractionalVariables(currentNode);
            if (fractionalVars == null || fractionalVars.isEmpty()) {
                recordBranchingFailureNoFractionalVariables();
                continue;
            }

            Variable branchingVar = selectBranchingVariable(fractionalVars, currentNode);
            if (branchingVar == null) {
                recordBranchingFailureNoSelectedVariable();
                continue;
            }

            for (int i = 0; i < 2; i++) {
                if (isBranchingTerminated()) {
                    break;
                }
                this.numNodesExplored++;
                recordChildSearchNodeSolved();
                Node<S, C, G> childNode = solveBranchedChildNode(currentNode, branchingVar, i, maxIterations);
                processSolvedChildNode(childNode);
            }
        }

        BranchSearchTermination terminationReason = isBranchingTerminated()
                ? BranchSearchTermination.TIME_LIMIT
                : BranchSearchTermination.TREE_EXHAUSTED;
        finishBranchSearch(terminationReason, rootNode);
        return hasIntegerIncumbent() ? this.optimalNode.copy() : rootNode.copy();
    }

    private boolean hasIntegerIncumbent() {
        return this.optimalNode != null
                && this.optimalNode.isFeasible()
                && this.optimalNode.isInteger()
                && this.optimalNode.getSolution() != null;
    }

    protected final void processSolvedChildNode(Node<S, C, G> childNode) {
        if (childNode == null || !childNode.isFeasible()) {
            recordInfeasibleSearchChild();
            return;
        }
        if (!isColumnGenerationClosed(childNode)) {
            recordBranchingFailureCgNotClosed();
            return;
        }
        if (shouldPruneByCurrentIncumbent(childNode)) {
            recordPrunedSearchNode();
            return;
        }
        if (childNode.isInteger()) {
            updateIncumbentFromIntegerNode(childNode);
            return;
        }
        childNode.getGraphs().forEach(GraphTemplate::clearLabels);
        this.searchTree.offer(childNode);
        recordQueuedSearchNode();
        calculateUpperBound(childNode);
    }

    protected final void updateIncumbentFromIntegerNode(Node<S, C, G> node) {
        if (node == null || node.getSolution() == null) {
            return;
        }
        if (!hasIntegerIncumbent()
                || node.getSolution().getObjValue() < this.optimalNode.getSolution().getObjValue() - INCUMBENT_TOLERANCE) {
            this.optimalNode = node.copy();
            recordIncumbentUpdate();
        }
    }

    protected final boolean shouldPruneByCurrentIncumbent(Node<S, C, G> node) {
        return hasIntegerIncumbent()
                && node != null
                && node.isFeasible()
                && node.getSolution() != null
                && node.getSolution().getObjValue() > this.optimalNode.getSolution().getObjValue() + INCUMBENT_TOLERANCE;
    }

    protected Node<S, C, G> solveBranchedChildNode(Node<S, C, G> parentNode,
                                                  Variable branchingVar,
                                                  int branchFlag,
                                                  int maxIterations) {
        if (parentNode == null) {
            throw new IllegalArgumentException("parentNode must not be null.");
        }
        if (branchingVar == null) {
            throw new IllegalArgumentException("branchingVar must not be null.");
        }
        if (maxIterations < 0) {
            throw new IllegalArgumentException("maxIterations must be non-negative; use 0 for convergence-driven CG.");
        }
        if (this.branchingStrategy.getBranchingRule() == null) {
            initializeBranchingComponents();
        }

        Node<S, C, G> childNode = createChildNode(parentNode, branchingVar, branchFlag);
        M masterProblem = initializeNodeMasterProblem(this.instance, childNode);
        try {
            columnGeneration(masterProblem, childNode, maxIterations);
            return childNode;
        } finally {
            closeMasterProblem(masterProblem);
        }
    }

    protected Node<S, C, G> createRootNode(){
        List<G> graphs = initializeGraphs();
        initializeRelaxation(graphs);
        logger.startTimer("PRIMAL_HEURISTIC");
        Solution<S> initialSol = obtainInitialSolution(graphs);
        logger.logPrimalHeuristic(this.accelerationStrategy.getPrimalHeuristic().getStrategyName(), initialSol.getObjValue());
        List<List<S>> usedScheduleSet = initializeRootUsedScheduleSet(initialSol);
        Node<S, C, G> rootNode = materializeRootNode(
                graphs,
                initialSol,
                usedScheduleSet,
                new ArrayList<>(),
                new ArrayList<>(),
                true,
                false
        );
        rootNode.setInteger(true);
        rootNode.setFeasible(true);
        this.optimalNode = rootNode.copy();
        return rootNode;
    }

    protected List<List<S>> initializeRootUsedScheduleSet(Solution<S> initialSolution) {
        List<List<S>> usedScheduleSet = new ArrayList<>();
        for (int k = 0; k < this.instance.getNumMachines(); k++) {
            List<S> schedules = new ArrayList<>();
            schedules.add(initialSolution.getBestSchedules().get(k));
            usedScheduleSet.add(schedules);
        }
        return usedScheduleSet;
    }

    protected Node<S, C, G> materializeRootNode(List<G> graphs,
                                                Solution<S> solution,
                                                List<List<S>> usedScheduleSet,
                                                List<C> usedCutSet,
                                                List<Constraint> branchConstraints,
                                                boolean feasible,
                                                boolean deriveIntegralityFromSolution) {
        Node<S, C, G> rootNode = new Node<>();
        rootNode.setGraphs(graphs);
        rootNode.setSolution(solution);
        rootNode.setUsedScheduleSet(usedScheduleSet);
        rootNode.setUsedCutSet(usedCutSet);
        rootNode.setBranchConstraints(branchConstraints);
        rootNode.setFeasible(feasible);
        if (deriveIntegralityFromSolution) {
            rootNode.checkIntegrality();
        }
        return rootNode;
    }

    protected ColumnGenerationStatus columnGeneration(M masterProblem, Node<S, C, G> node, int maxIterations) {
        recordColumnGenerationStart();
        resetCutSeparationGuard();
        node.setColumnGenerationStatus(ColumnGenerationStatus.NOT_STARTED);
        String type = "Exact Pricing";
        if (this.config.getPricing().isHeuristicEnabled()) {
            type = "Heuristic Pricing";
            setHeuristicPricing();
        }

        int iter = 0;
        M activeMasterProblem = masterProblem;
        try {
            while (hasColumnGenerationIterationBudget(iter, maxIterations)) {
                if (isSolveTimeLimitReached()) {
                    node.setInteger(false);
                    node.setColumnGenerationStatus(ColumnGenerationStatus.ITERATION_LIMIT);
                    return ColumnGenerationStatus.ITERATION_LIMIT;
                }
                iter++;
                recordColumnGenerationIteration(type);
                if (!solveMasterProblem(activeMasterProblem)) {
                    node.setFeasible(false);
                    node.setInteger(false);
                    node.setColumnGenerationStatus(ColumnGenerationStatus.MASTER_INFEASIBLE);
                    return ColumnGenerationStatus.MASTER_INFEASIBLE;
                }
                updateNodeFromMasterProblem(node, activeMasterProblem);
                List<S> newSchedules = collectNewColumns(node, type, iter);
                if (wasLastPricingTimeLimitReached()) {
                    node.setInteger(false);
                    node.setColumnGenerationStatus(ColumnGenerationStatus.ITERATION_LIMIT);
                    return ColumnGenerationStatus.ITERATION_LIMIT;
                }
                if (newSchedules.isEmpty()) {
                    if ("Exact Pricing".equals(type)) {
                        if (trySeparateAndAddCuts(activeMasterProblem, node)) {
                            continue;
                        }
                        if (tryUpdateRelaxation(node)) {
                            activeMasterProblem = rebuildMasterProblemForNode(activeMasterProblem, node);
                            iter = 0;
                            continue;
                        }
                        node.setColumnGenerationStatus(ColumnGenerationStatus.CONVERGED);
                        node.checkIntegrality();
                        if (!node.isInteger()) {
                            boolean repaired = repairRouteFractionalIntegerFlow(node);
                            recordIntegerFlowRepairAttempt(repaired);
                        }
                        return ColumnGenerationStatus.CONVERGED;
                    }
                    iter = 0;
                    type = switchHeuristicPricing(type);
                    continue;
                }
                recordColumnsAdded(newSchedules.size());
                addColumnsToMasterProblem(activeMasterProblem, newSchedules);
                appendColumnsToNode(node, newSchedules);
            }

            if (solveMasterProblem(activeMasterProblem)) {
                updateNodeFromMasterProblem(node, activeMasterProblem);
                node.setInteger(false);
                node.setColumnGenerationStatus(ColumnGenerationStatus.ITERATION_LIMIT);
                return ColumnGenerationStatus.ITERATION_LIMIT;
            } else {
                node.setFeasible(false);
                node.setInteger(false);
                node.setColumnGenerationStatus(ColumnGenerationStatus.MASTER_INFEASIBLE);
                return ColumnGenerationStatus.MASTER_INFEASIBLE;
            }
        } finally {
            if (activeMasterProblem != masterProblem) {
                closeMasterProblem(activeMasterProblem);
            }
        }
    }

    protected final boolean isColumnGenerationClosed(Node<S, C, G> node) {
        return node != null
                && node.getColumnGenerationStatus() != null
                && node.getColumnGenerationStatus().isClosed();
    }

    private boolean hasColumnGenerationIterationBudget(int completedIterations, int maxIterations) {
        return maxIterations == 0 || completedIterations < maxIterations;
    }

    private boolean isSolvingBranchChild() {
        return this.activeBranchSearchStatistics != null;
    }

    private void initializeRelaxation(List<G> graphs) {
        if (this.config == null
                || this.config.getRelaxation() == null
                || !this.config.getRelaxation().isEnabled()
                || this.accelerationStrategy == null
                || this.accelerationStrategy.getRelaxation() == null) {
            return;
        }
        this.accelerationStrategy.getRelaxation().initialize(this.config, graphs);
    }

    private boolean tryUpdateRelaxation(Node<S, C, G> node) {
        if (this.config == null
                || this.config.getRelaxation() == null
                || !this.config.getRelaxation().isEnabled()
                || this.accelerationStrategy == null
                || this.accelerationStrategy.getRelaxation() == null
                || node == null) {
            return false;
        }
        return this.accelerationStrategy.getRelaxation().update(node);
    }

    protected M rebuildMasterProblemForNode(M currentMasterProblem, Node<S, C, G> node) {
        closeMasterProblem(currentMasterProblem);
        return initializeNodeMasterProblem(this.instance, node);
    }

    private StrongBranchingEvaluator<S, C, G> createStrongBranchingEvaluator() {
        return new StrongBranchingEvaluator<S, C, G>() {
            @Override
            public Node<S, C, G> solveRestrictedMasterChild(Node<S, C, G> parentNode,
                                                            Variable branchingVar,
                                                            int branchFlag) {
                return BPCSolverTemplate.this.solveRestrictedMasterChildForStrongBranching(
                        parentNode,
                        branchingVar,
                        branchFlag
                );
            }

            @Override
            public Node<S, C, G> runHeuristicColumnGeneration(Node<S, C, G> childNode, BPCLogger logger) {
                return BPCSolverTemplate.this.runHeuristicColumnGenerationForStrongBranching(childNode);
            }
        };
    }

    protected Node<S, C, G> solveRestrictedMasterChildForStrongBranching(Node<S, C, G> parentNode,
                                                                         Variable branchingVar,
                                                                         int branchFlag) {
        Node<S, C, G> childNode = createChildNode(parentNode, branchingVar, branchFlag);
        M masterProblem = initializeNodeMasterProblem(this.instance, childNode);
        try {
            if (!solveMasterProblem(masterProblem)) {
                childNode.setFeasible(false);
                childNode.setInteger(false);
                childNode.setColumnGenerationStatus(ColumnGenerationStatus.MASTER_INFEASIBLE);
                return childNode;
            }
            updateNodeFromMasterProblem(childNode, masterProblem);
            childNode.setColumnGenerationStatus(ColumnGenerationStatus.NOT_STARTED);
            childNode.isIntegerSolution();
            return childNode;
        } finally {
            closeMasterProblem(masterProblem);
        }
    }

    protected Node<S, C, G> runHeuristicColumnGenerationForStrongBranching(Node<S, C, G> childNode) {
        if (childNode == null || !childNode.isFeasible()) {
            return childNode;
        }
        ensureStrongBranchingHeuristicPricing();
        setHeuristicPricing();
        String type = "Heuristic Pricing";
        M masterProblem = initializeNodeMasterProblem(this.instance, childNode);
        try {
            int iter = 0;
            while (true) {
                iter++;
                if (!solveMasterProblem(masterProblem)) {
                    childNode.setFeasible(false);
                    childNode.setInteger(false);
                    childNode.setColumnGenerationStatus(ColumnGenerationStatus.MASTER_INFEASIBLE);
                    return childNode;
                }
                updateNodeFromMasterProblem(childNode, masterProblem);
                List<S> newSchedules = collectNewColumns(childNode, type, iter);
                if (newSchedules.isEmpty()) {
                    if ("Exact Pricing".equals(type)) {
                        childNode.setColumnGenerationStatus(ColumnGenerationStatus.NOT_STARTED);
                        childNode.isIntegerSolution();
                        return childNode;
                    }
                    iter = 0;
                    type = switchHeuristicPricing(type);
                    if ("Exact Pricing".equals(type)) {
                        childNode.setColumnGenerationStatus(ColumnGenerationStatus.NOT_STARTED);
                        childNode.isIntegerSolution();
                        return childNode;
                    }
                    continue;
                }
                addColumnsToMasterProblem(masterProblem, newSchedules);
                appendColumnsToNode(childNode, newSchedules);
            }
        } finally {
            closeMasterProblem(masterProblem);
        }
    }

    private boolean trySeparateAndAddCuts(M masterProblem, Node<S, C, G> node) {
        if (this.config == null
                || this.config.getCutting() == null
                || !this.config.getCutting().isEnabled()
                || node == null) {
            return false;
        }
        if (shouldSkipCutSeparation(node)) {
            return false;
        }
        int remainingSlots = this.config.getCutting().getMaxNumCut() - countCuts(node);
        if (remainingSlots <= 0) {
            return false;
        }

        long startNanos = System.nanoTime();
        int generatedCount = 0;
        int addedCount = 0;
        try {
            List<C> generatedCuts = cutGeneration(node);
            generatedCount = generatedCuts == null ? 0 : generatedCuts.size();
            if (generatedCuts == null || generatedCuts.isEmpty()) {
                return false;
            }
            List<C> cuts = new ArrayList<>(generatedCuts);
            cuts.removeIf(node.getUsedCutSet()::contains);
            if (cuts.isEmpty()) {
                return false;
            }

            int configuredLimit = this.config.getCutting().getNumAddedCut();
            int numAddedCut = Math.min(remainingSlots, configuredLimit <= 0 ? remainingSlots : configuredLimit);
            if (numAddedCut <= 0) {
                return false;
            }
            List<C> selectedCuts = cuts.stream()
                    .limit(numAddedCut)
                    .collect(Collectors.toList());
            addedCount = selectedCuts.size();
            addCutsToMasterProblem(masterProblem, selectedCuts);
            appendCutsToNode(node, selectedCuts);
            recordCutSeparationAccepted(node, addedCount);
            return true;
        } finally {
            recordCutSeparationResult(generatedCount, addedCount, System.nanoTime() - startNanos);
        }
    }

    private void resetCutSeparationGuard() {
        this.cutSeparationGuardState = new CutSeparationGuardState();
    }

    private void recordCutGuardPricingElapsed(String type, long elapsedNanos) {
        if (!"Exact Pricing".equals(type)
                || elapsedNanos <= 0L
                || this.cutSeparationGuardState == null
                || !isLmSrcCuttingGuardEnabled()) {
            return;
        }
        this.cutSeparationGuardState.maxExactPricingCallNanos = Math.max(
                this.cutSeparationGuardState.maxExactPricingCallNanos,
                elapsedNanos
        );
    }

    private boolean shouldSkipCutSeparation(Node<S, C, G> node) {
        if (!isLmSrcCuttingGuardEnabled()) {
            return false;
        }
        CutSeparationGuardState state = getCutSeparationGuardState();
        double pricingLimitSeconds = this.config.getCutting().getStopAfterPricingSeconds();
        if (pricingLimitSeconds > 0.0
                && state.maxExactPricingCallNanos > secondsToNanos(pricingLimitSeconds)) {
            state.stoppedBySlowPricing = true;
            recordCutGuardState(state);
            return true;
        }
        if (state.stoppedByTrailing) {
            recordCutGuardState(state);
            return true;
        }
        return false;
    }

    private void recordCutSeparationAccepted(Node<S, C, G> node, int addedCount) {
        if (addedCount <= 0 || !isLmSrcCuttingGuardEnabled()) {
            return;
        }
        CutSeparationGuardState state = getCutSeparationGuardState();
        state.cutRoundsWithAddedCuts++;
        double currentGap = calculateCutGuardGap(node);
        state.lastGap = currentGap;
        int trailingRounds = this.config.getCutting().getTrailingRounds();
        double minImprovement = this.config.getCutting().getMinTrailingGapImprovement();
        if (Double.isFinite(currentGap)) {
            state.gapHistory.add(currentGap);
        }
        if (Double.isFinite(currentGap)
                && trailingRounds > 0
                && minImprovement > 0.0
                && state.gapHistory.size() > trailingRounds) {
            int previousIndex = state.gapHistory.size() - trailingRounds - 1;
            double previousGap = state.gapHistory.get(previousIndex);
            if (previousGap > INCUMBENT_TOLERANCE
                    && Double.isFinite(previousGap)
                    && Double.isFinite(currentGap)) {
                double relativeImprovement = (previousGap - currentGap) / previousGap;
                if (relativeImprovement < minImprovement - INCUMBENT_TOLERANCE) {
                    state.stoppedByTrailing = true;
                }
            }
        }
        recordCutGuardState(state);
    }

    private boolean isLmSrcCuttingGuardEnabled() {
        return this.config != null
                && this.config.getCutting() != null
                && this.config.getCutting().isEnabled()
                && this.config.getCutting().isCutSeparationGuardEnabled();
    }

    private CutSeparationGuardState getCutSeparationGuardState() {
        if (this.cutSeparationGuardState == null) {
            this.cutSeparationGuardState = new CutSeparationGuardState();
        }
        return this.cutSeparationGuardState;
    }

    private double calculateCutGuardGap(Node<S, C, G> node) {
        if (node == null || node.getSolution() == null) {
            return Double.NaN;
        }
        double lowerBound = node.getSolution().getObjValue();
        double upperBound = hasIntegerIncumbent()
                && this.optimalNode != null
                && this.optimalNode.getSolution() != null
                ? this.optimalNode.getSolution().getObjValue()
                : Double.POSITIVE_INFINITY;
        if (!Double.isFinite(lowerBound) || !Double.isFinite(upperBound)) {
            return Double.POSITIVE_INFINITY;
        }
        return Math.max(0.0, (upperBound - lowerBound) / (1.0 + Math.abs(upperBound)));
    }

    private void recordCutGuardState(CutSeparationGuardState state) {
        if (this.activeRunStatistics != null && state != null) {
            this.activeRunStatistics.recordCutGuardState(state);
        }
    }

    private long secondsToNanos(double seconds) {
        return Math.max(0L, Math.round(seconds * 1_000_000_000.0));
    }

    protected List<S> collectNewColumns(Node<S, C, G> node, String type, int iter) {
        List<S> addedSchedules = new ArrayList<>();
        for (int k = 0; k < node.getGraphs().size(); k++) {
            recordPricingCall(type);
            G graph = node.getGraphs().get(k);
            List<S> newSchedules = pricingAlgorithm(
                    type,
                    graph,
                    k,
                    node.getUsedCutSet(),
                    node.getBranchConstraints(),
                    node.getSolution().getDualsOfMachines()[k],
                    node.getSolution().getDualsOfJobs(),
                    node.getSolution().getDualsOfCuts(),
                    node.getSolution().getDualsOfBranches(),
                    iter
            );
            List<S> filteredSchedules = filterOutExistingColumns(newSchedules, node.getUsedScheduleSet().get(k));
            addedSchedules.addAll(selectNewColumnsForGraph(filteredSchedules));
        }
        return addedSchedules;
    }

    protected boolean wasLastPricingTimeLimitReached() {
        return false;
    }

    protected List<S> selectNewColumnsForGraph(List<S> candidateSchedules) {
        return candidateSchedules.stream()
                .limit(this.config.getColumnGeneration().getNumAddedColumn())
                .collect(Collectors.toList());
    }

    protected boolean repairRouteFractionalIntegerFlow(Node<S, C, G> node) {
        return false;
    }


    protected void calculateUpperBound(Node<S, C, G> node){
        recordPrimalHeuristicStart();
        long startNanos = System.nanoTime();
        Solution<S> feasibleSolution = obtainFeasibleSolution(node);
        recordPrimalHeuristicElapsed(System.nanoTime() - startNanos);
        if (feasibleSolution == null || Double.isInfinite(feasibleSolution.getObjValue())) {
            return;
        }
        if(!hasIntegerIncumbent() || feasibleSolution.getObjValue() < this.optimalNode.getSolution().getObjValue()){
            setBestSolution(node, feasibleSolution);
        }
    }

    protected void setBestSolution(Node<S, C, G> node, Solution<S> bestSolution){
        this.optimalNode = node.copy();
        this.optimalNode.setSolution(bestSolution);
        this.optimalNode.setFeasible(true);
        this.optimalNode.setInteger(true);
        recordIncumbentUpdate();
    }

    protected final Node<S, C, G> createChildNode(Node<S, C, G> parentNode, Variable branchingVar, int flag) {
        Node<S, C, G> childNode = parentNode.copy();
        Constraint constraint = createBranchingConstraint(childNode, branchingVar, flag);
        childNode.getBranchConstraints().add(constraint);
        return childNode;
    }


    protected final Constraint createBranchingConstraint(Node<S,C, G> node, Variable branchingVar, int flag) {
        return branchingStrategy.getBranchingRule().createBranchingConstraint(node, instance.getNumMachines(), branchingVar, flag);
    }

    protected final List<Variable> getFractionalVariables(Node<S, C, G> node){
        return branchingStrategy.getBranchingRule().getFractionalVariables(instance.getNumMachines(), instance.getNumJobs(), node);
    }

    protected final Variable selectBranchingVariable(List<Variable> fractionalVars, Node<S, C, G> node) {
        return branchingStrategy.getVariableSelectionStrategy().selectBranchingVariable(fractionalVars, node, this.logger);
    }

    protected final boolean isBranchingTerminated() {
        long elapsedTime = (System.nanoTime() - startTime)/1_000_000_000L;
        return elapsedTime >= this.branchingTimeLimitSeconds || isSolveTimeLimitReached();
    }

    public final void setBranchingTimeLimitSeconds(long branchingTimeLimitSeconds) {
        if (branchingTimeLimitSeconds <= 0) {
            throw new IllegalArgumentException("branchingTimeLimitSeconds must be positive.");
        }
        this.branchingTimeLimitSeconds = branchingTimeLimitSeconds;
    }

    public final long getBranchingTimeLimitSeconds() {
        return this.branchingTimeLimitSeconds;
    }

    private boolean isSolveTimeLimitReached() {
        if (this.solveStartTime <= 0L) {
            return false;
        }
        long elapsedTime = (System.nanoTime() - this.solveStartTime) / 1_000_000_000L;
        return elapsedTime >= this.branchingTimeLimitSeconds;
    }

    protected final long getSolveDeadlineNanos() {
        if (this.solveStartTime <= 0L || this.branchingTimeLimitSeconds <= 0L) {
            return Long.MAX_VALUE;
        }
        return this.solveStartTime + this.branchingTimeLimitSeconds * 1_000_000_000L;
    }

    public BranchSearchStatistics getLastBranchSearchStatistics() {
        return this.lastBranchSearchStatistics == null
                ? BranchSearchStatistics.notStarted()
                : this.lastBranchSearchStatistics.copy();
    }

    protected final BranchSearchStatistics buildRootIntegerSearchStatistics(Node<S, C, G> rootNode) {
        return BranchSearchStatistics.rootInteger(
                rootNode == null || rootNode.getSolution() == null
                        ? Double.POSITIVE_INFINITY
                        : rootNode.getSolution().getObjValue(),
                countColumns(rootNode),
                countCuts(rootNode)
        );
    }

    protected final BranchSearchStatistics buildNoBranchSearchStatistics(Node<S, C, G> resultNode) {
        return BranchSearchStatistics.notStarted(
                resultNode == null || resultNode.getSolution() == null
                        ? Double.POSITIVE_INFINITY
                        : resultNode.getSolution().getObjValue(),
                countColumns(resultNode),
                countCuts(resultNode)
        );
    }

    private void finishBranchSearch(BranchSearchTermination terminationReason, Node<S, C, G> fallbackNode) {
        if (this.activeBranchSearchStatistics == null) {
            return;
        }
        Node<S, C, G> resultNode = hasIntegerIncumbent() ? this.optimalNode : fallbackNode;
        boolean solvedToOptimality = hasIntegerIncumbent()
                && this.activeBranchSearchStatistics.getBranchingFailures() == 0
                && (terminationReason == BranchSearchTermination.ROOT_INTEGER
                || (terminationReason == BranchSearchTermination.TREE_EXHAUSTED
                && (this.searchTree == null || this.searchTree.isEmpty())));
        this.activeBranchSearchStatistics.finish(
                terminationReason,
                solvedToOptimality,
                this.numNodesExplored,
                this.searchTree == null ? 0 : this.searchTree.size(),
                elapsedBranchingMillis(),
                resultNode == null || resultNode.getSolution() == null
                        ? Double.POSITIVE_INFINITY
                        : resultNode.getSolution().getObjValue(),
                countColumns(resultNode),
                countCuts(resultNode)
        );
        this.lastBranchSearchStatistics = this.activeBranchSearchStatistics.copy();
        logBranchingSearch(this.lastBranchSearchStatistics);
        this.activeBranchSearchStatistics = null;
    }

    private long elapsedBranchingMillis() {
        if (this.startTime <= 0L) {
            return 0L;
        }
        return (System.nanoTime() - this.startTime) / 1_000_000L;
    }

    private void logBranchingSearch(BranchSearchStatistics statistics) {
        if (this.logger == null
                || this.branchingStrategy == null
                || this.branchingStrategy.getBranchingRule() == null
                || this.branchingStrategy.getNodeSelectionStrategy() == null
                || this.branchingStrategy.getVariableSelectionStrategy() == null) {
            return;
        }
        this.logger.logBranching(
                this.branchingStrategy.getBranchingRule().getStrategyName(),
                this.branchingStrategy.getNodeSelectionStrategy().getStrategyName(),
                this.branchingStrategy.getVariableSelectionStrategy().getStrategyName(),
                statistics.isSolvedToOptimality(),
                statistics.getNodesExplored(),
                statistics.getResultColumnCount(),
                statistics.getResultCutCount(),
                statistics.getResultObjectiveValue()
        );
    }

    private void recordQueuedSearchNode() {
        if (this.activeBranchSearchStatistics != null) {
            this.activeBranchSearchStatistics.recordQueuedNode(this.searchTree == null ? 0 : this.searchTree.size());
        }
    }

    private void recordProcessedSearchNode() {
        if (this.activeBranchSearchStatistics != null) {
            this.activeBranchSearchStatistics.recordProcessedNode(this.searchTree == null ? 0 : this.searchTree.size());
        }
    }

    private void recordChildSearchNodeSolved() {
        if (this.activeBranchSearchStatistics != null) {
            this.activeBranchSearchStatistics.recordChildSolved(this.numNodesExplored);
        }
    }

    private void recordPrunedSearchNode() {
        if (this.activeBranchSearchStatistics != null) {
            this.activeBranchSearchStatistics.recordPrunedNode();
        }
    }

    private void recordInfeasibleSearchChild() {
        if (this.activeBranchSearchStatistics != null) {
            this.activeBranchSearchStatistics.recordInfeasibleChild();
        }
    }

    private void recordBranchingFailureNoFractionalVariables() {
        if (this.activeBranchSearchStatistics != null) {
            this.activeBranchSearchStatistics.recordNoFractionalVariablesFailure();
        }
    }

    private void recordBranchingFailureNoSelectedVariable() {
        if (this.activeBranchSearchStatistics != null) {
            this.activeBranchSearchStatistics.recordNoSelectedVariableFailure();
        }
    }

    private void recordBranchingFailureCgNotClosed() {
        if (this.activeBranchSearchStatistics != null) {
            this.activeBranchSearchStatistics.recordCgNotClosedFailure();
        }
    }

    private void recordIncumbentUpdate() {
        if (this.activeBranchSearchStatistics != null) {
            this.activeBranchSearchStatistics.recordIncumbentUpdate();
        }
    }

    protected final int countColumns(Node<S, C, G> node) {
        if (node == null || node.getUsedScheduleSet() == null) {
            return 0;
        }
        int numColumns = 0;
        for (List<S> schedules : node.getUsedScheduleSet()) {
            if (schedules != null) {
                numColumns += schedules.size();
            }
        }
        return numColumns;
    }

    protected final int countCuts(Node<S, C, G> node) {
        return node == null || node.getUsedCutSet() == null ? 0 : node.getUsedCutSet().size();
    }

    public enum BranchSearchTermination {
        NOT_STARTED,
        ROOT_INTEGER,
        TREE_EXHAUSTED,
        TIME_LIMIT,
        CG_ITERATION_LIMIT
    }

    @Getter
    public static final class BPCRunStatistics {
        private int columnGenerationRuns;
        private int columnGenerationIterations;
        private int exactPricingIterations;
        private int heuristicPricingIterations;
        private int masterSolves;
        private int exactPricingCalls;
        private int heuristicPricingCalls;
        private int primalHeuristicCalls;
        private int columnsAdded;
        private int cutSeparationCalls;
        private int cutsGenerated;
        private int cutsAdded;
        private int cutGuardRounds;
        private int integerFlowRepairAttempts;
        private int integerFlowRepairSuccesses;
        private int integerFlowRepairFailuresInvalidInput;
        private int integerFlowRepairFailuresNoArcFlow;
        private int integerFlowRepairFailuresNoFractionalRoute;
        private int integerFlowRepairFailuresArcFlowNotIntegral;
        private int integerFlowRepairFailuresCustomerDegree;
        private int integerFlowRepairFailuresDecomposition;
        private int integerFlowRepairFailuresStageInfeasible;
        private int integerFlowRepairFailuresBranchInfeasible;
        private int integerFlowRepairFailuresObjectiveMismatch;
        private String integerFlowRepairLastFailureReason = "";
        private boolean cutSeparationStoppedBySlowPricing;
        private boolean cutSeparationStoppedByTrailing;
        private long labelsCreated;
        private long labelsExtended;
        private long labelsDominated;
        private long labelsRemovedByDominance;
        private int maxLabelsPerVertex;
        private int sinkLabels;
        private long elapsedMillis;
        private long masterSolveElapsedMillis;
        private long exactPricingElapsedMillis;
        private long heuristicPricingElapsedMillis;
        private long primalHeuristicElapsedMillis;
        private long cutSeparationElapsedMillis;
        private long dominanceElapsedMillis;
        private long extensionElapsedMillis;
        private long cutGuardMaxExactPricingCallMillis;
        private double cutGuardLastGap = Double.NaN;
        private boolean pricingTimeLimitReached;

        @Getter(AccessLevel.NONE)
        @Setter(AccessLevel.NONE)
        private long startNanos;

        private BPCRunStatistics() {
        }

        private BPCRunStatistics(BPCRunStatistics other) {
            this.columnGenerationRuns = other.columnGenerationRuns;
            this.columnGenerationIterations = other.columnGenerationIterations;
            this.exactPricingIterations = other.exactPricingIterations;
            this.heuristicPricingIterations = other.heuristicPricingIterations;
            this.masterSolves = other.masterSolves;
            this.exactPricingCalls = other.exactPricingCalls;
            this.heuristicPricingCalls = other.heuristicPricingCalls;
            this.primalHeuristicCalls = other.primalHeuristicCalls;
            this.columnsAdded = other.columnsAdded;
            this.cutSeparationCalls = other.cutSeparationCalls;
            this.cutsGenerated = other.cutsGenerated;
            this.cutsAdded = other.cutsAdded;
            this.cutGuardRounds = other.cutGuardRounds;
            this.integerFlowRepairAttempts = other.integerFlowRepairAttempts;
            this.integerFlowRepairSuccesses = other.integerFlowRepairSuccesses;
            this.integerFlowRepairFailuresInvalidInput = other.integerFlowRepairFailuresInvalidInput;
            this.integerFlowRepairFailuresNoArcFlow = other.integerFlowRepairFailuresNoArcFlow;
            this.integerFlowRepairFailuresNoFractionalRoute = other.integerFlowRepairFailuresNoFractionalRoute;
            this.integerFlowRepairFailuresArcFlowNotIntegral = other.integerFlowRepairFailuresArcFlowNotIntegral;
            this.integerFlowRepairFailuresCustomerDegree = other.integerFlowRepairFailuresCustomerDegree;
            this.integerFlowRepairFailuresDecomposition = other.integerFlowRepairFailuresDecomposition;
            this.integerFlowRepairFailuresStageInfeasible = other.integerFlowRepairFailuresStageInfeasible;
            this.integerFlowRepairFailuresBranchInfeasible = other.integerFlowRepairFailuresBranchInfeasible;
            this.integerFlowRepairFailuresObjectiveMismatch = other.integerFlowRepairFailuresObjectiveMismatch;
            this.integerFlowRepairLastFailureReason = other.integerFlowRepairLastFailureReason;
            this.cutSeparationStoppedBySlowPricing = other.cutSeparationStoppedBySlowPricing;
            this.cutSeparationStoppedByTrailing = other.cutSeparationStoppedByTrailing;
            this.labelsCreated = other.labelsCreated;
            this.labelsExtended = other.labelsExtended;
            this.labelsDominated = other.labelsDominated;
            this.labelsRemovedByDominance = other.labelsRemovedByDominance;
            this.maxLabelsPerVertex = other.maxLabelsPerVertex;
            this.sinkLabels = other.sinkLabels;
            this.elapsedMillis = other.elapsedMillis;
            this.masterSolveElapsedMillis = other.masterSolveElapsedMillis;
            this.exactPricingElapsedMillis = other.exactPricingElapsedMillis;
            this.heuristicPricingElapsedMillis = other.heuristicPricingElapsedMillis;
            this.primalHeuristicElapsedMillis = other.primalHeuristicElapsedMillis;
            this.cutSeparationElapsedMillis = other.cutSeparationElapsedMillis;
            this.dominanceElapsedMillis = other.dominanceElapsedMillis;
            this.extensionElapsedMillis = other.extensionElapsedMillis;
            this.cutGuardMaxExactPricingCallMillis = other.cutGuardMaxExactPricingCallMillis;
            this.cutGuardLastGap = other.cutGuardLastGap;
            this.pricingTimeLimitReached = other.pricingTimeLimitReached;
            this.startNanos = other.startNanos;
        }

        public static BPCRunStatistics empty() {
            return new BPCRunStatistics();
        }

        private static BPCRunStatistics started() {
            BPCRunStatistics statistics = new BPCRunStatistics();
            statistics.startNanos = System.nanoTime();
            return statistics;
        }

        public BPCRunStatistics copy() {
            return new BPCRunStatistics(this);
        }

        private void recordColumnGenerationStart() {
            this.columnGenerationRuns++;
        }

        private void recordColumnGenerationIteration(String type) {
            this.columnGenerationIterations++;
            if (isHeuristicPricing(type)) {
                this.heuristicPricingIterations++;
            } else {
                this.exactPricingIterations++;
            }
        }

        private void recordMasterSolve() {
            this.masterSolves++;
        }

        private void recordMasterSolveElapsed(long elapsedNanos) {
            this.masterSolveElapsedMillis += nanosToMillis(elapsedNanos);
        }

        private void recordPricingCall(String type) {
            if (isHeuristicPricing(type)) {
                this.heuristicPricingCalls++;
            } else {
                this.exactPricingCalls++;
            }
        }

        private void recordPricingElapsed(String type, long elapsedNanos) {
            if (isHeuristicPricing(type)) {
                this.heuristicPricingElapsedMillis += nanosToMillis(elapsedNanos);
            } else {
                this.exactPricingElapsedMillis += nanosToMillis(elapsedNanos);
            }
        }

        private void recordPrimalHeuristicStart() {
            this.primalHeuristicCalls++;
        }

        private void recordPrimalHeuristicElapsed(long elapsedNanos) {
            this.primalHeuristicElapsedMillis += nanosToMillis(elapsedNanos);
        }

        private void recordIntegerFlowRepairAttempt(boolean success) {
            this.integerFlowRepairAttempts++;
            if (success) {
                this.integerFlowRepairSuccesses++;
            }
        }

        private void recordIntegerFlowRepairFailure(String reason) {
            String normalizedReason = reason == null || reason.trim().isEmpty()
                    ? "unknown"
                    : reason.trim();
            this.integerFlowRepairLastFailureReason = normalizedReason;
            switch (normalizedReason) {
                case "invalidInput":
                    this.integerFlowRepairFailuresInvalidInput++;
                    break;
                case "noArcFlow":
                    this.integerFlowRepairFailuresNoArcFlow++;
                    break;
                case "noFractionalRoute":
                    this.integerFlowRepairFailuresNoFractionalRoute++;
                    break;
                case "arcFlowNotIntegral":
                    this.integerFlowRepairFailuresArcFlowNotIntegral++;
                    break;
                case "customerDegree":
                    this.integerFlowRepairFailuresCustomerDegree++;
                    break;
                case "decomposition":
                    this.integerFlowRepairFailuresDecomposition++;
                    break;
                case "stageInfeasible":
                    this.integerFlowRepairFailuresStageInfeasible++;
                    break;
                case "branchInfeasible":
                    this.integerFlowRepairFailuresBranchInfeasible++;
                    break;
                case "objectiveMismatch":
                    this.integerFlowRepairFailuresObjectiveMismatch++;
                    break;
                default:
                    this.integerFlowRepairFailuresInvalidInput++;
                    break;
            }
        }

        private void recordColumnsAdded(int count) {
            this.columnsAdded += Math.max(0, count);
        }

        private void recordCutSeparationResult(int generatedCount, int addedCount, long elapsedNanos) {
            this.cutSeparationCalls++;
            this.cutsGenerated += Math.max(0, generatedCount);
            this.cutsAdded += Math.max(0, addedCount);
            this.cutSeparationElapsedMillis += nanosToMillis(elapsedNanos);
        }

        private void recordCutGuardState(CutSeparationGuardState state) {
            this.cutGuardRounds = Math.max(this.cutGuardRounds, state.cutRoundsWithAddedCuts);
            this.cutSeparationStoppedBySlowPricing |= state.stoppedBySlowPricing;
            this.cutSeparationStoppedByTrailing |= state.stoppedByTrailing;
            this.cutGuardMaxExactPricingCallMillis = Math.max(
                    this.cutGuardMaxExactPricingCallMillis,
                    nanosToMillis(state.maxExactPricingCallNanos)
            );
            this.cutGuardLastGap = state.lastGap;
        }

        private void recordPricingDiagnostics(PricingDiagnostics.Snapshot snapshot) {
            this.labelsCreated += Math.max(0L, snapshot.getLabelsCreated());
            this.labelsExtended += Math.max(0L, snapshot.getLabelsExtended());
            this.labelsDominated += Math.max(0L, snapshot.getLabelsDominated());
            this.labelsRemovedByDominance += Math.max(0L, snapshot.getLabelsRemovedByDominance());
            this.maxLabelsPerVertex = Math.max(this.maxLabelsPerVertex, snapshot.getMaxLabelsPerVertex());
            this.sinkLabels = Math.max(this.sinkLabels, snapshot.getSinkLabels());
            this.pricingTimeLimitReached |= snapshot.isTimeLimitReached();
            this.dominanceElapsedMillis += nanosToMillis(snapshot.getDominanceElapsedNanos());
            this.extensionElapsedMillis += nanosToMillis(snapshot.getExtensionElapsedNanos());
        }

        private void finish() {
            if (this.startNanos <= 0L) {
                return;
            }
            this.elapsedMillis = Math.max(0L, Math.round((System.nanoTime() - this.startNanos) / 1_000_000.0));
        }

        private static boolean isHeuristicPricing(String type) {
            return "Heuristic Pricing".equals(type);
        }

        private static long nanosToMillis(long nanos) {
            return Math.max(0L, Math.round(nanos / 1_000_000.0));
        }
    }

    private static final class CutSeparationGuardState {
        private long maxExactPricingCallNanos;
        private boolean stoppedBySlowPricing;
        private boolean stoppedByTrailing;
        private int cutRoundsWithAddedCuts;
        private double lastGap = Double.NaN;
        private final List<Double> gapHistory = new ArrayList<>();
    }

    @Getter
    public static final class BranchSearchStatistics {
        private BranchSearchTermination terminationReason;
        private boolean solvedToOptimality;
        private int nodesExplored;
        private int nodesProcessed;
        private int childrenSolved;
        private int nodesPrunedByIncumbent;
        private int infeasibleChildren;
        private int incumbentUpdates;
        private int fractionalNodesQueued;
        private int branchingFailures;
        private int branchingFailuresNoFractionalVariables;
        private int branchingFailuresNoSelectedVariable;
        private int branchingFailuresCgNotClosed;
        private int maxOpenNodes;
        private int openNodesAtEnd;
        private long elapsedMillis;
        private double resultObjectiveValue;
        private int resultColumnCount;
        private int resultCutCount;

        private BranchSearchStatistics() {
            this.terminationReason = BranchSearchTermination.NOT_STARTED;
            this.solvedToOptimality = false;
            this.resultObjectiveValue = Double.POSITIVE_INFINITY;
        }

        private BranchSearchStatistics(BranchSearchStatistics other) {
            this.terminationReason = other.terminationReason;
            this.solvedToOptimality = other.solvedToOptimality;
            this.nodesExplored = other.nodesExplored;
            this.nodesProcessed = other.nodesProcessed;
            this.childrenSolved = other.childrenSolved;
            this.nodesPrunedByIncumbent = other.nodesPrunedByIncumbent;
            this.infeasibleChildren = other.infeasibleChildren;
            this.incumbentUpdates = other.incumbentUpdates;
            this.fractionalNodesQueued = other.fractionalNodesQueued;
            this.branchingFailures = other.branchingFailures;
            this.branchingFailuresNoFractionalVariables = other.branchingFailuresNoFractionalVariables;
            this.branchingFailuresNoSelectedVariable = other.branchingFailuresNoSelectedVariable;
            this.branchingFailuresCgNotClosed = other.branchingFailuresCgNotClosed;
            this.maxOpenNodes = other.maxOpenNodes;
            this.openNodesAtEnd = other.openNodesAtEnd;
            this.elapsedMillis = other.elapsedMillis;
            this.resultObjectiveValue = other.resultObjectiveValue;
            this.resultColumnCount = other.resultColumnCount;
            this.resultCutCount = other.resultCutCount;
        }

        public static BranchSearchStatistics notStarted() {
            return new BranchSearchStatistics();
        }

        public static BranchSearchStatistics notStarted(double objectiveValue, int columnCount, int cutCount) {
            BranchSearchStatistics statistics = new BranchSearchStatistics();
            statistics.resultObjectiveValue = objectiveValue;
            statistics.resultColumnCount = columnCount;
            statistics.resultCutCount = cutCount;
            return statistics;
        }

        public static BranchSearchStatistics rootInteger(double objectiveValue, int columnCount, int cutCount) {
            BranchSearchStatistics statistics = new BranchSearchStatistics();
            statistics.terminationReason = BranchSearchTermination.ROOT_INTEGER;
            statistics.solvedToOptimality = true;
            statistics.nodesExplored = 1;
            statistics.nodesProcessed = 1;
            statistics.resultObjectiveValue = objectiveValue;
            statistics.resultColumnCount = columnCount;
            statistics.resultCutCount = cutCount;
            return statistics;
        }

        private static BranchSearchStatistics started(int initialNodesExplored) {
            BranchSearchStatistics statistics = new BranchSearchStatistics();
            statistics.nodesExplored = initialNodesExplored;
            return statistics;
        }

        public BranchSearchStatistics copy() {
            return new BranchSearchStatistics(this);
        }

        private void recordQueuedNode(int openNodeCount) {
            this.fractionalNodesQueued++;
            updateOpenNodeStats(openNodeCount);
        }

        private void recordProcessedNode(int openNodeCount) {
            this.nodesProcessed++;
            updateOpenNodeStats(openNodeCount);
        }

        private void recordChildSolved(int nodesExplored) {
            this.childrenSolved++;
            this.nodesExplored = nodesExplored;
        }

        private void recordPrunedNode() {
            this.nodesPrunedByIncumbent++;
        }

        private void recordInfeasibleChild() {
            this.infeasibleChildren++;
        }

        private void recordIncumbentUpdate() {
            this.incumbentUpdates++;
        }

        private void recordNoFractionalVariablesFailure() {
            this.branchingFailures++;
            this.branchingFailuresNoFractionalVariables++;
        }

        private void recordNoSelectedVariableFailure() {
            this.branchingFailures++;
            this.branchingFailuresNoSelectedVariable++;
        }

        private void recordCgNotClosedFailure() {
            this.branchingFailures++;
            this.branchingFailuresCgNotClosed++;
        }

        private void finish(BranchSearchTermination terminationReason,
                            boolean solvedToOptimality,
                            int nodesExplored,
                            int openNodesAtEnd,
                            long elapsedMillis,
                            double resultObjectiveValue,
                            int resultColumnCount,
                            int resultCutCount) {
            this.terminationReason = terminationReason;
            this.solvedToOptimality = solvedToOptimality;
            this.nodesExplored = nodesExplored;
            this.openNodesAtEnd = openNodesAtEnd;
            this.elapsedMillis = elapsedMillis;
            this.resultObjectiveValue = resultObjectiveValue;
            this.resultColumnCount = resultColumnCount;
            this.resultCutCount = resultCutCount;
            updateOpenNodeStats(openNodesAtEnd);
        }

        private void updateOpenNodeStats(int openNodeCount) {
            this.maxOpenNodes = Math.max(this.maxOpenNodes, openNodeCount);
        }
    }

    protected abstract List<S> generateInitialColumns(T instance);
    protected abstract void prepareRootSolveContext(T instance, List<S> initialSchedules, int maxAddedColumnsPerIteration);
    protected abstract M initializeRootMasterProblem(T instance, List<S> initialSchedules);
    protected abstract M initializeNodeMasterProblem(T instance, Node<S, C, G> node);
    protected abstract List<G> initializeGraphs();
    protected abstract Solution<S> obtainInitialSolution(List<G> graphs);
    protected abstract Solution<S> obtainFeasibleSolution(Node<S, C, G> node);
    protected abstract Solution<S> divingHeuristic(Node<S, C, G> node);
    protected abstract List<C> cutGeneration(Node<S, C, G> node);
    protected abstract List<S> pricingAlgorithm(String type, G graph, int index, List<C> cuts, List<Constraint> branchConstraints, double dualsOfMachines, double[] dualsOfJobs, List<Double> dualsOfCuts, List<Double> dualsOfBranches, int iter);
    protected abstract boolean solveMasterProblem(M masterProblem);
    protected abstract void updateNodeFromMasterProblem(Node<S, C, G> node, M masterProblem);
    protected abstract void addColumnsToMasterProblem(M masterProblem, List<S> newSchedules);
    protected abstract void addCutsToMasterProblem(M masterProblem, List<C> newCuts);
    protected abstract void appendColumnsToNode(Node<S, C, G> node, List<S> newSchedules);
    protected abstract void appendCutsToNode(Node<S, C, G> node, List<C> newCuts);
    protected abstract List<S> filterOutExistingColumns(List<S> candidateSchedules, List<S> existingSchedules);
    protected abstract void ensureStrongBranchingHeuristicPricing();
    protected abstract void setHeuristicPricing();
    protected abstract String switchHeuristicPricing(String type);
    protected abstract List<int[]> obtainNonImprovingArcs(int machineIndex, G graph, Node<S, C, G> node, double upperBound);
    protected abstract List<S> enumerateImprovingSchedules(int machineIndex, G graph, Node<S, C, G> node, double upperBound);
    protected abstract void exportOptimalSolution(Node<S,C,G> optimalNode);

    protected void closeMasterProblem(M masterProblem) {
        if (masterProblem instanceof AutoCloseable) {
            try {
                ((AutoCloseable) masterProblem).close();
            } catch (Exception ignored) {
                // Native solver cleanup should not mask a solved stage result.
            }
        }
    }
}

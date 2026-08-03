package org.zzc.bpcsolver.algorithm;

import gnu.trove.list.array.TIntArrayList;
import org.zzc.bpcsolver.algorithm.config.Configuration;
import org.zzc.bpcsolver.algorithm.cutting.Cutting;
import org.zzc.bpcsolver.algorithm.cutting.LmSrc;
import org.zzc.bpcsolver.algorithm.cutting.LmSrcGeneration;
import org.zzc.bpcsolver.algorithm.factory.AccelerationStrategyFactory;
import org.zzc.bpcsolver.algorithm.factory.CuttingStrategyFactory;
import org.zzc.bpcsolver.algorithm.factory.PricingStrategyFactory;
import org.zzc.bpcsolver.algorithm.logger.BPCLogger;
import org.zzc.bpcsolver.algorithm.pricing.MultiStageHeuristicPricing;
import org.zzc.bpcsolver.algorithm.pricing.PricingDiagnostics;
import org.zzc.bpcsolver.core.branch.ColumnGenerationStatus;
import org.zzc.bpcsolver.core.branch.Constraint;
import org.zzc.bpcsolver.core.branch.Node;
import org.zzc.bpcsolver.core.branch.Solution;
import org.zzc.bpcsolver.core.branch.StageSolution;
import org.zzc.bpcsolver.core.branch.Variable;
import org.zzc.bpcsolver.core.graph.Arc;
import org.zzc.bpcsolver.core.graph.Cycle;
import org.zzc.bpcsolver.core.graph.Graph;
import org.zzc.bpcsolver.core.graph.Route;
import org.zzc.bpcsolver.core.graph.RouteFeasibilityValidator;
import org.zzc.bpcsolver.core.graph.Vertex;
import org.zzc.bpcsolver.model.DVRPMasterProblem;
import org.zzc.bpcsolver.problem.entity.Depot;
import org.zzc.bpcsolver.problem.entity.DynamicInstance;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class BPCSolver extends BPCSolverTemplate<DynamicInstance, Route, LmSrc, Graph, Vertex, Arc, Cycle, DVRPMasterProblem> {

    private static final double EPSILON = 1e-6;
    private static final String DEFAULT_SOLVER_CONFIG_PATH =
            "src/main/java/org/zzc/bpcsolver/algorithm/config/bpc_solver.yaml";
    public static final int DEFAULT_MAX_ADDED_COLUMNS_PER_ITERATION = 10;
    public static final int UNLIMITED_MAX_ITERATIONS = 0;
    public static final int DEFAULT_MAX_ITERATIONS = UNLIMITED_MAX_ITERATIONS;
    private final RouteFeasibilityValidator routeFeasibilityValidator = new RouteFeasibilityValidator(EPSILON);
    private List<Route> currentInitialRoutes = new ArrayList<>();
    private List<Graph> currentGraphs = new ArrayList<>();
    private String heuristicPricingRule = "none";
    private String primalHeuristicRule = "column-pool-integer-master";
    private boolean parallelPricing = true;
    private String cuttingRule;
    private String relaxationRule;
    private String branchingSwitch;
    private String branchVariable;
    private String variableSelectionRule;
    private boolean lastPricingTimeLimitReached;

    public BPCSolver() {
    }

    public void setHeuristicPricingRule(String heuristicPricingRule) {
        if (heuristicPricingRule == null || heuristicPricingRule.trim().isEmpty()) {
            this.heuristicPricingRule = "none";
            return;
        }
        this.heuristicPricingRule = heuristicPricingRule.trim();
    }

    public String getHeuristicPricingRule() {
        return heuristicPricingRule;
    }

    public void setPrimalHeuristicRule(String primalHeuristicRule) {
        if (primalHeuristicRule == null || primalHeuristicRule.trim().isEmpty()) {
            this.primalHeuristicRule = "column-pool-integer-master";
            return;
        }
        this.primalHeuristicRule = primalHeuristicRule.trim();
    }

    public String getPrimalHeuristicRule() {
        return primalHeuristicRule;
    }

    public void setParallelPricing(boolean parallelPricing) {
        this.parallelPricing = parallelPricing;
    }

    public boolean isParallelPricing() {
        return parallelPricing;
    }

    public void setCuttingRule(String cuttingRule) {
        if (cuttingRule == null || cuttingRule.trim().isEmpty() || "default".equalsIgnoreCase(cuttingRule.trim())) {
            this.cuttingRule = null;
            return;
        }
        this.cuttingRule = cuttingRule.trim();
    }

    public String getCuttingRule() {
        return cuttingRule;
    }

    public void setRelaxationRule(String relaxationRule) {
        if (relaxationRule == null
                || relaxationRule.trim().isEmpty()
                || "default".equalsIgnoreCase(relaxationRule.trim())) {
            this.relaxationRule = null;
            return;
        }
        this.relaxationRule = relaxationRule.trim();
    }

    public String getRelaxationRule() {
        return relaxationRule;
    }

    public void setBranchingSwitch(String branchingSwitch) {
        if (branchingSwitch == null || branchingSwitch.trim().isEmpty() || "default".equalsIgnoreCase(branchingSwitch.trim())) {
            this.branchingSwitch = null;
            return;
        }
        this.branchingSwitch = branchingSwitch.trim();
    }

    public String getBranchingSwitch() {
        return branchingSwitch;
    }

    public void setBranchVariable(String branchVariable) {
        if (branchVariable == null || branchVariable.trim().isEmpty() || "default".equalsIgnoreCase(branchVariable.trim())) {
            this.branchVariable = null;
            return;
        }
        this.branchVariable = branchVariable.trim();
    }

    public String getBranchVariable() {
        return branchVariable;
    }

    public void setVariableSelectionRule(String variableSelectionRule) {
        if (variableSelectionRule == null
                || variableSelectionRule.trim().isEmpty()
                || "default".equalsIgnoreCase(variableSelectionRule.trim())) {
            this.variableSelectionRule = null;
            return;
        }
        this.variableSelectionRule = variableSelectionRule.trim();
    }

    public String getVariableSelectionRule() {
        return variableSelectionRule;
    }

    @Override
    public void initialize (DynamicInstance instance, Configuration config, BPCLogger logger){
        super.initialize(instance, config, logger);
        this.accelerationStrategy.setPrimalHeuristic(AccelerationStrategyFactory.createPrimalHeuristic(config));
        this.accelerationStrategy.setRelaxation(AccelerationStrategyFactory.createRelaxation(this.config));
        this.accelerationStrategy.setDivingHeuristic(AccelerationStrategyFactory.createDivingHeuristic(config));
        this.accelerationStrategy.setStabilization(AccelerationStrategyFactory.createStabilization(config));
        this.accelerationStrategy.setVariableFixing(AccelerationStrategyFactory.createVariableFixing(config));
        this.accelerationStrategy.setScheduleEnumeration(AccelerationStrategyFactory.createScheduleEnumeration(config));
        if(this.config.getRelaxation().isEnabled()){
            this.accelerationStrategy.setRelaxation(AccelerationStrategyFactory.createRelaxation(this.config));
        }
        if(this.config.getDiving().isEnabled()){
            this.accelerationStrategy.setDivingHeuristic(AccelerationStrategyFactory.createDivingHeuristic(config));
        }
        if(this.config.getStabilization().isEnabled()){
            this.accelerationStrategy.setStabilization(AccelerationStrategyFactory.createStabilization(config));
        }
        this.pricingStrategy.setExactPricingAlgorithm(PricingStrategyFactory.createPricingAlgorithm(config));
        if(config.getPricing().isHeuristicEnabled()){
            this.pricingStrategy.setHeuristicPricingAlgorithm(PricingStrategyFactory.createHeuristicPricingAlgorithm(config));
        }
        this.cuttingStrategy.setCuttingStrategy(CuttingStrategyFactory.createCuttingMethod(config));
        this.cuttingStrategy.getCuttingStrategy().Initialize(this.instance.getNumJobs(), this.config);
        initializeCuttingBaseSetsForCurrentStaticRun();
        if(config.getVariableFixing().isEnabled()){
            this.accelerationStrategy.setVariableFixing(AccelerationStrategyFactory.createVariableFixing(config));
        }
        if(config.getScheduleEnumeration().isEnabled()){
            this.accelerationStrategy.setScheduleEnumeration(AccelerationStrategyFactory.createScheduleEnumeration(config));
        }
    }

    private void initializeCuttingBaseSetsForCurrentStaticRun() {
        Cutting<Route, LmSrc> cutting = this.cuttingStrategy.getCuttingStrategy();
        if (cutting instanceof LmSrcGeneration && this.instance != null) {
            ((LmSrcGeneration) cutting).initializeBaseSetsFromCustomerIds(this.instance.getRemainingCustomerIds());
        }
    }

    public StageSolution solve(DynamicInstance instance) {
        return solve(instance, DEFAULT_MAX_ADDED_COLUMNS_PER_ITERATION, DEFAULT_MAX_ITERATIONS);
    }

    public StageSolution solve(DynamicInstance instance, int maxAddedColumnsPerIteration, int maxIterations) {
        return solveNode(instance, maxAddedColumnsPerIteration, maxIterations).getSolution().copy();
    }

    public SolveResult solveNode(DynamicInstance instance, int maxAddedColumnsPerIteration, int maxIterations) {
        if (instance == null) {
            throw new IllegalArgumentException("instance must not be null.");
        }
        if (maxAddedColumnsPerIteration <= 0) {
            throw new IllegalArgumentException("maxAddedColumnsPerIteration must be positive.");
        }
        if (maxIterations < 0) {
            throw new IllegalArgumentException("maxIterations must be non-negative; use 0 for convergence-driven CG.");
        }

        try {
            RootSolveState rootSolveState = solveRootNode(instance, maxAddedColumnsPerIteration, maxIterations);
            Node<Route, LmSrc, Graph> rootNode = rootSolveState.getRootNode();
            Node<Route, LmSrc, Graph> resultNode = isColumnGenerationClosed(rootNode) && rootNode.isInteger()
                    ? rootNode
                    : branchAndPriceFromRootNode(rootNode, maxIterations);
            if (requiresElementaryFallback(resultNode)) {
                finishRunStatistics();
                return solveNodeWithElementaryPricing(instance, maxAddedColumnsPerIteration, maxIterations);
            }
            StageSolution stageSolution = toStageSolution(resultNode);
            BranchSearchStatistics searchStatistics = isColumnGenerationClosed(rootNode) && rootNode.isInteger()
                    ? buildRootIntegerSearchStatistics(rootNode)
                    : getLastBranchSearchStatistics();
            finishRunStatistics();
            return new SolveResult(
                    resultNode,
                    stageSolution,
                    determineResultStatus(isColumnGenerationClosed(rootNode) && rootNode.isInteger(), resultNode),
                    searchStatistics,
                    getLastRunStatistics(),
                    searchStatistics.isSolvedToOptimality()
            );
        } catch (RuntimeException ex) {
            finishRunStatistics();
            throw ex;
        }
    }

    private boolean requiresElementaryFallback(Node<Route, LmSrc, Graph> resultNode) {
        if (this.config == null
                || this.config.getRelaxation() == null
                || !this.config.getRelaxation().isEnabled()
                || resultNode == null
                || resultNode.getSolution() == null) {
            return false;
        }

        if (resultNode.isInteger()) {
            if (resultNode.getSolution().getBestSchedules() == null) {
                return false;
            }
            for (Route route : resultNode.getSolution().getBestSchedules()) {
                if (hasRepeatedRemainingCustomer(route)) {
                    return true;
                }
            }
            return false;
        }

        for (Route route : getPositiveValueRoutes(resultNode)) {
            if (hasRepeatedRemainingCustomer(route)) {
                return true;
            }
        }
        return false;
    }

    private SolveResult solveNodeWithElementaryPricing(DynamicInstance instance,
                                                       int maxAddedColumnsPerIteration,
                                                       int maxIterations) {
        BPCSolver fallbackSolver = new BPCSolver();
        fallbackSolver.setHeuristicPricingRule(this.heuristicPricingRule);
        fallbackSolver.setPrimalHeuristicRule(this.primalHeuristicRule);
        fallbackSolver.setParallelPricing(this.parallelPricing);
        fallbackSolver.setCuttingRule(this.cuttingRule);
        fallbackSolver.setRelaxationRule("none");
        fallbackSolver.setVariableSelectionRule(this.variableSelectionRule);
        fallbackSolver.setBranchingTimeLimitSeconds(getRemainingSolveTimeLimitSeconds());
        return fallbackSolver.solveNode(instance, maxAddedColumnsPerIteration, maxIterations);
    }

    private long getRemainingSolveTimeLimitSeconds() {
        long deadlineNanos = getSolveDeadlineNanos();
        if (deadlineNanos == Long.MAX_VALUE) {
            return getBranchingTimeLimitSeconds();
        }
        long remainingNanos = deadlineNanos - System.nanoTime();
        return Math.max(1L, (long) Math.ceil(remainingNanos / 1_000_000_000.0));
    }

    private boolean hasRepeatedRemainingCustomer(Route route) {
        if (route == null || route.getVisitedVertices() == null || this.instance == null) {
            return false;
        }
        Set<Integer> remainingCustomers = new HashSet<>(this.instance.getRemainingCustomerIds());
        Set<Integer> seen = new HashSet<>();
        for (int nodeId : route.getVisitedVertices().toArray()) {
            if (!remainingCustomers.contains(nodeId)) {
                continue;
            }
            if (!seen.add(nodeId)) {
                return true;
            }
        }
        return false;
    }

    Node<Route, LmSrc, Graph> solveChildNodeForCurrentInstance(Node<Route, LmSrc, Graph> parentNode,
                                                               Variable branchingVar,
                                                               int branchFlag,
                                                               int maxIterations) {
        return solveBranchedChildNode(parentNode, branchingVar, branchFlag, maxIterations);
    }

    SolveResult solveRootOnlyNode(DynamicInstance instance, int maxAddedColumnsPerIteration, int maxIterations) {
        if (instance == null) {
            throw new IllegalArgumentException("instance must not be null.");
        }
        if (maxAddedColumnsPerIteration <= 0) {
            throw new IllegalArgumentException("maxAddedColumnsPerIteration must be positive.");
        }
        if (maxIterations < 0) {
            throw new IllegalArgumentException("maxIterations must be non-negative; use 0 for convergence-driven CG.");
        }

        try {
            RootSolveState rootSolveState = solveRootNode(instance, maxAddedColumnsPerIteration, maxIterations);
            Node<Route, LmSrc, Graph> rootNode = rootSolveState.getRootNode();
            StageSolution stageSolution = toStageSolution(rootNode);
            finishRunStatistics();
            return new SolveResult(rootNode, stageSolution, determineResultStatus(true, rootNode),
                    isColumnGenerationClosed(rootNode) && rootNode.isInteger()
                            ? buildRootIntegerSearchStatistics(rootNode)
                            : buildNoBranchSearchStatistics(rootNode),
                    getLastRunStatistics(),
                    isColumnGenerationClosed(rootNode) && rootNode.isInteger());
        } catch (RuntimeException ex) {
            finishRunStatistics();
            throw ex;
        }
    }

    Node<Route, LmSrc, Graph> branchAndPriceFromCurrentRootNode(Node<Route, LmSrc, Graph> rootNode,
                                                                int maxIterations) {
        return branchAndPriceFromRootNode(rootNode, maxIterations);
    }

    SolveResult branchAndPriceResultFromCurrentRootNode(Node<Route, LmSrc, Graph> rootNode,
                                                        int maxIterations) {
        startRunStatistics();
        try {
            Node<Route, LmSrc, Graph> resultNode = branchAndPriceFromRootNode(rootNode, maxIterations);
            StageSolution stageSolution = toStageSolution(resultNode);
            finishRunStatistics();
            return new SolveResult(
                    resultNode,
                    stageSolution,
                    determineResultStatus(rootNode != null && rootNode.isInteger(), resultNode),
                    getLastBranchSearchStatistics(),
                    getLastRunStatistics(),
                    getLastBranchSearchStatistics().isSolvedToOptimality()
            );
        } catch (RuntimeException ex) {
            finishRunStatistics();
            throw ex;
        }
    }

    Node<Route, LmSrc, Graph> solveBranchAndPriceNode(DynamicInstance instance,
                                                       int maxAddedColumnsPerIteration,
                                                       int maxIterations) {
        if (instance == null) {
            throw new IllegalArgumentException("instance must not be null.");
        }
        if (maxAddedColumnsPerIteration <= 0) {
            throw new IllegalArgumentException("maxAddedColumnsPerIteration must be positive.");
        }
        if (maxIterations < 0) {
            throw new IllegalArgumentException("maxIterations must be non-negative; use 0 for convergence-driven CG.");
        }

        return solveNode(instance, maxAddedColumnsPerIteration, maxIterations).getResultNode();
    }

    List<Route> generateInitialRoutesForCurrentInstance(DynamicInstance instance) {
        return copyRoutes(generateInitialRoutes(instance));
    }

    @Override
    protected List<Route> generateInitialColumns(DynamicInstance instance) {
        return generateInitialRoutes(instance);
    }

    @Override
    protected void prepareRootSolveContext(DynamicInstance instance, List<Route> initialSchedules, int maxAddedColumnsPerIteration) {
        bootstrapRootSolveContext(instance, initialSchedules, maxAddedColumnsPerIteration);
    }

    @Override
    protected DVRPMasterProblem initializeRootMasterProblem(DynamicInstance instance, List<Route> initialSchedules) {
        DVRPMasterProblem masterProblem = new DVRPMasterProblem();
        masterProblem.initialize(instance, initialSchedules, false);
        masterProblem.build();
        return masterProblem;
    }

    @Override
    protected DVRPMasterProblem initializeNodeMasterProblem(DynamicInstance instance, Node<Route, LmSrc, Graph> node) {
        DVRPMasterProblem masterProblem = new DVRPMasterProblem();
        masterProblem.initialize(instance, flattenAllowedUsedScheduleSet(node), false);
        masterProblem.configureCuts(node.getUsedCutSet());
        masterProblem.configureBranching(node.getBranchConstraints(), this.branchingStrategy.getBranchingRule());
        masterProblem.build();
        return masterProblem;
    }

    @Override
    protected List<Graph> initializeGraphs() {
        if (this.instance == null) {
            return new ArrayList<>();
        }
        this.currentGraphs = initializeGraphsForInstance(this.instance);
        return copyGraphs(this.currentGraphs);
    }

    @Override
    protected Solution<Route> obtainInitialSolution(List<Graph> graphs) {
        Solution<Route> initialSolution = new Solution<>(graphs.size(), this.instance.getNumJobs());
        initialSolution.setBestSchedules(new ArrayList<>());
        initialSolution.setDualsOfCuts(new ArrayList<>());
        initialSolution.setDualsOfBranches(new ArrayList<>());
        initialSolution.setYks(buildYksByGraph(graphs, this.currentInitialRoutes));
        return initialSolution;
    }

    @Override
    protected List<List<Route>> initializeRootUsedScheduleSet(Solution<Route> initialSolution) {
        return groupRoutesByStartDepot(this.currentGraphs, this.currentInitialRoutes);
    }

    @Override
    protected Solution<Route> obtainFeasibleSolution(Node<Route, LmSrc, Graph> node) {
        if (node == null) {
            return new Solution<>(this.instance.getNumMachines(), this.instance.getNumJobs());
        }

        Solution<Route> solution = this.accelerationStrategy.getPrimalHeuristic().obtainFeasibleSolution(
                this.instance,
                node.getGraphs(),
                copyUsedScheduleSet(node.getUsedScheduleSet()),
                node.getSolution().getYks()
        );
        return solution;
    }

    @Override
    protected Solution<Route> divingHeuristic(Node<Route, LmSrc, Graph> node) {
        this.accelerationStrategy.getDivingHeuristic().initialize(
                this.instance,
                this.config,
                this.pricingStrategy.getHeuristicPricingAlgorithm(),
                this.branchingStrategy.getBranchingRule()
        );
        return this.accelerationStrategy.getDivingHeuristic().solve(node, this.logger);
    }

    @Override
    protected List<LmSrc> cutGeneration(Node<Route, LmSrc, Graph> node) {
        return this.cuttingStrategy.getCuttingStrategy().separateCuts(
                this.instance.getNumMachines(),
                node.getUsedScheduleSet(),
                node.getSolution().getYks()
        );
    }

    @Override
    protected List<Route> pricingAlgorithm(String state, Graph graph, int index, List<LmSrc> cuts, List<Constraint> branchConstraints, double dualsOfMachines, double[] dualsOfJobs, List<Double> dualsOfCuts, List<Double> dualsOfBranches, int iter) {
        switch (state){
            case "Exact Pricing":
                return this.pricingStrategy.getExactPricingAlgorithm().solve(graph, index, cuts, branchConstraints, dualsOfMachines, dualsOfJobs, dualsOfCuts, dualsOfBranches, this.branchingStrategy.getBranchingRule(), this.config, this.logger, iter);
            case "Heuristic Pricing":
                return this.pricingStrategy.getHeuristicPricingAlgorithm().solve(graph, index, cuts, branchConstraints, dualsOfMachines, dualsOfJobs, dualsOfCuts, dualsOfBranches, this.branchingStrategy.getBranchingRule(), this.config, this.logger, iter);
        }
        return this.pricingStrategy.getExactPricingAlgorithm().solve(graph, index, cuts, branchConstraints, dualsOfMachines, dualsOfJobs, dualsOfCuts, dualsOfBranches, this.branchingStrategy.getBranchingRule(), this.config, this.logger, iter);
    }

    @Override
    protected boolean solveMasterProblem(DVRPMasterProblem masterProblem) {
        if (masterProblem == null) {
            throw new IllegalArgumentException("masterProblem must not be null.");
        }
        long startNanos = System.nanoTime();
        recordMasterSolve();
        boolean solved = masterProblem.solve();
        recordMasterSolveElapsed(System.nanoTime() - startNanos);
        return solved;
    }

    @Override
    protected void updateNodeFromMasterProblem(Node<Route, LmSrc, Graph> node, DVRPMasterProblem masterProblem) {
        if (masterProblem == null) {
            throw new IllegalArgumentException("masterProblem must not be null.");
        }
        masterProblem.configureBranching(node.getBranchConstraints(), this.branchingStrategy.getBranchingRule());
        synchronizeNodeFromMasterProblem(node, masterProblem);
    }

    @Override
    protected void addColumnsToMasterProblem(DVRPMasterProblem masterProblem, List<Route> newSchedules) {
        if (masterProblem == null) {
            throw new IllegalArgumentException("masterProblem must not be null.");
        }
        masterProblem.addColumns(newSchedules);
    }

    @Override
    protected void addCutsToMasterProblem(DVRPMasterProblem masterProblem, List<LmSrc> newCuts) {
        if (masterProblem == null) {
            throw new IllegalArgumentException("masterProblem must not be null.");
        }
        masterProblem.addCuts(newCuts);
    }

    @Override
    protected void appendColumnsToNode(Node<Route, LmSrc, Graph> node, List<Route> newSchedules) {
        for (Route route : newSchedules) {
            for (int k = 0; k < node.getGraphs().size(); k++) {
                if (node.getGraphs().get(k).getStartDepotId() == route.getStartDepotId()) {
                    node.getUsedScheduleSet().get(k).add(route.copy());
                    break;
                }
            }
        }
    }

    @Override
    protected void appendCutsToNode(Node<Route, LmSrc, Graph> node, List<LmSrc> newCuts) {
        if (node == null || newCuts == null) {
            return;
        }
        for (LmSrc cut : newCuts) {
            if (cut != null) {
                node.getUsedCutSet().add(cut.copy());
            }
        }
    }

    @Override
    protected List<Route> filterOutExistingColumns(List<Route> candidateSchedules, List<Route> existingSchedules) {
        return filterOutExistingRoutes(candidateSchedules, existingSchedules);
    }

    @Override
    protected void ensureStrongBranchingHeuristicPricing() {
        if (this.pricingStrategy.getHeuristicPricingAlgorithm() == null) {
            this.pricingStrategy.setHeuristicPricingAlgorithm(new MultiStageHeuristicPricing());
        }
        if (this.config != null && this.config.getPricing() != null) {
            this.config.getPricing().setCurrentHeuristic(null);
        }
    }

    @Override
    protected List<Route> collectNewColumns(Node<Route, LmSrc, Graph> node, String type, int iter) {
        this.lastPricingTimeLimitReached = false;
        if (this.config != null
                && this.config.getColumnGeneration() != null
                && this.config.getColumnGeneration().isParallelPricingEnabled()) {
            return collectPerGraphColumnsParallel(node, type, iter);
        }
        return collectPerGraphColumnsSerial(node, type, iter);
    }

    @Override
    protected boolean wasLastPricingTimeLimitReached() {
        return this.lastPricingTimeLimitReached;
    }

    @Override
    protected boolean repairRouteFractionalIntegerFlow(Node<Route, LmSrc, Graph> node) {
        // RouteOpt-style proof repair: close only route-fractional LP nodes that
        // already encode an executable integer directed-arc flow at the same cost.
        if (node == null
                || node.getSolution() == null
                || node.getSolution().getYks() == null
                || node.getUsedScheduleSet() == null
                || node.isInteger()) {
            recordIntegerFlowRepairFailure("invalidInput");
            return false;
        }

        Map<ArcKey, Double> arcFlows = aggregatePositiveRouteArcFlows(node);
        if (arcFlows.isEmpty()) {
            recordIntegerFlowRepairFailure("noArcFlow");
            return false;
        }
        if (!hasFractionalPositiveRouteValue(node)) {
            recordIntegerFlowRepairFailure("noFractionalRoute");
            return false;
        }
        if (!allArcFlowsIntegral(arcFlows)) {
            recordIntegerFlowRepairFailure("arcFlowNotIntegral");
            return false;
        }

        Map<ArcKey, Integer> arcCounts = roundArcFlows(arcFlows);
        if (!customersHaveOneIncomingAndOutgoingArc(arcCounts)) {
            recordIntegerFlowRepairFailure("customerDegree");
            return false;
        }

        List<Route> repairedRoutes = decomposeIntegralArcFlow(arcCounts, node);
        if (repairedRoutes.isEmpty() || hasRemainingArcCount(arcCounts)) {
            recordIntegerFlowRepairFailure("decomposition");
            return false;
        }
        for (Route route : repairedRoutes) {
            route.setValue(1.0);
        }

        boolean stageFeasible = isStageFeasible(repairedRoutes);
        boolean branchFeasible = stageFeasible && satisfiesBranchConstraints(repairedRoutes, node);
        if (!stageFeasible || !branchFeasible) {
            recordIntegerFlowRepairFailure(!stageFeasible ? "stageInfeasible" : "branchInfeasible");
            return false;
        }

        double repairedObjective = repairedRoutes.stream().mapToDouble(Route::getCost).sum();
        double lpObjective = node.getSolution().getObjValue();
        if (Math.abs(repairedObjective - lpObjective) > 1e-4) {
            recordIntegerFlowRepairFailure("objectiveMismatch");
            return false;
        }

        node.setInteger(true);
        node.getSolution().setObjValue(repairedObjective);
        node.getSolution().setBestSchedules(copyRoutes(repairedRoutes));
        return true;
    }

    private List<Route> collectPerGraphColumnsSerial(Node<Route, LmSrc, Graph> node, String type, int iter) {
        List<Route> addedSchedules = new ArrayList<>();
        List<Graph> graphs = node.getGraphs();
        if (graphs == null || graphs.isEmpty()) {
            return addedSchedules;
        }

        for (int k = 0; k < graphs.size(); k++) {
            PricingTaskInput input = buildPricingTaskInput(node, graphs.get(k), k);
            recordPricingCall(type);
            PricingTaskResult result = runPricingTask(input, type, iter);
            recordPricingElapsed(type, result.getElapsedNanos());
            recordPricingDiagnostics(result.getDiagnostics());
            this.lastPricingTimeLimitReached |= result.getDiagnostics().isTimeLimitReached();
            List<Route> filteredSchedules = filterOutExistingColumns(
                    result.getRoutes(),
                    result.getExistingRoutes()
            );
            addedSchedules.addAll(selectNewColumnsForGraph(filteredSchedules));
        }
        return addedSchedules;
    }

    private List<Route> collectPerGraphColumnsParallel(Node<Route, LmSrc, Graph> node, String type, int iter) {
        List<Graph> graphs = node.getGraphs();
        if (graphs == null || graphs.isEmpty()) {
            return new ArrayList<>();
        }

        int threadCount = Math.max(1, Math.min(graphs.size(), Runtime.getRuntime().availableProcessors()));
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        try {
            List<Future<PricingTaskResult>> futures = new ArrayList<>();
            for (int k = 0; k < graphs.size(); k++) {
                PricingTaskInput input = buildPricingTaskInput(node, graphs.get(k), k);
                recordPricingCall(type);
                futures.add(executor.submit(() -> runPricingTask(input, type, iter)));
            }

            List<PricingTaskResult> pricingResults = new ArrayList<>();
            for (Future<PricingTaskResult> future : futures) {
                pricingResults.add(future.get());
            }
            pricingResults.sort((left, right) -> Integer.compare(left.getGraphIndex(), right.getGraphIndex()));

            List<Route> addedSchedules = new ArrayList<>();
            for (PricingTaskResult result : pricingResults) {
                recordPricingElapsed(type, result.getElapsedNanos());
                recordPricingDiagnostics(result.getDiagnostics());
                this.lastPricingTimeLimitReached |= result.getDiagnostics().isTimeLimitReached();
                List<Route> filteredSchedules = filterOutExistingColumns(
                        result.getRoutes(),
                        result.getExistingRoutes()
                );
                addedSchedules.addAll(selectNewColumnsForGraph(filteredSchedules));
            }
            return addedSchedules;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while collecting pricing columns.", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new IllegalStateException("Exact/heuristic pricing failed.", cause);
        } finally {
            executor.shutdown();
        }
    }

    @Override
    protected void setHeuristicPricing() {
        this.pricingStrategy.getHeuristicPricingAlgorithm().reset(this.config);
    }

    @Override
    protected String switchHeuristicPricing(String type) {
        type = this.pricingStrategy.getHeuristicPricingAlgorithm().switchHeuristic(this.config, type);
        return type;
    }

    @Override
    protected List<int[]> obtainNonImprovingArcs(int machineIndex, Graph graph, Node<Route, LmSrc, Graph> node, double upperBound) {
        return this.accelerationStrategy.getVariableFixing().solve(graph, node, upperBound);
    }


    @Override
    protected List<Route> enumerateImprovingSchedules(int machineIndex, Graph graph, Node<Route, LmSrc,  Graph> node, double upperBound) {
        return this.accelerationStrategy.getScheduleEnumeration().solve(
                machineIndex,
                graph,
                node,
                upperBound,
                this.config,
                this.logger
        );
    }

    @Override
    protected void exportOptimalSolution(Node<Route, LmSrc, Graph> optimalNode) {
        if (logger != null) {
            logger.logSolution(optimalNode);
        }
    }

    private void synchronizeNodeFromMasterProblem(Node<Route, LmSrc, Graph> node,
                                                  DVRPMasterProblem masterProblem) {
        StageSolution solution = toStageSolution(node, masterProblem);
        node.setSolution(toNodeSolution(masterProblem, solution, node.getGraphs()));
        node.setUsedScheduleSet(groupRoutesByStartDepot(node.getGraphs(), masterProblem.getRoutes()));
        node.setFeasible(true);
    }

    private StageSolution toStageSolution(Node<Route, LmSrc, Graph> rootNode,
                                          DVRPMasterProblem masterProblem) {
        StageSolution stageSolution = new StageSolution();
        masterProblem.updateSolution(stageSolution);
        stageSolution.setIntegerSolution(rootNode.isInteger());
        if (rootNode.isInteger()) {
            stageSolution.setSelectedRoutes(copyRoutes(rootNode.getSolution().getBestSchedules()));
        }
        return stageSolution;
    }

    private StageSolution toStageSolution(Node<Route, LmSrc, Graph> node) {
        if (node == null || node.getSolution() == null) {
            throw new IllegalArgumentException("node and node solution must not be null.");
        }

        StageSolution stageSolution = new StageSolution();
        stageSolution.setObjectiveValue(node.getSolution().getObjValue());
        stageSolution.setIntegerSolution(node.isInteger());
        stageSolution.setAllRoutes(flattenUsedScheduleSet(node.getUsedScheduleSet()));
        if (node.isInteger()) {
            stageSolution.setSelectedRoutes(copyRoutes(node.getSolution().getBestSchedules()));
        } else {
            stageSolution.setSelectedRoutes(getPositiveValueRoutes(node));
        }
        stageSolution.setPricingCustomerDuals(node.getSolution().getDualsOfJobs() == null
                ? new double[0]
                : node.getSolution().getDualsOfJobs().clone());
        stageSolution.setStartDepotConstraintDuals(expandMachineDualsToStartDepotIds(node));
        stageSolution.setCutConstraintDuals(node.getSolution().getDualsOfCuts() == null
                ? new ArrayList<>()
                : new ArrayList<>(node.getSolution().getDualsOfCuts()));
        return stageSolution;
    }

    private SolveStatus determineResultStatus(boolean rootWasInteger,
                                              Node<Route, LmSrc, Graph> resultNode) {
        if (!isColumnGenerationClosed(resultNode)) {
            return mapOpenColumnGenerationStatus(resultNode);
        }
        if (resultNode != null && resultNode.isInteger()) {
            return rootWasInteger ? SolveStatus.INTEGER_ROOT : SolveStatus.INTEGER_INCUMBENT;
        }
        return SolveStatus.LP_FALLBACK;
    }

    private SolveStatus mapOpenColumnGenerationStatus(Node<Route, LmSrc, Graph> resultNode) {
        ColumnGenerationStatus columnGenerationStatus = resultNode == null
                ? ColumnGenerationStatus.NOT_STARTED
                : resultNode.getColumnGenerationStatus();
        if (columnGenerationStatus == ColumnGenerationStatus.ITERATION_LIMIT) {
            return SolveStatus.CG_ITERATION_LIMIT;
        }
        if (columnGenerationStatus == ColumnGenerationStatus.MASTER_INFEASIBLE) {
            return SolveStatus.MASTER_INFEASIBLE;
        }
        return SolveStatus.LP_FALLBACK;
    }

    private List<Graph> initializeGraphsForInstance(DynamicInstance instance) {
        List<Graph> graphs = new ArrayList<>();
        Graph originGraph = new Graph();
        originGraph.buildFromInstance(instance, instance.getOriginDepot().getId());
        graphs.add(originGraph);
        for (Depot externalDepot : instance.getExternalDepots()) {
            Graph externalGraph = new Graph();
            externalGraph.buildFromInstance(instance, externalDepot.getId());
            graphs.add(externalGraph);
        }
        return graphs;
    }

    private List<List<Route>> groupRoutesByStartDepot(List<Graph> graphs, List<Route> routes) {
        List<List<Route>> groupedRoutes = new ArrayList<>();
        for (Graph graph : graphs) {
            List<Route> graphRoutes = new ArrayList<>();
            for (Route route : routes) {
                if (route != null && route.getStartDepotId() == graph.getStartDepotId()) {
                    graphRoutes.add(route.copy());
                }
            }
            groupedRoutes.add(graphRoutes);
        }
        return groupedRoutes;
    }

    private Solution<Route> toNodeSolution(DVRPMasterProblem masterProblem,
                                           StageSolution stageSolution,
                                           List<Graph> graphs) {
        Solution<Route> solution = new Solution<>(graphs.size(), masterProblem.getInstance().getNumJobs());
        solution.setObjValue(stageSolution.getObjectiveValue());
        solution.setBestSchedules(new ArrayList<>());
        solution.setDualsOfJobs(stageSolution.getPricingCustomerDuals() == null
                ? new double[masterProblem.getInstance().getNumJobs()]
                : stageSolution.getPricingCustomerDuals().clone());
        solution.setDualsOfMachines(extractMachineDuals(graphs, stageSolution.getStartDepotConstraintDuals()));
        solution.setDualsOfCuts(stageSolution.getCutConstraintDuals() == null
                ? new ArrayList<>()
                : new ArrayList<>(stageSolution.getCutConstraintDuals()));
        solution.setDualsOfBranches(masterProblem.getBranchConstraintDuals());
        solution.setYks(buildYksByGraph(graphs, masterProblem.getRoutes()));
        return solution;
    }

    private List<Route> copyRoutes(List<Route> routes) {
        List<Route> copies = new ArrayList<>();
        for (Route route : routes) {
            copies.add(route == null ? null : route.copy());
        }
        return copies;
    }

    private List<Route> getPositiveValueRoutes(Node<Route, LmSrc, Graph> node) {
        List<Route> routes = new ArrayList<>();
        for (int k = 0; k < node.getUsedScheduleSet().size(); k++) {
            List<Route> graphRoutes = node.getUsedScheduleSet().get(k);
            double[] values = node.getSolution().getYks().get(k);
            for (int s = 0; s < graphRoutes.size(); s++) {
                if (values[s] > EPSILON) {
                    Route route = graphRoutes.get(s).copy();
                    route.setValue(values[s]);
                    routes.add(route);
                }
            }
        }
        return routes;
    }

    private List<Route> flattenUsedScheduleSet(List<List<Route>> usedScheduleSet) {
        List<Route> routes = new ArrayList<>();
        if (usedScheduleSet == null) {
            return routes;
        }
        for (List<Route> graphRoutes : usedScheduleSet) {
            if (graphRoutes == null) {
                continue;
            }
            routes.addAll(copyRoutes(graphRoutes));
        }
        return routes;
    }

    private List<Route> flattenAllowedUsedScheduleSet(Node<Route, LmSrc, Graph> node) {
        return node == null ? new ArrayList<>() : flattenUsedScheduleSet(node.getUsedScheduleSet());
    }

    private Map<ArcKey, Double> aggregatePositiveRouteArcFlows(Node<Route, LmSrc, Graph> node) {
        Map<ArcKey, Double> arcFlows = new TreeMap<>();
        List<double[]> valuesByGraph = node.getSolution().getYks();
        for (int k = 0; k < node.getUsedScheduleSet().size() && k < valuesByGraph.size(); k++) {
            List<Route> routes = node.getUsedScheduleSet().get(k);
            double[] values = valuesByGraph.get(k);
            if (routes == null || values == null) {
                continue;
            }
            for (int s = 0; s < routes.size() && s < values.length; s++) {
                double value = values[s];
                if (value <= EPSILON) {
                    continue;
                }
                TIntArrayList vertices = routes.get(s).getVisitedVertices();
                if (vertices == null || vertices.size() < 2) {
                    return new TreeMap<>();
                }
                for (int i = 0; i < vertices.size() - 1; i++) {
                    ArcKey key = new ArcKey(vertices.get(i), vertices.get(i + 1));
                    arcFlows.put(key, arcFlows.getOrDefault(key, 0.0) + value);
                }
            }
        }
        return arcFlows;
    }

    private boolean hasFractionalPositiveRouteValue(Node<Route, LmSrc, Graph> node) {
        for (double[] values : node.getSolution().getYks()) {
            if (values == null) {
                continue;
            }
            for (double value : values) {
                if (value > EPSILON && Math.abs(value - Math.rint(value)) > EPSILON) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean allArcFlowsIntegral(Map<ArcKey, Double> arcFlows) {
        for (double value : arcFlows.values()) {
            if (value > EPSILON && Math.abs(value - Math.rint(value)) > EPSILON) {
                return false;
            }
        }
        return true;
    }

    private Map<ArcKey, Integer> roundArcFlows(Map<ArcKey, Double> arcFlows) {
        Map<ArcKey, Integer> arcCounts = new TreeMap<>();
        for (Map.Entry<ArcKey, Double> entry : arcFlows.entrySet()) {
            int count = (int) Math.rint(entry.getValue());
            if (count > 0) {
                arcCounts.put(entry.getKey(), count);
            }
        }
        return arcCounts;
    }

    private boolean customersHaveOneIncomingAndOutgoingArc(Map<ArcKey, Integer> arcCounts) {
        for (Integer customerId : this.instance.getRemainingCustomerIds()) {
            if (countIncomingArcs(arcCounts, customerId) != 1 || countOutgoingArcs(arcCounts, customerId) != 1) {
                return false;
            }
        }
        return true;
    }

    private List<Route> decomposeIntegralArcFlow(Map<ArcKey, Integer> arcCounts,
                                                 Node<Route, LmSrc, Graph> node) {
        List<Route> repairedRoutes = new ArrayList<>();
        int routeIndex = flattenUsedScheduleSet(node.getUsedScheduleSet()).size();
        for (Graph graph : node.getGraphs()) {
            if (graph == null) {
                continue;
            }
            int startDepotId = graph.getStartDepotId();
            while (countOutgoingArcs(arcCounts, startDepotId) > 0) {
                Route route = buildRouteFromArcCounts(startDepotId, routeIndex++, arcCounts);
                if (route == null) {
                    return new ArrayList<>();
                }
                repairedRoutes.add(route);
            }
        }
        return repairedRoutes;
    }

    private Route buildRouteFromArcCounts(int startDepotId, int routeIndex, Map<ArcKey, Integer> arcCounts) {
        TIntArrayList vertices = new TIntArrayList();
        vertices.add(startDepotId);
        int currentNodeId = startDepotId;
        int returnDepotId = this.instance.getReturnDepot().getId();
        int guard = this.instance.getRemainingCustomerIds().size() + this.instance.getExternalDepots().size() + 3;
        while (currentNodeId != returnDepotId && guard-- > 0) {
            Integer nextNodeId = pollNextHead(currentNodeId, arcCounts);
            if (nextNodeId == null) {
                return null;
            }
            vertices.add(nextNodeId);
            currentNodeId = nextNodeId;
        }
        if (currentNodeId != returnDepotId) {
            return null;
        }

        Route route = new Route(startDepotId, routeIndex,
                this.instance.getProblemData().getStaticNodeCount() + this.instance.getExternalDepots().size());
        route.setVisitedVertices(vertices);
        try {
            route.update(this.instance);
        } catch (RuntimeException e) {
            return null;
        }
        return route;
    }

    private Integer pollNextHead(int tailNodeId, Map<ArcKey, Integer> arcCounts) {
        ArcKey selectedKey = null;
        int selectedCount = 0;
        for (Map.Entry<ArcKey, Integer> entry : arcCounts.entrySet()) {
            ArcKey key = entry.getKey();
            int count = entry.getValue();
            if (count <= 0 || key.tail != tailNodeId) {
                continue;
            }
            selectedKey = key;
            selectedCount = count;
            break;
        }
        if (selectedKey == null) {
            return null;
        }
        if (selectedCount == 1) {
            arcCounts.remove(selectedKey);
        } else {
            arcCounts.put(selectedKey, selectedCount - 1);
        }
        return selectedKey.head;
    }

    private boolean hasRemainingArcCount(Map<ArcKey, Integer> arcCounts) {
        for (Integer count : arcCounts.values()) {
            if (count != null && count > 0) {
                return true;
            }
        }
        return false;
    }

    private int countIncomingArcs(Map<ArcKey, Integer> arcCounts, int nodeId) {
        int count = 0;
        for (Map.Entry<ArcKey, Integer> entry : arcCounts.entrySet()) {
            if (entry.getKey().head == nodeId) {
                count += entry.getValue();
            }
        }
        return count;
    }

    private int countOutgoingArcs(Map<ArcKey, Integer> arcCounts, int nodeId) {
        int count = 0;
        for (Map.Entry<ArcKey, Integer> entry : arcCounts.entrySet()) {
            if (entry.getKey().tail == nodeId) {
                count += entry.getValue();
            }
        }
        return count;
    }

    private boolean isStageFeasible(List<Route> repairedRoutes) {
        StageSolution stageSolution = new StageSolution();
        stageSolution.setSelectedRoutes(copyRoutes(repairedRoutes));
        return new RouteFeasibilityValidator().findStageSolutionViolations(this.instance, stageSolution).isEmpty();
    }

    private boolean satisfiesBranchConstraints(List<Route> repairedRoutes, Node<Route, LmSrc, Graph> node) {
        if (node.getBranchConstraints() == null || node.getBranchConstraints().isEmpty()) {
            return true;
        }
        if (this.branchingStrategy == null || this.branchingStrategy.getBranchingRule() == null) {
            return false;
        }
        for (Constraint constraint : node.getBranchConstraints()) {
            if (constraint == null || constraint.getCoeff_1() == null || constraint.getVariable() == null) {
                return false;
            }
            double lhs = 0.0;
            for (Route route : repairedRoutes) {
                int machineIndex = getMachineIndex(route.getStartDepotId(), node.getGraphs());
                if (machineIndex < 0 || machineIndex >= constraint.getCoeff_1().size()) {
                    return false;
                }
                lhs += constraint.getCoeff_1().get(machineIndex)
                        * this.branchingStrategy.getBranchingRule().calculateVariableVisitedTimes(
                        route,
                        constraint.getVariable()
                );
            }
            if (lhs + EPSILON < constraint.getRightSideValue()) {
                return false;
            }
        }
        return true;
    }

    private int getMachineIndex(int startDepotId, List<Graph> graphs) {
        for (int i = 0; i < graphs.size(); i++) {
            if (graphs.get(i) != null && graphs.get(i).getStartDepotId() == startDepotId) {
                return i;
            }
        }
        return -1;
    }

    private List<List<Route>> copyUsedScheduleSet(List<List<Route>> usedScheduleSet) {
        List<List<Route>> copies = new ArrayList<>();
        if (usedScheduleSet == null) {
            return copies;
        }
        for (List<Route> routes : usedScheduleSet) {
            copies.add(copyRoutes(routes));
        }
        return copies;
    }

    private List<Graph> copyGraphs(List<Graph> graphs) {
        List<Graph> copies = new ArrayList<>();
        for (Graph graph : graphs) {
            copies.add(graph == null ? null : graph.copy());
        }
        return copies;
    }

    private double[] extractMachineDuals(List<Graph> graphs, double[] startDepotDuals) {
        double[] duals = new double[graphs.size()];
        if (startDepotDuals == null) {
            return duals;
        }
        for (int i = 0; i < graphs.size(); i++) {
            int startDepotId = graphs.get(i).getStartDepotId();
            if (startDepotId >= 0 && startDepotId < startDepotDuals.length) {
                duals[i] = startDepotDuals[startDepotId];
            }
        }
        return duals;
    }

    private double[] expandMachineDualsToStartDepotIds(Node<Route, LmSrc, Graph> node) {
        int maxStartDepotId = this.instance.getReturnDepot().getId();
        for (Graph graph : node.getGraphs()) {
            maxStartDepotId = Math.max(maxStartDepotId, graph.getStartDepotId());
        }

        double[] startDepotDuals = new double[maxStartDepotId + 1];
        double[] machineDuals = node.getSolution().getDualsOfMachines();
        for (int i = 0; i < node.getGraphs().size() && i < machineDuals.length; i++) {
            startDepotDuals[node.getGraphs().get(i).getStartDepotId()] = machineDuals[i];
        }
        return startDepotDuals;
    }

    private List<double[]> buildYksByGraph(List<Graph> graphs, List<Route> routes) {
        List<double[]> yks = new ArrayList<>();
        for (Graph graph : graphs) {
            List<Route> graphRoutes = new ArrayList<>();
            for (Route route : routes) {
                if (route != null && route.getStartDepotId() == graph.getStartDepotId()) {
                    graphRoutes.add(route);
                }
            }
            double[] yk = new double[graphRoutes.size()];
            for (int i = 0; i < graphRoutes.size(); i++) {
                yk[i] = graphRoutes.get(i).getValue();
            }
            yks.add(yk);
        }
        return yks;
    }

    private List<Route> filterOutExistingRoutes(List<Route> candidateRoutes, List<Route> existingRoutes) {
        Set<String> existingSignatures = new HashSet<>();
        for (Route route : existingRoutes) {
            existingSignatures.add(buildRouteSignature(route));
        }

        List<Route> filteredRoutes = new ArrayList<>();
        for (Route route : candidateRoutes) {
            String signature = buildRouteSignature(route);
            if (existingSignatures.contains(signature)) {
                continue;
            }
            filteredRoutes.add(route.copy());
        }
        return filteredRoutes;
    }

    private void bootstrapRootSolveContext(DynamicInstance instance, List<Route> initialRoutes, int maxAddedColumnsPerIteration) {
        Configuration config = createDefaultConfiguration(maxAddedColumnsPerIteration);
        BPCLogger logger = new BPCLogger(config, "dvrp-root");
        initialize(instance, config, logger);
        this.currentInitialRoutes = copyRoutes(initialRoutes);
        this.currentGraphs = new ArrayList<>();
    }

    private Configuration createDefaultConfiguration(int maxAddedColumnsPerIteration) {
        Configuration config = new Configuration();

        Configuration.LoggingConfig logging = new Configuration.LoggingConfig();
        Configuration.LoggingConfig.ComponentsConfig components = new Configuration.LoggingConfig.ComponentsConfig();
        logging.setLOGGING_SWITCH("off");
        logging.setComponents(components);
        config.setLogging(logging);

        Configuration.RelaxationConfig relaxation = new Configuration.RelaxationConfig();
        relaxation.setRelaxationSwitch("none");
        relaxation.setNgSize(4);
        relaxation.setMaxNgSize(10);
        config.setRelaxation(relaxation);
        applyYamlRelaxationConfigIfPresent(config);
        applyProgrammaticRelaxationRuleIfPresent(config);

        Configuration.StabilizationConfig stabilization = new Configuration.StabilizationConfig();
        stabilization.setStabilizationSwitch("none");
        config.setStabilization(stabilization);

        Configuration.DivingConfig diving = new Configuration.DivingConfig();
        diving.setDivingSwitch("none");
        config.setDiving(diving);

        Configuration.PrimalHeuristicConfig primalHeuristic = new Configuration.PrimalHeuristicConfig();
        primalHeuristic.setPrimalHeuristicSwitch(this.primalHeuristicRule);
        config.setPrimalHeuristic(primalHeuristic);

        Configuration.ColumnGenerationConfig columnGeneration = new Configuration.ColumnGenerationConfig();
        columnGeneration.setColumnGenerationSwitch("none");
        columnGeneration.setNumAddedColumn(maxAddedColumnsPerIteration);
        columnGeneration.setMaxNumColumn(Integer.MAX_VALUE);
        columnGeneration.setParallelPricing(this.parallelPricing);
        config.setColumnGeneration(columnGeneration);

        Configuration.CuttingConfig cutting = new Configuration.CuttingConfig();
        cutting.setCuttingSwitch("none");
        cutting.setNumAddedCut(0);
        cutting.setMaxNumCut(0);
        cutting.setBaseSetSize(3);
        cutting.setMultiplier(0.5);
        config.setCutting(cutting);
        applyYamlCuttingConfigIfPresent(config);
        applyProgrammaticCuttingRuleIfPresent(config);

        Configuration.BranchingConfig branching = new Configuration.BranchingConfig();
        branching.setBranchingSwitch("branch-and-bound");
        branching.setBranchVariable("qij");
        branching.setVariableSelectionRule("most fractional branching");
        branching.setNodeSelectionRule("best-first selection");
        branching.setNumCandidateVariables1(10);
        branching.setNumCandidateVariables2(4);
        config.setBranching(branching);
        applyYamlBranchingConfigIfPresent(config);
        applyProgrammaticBranchingRuleIfPresent(config);
        applyProgrammaticVariableSelectionRuleIfPresent(config);

        Configuration.PricingConfig pricing = new Configuration.PricingConfig();
        pricing.setPricingSwitch("forward labeling");
        pricing.setHeuristicPricing(this.heuristicPricingRule);
        config.setPricing(pricing);

        Configuration.VariableFixingConfig variableFixing = new Configuration.VariableFixingConfig();
        variableFixing.setVariableFixingSwitch("none");
        config.setVariableFixing(variableFixing);

        Configuration.ScheduleEnumerationConfig scheduleEnumeration = new Configuration.ScheduleEnumerationConfig();
        scheduleEnumeration.setScheduleEnumerationSwitch("none");
        config.setScheduleEnumeration(scheduleEnumeration);

        return config;
    }

    private void applyYamlCuttingConfigIfPresent(Configuration config) {
        try {
            Configuration loadedConfig = Configuration.loadFromFile(DEFAULT_SOLVER_CONFIG_PATH);
            if (loadedConfig != null && loadedConfig.getCutting() != null) {
                config.setCutting(loadedConfig.getCutting());
            }
        } catch (Exception ignored) {
            // The programmatic defaults keep tests and ad-hoc runs independent from the YAML file location.
        }
    }

    private void applyProgrammaticCuttingRuleIfPresent(Configuration config) {
        if (this.cuttingRule == null || config == null) {
            return;
        }
        Configuration.CuttingConfig cutting = config.getCutting();
        if (cutting == null) {
            cutting = new Configuration.CuttingConfig();
            config.setCutting(cutting);
        }
        cutting.setCuttingSwitch(this.cuttingRule);
        if ("none".equalsIgnoreCase(this.cuttingRule)) {
            cutting.setNumAddedCut(0);
            cutting.setMaxNumCut(0);
            return;
        }
        if (cutting.getNumAddedCut() <= 0) {
            cutting.setNumAddedCut(4);
        }
        if (cutting.getMaxNumCut() <= 0) {
            cutting.setMaxNumCut(100);
        }
        if (cutting.getBaseSetSize() <= 0) {
            cutting.setBaseSetSize(3);
        }
        if (cutting.getMultiplier() <= 0.0) {
            cutting.setMultiplier(0.5);
        }
    }

    private void applyProgrammaticBranchingRuleIfPresent(Configuration config) {
        if (config == null) {
            return;
        }
        Configuration.BranchingConfig branching = config.getBranching();
        if (branching == null) {
            branching = new Configuration.BranchingConfig();
            config.setBranching(branching);
        }
        if (this.branchingSwitch != null) {
            branching.setBranchingSwitch(this.branchingSwitch);
        }
        if (this.branchVariable != null) {
            branching.setBranchVariable(this.branchVariable);
        }
        if (branching.getBranchingSwitch() == null || branching.getBranchingSwitch().trim().isEmpty()) {
            branching.setBranchingSwitch("branch-and-bound");
        }
        if (branching.getBranchVariable() == null || branching.getBranchVariable().trim().isEmpty()) {
            branching.setBranchVariable("qij");
        }
    }

    private void applyYamlBranchingConfigIfPresent(Configuration config) {
        try {
            Configuration loadedConfig = Configuration.loadFromFile(DEFAULT_SOLVER_CONFIG_PATH);
            if (loadedConfig != null && loadedConfig.getBranching() != null) {
                config.setBranching(loadedConfig.getBranching());
            }
        } catch (Exception ignored) {
            // Programmatic defaults keep tests independent from the YAML file location.
        }
        normalizeBranchingConfig(config);
    }

    private void normalizeBranchingConfig(Configuration config) {
        if (config == null) {
            return;
        }
        Configuration.BranchingConfig branching = config.getBranching();
        if (branching == null) {
            branching = new Configuration.BranchingConfig();
            config.setBranching(branching);
        }
        if (branching.getBranchingSwitch() == null || branching.getBranchingSwitch().trim().isEmpty()) {
            branching.setBranchingSwitch("branch-and-bound");
        }
        if (branching.getBranchVariable() == null || branching.getBranchVariable().trim().isEmpty()) {
            branching.setBranchVariable("qij");
        }
        if (branching.getVariableSelectionRule() == null || branching.getVariableSelectionRule().trim().isEmpty()) {
            branching.setVariableSelectionRule("most fractional branching");
        }
        if (branching.getNodeSelectionRule() == null || branching.getNodeSelectionRule().trim().isEmpty()) {
            branching.setNodeSelectionRule("best-first selection");
        }
        if (branching.getNumCandidateVariables1() <= 0) {
            branching.setNumCandidateVariables1(10);
        }
        if (branching.getNumCandidateVariables2() <= 0) {
            branching.setNumCandidateVariables2(4);
        }
    }

    private void applyProgrammaticVariableSelectionRuleIfPresent(Configuration config) {
        if (this.variableSelectionRule == null || config == null) {
            return;
        }
        normalizeBranchingConfig(config);
        config.getBranching().setVariableSelectionRule(this.variableSelectionRule);
    }

    private List<Route> generateInitialRoutes(DynamicInstance instance) {
        instance.validate();

        List<Route> initialRoutes = new ArrayList<>();
        int routeIndex = 0;

        for (int customerId : instance.getRemainingCustomerIds()) {
            Route route = buildRouteIfFeasible(
                    instance,
                    instance.getOriginDepot().getId(),
                    routeIndex,
                    instance.getOriginDepot().getId(),
                    customerId,
                    instance.getReturnDepot().getId()
            );
            if (route != null) {
                initialRoutes.add(route);
                routeIndex++;
            }
        }

        for (Depot externalDepot : instance.getExternalDepots()) {
            Route route = buildRouteIfFeasible(
                    instance,
                    externalDepot.getId(),
                    routeIndex,
                    externalDepot.getId(),
                    instance.getReturnDepot().getId()
            );
            if (route != null) {
                initialRoutes.add(route);
                routeIndex++;
            }
        }

        return initialRoutes;
    }

    private Route buildRouteIfFeasible(DynamicInstance instance, int startDepotId, int routeIndex, int... nodeIds) {
        TIntArrayList visitedVertices = new TIntArrayList(nodeIds);
        Route route = new Route(startDepotId, routeIndex, buildRouteVectorSize(instance));
        route.setVisitedVertices(visitedVertices);
        route.update(instance);
        if (!routeFeasibilityValidator.isRouteFeasible(instance, route)) {
            return null;
        }
        return route;
    }

    private int buildRouteVectorSize(DynamicInstance instance) {
        int maxNodeId = instance.getReturnDepot().getId();
        for (Depot externalDepot : instance.getExternalDepots()) {
            maxNodeId = Math.max(maxNodeId, externalDepot.getId());
        }
        return maxNodeId + 1;
    }

    @Override
    protected List<Route> selectNewColumnsForGraph(List<Route> candidateRoutes) {
        List<Route> sortedCandidates = new ArrayList<>(candidateRoutes);
        sortedCandidates.sort(DeterministicOrdering.routeComparator());

        List<Route> selectedRoutes = new ArrayList<>();
        for (Route route : sortedCandidates) {
            selectedRoutes.add(route.copy());
            if (selectedRoutes.size() >= this.config.getColumnGeneration().getNumAddedColumn()) {
                break;
            }
        }
        return selectedRoutes;
    }

    private static final class ArcKey implements Comparable<ArcKey> {
        private final int tail;
        private final int head;

        private ArcKey(int tail, int head) {
            this.tail = tail;
            this.head = head;
        }

        @Override
        public int compareTo(ArcKey other) {
            int tailCompare = Integer.compare(this.tail, other.tail);
            if (tailCompare != 0) {
                return tailCompare;
            }
            return Integer.compare(this.head, other.head);
        }
    }

    private static final class PricingTaskResult {
        private final int graphIndex;
        private final List<Route> routes;
        private final List<Constraint> branchConstraints;
        private final List<Route> existingRoutes;
        private final long elapsedNanos;
        private final PricingDiagnostics.Snapshot diagnostics;

        private PricingTaskResult(int graphIndex,
                                  List<Route> routes,
                                  List<Constraint> branchConstraints,
                                  List<Route> existingRoutes,
                                  long elapsedNanos,
                                  PricingDiagnostics.Snapshot diagnostics) {
            this.graphIndex = graphIndex;
            this.routes = copyRouteList(routes);
            this.branchConstraints = copyBranchConstraints(branchConstraints);
            this.existingRoutes = copyRouteList(existingRoutes);
            this.elapsedNanos = elapsedNanos;
            this.diagnostics = diagnostics == null ? new PricingDiagnostics.Snapshot() : diagnostics.copy();
        }

        private int getGraphIndex() {
            return graphIndex;
        }

        private List<Route> getRoutes() {
            return copyRouteList(routes);
        }

        private List<Constraint> getBranchConstraints() {
            return copyBranchConstraints(branchConstraints);
        }

        private List<Route> getExistingRoutes() {
            return copyRouteList(existingRoutes);
        }

        private long getElapsedNanos() {
            return elapsedNanos;
        }

        private PricingDiagnostics.Snapshot getDiagnostics() {
            return diagnostics.copy();
        }
    }

    private void applyYamlRelaxationConfigIfPresent(Configuration config) {
        try {
            Configuration loadedConfig = Configuration.loadFromFile(DEFAULT_SOLVER_CONFIG_PATH);
            if (loadedConfig != null && loadedConfig.getRelaxation() != null) {
                config.setRelaxation(loadedConfig.getRelaxation());
            }
        } catch (Exception ignored) {
            // The programmatic defaults keep tests and ad-hoc runs independent from the YAML file location.
        }
    }

    private void applyProgrammaticRelaxationRuleIfPresent(Configuration config) {
        if (this.relaxationRule == null || config == null) {
            return;
        }
        Configuration.RelaxationConfig relaxation = config.getRelaxation();
        if (relaxation == null) {
            relaxation = new Configuration.RelaxationConfig();
            config.setRelaxation(relaxation);
        }
        relaxation.setRelaxationSwitch(this.relaxationRule);
        if (relaxation.getNgSize() <= 0) {
            relaxation.setNgSize(4);
        }
        if (relaxation.getMaxNgSize() <= 0) {
            relaxation.setMaxNgSize(Math.max(10, relaxation.getNgSize()));
        }
    }

    private PricingTaskInput buildPricingTaskInput(Node<Route, LmSrc, Graph> node, Graph graph, int graphIndex) {
        return new PricingTaskInput(
                graph,
                graphIndex,
                copyCuts(node.getUsedCutSet()),
                copyBranchConstraints(node.getBranchConstraints()),
                node.getSolution().getDualsOfMachines()[graphIndex],
                node.getSolution().getDualsOfJobs(),
                node.getSolution().getDualsOfCuts(),
                node.getSolution().getDualsOfBranches(),
                node.getUsedScheduleSet().get(graphIndex)
        );
    }

    private PricingTaskResult runPricingTask(PricingTaskInput input, String type, int iter) {
        long startNanos = System.nanoTime();
        PricingDiagnostics.reset();
        PricingDiagnostics.setDeadlineNanos(getSolveDeadlineNanos());
        List<Route> newSchedules;
        PricingDiagnostics.Snapshot diagnostics;
        try {
            newSchedules = pricingAlgorithm(
                    type,
                    input.getGraph(),
                    input.getGraphIndex(),
                    input.getCuts(),
                    input.getBranchConstraints(),
                    input.getDualsOfMachines(),
                    input.getDualsOfJobs(),
                    input.getDualsOfCuts(),
                    input.getDualsOfBranches(),
                    iter
            );
        } finally {
            diagnostics = PricingDiagnostics.snapshotAndClear();
        }
        return new PricingTaskResult(
                input.getGraphIndex(),
                newSchedules,
                input.getBranchConstraints(),
                input.getExistingRoutes(),
                System.nanoTime() - startNanos,
                diagnostics
        );
    }

    private static List<LmSrc> copyCuts(List<LmSrc> cuts) {
        List<LmSrc> copies = new ArrayList<>();
        if (cuts == null) {
            return copies;
        }
        for (LmSrc cut : cuts) {
            copies.add(cut == null ? null : cut.copy());
        }
        return copies;
    }

    private static List<Constraint> copyBranchConstraints(List<Constraint> constraints) {
        List<Constraint> copies = new ArrayList<>();
        if (constraints == null) {
            return copies;
        }
        for (Constraint constraint : constraints) {
            copies.add(constraint == null ? null : new Constraint(constraint));
        }
        return copies;
    }

    private static List<Route> copyRouteList(List<Route> routes) {
        List<Route> copies = new ArrayList<>();
        if (routes == null) {
            return copies;
        }
        for (Route route : routes) {
            copies.add(route == null ? null : route.copy());
        }
        return copies;
    }

    private static final class PricingTaskInput {
        private final Graph graph;
        private final int graphIndex;
        private final List<LmSrc> cuts;
        private final List<Constraint> branchConstraints;
        private final double dualsOfMachines;
        private final double[] dualsOfJobs;
        private final List<Double> dualsOfCuts;
        private final List<Double> dualsOfBranches;
        private final List<Route> existingRoutes;

        private PricingTaskInput(Graph graph,
                                 int graphIndex,
                                 List<LmSrc> cuts,
                                 List<Constraint> branchConstraints,
                                 double dualsOfMachines,
                                 double[] dualsOfJobs,
                                 List<Double> dualsOfCuts,
                                 List<Double> dualsOfBranches,
                                 List<Route> existingRoutes) {
            this.graph = graph;
            this.graphIndex = graphIndex;
            this.cuts = copyCuts(cuts);
            this.branchConstraints = copyBranchConstraints(branchConstraints);
            this.dualsOfMachines = dualsOfMachines;
            this.dualsOfJobs = dualsOfJobs == null ? new double[0] : dualsOfJobs.clone();
            this.dualsOfCuts = dualsOfCuts == null ? new ArrayList<>() : new ArrayList<>(dualsOfCuts);
            this.dualsOfBranches = dualsOfBranches == null ? new ArrayList<>() : new ArrayList<>(dualsOfBranches);
            this.existingRoutes = copyRouteList(existingRoutes);
        }

        private Graph getGraph() {
            return graph;
        }

        private int getGraphIndex() {
            return graphIndex;
        }

        private List<LmSrc> getCuts() {
            return copyCuts(cuts);
        }

        private List<Constraint> getBranchConstraints() {
            return copyBranchConstraints(branchConstraints);
        }

        private double getDualsOfMachines() {
            return dualsOfMachines;
        }

        private double[] getDualsOfJobs() {
            return dualsOfJobs.clone();
        }

        private List<Double> getDualsOfCuts() {
            return new ArrayList<>(dualsOfCuts);
        }

        private List<Double> getDualsOfBranches() {
            return new ArrayList<>(dualsOfBranches);
        }

        private List<Route> getExistingRoutes() {
            return copyRouteList(existingRoutes);
        }
    }

    private String buildRouteSignature(Route route) {
        if (route == null || route.getVisitedVertices() == null) {
            return "null";
        }
        StringBuilder builder = new StringBuilder();
        builder.append(route.getStartDepotId()).append(':');
        int[] nodes = route.getVisitedVertices().toArray();
        for (int i = 0; i < nodes.length; i++) {
            if (i > 0) {
                builder.append('-');
            }
            builder.append(nodes[i]);
        }
        return builder.toString();
    }

    public enum SolveStatus {
        INTEGER_ROOT,
        INTEGER_INCUMBENT,
        CG_ITERATION_LIMIT,
        MASTER_INFEASIBLE,
        LP_FALLBACK
    }

    public static class SolveResult {

        private final Node<Route, LmSrc, Graph> resultNode;
        private final StageSolution solution;
        private final SolveStatus status;
        private final BranchSearchStatistics branchSearchStatistics;
        private final BPCRunStatistics runStatistics;
        private final boolean certifiedExact;

        public SolveResult(Node<Route, LmSrc, Graph> resultNode, StageSolution solution) {
            this(resultNode, solution, resultNode != null && resultNode.isInteger()
                    ? SolveStatus.INTEGER_INCUMBENT
                    : SolveStatus.LP_FALLBACK);
        }

        public SolveResult(Node<Route, LmSrc, Graph> resultNode, StageSolution solution, SolveStatus status) {
            this(resultNode, solution, status, BranchSearchStatistics.notStarted(
                    resultNode == null || resultNode.getSolution() == null
                            ? Double.POSITIVE_INFINITY
                            : resultNode.getSolution().getObjValue(),
                    0,
                    0
            ));
        }

        public SolveResult(Node<Route, LmSrc, Graph> resultNode,
                           StageSolution solution,
                           SolveStatus status,
                           BranchSearchStatistics branchSearchStatistics) {
            this(resultNode, solution, status, branchSearchStatistics, BPCRunStatistics.empty());
        }

        public SolveResult(Node<Route, LmSrc, Graph> resultNode,
                           StageSolution solution,
                           SolveStatus status,
                           BranchSearchStatistics branchSearchStatistics,
                           BPCRunStatistics runStatistics) {
            this(resultNode, solution, status, branchSearchStatistics, runStatistics, false);
        }

        public SolveResult(Node<Route, LmSrc, Graph> resultNode,
                           StageSolution solution,
                           SolveStatus status,
                           BranchSearchStatistics branchSearchStatistics,
                           BPCRunStatistics runStatistics,
                           boolean certifiedExact) {
            if (resultNode == null) {
                throw new IllegalArgumentException("resultNode must not be null.");
            }
            if (solution == null) {
                throw new IllegalArgumentException("solution must not be null.");
            }
            if (status == null) {
                throw new IllegalArgumentException("status must not be null.");
            }
            this.resultNode = resultNode.copy();
            this.solution = solution.copy();
            this.status = status;
            this.branchSearchStatistics = branchSearchStatistics == null
                    ? BranchSearchStatistics.notStarted()
                    : branchSearchStatistics.copy();
            this.runStatistics = runStatistics == null
                    ? BPCRunStatistics.empty()
                    : runStatistics.copy();
            this.certifiedExact = certifiedExact;
        }

        public Node<Route, LmSrc, Graph> getResultNode() {
            return resultNode.copy();
        }

        /**
         * Compatibility alias for older tests/callers that still use the root-only name.
         */
        @Deprecated
        public Node<Route, LmSrc, Graph> getRootNode() {
            return getResultNode();
        }

        public StageSolution getSolution() {
            return solution.copy();
        }

        public SolveStatus getStatus() {
            return status;
        }

        public BranchSearchStatistics getBranchSearchStatistics() {
            return branchSearchStatistics.copy();
        }

        public BPCRunStatistics getRunStatistics() {
            return runStatistics.copy();
        }

        public boolean hasIntegerIncumbent() {
            return status == SolveStatus.INTEGER_ROOT || status == SolveStatus.INTEGER_INCUMBENT;
        }

        public boolean isCertifiedExact() {
            return certifiedExact;
        }

        public boolean isFallback() {
            return status == SolveStatus.LP_FALLBACK;
        }
    }
}

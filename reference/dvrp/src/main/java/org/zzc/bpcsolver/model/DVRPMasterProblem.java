package org.zzc.bpcsolver.model;

import gnu.trove.list.array.TIntArrayList;
import ilog.concert.IloColumn;
import ilog.concert.IloException;
import ilog.concert.IloLinearNumExpr;
import ilog.concert.IloNumVar;
import ilog.concert.IloNumVarType;
import ilog.concert.IloObjective;
import ilog.concert.IloRange;
import ilog.cplex.IloCplex;
import lombok.Getter;
import org.zzc.bpcsolver.algorithm.branching.BranchingRule;
import org.zzc.bpcsolver.algorithm.cutting.LmSrc;
import org.zzc.bpcsolver.core.branch.StageSolution;
import org.zzc.bpcsolver.core.branch.Constraint;
import org.zzc.bpcsolver.core.graph.Graph;
import org.zzc.bpcsolver.core.graph.Route;
import org.zzc.bpcsolver.problem.entity.Depot;
import org.zzc.bpcsolver.problem.entity.DynamicInstance;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Getter
public class DVRPMasterProblem implements AutoCloseable {

    private static final double EPSILON = 1e-6;

    private DynamicInstance instance;
    private boolean integerModel;
    private IloCplex model;
    private IloObjective objective;
    private List<Route> routes;
    private List<IloNumVar> routeVariables;
    private List<IloRange> customerConstraints;
    private IloRange originVehicleConstraint;
    private List<IloRange> externalDepotConstraints;
    private List<IloRange> cutConstraints;
    private List<LmSrc> cutDefinitions;
    private List<IloRange> branchConstraints;
    private List<Constraint> branchConstraintDefinitions;
    private BranchingRule<Route, LmSrc, Graph> branchingRule;
    private double[] variableValues;

    public DVRPMasterProblem() {
        this.routes = new ArrayList<>();
        this.routeVariables = new ArrayList<>();
        this.customerConstraints = new ArrayList<>();
        this.externalDepotConstraints = new ArrayList<>();
        this.cutConstraints = new ArrayList<>();
        this.cutDefinitions = new ArrayList<>();
        this.branchConstraints = new ArrayList<>();
        this.branchConstraintDefinitions = new ArrayList<>();
    }

    public void initialize(DynamicInstance instance, List<Route> initialRoutes, boolean integerModel) {
        if (instance == null) {
            throw new IllegalArgumentException("instance must not be null.");
        }
        if (initialRoutes == null) {
            throw new IllegalArgumentException("initialRoutes must not be null.");
        }

        end();
        this.instance = instance.copy();
        this.integerModel = integerModel;
        this.routes = new ArrayList<>();
        for (Route route : initialRoutes) {
            this.routes.add(route.copy());
        }
        this.routeVariables = new ArrayList<>();
        this.customerConstraints = new ArrayList<>();
        this.externalDepotConstraints = new ArrayList<>();
        this.cutConstraints = new ArrayList<>();
        this.cutDefinitions = new ArrayList<>();
        this.branchConstraints = new ArrayList<>();
        this.branchConstraintDefinitions = new ArrayList<>();
        this.branchingRule = null;
        this.originVehicleConstraint = null;
        this.variableValues = new double[0];

        try {
            this.model = new IloCplex();
            this.model.setOut(null);
            this.model.setParam(IloCplex.Param.Simplex.Tolerances.Feasibility, 1e-9);
            this.model.setParam(IloCplex.Param.RootAlgorithm, IloCplex.Algorithm.Primal);
            this.objective = this.model.addMinimize();
        } catch (IloException e) {
            throw new IllegalStateException("Failed to initialize DVRP master problem.", e);
        }
    }

    public void end() {
        if (this.model != null) {
            this.model.end();
            this.model = null;
        }
        this.objective = null;
        this.routeVariables = new ArrayList<>();
        this.customerConstraints = new ArrayList<>();
        this.externalDepotConstraints = new ArrayList<>();
        this.cutConstraints = new ArrayList<>();
        this.cutDefinitions = new ArrayList<>();
        this.branchConstraints = new ArrayList<>();
        this.branchConstraintDefinitions = new ArrayList<>();
        this.originVehicleConstraint = null;
        this.branchingRule = null;
        this.variableValues = new double[0];
    }

    @Override
    public void close() {
        end();
    }

    public void configureBranching(List<Constraint> branchConstraints, BranchingRule<Route, LmSrc, Graph> branchingRule) {
        this.branchConstraintDefinitions = new ArrayList<>();
        if (branchConstraints != null) {
            for (Constraint branchConstraint : branchConstraints) {
                this.branchConstraintDefinitions.add(branchConstraint == null ? null : new Constraint(branchConstraint));
            }
        }
        this.branchingRule = branchingRule;
    }

    public void configureCuts(List<LmSrc> cuts) {
        this.cutDefinitions = new ArrayList<>();
        if (cuts == null) {
            return;
        }
        for (LmSrc cut : cuts) {
            this.cutDefinitions.add(cut == null ? null : cut.copy());
        }
    }

    public void addCuts(List<LmSrc> cuts) {
        ensureBuilt();
        if (cuts == null || cuts.isEmpty()) {
            return;
        }

        try {
            for (LmSrc cutInput : cuts) {
                if (cutInput == null) {
                    continue;
                }
                LmSrc cut = cutInput.copy();
                initializeCutCoefficientStorage(cut);

                IloLinearNumExpr expr = this.model.linearNumExpr();
                for (int j = 0; j < this.routes.size(); j++) {
                    Route route = this.routes.get(j);
                    int machineIndex = getMachineIndex(route.getStartDepotId());
                    double coefficient = cut.calculateCoefficient(route.getVisitedVertices(), machineIndex);
                    cut.getCoefficient().get(machineIndex).add(coefficient);
                    expr.addTerm(coefficient, this.routeVariables.get(j));
                }
                this.cutDefinitions.add(cut);
                this.cutConstraints.add(this.model.addLe(expr, cut.getConstant()));
            }
        } catch (IloException e) {
            throw new IllegalStateException("Failed to add DVRP cut rows.", e);
        }
    }

    public void build() {
        ensureInitialized();
        try {
            buildVariables();
            buildObjective();
            buildCustomerConstraints();
            buildOriginVehicleConstraint();
            buildExternalDepotConstraints();
            buildCutConstraints();
            buildBranchConstraints();
        } catch (IloException e) {
            throw new IllegalStateException("Failed to build DVRP master problem.", e);
        }
    }

    public void addColumns(List<Route> newRoutes) {
        ensureBuilt();
        if (newRoutes == null) {
            throw new IllegalArgumentException("newRoutes must not be null.");
        }

        try {
            for (Route route : newRoutes) {
                Route copiedRoute = route.copy();
                IloColumn column = this.model.column(this.objective, copiedRoute.getCost());
                for (int i = 0; i < this.instance.getRemainingCustomerIds().size(); i++) {
                    int customerId = this.instance.getRemainingCustomerIds().get(i);
                    column = this.model.column(this.customerConstraints.get(i), countVisits(copiedRoute, customerId)).and(column);
                }
                if (this.originVehicleConstraint != null) {
                    column = this.model.column(this.originVehicleConstraint, copiedRoute.getStartDepotId() == this.instance.getOriginDepot().getId() ? 1.0 : 0.0).and(column);
                }
                for (int i = 0; i < this.instance.getExternalDepots().size(); i++) {
                    Depot externalDepot = this.instance.getExternalDepots().get(i);
                    column = this.model.column(this.externalDepotConstraints.get(i), copiedRoute.getStartDepotId() == externalDepot.getId() ? 1.0 : 0.0).and(column);
                }
                column = appendCutColumns(column, copiedRoute);
                column = appendBranchColumns(column, copiedRoute);

                IloNumVar variable = this.model.numVar(column, 0.0, 1.0, this.integerModel ? IloNumVarType.Int : IloNumVarType.Float);
                this.routeVariables.add(variable);
                this.routes.add(copiedRoute);
            }
        } catch (IloException e) {
            throw new IllegalStateException("Failed to add columns to DVRP master problem.", e);
        }
    }

    public boolean solve() {
        ensureBuilt();
        try {
            if (!this.model.solve()) {
                return false;
            }
            this.variableValues = new double[this.routeVariables.size()];
            for (int i = 0; i < this.routeVariables.size(); i++) {
                this.variableValues[i] = this.model.getValue(this.routeVariables.get(i));
                this.routes.get(i).setValue(this.variableValues[i]);
            }
            return true;
        } catch (IloException e) {
            throw new IllegalStateException("Failed to solve DVRP master problem.", e);
        }
    }

    public double getObjectiveValue() {
        ensureBuilt();
        try {
            return this.model.getObjValue();
        } catch (IloException e) {
            throw new IllegalStateException("Failed to read objective value.", e);
        }
    }

    public void updateSolution(StageSolution stageSolution) {
        ensureBuilt();
        if (stageSolution == null) {
            throw new IllegalArgumentException("stageSolution must not be null.");
        }

        stageSolution.setObjectiveValue(getObjectiveValue());
        stageSolution.setAllRoutes(copyRoutes(this.routes));
        stageSolution.setSelectedRoutes(getSelectedRoutes());
        if (!this.integerModel) {
            stageSolution.setPricingCustomerDuals(getPricingCustomerDuals());
            stageSolution.setStartDepotConstraintDuals(getStartDepotConstraintDuals());
            stageSolution.setCutConstraintDuals(getCutConstraintDuals());
            stageSolution.setIntegerSolution(isCurrentSolutionInteger());
        } else {
            stageSolution.setPricingCustomerDuals(new double[0]);
            stageSolution.setStartDepotConstraintDuals(new double[0]);
            stageSolution.setCutConstraintDuals(new ArrayList<>());
            stageSolution.setIntegerSolution(true);
        }
    }

    public List<Route> getSelectedRoutes() {
        ensureBuilt();
        List<Route> selectedRoutes = new ArrayList<>();
        for (int i = 0; i < this.routes.size(); i++) {
            if (i < this.variableValues.length && this.variableValues[i] > EPSILON) {
                Route route = this.routes.get(i).copy();
                route.setValue(this.variableValues[i]);
                if (isSelectableSelectedRoute(this.instance, route)) {
                    selectedRoutes.add(route);
                }
            }
        }
        return selectedRoutes;
    }

    static boolean isSelectableSelectedRoute(DynamicInstance instance, Route route) {
        if (instance == null || route == null || route.getVisitedVertices() == null) {
            return false;
        }
        if (servesRemainingCustomer(instance, route)) {
            return true;
        }
        return route.getStartDepotId() != instance.getOriginDepot().getId();
    }

    private static boolean servesRemainingCustomer(DynamicInstance instance, Route route) {
        for (int nodeId : route.getVisitedVertices().toArray()) {
            if (instance.getRemainingCustomerIds().contains(nodeId)) {
                return true;
            }
        }
        return false;
    }

    public double[] getPricingCustomerDuals() {
        ensureBuilt();
        if (this.integerModel) {
            throw new IllegalStateException("Dual values are only available for LP models.");
        }

        double[] duals = createNodeIndexedDualArray();
        try {
            for (int i = 0; i < this.customerConstraints.size(); i++) {
                int customerId = this.instance.getRemainingCustomerIds().get(i);
                duals[customerId] = this.model.getDual(this.customerConstraints.get(i));
            }
        } catch (IloException e) {
            throw new IllegalStateException("Failed to read pricing customer duals.", e);
        }
        return duals;
    }

    public double[] getNodeDuals() {
        return getPricingCustomerDuals();
    }

    public double[] getStartDepotConstraintDuals() {
        ensureBuilt();
        if (this.integerModel) {
            throw new IllegalStateException("Dual values are only available for LP models.");
        }

        double[] duals = createNodeIndexedDualArray();
        try {
            duals[this.instance.getOriginDepot().getId()] = this.model.getDual(this.originVehicleConstraint);
            for (int i = 0; i < this.instance.getExternalDepots().size(); i++) {
                int externalDepotId = this.instance.getExternalDepots().get(i).getId();
                duals[externalDepotId] = this.model.getDual(this.externalDepotConstraints.get(i));
            }
        } catch (IloException e) {
            throw new IllegalStateException("Failed to read start-depot constraint duals.", e);
        }
        return duals;
    }

    public List<Double> getBranchConstraintDuals() {
        ensureBuilt();
        if (this.integerModel) {
            throw new IllegalStateException("Dual values are only available for LP models.");
        }
        if (this.branchConstraints.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            double[] duals = this.model.getDuals(this.branchConstraints.toArray(new IloRange[0]));
            return Arrays.stream(duals).boxed().collect(Collectors.toList());
        } catch (IloException e) {
            throw new IllegalStateException("Failed to read branch constraint duals.", e);
        }
    }

    public List<Double> getCutConstraintDuals() {
        ensureBuilt();
        if (this.integerModel) {
            throw new IllegalStateException("Dual values are only available for LP models.");
        }
        if (this.cutConstraints.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            double[] duals = this.model.getDuals(this.cutConstraints.toArray(new IloRange[0]));
            return Arrays.stream(duals).boxed().collect(Collectors.toList());
        } catch (IloException e) {
            throw new IllegalStateException("Failed to read cut constraint duals.", e);
        }
    }

    private void buildVariables() throws IloException {
        this.routeVariables.clear();
        for (int i = 0; i < this.routes.size(); i++) {
            this.routeVariables.add(this.model.numVar(0.0, 1.0, this.integerModel ? IloNumVarType.Int : IloNumVarType.Float));
        }
    }

    private void buildObjective() throws IloException {
        IloLinearNumExpr objectiveExpr = this.model.linearNumExpr();
        for (int i = 0; i < this.routes.size(); i++) {
            objectiveExpr.addTerm(this.routes.get(i).getCost(), this.routeVariables.get(i));
        }
        this.objective.setExpr(objectiveExpr);
    }

    private void buildCustomerConstraints() throws IloException {
        this.customerConstraints.clear();
        for (Integer customerId : this.instance.getRemainingCustomerIds()) {
            IloLinearNumExpr expr = this.model.linearNumExpr();
            for (int j = 0; j < this.routes.size(); j++) {
                expr.addTerm(countVisits(this.routes.get(j), customerId), this.routeVariables.get(j));
            }
            this.customerConstraints.add(this.model.addEq(expr, 1.0));
        }
    }

    private void buildOriginVehicleConstraint() throws IloException {
        IloLinearNumExpr expr = this.model.linearNumExpr();
        for (int j = 0; j < this.routes.size(); j++) {
            expr.addTerm(this.routes.get(j).getStartDepotId() == this.instance.getOriginDepot().getId() ? 1.0 : 0.0, this.routeVariables.get(j));
        }
        this.originVehicleConstraint = this.model.addGe(expr, 0.0);
    }

    private void buildExternalDepotConstraints() throws IloException {
        this.externalDepotConstraints.clear();
        for (Depot externalDepot : this.instance.getExternalDepots()) {
            IloLinearNumExpr expr = this.model.linearNumExpr();
            for (int j = 0; j < this.routes.size(); j++) {
                expr.addTerm(this.routes.get(j).getStartDepotId() == externalDepot.getId() ? 1.0 : 0.0, this.routeVariables.get(j));
            }
            this.externalDepotConstraints.add(this.model.addEq(expr, externalDepot.getVehicleCount()));
        }
    }

    private void buildCutConstraints() throws IloException {
        this.cutConstraints.clear();
        if (this.cutDefinitions == null || this.cutDefinitions.isEmpty()) {
            return;
        }
        for (LmSrc cut : this.cutDefinitions) {
            initializeCutCoefficientStorage(cut);
            IloLinearNumExpr expr = this.model.linearNumExpr();
            for (int j = 0; j < this.routes.size(); j++) {
                Route route = this.routes.get(j);
                int machineIndex = getMachineIndex(route.getStartDepotId());
                double coefficient = cut.calculateCoefficient(route.getVisitedVertices(), machineIndex);
                cut.getCoefficient().get(machineIndex).add(coefficient);
                expr.addTerm(coefficient, this.routeVariables.get(j));
            }
            this.cutConstraints.add(this.model.addLe(expr, cut.getConstant()));
        }
    }

    private void buildBranchConstraints() throws IloException {
        this.branchConstraints.clear();
        if (this.branchConstraintDefinitions == null || this.branchConstraintDefinitions.isEmpty() || this.branchingRule == null) {
            return;
        }
        for (Constraint branchConstraint : this.branchConstraintDefinitions) {
            IloLinearNumExpr expr = this.model.linearNumExpr();
            for (int j = 0; j < this.routes.size(); j++) {
                Route route = this.routes.get(j);
                int machineIndex = getMachineIndex(route.getStartDepotId());
                int visitedTimes = this.branchingRule.calculateVariableVisitedTimes(route, branchConstraint.getVariable());
                expr.addTerm(branchConstraint.getCoeff_1().get(machineIndex) * visitedTimes, this.routeVariables.get(j));
            }
            this.branchConstraints.add(this.model.addGe(expr, branchConstraint.getRightSideValue()));
        }
    }

    private int countVisits(Route route, int nodeId) {
        if (route != null
                && route.getVisitedVector() != null
                && nodeId >= 0
                && nodeId < route.getVisitedVector().length
                && route.getVisitedVector()[nodeId] > 0) {
            return route.getVisitedVector()[nodeId];
        }
        TIntArrayList visitedVertices = route.getVisitedVertices();
        if (visitedVertices == null) {
            return 0;
        }
        int visits = 0;
        for (int i = 0; i < visitedVertices.size(); i++) {
            if (visitedVertices.get(i) == nodeId) {
                visits++;
            }
        }
        return visits;
    }

    private List<Route> copyRoutes(List<Route> sourceRoutes) {
        List<Route> copies = new ArrayList<>();
        for (Route route : sourceRoutes) {
            copies.add(route == null ? null : route.copy());
        }
        return copies;
    }

    private double[] createNodeIndexedDualArray() {
        int maxNodeId = this.instance.getReturnDepot().getId();
        maxNodeId = Math.max(maxNodeId, this.instance.getOriginDepot().getId());
        for (Depot externalDepot : this.instance.getExternalDepots()) {
            maxNodeId = Math.max(maxNodeId, externalDepot.getId());
        }
        return new double[maxNodeId + 1];
    }

    private IloColumn appendBranchColumns(IloColumn baseColumn, Route route) throws IloException {
        if (this.branchConstraints.isEmpty() || this.branchingRule == null) {
            return baseColumn;
        }
        int machineIndex = getMachineIndex(route.getStartDepotId());
        IloColumn column = baseColumn;
        for (int i = 0; i < this.branchConstraints.size(); i++) {
            Constraint branchConstraint = this.branchConstraintDefinitions.get(i);
            int visitedTimes = this.branchingRule.calculateVariableVisitedTimes(route, branchConstraint.getVariable());
            column = this.model.column(this.branchConstraints.get(i), branchConstraint.getCoeff_1().get(machineIndex) * visitedTimes).and(column);
        }
        return column;
    }

    private IloColumn appendCutColumns(IloColumn baseColumn, Route route) throws IloException {
        if (this.cutConstraints.isEmpty() || this.cutDefinitions == null || this.cutDefinitions.isEmpty()) {
            return baseColumn;
        }
        int machineIndex = getMachineIndex(route.getStartDepotId());
        IloColumn column = baseColumn;
        for (int i = 0; i < this.cutConstraints.size(); i++) {
            LmSrc cut = this.cutDefinitions.get(i);
            double coefficient = cut.calculateCoefficient(route.getVisitedVertices(), machineIndex);
            cut.getCoefficient().get(machineIndex).add(coefficient);
            column = this.model.column(this.cutConstraints.get(i), coefficient).and(column);
        }
        return column;
    }

    private void initializeCutCoefficientStorage(LmSrc cut) {
        if (cut == null) {
            return;
        }
        List<List<Double>> coefficients = new ArrayList<>();
        int numMachines = getNumMachines();
        for (int k = 0; k < numMachines; k++) {
            coefficients.add(new ArrayList<>());
        }
        cut.setCoefficient(coefficients);
    }

    private int getNumMachines() {
        return 1 + this.instance.getExternalDepots().size();
    }

    private int getMachineIndex(int startDepotId) {
        if (startDepotId == this.instance.getOriginDepot().getId()) {
            return 0;
        }
        for (int i = 0; i < this.instance.getExternalDepots().size(); i++) {
            if (this.instance.getExternalDepots().get(i).getId() == startDepotId) {
                return i + 1;
            }
        }
        throw new IllegalArgumentException("Unknown start depot id: " + startDepotId);
    }

    private boolean isCurrentSolutionInteger() {
        for (double value : this.variableValues) {
            if (Math.abs(value - Math.rint(value)) > EPSILON) {
                return false;
            }
        }
        return true;
    }

    private void ensureInitialized() {
        if (this.instance == null || this.model == null || this.objective == null) {
            throw new IllegalStateException("DVRP master problem is not initialized.");
        }
    }

    private void ensureBuilt() {
        ensureInitialized();
        if (this.routeVariables.isEmpty() && !this.routes.isEmpty()) {
            throw new IllegalStateException("DVRP master problem has not been built.");
        }
        if (this.originVehicleConstraint == null) {
            throw new IllegalStateException("DVRP master problem has not been built.");
        }
    }
}

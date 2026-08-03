package org.zzc.bpcsolver.exact;

import gnu.trove.list.array.TIntArrayList;
import ilog.concert.IloException;
import ilog.concert.IloLinearNumExpr;
import ilog.concert.IloNumVar;
import ilog.cplex.IloCplex;
import org.zzc.bpcsolver.core.branch.StageSolution;
import org.zzc.bpcsolver.core.graph.Route;
import org.zzc.bpcsolver.core.graph.RouteFeasibilityValidator;
import org.zzc.bpcsolver.problem.entity.Depot;
import org.zzc.bpcsolver.problem.entity.DynamicInstance;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OriginalMipDVRPSolver {

    private static final double EPSILON = 1e-6;

    private final DVRPExactSolutionValidator validator;

    public OriginalMipDVRPSolver() {
        this(new DVRPExactSolutionValidator());
    }

    public OriginalMipDVRPSolver(DVRPExactSolutionValidator validator) {
        if (validator == null) {
            throw new IllegalArgumentException("validator must not be null.");
        }
        this.validator = validator;
    }

    public StageSolution solve(DynamicInstance instance) {
        return solve(instance, Options.defaults()).getStageSolution();
    }

    public Result solve(DynamicInstance instance, Options options) {
        if (instance == null) {
            throw new IllegalArgumentException("instance must not be null.");
        }
        if (options == null) {
            throw new IllegalArgumentException("options must not be null.");
        }
        instance.validate();
        long solveStartNanos = System.nanoTime();

        if (instance.getRemainingCustomerIds().isEmpty()) {
            StageSolution emptySolution = new StageSolution();
            emptySolution.setIntegerSolution(true);
            return new Result(emptySolution, 0.0, 0.0, true, "success", elapsedMillisSince(solveStartNanos));
        }

        IloCplex cplex = null;
        try {
            cplex = new IloCplex();
            configureCplex(cplex, options);

            ModelData modelData = buildModel(cplex, instance, options);
            if (options.getExportModelPath() != null && !options.getExportModelPath().trim().isEmpty()) {
                cplex.exportModel(options.getExportModelPath());
            }

            boolean solved = cplex.solve();
            if (!solved) {
                return new Result(
                        new StageSolution(),
                        Double.POSITIVE_INFINITY,
                        Double.NaN,
                        false,
                        cplex.getStatus().toString(),
                        elapsedMillisSince(solveStartNanos)
                );
            }

            List<Route> routes = extractRoutes(cplex, instance, modelData);
            StageSolution solution = new StageSolution();
            solution.setSelectedRoutes(copyRoutes(routes));
            solution.setAllRoutes(copyRoutes(routes));
            solution.setObjectiveValue(sumRouteCosts(routes));
            solution.setIntegerSolution(true);

            if (options.isValidateSolution()) {
                buildValidator(options).assertValid(instance, solution);
            }
            return new Result(solution, cplex.getObjValue(), cplex.getBestObjValue(), true,
                    cplex.getStatus().toString(),
                    elapsedMillisSince(solveStartNanos));
        } catch (IloException e) {
            throw new IllegalStateException("Failed to solve DVRP original MIP.", e);
        } finally {
            if (cplex != null) {
                cplex.end();
            }
        }
    }

    private long elapsedMillisSince(long startNanos) {
        return Math.max(0L, Math.round((System.nanoTime() - startNanos) / 1_000_000.0));
    }

    private void configureCplex(IloCplex cplex, Options options) throws IloException {
        if (!options.isOutputEnabled()) {
            cplex.setOut(null);
        }
        cplex.setParam(IloCplex.Param.Simplex.Tolerances.Feasibility, 1e-9);
        cplex.setParam(IloCplex.Param.MIP.Tolerances.Integrality, 1e-9);
        if (options.getTimeLimitSeconds() > 0.0) {
            cplex.setParam(IloCplex.Param.TimeLimit, options.getTimeLimitSeconds());
        }
        if (options.getMipGap() >= 0.0) {
            cplex.setParam(IloCplex.Param.MIP.Tolerances.MIPGap, options.getMipGap());
        }
    }

    private DVRPExactSolutionValidator buildValidator(Options options) {
        if (options.getStartTimeMode() == StartTimeMode.START_DEPOT_EARLIEST) {
            return new DVRPExactSolutionValidator(new RouteFeasibilityValidator(
                    EPSILON,
                    RouteFeasibilityValidator.StartTimePolicy.START_DEPOT_EARLIEST
            ));
        }
        return validator;
    }

    private ModelData buildModel(IloCplex cplex, DynamicInstance instance, Options options) throws IloException {
        List<Integer> customers = new ArrayList<>(instance.getRemainingCustomerIds());
        List<Vehicle> vehicles = buildVehicles(instance, options);
        double bigM = calculateBigM(instance);

        ModelData modelData = new ModelData(customers, vehicles, options);
        IloLinearNumExpr objective = cplex.linearNumExpr();

        for (int vehicleIndex = 0; vehicleIndex < vehicles.size(); vehicleIndex++) {
            Vehicle vehicle = vehicles.get(vehicleIndex);
            VehicleVars vars = buildVehicleVariables(cplex, instance, customers, vehicle);
            modelData.vehicleVars.add(vars);
            addVehicleObjectiveTerms(objective, instance, vars);
            addVehicleFlowConstraints(cplex, customers, vehicle, vars);
            addVehicleCapacityConstraint(cplex, instance, customers, vehicle, vars);
            addVehicleTimeConstraints(cplex, instance, customers, vehicle, vars, options, bigM);
            addVehicleSubtourConstraints(cplex, customers, vars);
        }

        cplex.addMinimize(objective);
        addCustomerCoverageConstraints(cplex, customers, modelData.vehicleVars);
        return modelData;
    }

    private List<Vehicle> buildVehicles(DynamicInstance instance, Options options) {
        List<Vehicle> vehicles = new ArrayList<>();
        int originVehicleCount = options.isUseAvailableOriginVehicleLimit()
                ? instance.getOriginDepot().getVehicleCount()
                : instance.getRemainingCustomerIds().size();
        originVehicleCount = Math.max(originVehicleCount, 0);
        for (int i = 0; i < originVehicleCount; i++) {
            vehicles.add(new Vehicle(
                    instance.getOriginDepot().getId(),
                    instance.getProblemData().getVehicleCapacity(),
                    false
            ));
        }
        for (Depot externalDepot : instance.getExternalDepots()) {
            int vehicleCount = Math.max(externalDepot.getVehicleCount(), 0);
            for (int i = 0; i < vehicleCount; i++) {
                vehicles.add(new Vehicle(externalDepot.getId(), externalDepot.getRemainingCapacity(), true));
            }
        }
        return vehicles;
    }

    private VehicleVars buildVehicleVariables(IloCplex cplex,
                                              DynamicInstance instance,
                                              List<Integer> customers,
                                              Vehicle vehicle) throws IloException {
        VehicleVars vars = new VehicleVars(vehicle.startDepotId);
        vars.use = cplex.boolVar("use_" + vehicle.startDepotId + "_" + System.identityHashCode(vars));
        for (Integer customerId : customers) {
            vars.yByCustomer.put(customerId, cplex.boolVar("y_" + vehicle.startDepotId + "_" + customerId));
            vars.timeByNode.put(customerId, cplex.numVar(0.0, calculateBigM(instance), "t_" + vehicle.startDepotId + "_" + customerId));
            vars.orderByCustomer.put(customerId, cplex.numVar(0.0, customers.size(), "ord_" + vehicle.startDepotId + "_" + customerId));
        }
        vars.timeByNode.put(instance.getReturnDepot().getId(),
                cplex.numVar(0.0, calculateBigM(instance), "t_" + vehicle.startDepotId + "_return"));

        List<Integer> tailNodes = new ArrayList<>();
        tailNodes.add(vehicle.startDepotId);
        tailNodes.addAll(customers);
        List<Integer> headNodes = new ArrayList<>(customers);
        headNodes.add(instance.getReturnDepot().getId());

        for (Integer tailNodeId : tailNodes) {
            Map<Integer, IloNumVar> outgoing = new HashMap<>();
            for (Integer headNodeId : headNodes) {
                if (tailNodeId.equals(headNodeId)) {
                    continue;
                }
                outgoing.put(headNodeId, cplex.boolVar("x_" + vehicle.startDepotId + "_" + tailNodeId + "_" + headNodeId));
            }
            vars.xByTailHead.put(tailNodeId, outgoing);
        }
        return vars;
    }

    private void addVehicleObjectiveTerms(IloLinearNumExpr objective,
                                          DynamicInstance instance,
                                          VehicleVars vars) throws IloException {
        for (Map.Entry<Integer, Map<Integer, IloNumVar>> tailEntry : vars.xByTailHead.entrySet()) {
            int tailNodeId = tailEntry.getKey();
            for (Map.Entry<Integer, IloNumVar> headEntry : tailEntry.getValue().entrySet()) {
                objective.addTerm(instance.getDistance(tailNodeId, headEntry.getKey()), headEntry.getValue());
            }
        }
    }

    private void addVehicleFlowConstraints(IloCplex cplex,
                                           List<Integer> customers,
                                           Vehicle vehicle,
                                           VehicleVars vars) throws IloException {
        IloLinearNumExpr startOut = cplex.linearNumExpr();
        for (IloNumVar arcVar : vars.xByTailHead.get(vehicle.startDepotId).values()) {
            startOut.addTerm(1.0, arcVar);
        }
        cplex.addEq(startOut, vars.use);

        IloLinearNumExpr returnIn = cplex.linearNumExpr();
        for (Map<Integer, IloNumVar> outgoing : vars.xByTailHead.values()) {
            IloNumVar returnArc = outgoing.get(vars.returnDepotId(customers));
            if (returnArc != null) {
                returnIn.addTerm(1.0, returnArc);
            }
        }
        cplex.addEq(returnIn, vars.use);

        for (Integer customerId : customers) {
            IloLinearNumExpr incoming = cplex.linearNumExpr();
            IloLinearNumExpr outgoing = cplex.linearNumExpr();
            for (Map.Entry<Integer, Map<Integer, IloNumVar>> tailEntry : vars.xByTailHead.entrySet()) {
                IloNumVar arcToCustomer = tailEntry.getValue().get(customerId);
                if (arcToCustomer != null) {
                    incoming.addTerm(1.0, arcToCustomer);
                }
            }
            Map<Integer, IloNumVar> customerOutgoing = vars.xByTailHead.get(customerId);
            if (customerOutgoing != null) {
                for (IloNumVar arcVar : customerOutgoing.values()) {
                    outgoing.addTerm(1.0, arcVar);
                }
            }
            cplex.addEq(incoming, vars.yByCustomer.get(customerId));
            cplex.addEq(outgoing, vars.yByCustomer.get(customerId));
        }

        if (vehicle.mandatory) {
            cplex.addEq(vars.use, 1.0);
        } else {
            IloLinearNumExpr servedCustomers = cplex.linearNumExpr();
            for (Integer customerId : customers) {
                servedCustomers.addTerm(1.0, vars.yByCustomer.get(customerId));
            }
            cplex.addGe(servedCustomers, vars.use);

            IloLinearNumExpr servedUpper = cplex.linearNumExpr();
            for (Integer customerId : customers) {
                servedUpper.addTerm(1.0, vars.yByCustomer.get(customerId));
            }
            servedUpper.addTerm(-customers.size(), vars.use);
            cplex.addLe(servedUpper, 0.0);
        }
    }

    private void addVehicleCapacityConstraint(IloCplex cplex,
                                              DynamicInstance instance,
                                              List<Integer> customers,
                                              Vehicle vehicle,
                                              VehicleVars vars) throws IloException {
        IloLinearNumExpr load = cplex.linearNumExpr();
        for (Integer customerId : customers) {
            load.addTerm(instance.getDemand(customerId), vars.yByCustomer.get(customerId));
        }
        cplex.addLe(load, vehicle.capacity);
    }

    private void addVehicleTimeConstraints(IloCplex cplex,
                                           DynamicInstance instance,
                                           List<Integer> customers,
                                           Vehicle vehicle,
                                           VehicleVars vars,
                                           Options options,
                                           double bigM) throws IloException {
        for (Integer customerId : customers) {
            IloNumVar time = vars.timeByNode.get(customerId);
            IloNumVar y = vars.yByCustomer.get(customerId);

            IloLinearNumExpr lower = cplex.linearNumExpr();
            lower.addTerm(1.0, time);
            lower.addTerm(-bigM, y);
            cplex.addGe(lower, instance.getEarliestTime(customerId) - bigM);

            IloLinearNumExpr upper = cplex.linearNumExpr();
            upper.addTerm(1.0, time);
            upper.addTerm(bigM, y);
            cplex.addLe(upper, instance.getLatestTime(customerId) + bigM);
        }

        int returnDepotId = instance.getReturnDepot().getId();
        IloNumVar returnTime = vars.timeByNode.get(returnDepotId);
        IloLinearNumExpr returnLower = cplex.linearNumExpr();
        returnLower.addTerm(1.0, returnTime);
        returnLower.addTerm(-bigM, vars.use);
        cplex.addGe(returnLower, instance.getEarliestTime(returnDepotId) - bigM);

        IloLinearNumExpr returnUpper = cplex.linearNumExpr();
        returnUpper.addTerm(1.0, returnTime);
        returnUpper.addTerm(bigM, vars.use);
        cplex.addLe(returnUpper, instance.getLatestTime(returnDepotId) + bigM);

        double startTime = resolveStartTime(instance, vehicle.startDepotId, options);
        for (Map.Entry<Integer, Map<Integer, IloNumVar>> tailEntry : vars.xByTailHead.entrySet()) {
            int tailNodeId = tailEntry.getKey();
            for (Map.Entry<Integer, IloNumVar> headEntry : tailEntry.getValue().entrySet()) {
                int headNodeId = headEntry.getKey();
                IloNumVar headTime = vars.timeByNode.get(headNodeId);
                IloLinearNumExpr propagation = cplex.linearNumExpr();
                propagation.addTerm(1.0, headTime);
                if (tailNodeId != vehicle.startDepotId) {
                    propagation.addTerm(-1.0, vars.timeByNode.get(tailNodeId));
                }
                propagation.addTerm(-bigM, headEntry.getValue());
                double tailTime = tailNodeId == vehicle.startDepotId ? startTime : 0.0;
                double rightSide = tailTime + instance.getServiceTime(tailNodeId)
                        + instance.getTravelTime(tailNodeId, headNodeId) - bigM;
                cplex.addGe(propagation, rightSide);
            }
        }
    }

    private void addVehicleSubtourConstraints(IloCplex cplex,
                                              List<Integer> customers,
                                              VehicleVars vars) throws IloException {
        int customerCount = customers.size();
        for (Integer customerId : customers) {
            IloLinearNumExpr lower = cplex.linearNumExpr();
            lower.addTerm(1.0, vars.orderByCustomer.get(customerId));
            lower.addTerm(-1.0, vars.yByCustomer.get(customerId));
            cplex.addGe(lower, 0.0);

            IloLinearNumExpr upper = cplex.linearNumExpr();
            upper.addTerm(1.0, vars.orderByCustomer.get(customerId));
            upper.addTerm(-customerCount, vars.yByCustomer.get(customerId));
            cplex.addLe(upper, 0.0);
        }

        for (Integer tailCustomerId : customers) {
            for (Integer headCustomerId : customers) {
                if (tailCustomerId.equals(headCustomerId)) {
                    continue;
                }
                IloNumVar arc = vars.xByTailHead.get(tailCustomerId).get(headCustomerId);
                if (arc == null) {
                    continue;
                }
                IloLinearNumExpr order = cplex.linearNumExpr();
                order.addTerm(1.0, vars.orderByCustomer.get(headCustomerId));
                order.addTerm(-1.0, vars.orderByCustomer.get(tailCustomerId));
                order.addTerm(-customerCount, arc);
                cplex.addGe(order, 1.0 - customerCount);
            }
        }
    }

    private void addCustomerCoverageConstraints(IloCplex cplex,
                                                List<Integer> customers,
                                                List<VehicleVars> vehicleVars) throws IloException {
        for (Integer customerId : customers) {
            IloLinearNumExpr coverage = cplex.linearNumExpr();
            for (VehicleVars vars : vehicleVars) {
                coverage.addTerm(1.0, vars.yByCustomer.get(customerId));
            }
            cplex.addEq(coverage, 1.0);
        }
    }

    private List<Route> extractRoutes(IloCplex cplex,
                                      DynamicInstance instance,
                                      ModelData modelData) throws IloException {
        List<Route> routes = new ArrayList<>();
        int routeIndex = 0;
        for (int vehicleIndex = 0; vehicleIndex < modelData.vehicles.size(); vehicleIndex++) {
            Vehicle vehicle = modelData.vehicles.get(vehicleIndex);
            VehicleVars vars = modelData.vehicleVars.get(vehicleIndex);
            if (cplex.getValue(vars.use) <= 0.5) {
                continue;
            }
            TIntArrayList path = extractRoutePath(cplex, instance, vehicle, vars);
            Route route = buildRoute(instance, vehicle, routeIndex, path, modelData.options);
            route.setValue(1.0);
            routes.add(route);
            routeIndex++;
        }
        return routes;
    }

    private TIntArrayList extractRoutePath(IloCplex cplex,
                                           DynamicInstance instance,
                                           Vehicle vehicle,
                                           VehicleVars vars) throws IloException {
        TIntArrayList path = new TIntArrayList();
        int returnDepotId = instance.getReturnDepot().getId();
        int currentNodeId = vehicle.startDepotId;
        path.add(currentNodeId);

        for (int step = 0; step <= instance.getRemainingCustomerIds().size() + 1; step++) {
            Integer nextNodeId = findSelectedHead(cplex, vars, currentNodeId);
            if (nextNodeId == null) {
                throw new IllegalStateException("Could not reconstruct MIP route from start depot " + vehicle.startDepotId);
            }
            path.add(nextNodeId);
            if (nextNodeId == returnDepotId) {
                return path;
            }
            currentNodeId = nextNodeId;
        }
        throw new IllegalStateException("Route reconstruction did not reach return depot from start depot "
                + vehicle.startDepotId);
    }

    private Integer findSelectedHead(IloCplex cplex, VehicleVars vars, int tailNodeId) throws IloException {
        Map<Integer, IloNumVar> outgoing = vars.xByTailHead.get(tailNodeId);
        if (outgoing == null) {
            return null;
        }
        for (Map.Entry<Integer, IloNumVar> entry : outgoing.entrySet()) {
            if (cplex.getValue(entry.getValue()) > 0.5) {
                return entry.getKey();
            }
        }
        return null;
    }

    private Route buildRoute(DynamicInstance instance,
                             Vehicle vehicle,
                             int routeIndex,
                             TIntArrayList path,
                             Options options) {
        int vectorSize = buildRouteVectorSize(instance);
        Route route = new Route(vehicle.startDepotId, routeIndex, vectorSize);
        route.setVisitedVertices(path);
        route.resetRouteState(vectorSize);

        double startTime = resolveStartTime(instance, vehicle.startDepotId, options);
        for (int i = 0; i < path.size(); i++) {
            int nodeId = path.get(i);
            route.getVisitedVector()[nodeId] = 1;
            if (i == 0) {
                route.getArrivalTimes().add(startTime);
                continue;
            }

            int previousNodeId = path.get(i - 1);
            double previousStartServiceTime = route.getArrivalTimes().get(i - 1);
            double departureTime = previousStartServiceTime + instance.getServiceTime(previousNodeId);
            double arrivalCandidate = departureTime + instance.getTravelTime(previousNodeId, nodeId);
            double startServiceTime = Math.max(arrivalCandidate, instance.getEarliestTime(nodeId));
            route.getArrivalTimes().add(startServiceTime);
            route.setCost(route.getCost() + instance.getDistance(previousNodeId, nodeId));
        }

        double load = 0.0;
        for (int nodeId : path.toArray()) {
            load += instance.getDemand(nodeId);
        }
        route.setLoad(load);
        return route;
    }

    private double resolveStartTime(DynamicInstance instance, int startDepotId, Options options) {
        if (options.getStartTimeMode() == StartTimeMode.START_DEPOT_EARLIEST) {
            return instance.getEarliestTime(startDepotId);
        }
        return instance.getRouteStartTime();
    }

    private double calculateBigM(DynamicInstance instance) {
        double maxLatest = instance.getReturnDepot().getLatestTime();
        for (Integer customerId : instance.getRemainingCustomerIds()) {
            maxLatest = Math.max(maxLatest, instance.getLatestTime(customerId));
        }
        for (Depot externalDepot : instance.getExternalDepots()) {
            maxLatest = Math.max(maxLatest, externalDepot.getLatestTime());
        }
        return Math.max(1_000_000.0, maxLatest + 10_000.0);
    }

    private int buildRouteVectorSize(DynamicInstance instance) {
        int maxNodeId = instance.getReturnDepot().getId();
        for (Depot externalDepot : instance.getExternalDepots()) {
            maxNodeId = Math.max(maxNodeId, externalDepot.getId());
        }
        return maxNodeId + 1;
    }

    private double sumRouteCosts(List<Route> routes) {
        double objective = 0.0;
        for (Route route : routes) {
            objective += route.getCost();
        }
        return objective;
    }

    private List<Route> copyRoutes(List<Route> routes) {
        List<Route> copies = new ArrayList<>();
        for (Route route : routes) {
            copies.add(route == null ? null : route.copy());
        }
        return copies;
    }

    public enum StartTimeMode {
        ROUTE_CLOCK,
        START_DEPOT_EARLIEST
    }

    public static class Options {

        private double timeLimitSeconds;
        private double mipGap;
        private boolean outputEnabled;
        private boolean validateSolution;
        private boolean useAvailableOriginVehicleLimit;
        private String exportModelPath;
        private StartTimeMode startTimeMode;

        public static Options defaults() {
            Options options = new Options();
            options.timeLimitSeconds = 60.0;
            options.mipGap = 0.0;
            options.outputEnabled = false;
            options.validateSolution = true;
            options.useAvailableOriginVehicleLimit = false;
            options.startTimeMode = StartTimeMode.ROUTE_CLOCK;
            return options;
        }

        public double getTimeLimitSeconds() {
            return timeLimitSeconds;
        }

        public Options setTimeLimitSeconds(double timeLimitSeconds) {
            this.timeLimitSeconds = timeLimitSeconds;
            return this;
        }

        public double getMipGap() {
            return mipGap;
        }

        public Options setMipGap(double mipGap) {
            this.mipGap = mipGap;
            return this;
        }

        public boolean isOutputEnabled() {
            return outputEnabled;
        }

        public Options setOutputEnabled(boolean outputEnabled) {
            this.outputEnabled = outputEnabled;
            return this;
        }

        public boolean isValidateSolution() {
            return validateSolution;
        }

        public Options setValidateSolution(boolean validateSolution) {
            this.validateSolution = validateSolution;
            return this;
        }

        public boolean isUseAvailableOriginVehicleLimit() {
            return useAvailableOriginVehicleLimit;
        }

        public Options setUseAvailableOriginVehicleLimit(boolean useAvailableOriginVehicleLimit) {
            this.useAvailableOriginVehicleLimit = useAvailableOriginVehicleLimit;
            return this;
        }

        public String getExportModelPath() {
            return exportModelPath;
        }

        public Options setExportModelPath(String exportModelPath) {
            this.exportModelPath = exportModelPath;
            return this;
        }

        public StartTimeMode getStartTimeMode() {
            return startTimeMode;
        }

        public Options setStartTimeMode(StartTimeMode startTimeMode) {
            if (startTimeMode == null) {
                throw new IllegalArgumentException("startTimeMode must not be null.");
            }
            this.startTimeMode = startTimeMode;
            return this;
        }
    }

    public static class Result {

        private final StageSolution stageSolution;
        private final double cplexObjectiveValue;
        private final double bestBound;
        private final boolean feasible;
        private final String solveStatus;
        private final long solveElapsedMillis;

        public Result(StageSolution stageSolution, double cplexObjectiveValue, double bestBound, boolean feasible) {
            this(stageSolution, cplexObjectiveValue, bestBound, feasible, "unknown", 0L);
        }

        public Result(StageSolution stageSolution,
                      double cplexObjectiveValue,
                      double bestBound,
                      boolean feasible,
                      String solveStatus,
                      long solveElapsedMillis) {
            if (stageSolution == null) {
                throw new IllegalArgumentException("stageSolution must not be null.");
            }
            this.stageSolution = stageSolution.copy();
            this.cplexObjectiveValue = cplexObjectiveValue;
            this.bestBound = bestBound;
            this.feasible = feasible;
            this.solveStatus = solveStatus == null ? "" : solveStatus;
            this.solveElapsedMillis = Math.max(0L, solveElapsedMillis);
        }

        public StageSolution getStageSolution() {
            return stageSolution.copy();
        }

        public double getCplexObjectiveValue() {
            return cplexObjectiveValue;
        }

        public double getBestBound() {
            return bestBound;
        }

        public boolean isFeasible() {
            return feasible;
        }

        public String getSolveStatus() {
            return solveStatus;
        }

        public long getSolveElapsedMillis() {
            return solveElapsedMillis;
        }
    }

    private static class ModelData {

        private final List<Integer> customers;
        private final List<Vehicle> vehicles;
        private final List<VehicleVars> vehicleVars;
        private final Options options;

        private ModelData(List<Integer> customers, List<Vehicle> vehicles, Options options) {
            this.customers = customers;
            this.vehicles = vehicles;
            this.options = options;
            this.vehicleVars = new ArrayList<>();
        }
    }

    private static class Vehicle {

        private final int startDepotId;
        private final int capacity;
        private final boolean mandatory;

        private Vehicle(int startDepotId, int capacity, boolean mandatory) {
            this.startDepotId = startDepotId;
            this.capacity = capacity;
            this.mandatory = mandatory;
        }
    }

    private static class VehicleVars {

        private final int startDepotId;
        private IloNumVar use;
        private final Map<Integer, IloNumVar> yByCustomer;
        private final Map<Integer, IloNumVar> timeByNode;
        private final Map<Integer, IloNumVar> orderByCustomer;
        private final Map<Integer, Map<Integer, IloNumVar>> xByTailHead;

        private VehicleVars(int startDepotId) {
            this.startDepotId = startDepotId;
            this.yByCustomer = new HashMap<>();
            this.timeByNode = new HashMap<>();
            this.orderByCustomer = new HashMap<>();
            this.xByTailHead = new HashMap<>();
        }

        private int returnDepotId(List<Integer> customers) {
            for (Map.Entry<Integer, Map<Integer, IloNumVar>> tailEntry : xByTailHead.entrySet()) {
                for (Integer headNodeId : tailEntry.getValue().keySet()) {
                    if (!customers.contains(headNodeId)) {
                        return headNodeId;
                    }
                }
            }
            throw new IllegalStateException("Return depot id not found for vehicle " + startDepotId);
        }
    }
}

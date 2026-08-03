package org.zzc.bpcsolver.exact;

import org.zzc.bpcsolver.algorithm.DVRPDynamicSolver;
import org.zzc.bpcsolver.core.branch.StageSolution;
import org.zzc.bpcsolver.core.graph.Route;
import org.zzc.bpcsolver.problem.config.ProblemConfig;
import org.zzc.bpcsolver.problem.config.ProblemConfigReader;
import org.zzc.bpcsolver.problem.entity.DynamicInstance;
import org.zzc.bpcsolver.problem.entity.ProblemData;
import org.zzc.bpcsolver.problem.loader.ProblemDataLoader;
import org.zzc.bpcsolver.problem.update.DynamicStateUpdater;
import org.zzc.bpcsolver.results.DVRPResultRecorder;

import java.util.ArrayList;
import java.util.List;

public class OriginalMipMain {

    public static void main(String[] args) {
        ProblemConfig config = new ProblemConfigReader().read("dvrp_td.yaml");
        applyOptionalInstanceOverride(config, args);
        ProblemData problemData = new ProblemDataLoader().load(config);

        double timeLimitSeconds = parseOptionalDoubleArg(args, 1, 60.0);
        OriginalMipDVRPSolver.StartTimeMode startTimeMode = parseOptionalStartTimeMode(args, 2);

        OriginalMipDVRPSolver solver = new OriginalMipDVRPSolver();
        OriginalMipDVRPSolver.Options options = OriginalMipDVRPSolver.Options.defaults()
                .setTimeLimitSeconds(timeLimitSeconds)
                .setStartTimeMode(startTimeMode);
        DynamicStateUpdater updater = new DynamicStateUpdater();
        DVRPResultRecorder resultRecorder = DVRPResultRecorder.fromDefaultSolverConfig()
                .forSolverFamily(DVRPResultRecorder.SolverFamily.CPLEX);

        List<Double> decisionTimes = DVRPDynamicSolver.buildEventDrivenDecisionTimes(problemData);
        DynamicInstance currentInstance = null;
        StageSolution currentSolution = new StageSolution();
        double totalActualDistance = 0.0;
        double horizon = problemData.getOriginDepot().getLatestTime();
        List<DVRPResultRecorder.OriginalMipStageRecord> stageRecords = new ArrayList<>();
        long runStartNanos = System.nanoTime();
        for (int i = 0; i < decisionTimes.size(); i++) {
            double currentTime = decisionTimes.get(i);
            if (currentTime >= horizon - 1e-9) {
                break;
            }
            if (currentInstance == null) {
                currentInstance = updater.createInitialInstance(problemData, currentTime);
            } else {
                currentInstance = updater.update(currentInstance, currentSolution, currentTime);
            }
            OriginalMipDVRPSolver.Result exactResult = currentInstance.getRemainingCustomerIds().isEmpty()
                    ? new OriginalMipDVRPSolver.Result(buildEmptyIntegerSolution(), 0.0, 0.0, true, "success", 0L)
                    : solver.solve(currentInstance, options);
            currentSolution = exactResult.getStageSolution();
            double nextTime = DVRPDynamicSolver.resolveNextDecisionTime(decisionTimes, i, horizon);
            double actualDistance = updater.calculateActualDistance(currentInstance, currentSolution, nextTime);
            totalActualDistance += actualDistance;
            stageRecords.add(new DVRPResultRecorder.OriginalMipStageRecord(
                    i,
                    currentInstance.getCurrentTime(),
                    currentSolution.getObjectiveValue(),
                    actualDistance,
                    exactResult.getSolveElapsedMillis(),
                    exactResult.getSolveStatus()
            ));
            printResult(currentInstance, exactResult, actualDistance);
            System.out.println();
        }
        long runElapsedMillis = elapsedMillisSince(runStartNanos);
        System.out.println("Total actual distance: " + totalActualDistance);
        resultRecorder.recordOriginalMipRun(
                config.getInstanceBaseName(),
                problemData.getCustomers().size(),
                joinDecisionTimes(decisionTimes, problemData.getOriginDepot().getLatestTime()),
                stageRecords,
                runElapsedMillis,
                buildRunNotes(timeLimitSeconds)
        );
    }

    private static long elapsedMillisSince(long startNanos) {
        return Math.max(0L, Math.round((System.nanoTime() - startNanos) / 1_000_000.0));
    }

    private static String buildRunNotes(double timeLimitSeconds) {
        return "timeLimit=" + timeLimitSeconds;
    }

    private static String joinDecisionTimes(List<Double> decisionTimes, double horizon) {
        List<String> values = new ArrayList<>();
        for (Double decisionTime : decisionTimes) {
            if (decisionTime == null || decisionTime >= horizon - 1e-9) {
                continue;
            }
            values.add(String.valueOf(decisionTime));
        }
        return String.join(";", values);
    }

    private static StageSolution buildEmptyIntegerSolution() {
        StageSolution solution = new StageSolution();
        solution.setIntegerSolution(true);
        return solution;
    }

    private static void printResult(DynamicInstance instance,
                                    OriginalMipDVRPSolver.Result result,
                                    double actualDistanceToNextStage) {
        StageSolution solution = result.getStageSolution();
        System.out.println("Current time: " + instance.getCurrentTime());
        System.out.println("Remaining customers: " + instance.getRemainingCustomerIds().size());
        System.out.println("External depots: " + instance.getExternalDepots().size());
        System.out.println("Selected routes: " + solution.getSelectedRoutes().size());
        System.out.println("Objective value: " + solution.getObjectiveValue());
        System.out.println("CPLEX objective value: " + result.getCplexObjectiveValue());
        System.out.println("CPLEX best bound: " + result.getBestBound());
        System.out.println("CPLEX feasible: " + result.isFeasible());
        System.out.println("Actual distance to next stage: " + actualDistanceToNextStage);
        System.out.println("Integer solution: " + solution.isIntegerSolution());
        for (Route route : solution.getSelectedRoutes()) {
            System.out.println(route.getVisitedVertices() + " cost=" + route.getCost());
        }
    }

    private static void applyOptionalInstanceOverride(ProblemConfig config, String[] args) {
        int index = 0;
        if (args == null || index >= args.length || args[index] == null || args[index].trim().isEmpty()) {
            return;
        }
        String instanceName = args[index].trim();
        String fileName = instanceName.endsWith(".xlsx") ? instanceName : instanceName + ".xlsx";
        config.setInstanceFileName(fileName);
        config.setInstanceBaseName(fileName.substring(0, fileName.length() - ".xlsx".length()));
    }

    private static double parseOptionalDoubleArg(String[] args, int index, double defaultValue) {
        if (args == null || index >= args.length || args[index] == null || args[index].trim().isEmpty()) {
            return defaultValue;
        }
        return Double.parseDouble(args[index].trim());
    }

    private static OriginalMipDVRPSolver.StartTimeMode parseOptionalStartTimeMode(String[] args, int index) {
        if (args == null || index >= args.length || args[index] == null || args[index].trim().isEmpty()) {
            return OriginalMipDVRPSolver.StartTimeMode.ROUTE_CLOCK;
        }
        String mode = args[index].trim();
        if ("depot-earliest".equalsIgnoreCase(mode)
                || "start-depot-earliest".equalsIgnoreCase(mode)
                || "mathematical".equalsIgnoreCase(mode)) {
            return OriginalMipDVRPSolver.StartTimeMode.START_DEPOT_EARLIEST;
        }
        if ("route-clock".equalsIgnoreCase(mode)
                || "default".equalsIgnoreCase(mode)) {
            return OriginalMipDVRPSolver.StartTimeMode.ROUTE_CLOCK;
        }
        throw new IllegalArgumentException("Unknown exact MIP start-time mode: " + mode);
    }
}

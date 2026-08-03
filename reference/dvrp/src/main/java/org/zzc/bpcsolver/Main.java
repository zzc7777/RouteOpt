package org.zzc.bpcsolver;

import org.zzc.bpcsolver.algorithm.DVRPDynamicSolver;
import org.zzc.bpcsolver.algorithm.BPCSolver;
import org.zzc.bpcsolver.core.branch.StageSolution;
import org.zzc.bpcsolver.core.graph.Route;
import org.zzc.bpcsolver.problem.entity.DynamicInstance;
import org.zzc.bpcsolver.problem.config.ProblemConfig;
import org.zzc.bpcsolver.problem.config.ProblemConfigReader;
import org.zzc.bpcsolver.problem.entity.ProblemData;
import org.zzc.bpcsolver.problem.loader.ProblemDataLoader;
import org.zzc.bpcsolver.results.DVRPResultRecorder;

import java.util.List;

public class Main {

    public static void main(String[] args) {
        ProblemConfigReader configReader = new ProblemConfigReader();
        ProblemConfig config = configReader.read("dvrp_td.yaml");
        applyOptionalInstanceOverride(config, args);
        ProblemDataLoader problemDataLoader = new ProblemDataLoader();
        ProblemData problemData = problemDataLoader.load(config);
        int maxAddedColumnsPerIteration = parseOptionalIntArg(
                args,
                0,
                BPCSolver.DEFAULT_MAX_ADDED_COLUMNS_PER_ITERATION
        );
        int maxIterations = parseOptionalIntArg(args, 1, BPCSolver.DEFAULT_MAX_ITERATIONS);
        String heuristicPricingRule = parseOptionalHeuristicPricingRule(args, 3);
        DVRPResultRecorder resultRecorder = DVRPResultRecorder.fromDefaultSolverConfig();

        if (problemData != null) {
            problemData.validate();
            DVRPDynamicSolver dynamicSolver = new DVRPDynamicSolver();
            if (heuristicPricingRule != null) {
                BPCSolver bpcSolver = new BPCSolver();
                bpcSolver.setHeuristicPricingRule(heuristicPricingRule);
                dynamicSolver = new DVRPDynamicSolver(new org.zzc.bpcsolver.problem.update.DynamicStateUpdater(), bpcSolver);
            }

            long solveStartNanos = System.nanoTime();
            List<DVRPDynamicSolver.Result> results = dynamicSolver.solveStages(
                    problemData,
                    maxAddedColumnsPerIteration,
                    maxIterations
            );
            long solveElapsedMillis = elapsedMillisSince(solveStartNanos);
            double totalActualDistance = 0.0;
            for (DVRPDynamicSolver.Result result : results) {
                printStageResult(result);
                totalActualDistance += result.getActualDistanceToNextStage();
                System.out.println();
            }
            System.out.println("Total actual distance: " + totalActualDistance);
            resultRecorder.recordJavaRun(
                    "java-bpc",
                    config.getInstanceBaseName(),
                    results,
                    solveElapsedMillis,
                    ""
            );
        }
    }

    private static long elapsedMillisSince(long startNanos) {
        return Math.max(0L, Math.round((System.nanoTime() - startNanos) / 1_000_000.0));
    }

    private static int parseOptionalIntArg(String[] args, int index, int defaultValue) {
        if (args == null || index >= args.length) {
            return defaultValue;
        }
        return Integer.parseInt(args[index]);
    }

    private static void applyOptionalInstanceOverride(ProblemConfig config, String[] args) {
        int index = 2;
        if (args == null || index >= args.length || args[index] == null || args[index].trim().isEmpty()) {
            return;
        }
        String instanceName = args[index].trim();
        String fileName = instanceName.endsWith(".xlsx") ? instanceName : instanceName + ".xlsx";
        config.setInstanceFileName(fileName);
        config.setInstanceBaseName(fileName.substring(0, fileName.length() - ".xlsx".length()));
    }

    private static String parseOptionalHeuristicPricingRule(String[] args, int index) {
        if (args == null || index >= args.length || args[index] == null) {
            return null;
        }
        String option = args[index].trim();
        if (option.isEmpty()
                || "none".equalsIgnoreCase(option)
                || "false".equalsIgnoreCase(option)
                || "true".equalsIgnoreCase(option)
                || "default".equalsIgnoreCase(option)) {
            return null;
        }
        return option;
    }

    private static void printStageResult(DVRPDynamicSolver.Result result) {
        DynamicInstance currentInstance = result.getStageInstance();
        StageSolution currentSolution = result.getStageSolution();
        System.out.println("Current time: " + currentInstance.getCurrentTime());
        System.out.println("Remaining customers: " + currentInstance.getRemainingCustomerIds().size());
        System.out.println("Served customers: " + currentInstance.getServedCustomerIds().size()
                + " " + currentInstance.getServedCustomerIds());
        System.out.println("External depots: " + currentInstance.getExternalDepots().size());
        currentInstance.getExternalDepots().forEach(depot -> System.out.println(
                "External depot " + depot.getId()
                        + " x=" + depot.getX()
                        + " y=" + depot.getY()
                        + " earliest=" + depot.getEarliestTime()
                        + " remainingCapacity=" + depot.getRemainingCapacity()
        ));
        System.out.println("Selected routes: " + currentSolution.getSelectedRoutes().size());
        System.out.println("Objective value: " + currentSolution.getObjectiveValue());
        System.out.println("Actual distance to next stage: " + result.getActualDistanceToNextStage());
        System.out.println("Integer solution: " + currentSolution.isIntegerSolution());
        for (Route route : currentSolution.getSelectedRoutes()) {
            System.out.println(route.getVisitedVertices() + " cost=" + route.getCost());
        }
    }
}

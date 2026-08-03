package org.zzc.bpcsolver.algorithm;

import lombok.Getter;
import org.zzc.bpcsolver.core.branch.StageSolution;
import org.zzc.bpcsolver.problem.entity.DynamicInstance;
import org.zzc.bpcsolver.problem.entity.ProblemData;
import org.zzc.bpcsolver.problem.update.DynamicStateUpdater;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DVRPDynamicSolver {

    private static final double EPSILON = 1e-9;

    private final DynamicStateUpdater dynamicStateUpdater;
    private final BPCSolver bpcSolver;

    public DVRPDynamicSolver() {
        this(new DynamicStateUpdater(), new BPCSolver());
    }

    public DVRPDynamicSolver(DynamicStateUpdater dynamicStateUpdater, BPCSolver bpcSolver) {
        if (dynamicStateUpdater == null) {
            throw new IllegalArgumentException("dynamicStateUpdater must not be null.");
        }
        if (bpcSolver == null) {
            throw new IllegalArgumentException("bpcSolver must not be null.");
        }
        this.dynamicStateUpdater = dynamicStateUpdater;
        this.bpcSolver = bpcSolver;
    }

    public Result solve(ProblemData problemData, double currentTime) {
        return solve(
                problemData,
                currentTime,
                BPCSolver.DEFAULT_MAX_ADDED_COLUMNS_PER_ITERATION,
                BPCSolver.DEFAULT_MAX_ITERATIONS
        );
    }

    public Result solve(ProblemData problemData, double currentTime,
                        int maxAddedColumnsPerIteration, int maxIterations) {
        if (problemData == null) {
            throw new IllegalArgumentException("problemData must not be null.");
        }
        DynamicInstance initialInstance = this.dynamicStateUpdater.createInitialInstance(problemData, currentTime);
        StageSolution stageSolution = this.bpcSolver.solve(
                initialInstance,
                maxAddedColumnsPerIteration,
                maxIterations
        );
        return new Result(initialInstance, stageSolution);
    }

    public List<Result> solveStages(ProblemData problemData,
                                    int maxAddedColumnsPerIteration,
                                    int maxIterations) {
        if (problemData == null) {
            throw new IllegalArgumentException("problemData must not be null.");
        }
        problemData.validate();

        List<Double> decisionTimes = buildEventDrivenDecisionTimes(problemData);
        List<Result> results = new ArrayList<>();
        DynamicInstance currentInstance = null;
        StageSolution currentSolution = new StageSolution();
        double horizon = problemData.getOriginDepot().getLatestTime();

        for (int timeIndex = 0; timeIndex < decisionTimes.size(); timeIndex++) {
            double currentTime = decisionTimes.get(timeIndex);
            if (currentTime >= horizon - EPSILON) {
                break;
            }
            if (currentInstance == null) {
                currentInstance = this.dynamicStateUpdater.createInitialInstance(problemData, currentTime);
            } else {
                currentInstance = this.dynamicStateUpdater.update(currentInstance, currentSolution, currentTime);
            }

            try {
                currentSolution = solveStageOrEmpty(currentInstance, maxAddedColumnsPerIteration, maxIterations);
            } catch (RuntimeException exception) {
                throw new IllegalStateException("Failed to solve dynamic stage at time " + currentTime + ".", exception);
            }
            double nextDecisionTime = resolveNextDecisionTime(decisionTimes, timeIndex, horizon);
            double actualDistanceToNextStage = this.dynamicStateUpdater.calculateActualDistance(
                    currentInstance,
                    currentSolution,
                    nextDecisionTime
            );
            results.add(new Result(currentInstance, currentSolution, actualDistanceToNextStage));
        }
        return results;
    }

    public Result solveNextStage(DynamicInstance previousInstance, StageSolution previousSolution, double currentTime,
                                 int maxAddedColumnsPerIteration, int maxIterations) {
        if (previousInstance == null) {
            throw new IllegalArgumentException("previousInstance must not be null.");
        }
        if (previousSolution == null) {
            throw new IllegalArgumentException("previousSolution must not be null.");
        }
        DynamicInstance nextInstance = this.dynamicStateUpdater.update(previousInstance, previousSolution, currentTime);
        StageSolution nextStageSolution = this.bpcSolver.solve(
                nextInstance,
                maxAddedColumnsPerIteration,
                maxIterations
        );
        return new Result(nextInstance, nextStageSolution);
    }

    private StageSolution solveStageOrEmpty(DynamicInstance instance, int maxAddedColumnsPerIteration, int maxIterations) {
        if (instance.getRemainingCustomerIds().isEmpty()) {
            StageSolution emptySolution = new StageSolution();
            emptySolution.setIntegerSolution(true);
            return emptySolution;
        }
        return this.bpcSolver.solve(instance, maxAddedColumnsPerIteration, maxIterations);
    }

    public static List<Double> buildEventDrivenDecisionTimes(ProblemData problemData) {
        if (problemData == null) {
            throw new IllegalArgumentException("problemData must not be null.");
        }
        List<Double> decisionTimes = new ArrayList<>();
        addDistinctTime(decisionTimes, problemData.getOriginDepot().getEarliestTime());
        for (org.zzc.bpcsolver.problem.entity.Customer customer : problemData.getCustomers()) {
            addDistinctTime(decisionTimes, customer.getCallInTime());
        }
        Collections.sort(decisionTimes);
        return decisionTimes;
    }

    public static double resolveNextDecisionTime(List<Double> decisionTimes, int currentIndex, double horizon) {
        if (decisionTimes == null) {
            throw new IllegalArgumentException("decisionTimes must not be null.");
        }
        if (currentIndex + 1 < decisionTimes.size()) {
            return decisionTimes.get(currentIndex + 1);
        }
        return horizon;
    }

    private static void addDistinctTime(List<Double> decisionTimes, double time) {
        for (double existingTime : decisionTimes) {
            if (Math.abs(existingTime - time) <= EPSILON) {
                return;
            }
        }
        decisionTimes.add(time);
    }

    @Getter
    public static class Result {

        private final DynamicInstance stageInstance;
        private final StageSolution stageSolution;
        private final double actualDistanceToNextStage;

        public Result(DynamicInstance stageInstance, StageSolution stageSolution) {
            this(stageInstance, stageSolution, 0.0);
        }

        public Result(DynamicInstance stageInstance, StageSolution stageSolution, double actualDistanceToNextStage) {
            if (stageInstance == null) {
                throw new IllegalArgumentException("stageInstance must not be null.");
            }
            if (stageSolution == null) {
                throw new IllegalArgumentException("stageSolution must not be null.");
            }
            this.stageInstance = stageInstance.copy();
            this.stageSolution = stageSolution.copy();
            this.actualDistanceToNextStage = actualDistanceToNextStage;
        }
    }
}

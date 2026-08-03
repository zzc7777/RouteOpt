package org.zzc.bpcsolver.algorithm.acceleration;

import org.zzc.bpcsolver.core.branch.Solution;
import org.zzc.bpcsolver.core.template.GraphTemplate;
import org.zzc.bpcsolver.core.template.PathTemplate;
import org.zzc.bpcsolver.problem.template.DynamicInstanceTemplate;

import java.util.List;

public interface PrimalHeuristic<T extends DynamicInstanceTemplate, S extends PathTemplate, G extends GraphTemplate> {

    Solution<S> obtainInitialSolution(T instance, List<G> graphs);

    Solution<S> obtainFeasibleSolution(T instance, List<G> graphs, List<List<S>> schedules, List<double[]> ysk);

    String getStrategyName();
}

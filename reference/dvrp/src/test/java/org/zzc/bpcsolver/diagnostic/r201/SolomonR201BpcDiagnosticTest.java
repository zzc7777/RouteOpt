package org.zzc.bpcsolver.diagnostic.r201;

import org.junit.jupiter.api.Test;
import org.zzc.bpcsolver.algorithm.acceleration.DVRPColumnPoolPrimalHeuristic;
import org.zzc.bpcsolver.core.branch.Solution;
import org.zzc.bpcsolver.core.branch.StageSolution;
import org.zzc.bpcsolver.core.graph.Graph;
import org.zzc.bpcsolver.core.graph.Route;
import org.zzc.bpcsolver.core.graph.RouteFeasibilityValidator;
import org.zzc.bpcsolver.model.DVRPMasterProblem;
import org.zzc.bpcsolver.problem.entity.DynamicInstance;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SolomonR201BpcDiagnosticTest {

    @Test
    void integerMasterShouldAcceptPublicR201RouteColumnsAsBetterIncumbent() {
        DynamicInstance instance = R201DiagnosticSupport.loadR201Instance();
        List<Route> publicRoutes = R201DiagnosticSupport.buildPublicWitnessRoutes(instance);

        try (DVRPMasterProblem masterProblem = new DVRPMasterProblem()) {
            masterProblem.initialize(instance, publicRoutes, true);
            masterProblem.build();

            assertTrue(masterProblem.solve());
            StageSolution solution = new StageSolution();
            masterProblem.updateSolution(solution);

            assertTrue(new RouteFeasibilityValidator()
                    .findStageSolutionViolations(instance, solution)
                    .isEmpty());
            assertEquals(R201DiagnosticSupport.PUBLIC_WITNESS_JAVA_FLOOR_OBJECTIVE,
                    solution.getObjectiveValue(), 1e-9);
            assertEquals(8, solution.getSelectedRoutes().size());
        }
    }

    @Test
    void columnPoolPrimalHeuristicShouldFindPublicR201RouteColumnsIfTheyExistInPool() {
        DynamicInstance instance = R201DiagnosticSupport.loadR201Instance();
        List<Route> publicRoutes = R201DiagnosticSupport.buildPublicWitnessRoutes(instance);
        Graph originGraph = new Graph();
        originGraph.buildFromInstance(instance, instance.getOriginDepot().getId());
        DVRPColumnPoolPrimalHeuristic heuristic = new DVRPColumnPoolPrimalHeuristic();

        Solution<Route> solution = heuristic.obtainFeasibleSolution(
                instance,
                Arrays.asList(originGraph),
                Arrays.asList(publicRoutes),
                Arrays.asList(new double[publicRoutes.size()])
        );

        assertEquals(R201DiagnosticSupport.PUBLIC_WITNESS_JAVA_FLOOR_OBJECTIVE,
                solution.getObjValue(), 1e-9);
        assertEquals(8, solution.getBestSchedules().size());
    }

    @Test
    void witnessInspectorShouldReportMissingPublicR201Columns() {
        DynamicInstance instance = R201DiagnosticSupport.loadR201Instance();
        List<Route> publicRoutes = R201DiagnosticSupport.buildPublicWitnessRoutes(instance);
        List<Route> partialPool = publicRoutes.subList(0, 3);

        R201DiagnosticSupport.RoutePoolCoverage result =
                R201DiagnosticSupport.inspectRoutePool(partialPool, publicRoutes);

        assertEquals(8, result.totalRoutes);
        assertEquals(3, result.matchedRoutes);
        assertEquals(5, result.missingRoutes.size());
    }
}

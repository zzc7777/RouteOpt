package org.zzc.bpcsolver.diagnostic.r201;

import org.junit.jupiter.api.Test;
import org.zzc.bpcsolver.core.graph.Route;
import org.zzc.bpcsolver.problem.entity.DynamicInstance;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SolomonR201ReferenceRouteTest {

    @Test
    void shouldShowDietmarWoR201RoutesAreFeasibleButUseDifferentDistanceConvention() {
        DynamicInstance instance = R201DiagnosticSupport.loadR201Instance();
        List<Route> routes = R201DiagnosticSupport.buildPublicWitnessRoutes(instance);
        List<String> violations = R201DiagnosticSupport.stageSolutionViolations(instance, routes);

        assertTrue(violations.isEmpty(), violations::toString);
        assertEquals(8, routes.size());
        assertEquals(R201DiagnosticSupport.PUBLIC_WITNESS_JAVA_FLOOR_OBJECTIVE,
                R201DiagnosticSupport.sumCosts(routes), 1e-9);
        assertNotEquals(R201DiagnosticSupport.SOLOMON_BPC_BENCHMARK_OBJECTIVE,
                R201DiagnosticSupport.sumCosts(routes), 1e-9,
                "The public route is feasible, but it does not reproduce the workbook distance "
                        + "under the current per-arc FLOOR convention.");
    }
}

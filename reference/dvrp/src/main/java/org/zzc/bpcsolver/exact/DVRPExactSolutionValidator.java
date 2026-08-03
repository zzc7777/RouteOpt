package org.zzc.bpcsolver.exact;

import org.zzc.bpcsolver.core.branch.StageSolution;
import org.zzc.bpcsolver.core.graph.RouteFeasibilityValidator;
import org.zzc.bpcsolver.problem.entity.DynamicInstance;

import java.util.List;

public class DVRPExactSolutionValidator {

    private final RouteFeasibilityValidator routeFeasibilityValidator;

    public DVRPExactSolutionValidator() {
        this(new RouteFeasibilityValidator());
    }

    public DVRPExactSolutionValidator(RouteFeasibilityValidator routeFeasibilityValidator) {
        if (routeFeasibilityValidator == null) {
            throw new IllegalArgumentException("routeFeasibilityValidator must not be null.");
        }
        this.routeFeasibilityValidator = routeFeasibilityValidator;
    }

    public void assertValid(DynamicInstance instance, StageSolution solution) {
        List<String> violations = findViolations(instance, solution);
        if (!violations.isEmpty()) {
            throw new IllegalStateException("Invalid DVRP solution: " + String.join("; ", violations));
        }
    }

    public List<String> findViolations(DynamicInstance instance, StageSolution solution) {
        return routeFeasibilityValidator.findStageSolutionViolations(instance, solution);
    }
}

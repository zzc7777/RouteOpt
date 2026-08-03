package org.zzc.bpcsolver.algorithm.branching;

import org.zzc.bpcsolver.core.branch.Constraint;
import org.zzc.bpcsolver.core.graph.Route;

import java.util.List;

public final class BranchingRouteFilter {

    private BranchingRouteFilter() {
    }

    public static boolean isArcAllowed(int machineIndex, int tailNodeId, int headNodeId,
                                       List<Constraint> branchConstraints) {
        // luckydragon-style branching keeps route generation feasible and enforces
        // branch decisions through master rows plus their pricing duals.
        return true;
    }

    public static boolean isRouteAllowed(Route route, int machineIndex, List<Constraint> branchConstraints) {
        return true;
    }
}

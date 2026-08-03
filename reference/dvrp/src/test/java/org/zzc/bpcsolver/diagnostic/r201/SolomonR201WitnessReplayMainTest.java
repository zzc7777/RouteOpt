package org.zzc.bpcsolver.diagnostic.r201;

import gnu.trove.list.array.TIntArrayList;
import org.junit.jupiter.api.Test;
import org.zzc.bpcsolver.algorithm.branching.VehicleCountThenArcBranching;
import org.zzc.bpcsolver.core.branch.Variable;
import org.zzc.bpcsolver.core.graph.Route;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SolomonR201WitnessReplayMainTest {

    @Test
    void witnessReplayShouldChooseRightVehicleCountBranchForEightRouteWitness() {
        List<Route> witnessRoutes = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            Route route = new Route(0, i, 101);
            route.setVisitedVertices(new TIntArrayList(new int[]{0, i + 1, 101}));
            witnessRoutes.add(route);
        }
        Variable vehicleCount = new Variable(
                VehicleCountThenArcBranching.VEHICLE_COUNT_TYPE,
                new int[0],
                7.935385
        );

        int usage = SolomonR201WitnessReplayMain.witnessUsage(witnessRoutes, vehicleCount);
        int chosenFlag = SolomonR201WitnessReplayMain.chooseWitnessBranch(vehicleCount, usage);

        assertEquals(8, usage);
        assertEquals(1, chosenFlag);
    }
}

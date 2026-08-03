package org.zzc.bpcsolver.diagnostic.r201;

import gnu.trove.list.array.TIntArrayList;
import org.zzc.bpcsolver.core.branch.StageSolution;
import org.zzc.bpcsolver.core.graph.Route;
import org.zzc.bpcsolver.core.graph.RouteFeasibilityValidator;
import org.zzc.bpcsolver.problem.config.ProblemConfig;
import org.zzc.bpcsolver.problem.config.ProblemConfigReader;
import org.zzc.bpcsolver.problem.entity.Depot;
import org.zzc.bpcsolver.problem.entity.DynamicInstance;
import org.zzc.bpcsolver.problem.entity.ProblemData;
import org.zzc.bpcsolver.problem.loader.ProblemDataLoader;
import org.zzc.bpcsolver.problem.metric.DecimalRoundingMode;
import org.zzc.bpcsolver.problem.update.DynamicStateUpdater;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class R201DiagnosticSupport {

    static final double SOLOMON_BPC_BENCHMARK_OBJECTIVE = 1143.2;
    static final double PUBLIC_WITNESS_JAVA_FLOOR_OBJECTIVE = 1143.6;
    static final Path DEFAULT_WITNESS_PATH = Paths.get("src/main/resources/r201.txt");

    private static final int[][] PUBLIC_WITNESS_ROUTES = {
            {52, 69, 30, 90, 10, 20, 66, 32, 70, 1},
            {33, 65, 71, 9, 51, 81, 79, 78, 34, 35, 68, 77},
            {28, 12, 29, 76, 50, 3, 54, 26},
            {95, 59, 92, 98, 14, 38, 44, 16, 61, 86, 85, 99, 94, 6, 96, 97, 13, 58},
            {5, 83, 45, 82, 47, 36, 49, 46, 48},
            {42, 15, 2, 73, 21, 40, 53, 87, 57, 43, 37, 100, 91, 93},
            {27, 31, 63, 64, 11, 19, 62, 88, 7, 18, 8, 84, 17, 60, 89},
            {72, 39, 67, 23, 75, 41, 22, 56, 74, 4, 55, 25, 24, 80}
    };

    private R201DiagnosticSupport() {
    }

    static DynamicInstance loadR201Instance() {
        ProblemConfig config = new ProblemConfigReader().read("dvrp_td.yaml");
        config.setResourcePath("Solomon100_Instance/");
        config.setInstanceFileName("r201.xlsx");
        config.setInstanceBaseName("r201");
        config.setNumCustomers(100);
        config.setVehicleCapacity(1000);
        config.setInitialVehicleCount(25);
        config.setDistanceRoundingMode(DecimalRoundingMode.FLOOR);
        config.setTravelTimeRoundingMode(DecimalRoundingMode.FLOOR);
        ProblemData problemData = new ProblemDataLoader().load(config);
        return new DynamicStateUpdater().createInitialInstance(
                problemData,
                problemData.getOriginDepot().getEarliestTime()
        );
    }

    static List<Route> buildPublicWitnessRoutes(DynamicInstance instance) {
        List<Route> routes = new ArrayList<>();
        for (int routeIndex = 0; routeIndex < PUBLIC_WITNESS_ROUTES.length; routeIndex++) {
            TIntArrayList visited = new TIntArrayList(PUBLIC_WITNESS_ROUTES[routeIndex].length + 2);
            visited.add(instance.getOriginDepot().getId());
            visited.add(PUBLIC_WITNESS_ROUTES[routeIndex]);
            visited.add(instance.getReturnDepot().getId());

            Route route = new Route(instance.getOriginDepot().getId(), routeIndex, routeVectorSize(instance));
            route.setVisitedVertices(visited);
            route.update(instance);
            routes.add(route);
        }
        return routes;
    }

    static List<Route> loadWitnessRoutes(DynamicInstance instance, Path witnessPath) throws IOException {
        List<Route> routes = new ArrayList<>();
        List<String> lines = Files.readAllLines(witnessPath, StandardCharsets.UTF_8);
        int routeIndex = 0;
        for (String line : lines) {
            String trimmed = line == null ? "" : line.trim();
            if (!trimmed.toLowerCase(Locale.ROOT).startsWith("route")) {
                continue;
            }
            int colon = trimmed.indexOf(':');
            if (colon < 0) {
                continue;
            }
            String routePart = trimmed.substring(colon + 1).trim();
            if (routePart.isEmpty()) {
                continue;
            }

            TIntArrayList visited = new TIntArrayList();
            visited.add(instance.getOriginDepot().getId());
            for (String token : routePart.split("\\s+")) {
                if (!token.trim().isEmpty()) {
                    visited.add(Integer.parseInt(token.trim()));
                }
            }
            visited.add(instance.getReturnDepot().getId());

            Route route = new Route(instance.getOriginDepot().getId(), routeIndex, routeVectorSize(instance));
            route.setVisitedVertices(visited);
            route.update(instance);
            routes.add(route);
            routeIndex++;
        }
        if (routes.isEmpty()) {
            throw new IllegalArgumentException("No witness routes parsed from " + witnessPath);
        }
        return routes;
    }

    static boolean isStageSolutionFeasible(DynamicInstance instance, List<Route> routes) {
        return stageSolutionViolations(instance, routes).isEmpty();
    }

    static List<String> stageSolutionViolations(DynamicInstance instance, List<Route> routes) {
        StageSolution solution = new StageSolution();
        solution.setSelectedRoutes(copyRoutes(routes));
        solution.setAllRoutes(copyRoutes(routes));
        solution.setObjectiveValue(sumCosts(routes));
        solution.setIntegerSolution(true);
        return new RouteFeasibilityValidator().findStageSolutionViolations(instance, solution);
    }

    static double sumCosts(List<Route> routes) {
        double sum = 0.0;
        if (routes == null) {
            return sum;
        }
        for (Route route : routes) {
            sum += route == null ? 0.0 : route.getCost();
        }
        return sum;
    }

    static List<Route> copyRoutes(List<Route> routes) {
        List<Route> copies = new ArrayList<>();
        if (routes == null) {
            return copies;
        }
        for (Route route : routes) {
            copies.add(route == null ? null : route.copy());
        }
        return copies;
    }

    static RoutePoolCoverage inspectRoutePool(List<Route> routePool, List<Route> witnessRoutes) {
        List<Route> missingRoutes = new ArrayList<>();
        List<Integer> missingRouteIndexes = new ArrayList<>();
        for (int i = 0; i < witnessRoutes.size(); i++) {
            Route witnessRoute = witnessRoutes.get(i);
            if (!containsRoute(routePool, witnessRoute)) {
                missingRoutes.add(witnessRoute.copy());
                missingRouteIndexes.add(i + 1);
            }
        }
        return new RoutePoolCoverage(witnessRoutes.size(), witnessRoutes.size() - missingRoutes.size(),
                missingRoutes, missingRouteIndexes);
    }

    static boolean containsRoute(List<Route> routePool, Route witnessRoute) {
        if (routePool == null || witnessRoute == null || witnessRoute.getVisitedVertices() == null) {
            return false;
        }
        for (Route route : routePool) {
            if (route != null
                    && route.getVisitedVertices() != null
                    && route.getVisitedVertices().equals(witnessRoute.getVisitedVertices())) {
                return true;
            }
        }
        return false;
    }

    static String format(double value) {
        if (Double.isInfinite(value)) {
            return value > 0 ? "Infinity" : "-Infinity";
        }
        if (Double.isNaN(value)) {
            return "NaN";
        }
        return String.format(Locale.ROOT, "%.6f", value);
    }

    static String csv(String value) {
        String text = value == null ? "" : value;
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    private static int routeVectorSize(DynamicInstance instance) {
        int maxNodeId = instance.getReturnDepot().getId();
        for (Depot externalDepot : instance.getExternalDepots()) {
            maxNodeId = Math.max(maxNodeId, externalDepot.getId());
        }
        return maxNodeId + 1;
    }

    static final class RoutePoolCoverage {
        final int totalRoutes;
        final int matchedRoutes;
        final List<Route> missingRoutes;
        final List<Integer> missingRouteIndexes;

        private RoutePoolCoverage(int totalRoutes,
                                  int matchedRoutes,
                                  List<Route> missingRoutes,
                                  List<Integer> missingRouteIndexes) {
            this.totalRoutes = totalRoutes;
            this.matchedRoutes = matchedRoutes;
            this.missingRoutes = new ArrayList<>(missingRoutes);
            this.missingRouteIndexes = new ArrayList<>(missingRouteIndexes);
        }
    }
}

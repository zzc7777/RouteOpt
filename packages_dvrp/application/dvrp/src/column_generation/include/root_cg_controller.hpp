/* DVRP adaptation of RouteOpt (Copyright (c) 2025 Zhengzhong (Ricky) You).
 * License: GPL-3.0. Root LP orchestration, not a branch-and-price tree. */
#ifndef ROUTEOPT_DVRP_ROOT_CG_CONTROLLER_HPP
#define ROUTEOPT_DVRP_ROOT_CG_CONTROLLER_HPP

#include "pricing_controller.hpp"

namespace RouteOpt::Application::DVRP {
    enum class RootCgStatus {
        LpConverged, LpInfeasible, TimeLimit, ResourceLimit,
        IterationLimit, NumericalUnresolved, Error
    };
    struct RootCgOptions {
        explicit RootCgOptions(PricingBackend backend) { pricing.backend = backend; }
        double timeLimitSeconds{60};
        std::size_t maxIterations{10000}; // Counts LP solves, including Phase-I.
        RmpOptions rmp;
        PricingOptions pricing;
        bool addInitialRoutes{true};
        double absoluteGapTolerance{1e-6}, relativeGapTolerance{1e-8};
    };
    struct RootCgBound {
        std::size_t stageId{}, modelRevision{}, rowCount{};
        RmpPhase phase{RmpPhase::Feasibility};
        double dualSum{}, minimumReducedCost{}; // delta includes 0 and Phase-I artificials.
        double rcRoundingGuard{}, sumRoundingGuard{};
        double lowerBound{}, rmpObjective{}, gap{};
        std::optional<double> totalLowerBound, totalRmpObjective; // Cost phase only.
        BranchContext context;
    };
    struct RootCgSnapshot {
        RmpLpResult lp;
        std::vector<RmpColumn> columns; // lambda[id] belongs to THIS snapshot, not a newer pool.
        std::optional<RootCgBound> bound;
    };
    struct RootCgIteration {
        std::size_t iteration{}, stageId{}, modelRevision{};
        std::size_t columnsBefore{}, columnsAfter{}, addedColumns{};
        RmpPhase phase{RmpPhase::Feasibility};
        RmpLpStatus lpStatus{RmpLpStatus::Error};
        std::optional<PricingStatus> pricingStatus;
        std::optional<double> objective, artificialSum;
        std::map<int, std::optional<double>> minimumReducedCosts;
        std::optional<RootCgBound> bound;
        double lpTimeLimit{}, pricingTimeLimit{}, lpSeconds{}, pricingSeconds{}, elapsedSeconds{};
        std::string action;
        std::size_t nodeId{};
    };
    struct RootCgResult {
        std::size_t stageId{};
        PricingBackend backend{PricingBackend::PlainLabeling};
        RootCgStatus status{RootCgStatus::Error};
        std::optional<RootCgSnapshot> snapshot;
        std::vector<RootCgIteration> iterations;
        double seconds{};
        std::string message;
        BranchContext context;
    };
    class DVRP_RootCgController {
    public:
        RootCgResult solve(const StageProblem& stage, const RootCgOptions& options) const;
        RootCgResult solve(const StageProblem& stage, const BranchContext& context, const RootCgOptions& options) const;
    };
}
#endif

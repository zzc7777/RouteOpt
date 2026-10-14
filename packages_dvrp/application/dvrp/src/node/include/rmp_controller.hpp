/*
 * DVRP adaptation of RouteOpt (Copyright (c) 2025 Zhengzhong (Ricky) You).
 * License: GPL-3.0. See node.cpp and add_column.cpp in packages/application/cvrp.
 */
#ifndef ROUTEOPT_DVRP_RMP_CONTROLLER_HPP
#define ROUTEOPT_DVRP_RMP_CONTROLLER_HPP

#include "dynamic_data.hpp"
#include <map>
#include <memory>
#include <optional>
#include <utility>

namespace RouteOpt::Application::DVRP {
    enum class RmpPhase { Feasibility, Cost };
    enum class RmpLpStatus { OptimalRmp, TimeLimit, Error };
    enum class RmpRowKind { Customer, ExternalStart };

    struct RmpRow {
        RmpRowKind kind{};
        int key{}; // Customer ID or external start key, in separate namespaces.
        int lpRow{};
    };
    struct RmpColumn {
        std::size_t id{}; // Stable, zero-based REAL route ID (not solver column index).
        StageRoute route;
        double cost{};
        int lpColumn{};
    };
    struct RmpDuals {
        std::size_t stageId{}, modelRevision{};
        RmpPhase phase{RmpPhase::Feasibility};
        std::map<int, double> customers, externalStarts;
    };
    struct RmpLpResult {
        std::size_t stageId{}, modelRevision{};
        RmpPhase phase{RmpPhase::Feasibility};
        RmpLpStatus status{RmpLpStatus::Error};
        std::optional<int> rawStatus;
        std::optional<double> objective, artificialSum, routeCost, totalPlannedCost;
        std::vector<double> lambda, reducedCosts; // Indexed by stable real column ID.
        std::vector<double> artificial; // Indexed by LP row.
        std::optional<RmpDuals> duals;
        double solveSeconds{};
        std::string message;
    };
    struct RmpOptions { double timeLimitSeconds{60.0}; }; // Per LP, not a BP time budget.

    struct RmpSnapshotColumn {
        int lpColumn{};
        std::optional<std::size_t> routeId; // Empty means artificial.
        double objective{}, lower{}, upper{};
        std::vector<std::pair<int, double>> coefficients;
    };
    struct RmpModelSnapshot {
        std::size_t stageId{}, modelRevision{};
        RmpPhase phase{RmpPhase::Feasibility};
        bool continuousLp{};
        std::vector<RmpRow> rows;
        std::vector<double> rhs;
        std::vector<char> senses;
        std::vector<RmpSnapshotColumn> columns;
    };

    class DVRP_RmpController {
    public:
        explicit DVRP_RmpController(const StageProblem& stage, RmpOptions options = {});
        ~DVRP_RmpController();
        DVRP_RmpController(const DVRP_RmpController&) = delete;
        DVRP_RmpController& operator=(const DVRP_RmpController&) = delete;

        std::vector<std::size_t> addInitialRoutes();
        std::vector<std::size_t> addRoutes(const std::vector<StageRoute>& routes);
        RmpLpResult solveLp();
        void enterCostPhase(); // Requires the controller's own current zero-artificial result.
        RmpModelSnapshot inspectModel() const; // Actual solver data, not cached coefficients.
        const std::vector<RmpColumn>& columns() const; // Read-only; invalidated by addRoutes.

    private:
        struct Impl;
        std::unique_ptr<Impl> impl_;
    };
}
#endif

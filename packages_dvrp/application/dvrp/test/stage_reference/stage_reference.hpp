#ifndef ROUTEOPT_DVRP_STAGE_REFERENCE_HPP
#define ROUTEOPT_DVRP_STAGE_REFERENCE_HPP

#include "dynamic_data.hpp"
#include <map>
#include <optional>
#include <utility>

namespace RouteOpt::Application::DVRP::Testing {
    inline constexpr std::size_t MAX_REFERENCE_CUSTOMERS = 5;
    inline constexpr std::size_t MAX_REFERENCE_EXTERNAL_STARTS = 5;

    struct EnumeratedRoute {
        StageRoute route;
        double cost{};
    };

    // The only factory exhausts the entire elementary route universe. No partial pools.
    class EnumeratedStage {
    public:
        const StageProblem& problem() const { return problem_; }
        const std::vector<EnumeratedRoute>& routes() const { return routes_; }
        double enumerationSeconds() const { return enumerationSeconds_; }
    private:
        StageProblem problem_;
        std::vector<EnumeratedRoute> routes_;
        double enumerationSeconds_{};
        EnumeratedStage(StageProblem problem, std::vector<EnumeratedRoute> routes, double seconds)
            : problem_(std::move(problem)), routes_(std::move(routes)), enumerationSeconds_(seconds) {}
        friend EnumeratedStage enumerateStage(const StageProblem& stage);
    };

    struct StageDuals {
        std::map<int, double> customers;
        std::map<int, double> externalStarts;
    };
    struct ReferenceOptions {
        double timeLimitSeconds{60.0}; // Per LP/MIP optimization, not enumeration/simulation.
    };
    enum class LpReferenceStatus { Optimal, Infeasible, TimeLimit, Error };
    struct LpReferenceResult {
        LpReferenceStatus status{LpReferenceStatus::Error};
        std::optional<double> routeCost;
        std::optional<double> totalPlannedCost;
        std::vector<double> lambda; // Same order as EnumeratedStage::routes().
        std::optional<StageDuals> duals;
        std::vector<double> reducedCosts;
        std::optional<int> rawStatus; // Empty for analytically solved empty model.
        double solveSeconds{};
        std::string message;
    };
    struct MipReferenceResult {
        StageSolution solution;
        std::optional<double> routeCost;
        std::optional<double> totalPlannedCost;
        std::optional<double> bestBound; // Route cost only, excludes fixedCostAfterTau.
        std::optional<int> rawStatus;
        double solveSeconds{};
    };

    EnumeratedStage enumerateStage(const StageProblem& stage);
    std::vector<double> computeReducedCosts(const EnumeratedStage& all, const StageDuals& duals);
    LpReferenceResult solveFullLp(const EnumeratedStage& all, const ReferenceOptions& options = {});
    MipReferenceResult solveFullMip(const EnumeratedStage& all, const ReferenceOptions& options = {});
}
#endif

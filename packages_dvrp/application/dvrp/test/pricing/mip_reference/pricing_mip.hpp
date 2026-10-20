// Test-only independent single-path MIP. Not a production pricing backend.
#ifndef ROUTEOPT_DVRP_PRICING_MIP_REFERENCE_HPP
#define ROUTEOPT_DVRP_PRICING_MIP_REFERENCE_HPP

#include "rmp_controller.hpp"

namespace RouteOpt::Application::DVRP::Testing {
    inline constexpr std::size_t MAX_PRICING_MIP_CUSTOMERS = 50;
    enum class PricingMipStatus { Optimal, Infeasible, Unfinished, Error };
    struct PricingMipWitness {
        StageRoute route;
        double cost{}, reducedCost{};
    };
    struct PricingMipResult {
        std::size_t stageId{}, modelRevision{};
        RmpPhase phase{};
        int startKey{};
        PricingMipStatus status{PricingMipStatus::Error};
        std::optional<int> rawStatus;
        std::optional<double> lowerBound;
        std::optional<PricingMipWitness> incumbent;
        double seconds{};
        std::string message;
    };
    // Limit is per CPLEX optimization; result.seconds includes preparation and validation.
    PricingMipResult solvePricingMip(const StageProblem&, const RmpDuals&, int startKey,
                                     double timeLimitSeconds = 10.0);
    // Recompute earliest schedule/distance/rc without production pricing helpers.
    PricingMipWitness checkPricingRoute(const StageProblem&, const RmpDuals&, const StageRoute&);

    enum class MinimumAudit { Certified, Unresolved, Mismatch };
    // claimed=nullopt means the COMPLETED pricer claims this start has no feasible route.
    // A numeric claim must have an independently checked feasible witness first.
    MinimumAudit certifyPricingMinimum(const PricingMipResult&, std::optional<double> claimed,
                                       double absoluteTolerance = 1e-6);
}
#endif

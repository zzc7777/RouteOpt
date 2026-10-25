/* DVRP pricing adaptation of RouteOpt. Copyright (c) 2025 Zhengzhong (Ricky) You.
 * License: GPL-3.0. See pricing_internal.hpp for original source locations. */
#ifndef ROUTEOPT_DVRP_PRICING_CONTROLLER_HPP
#define ROUTEOPT_DVRP_PRICING_CONTROLLER_HPP

#include "rmp_controller.hpp" // Value types only; no solver headers or solver linkage.
#include <memory>

namespace RouteOpt::Application::DVRP {
    enum class PricingBackend { PlainLabeling, BucketGraph };
    enum class PricingStatus { Complete, TimeLimit, ResourceLimit, Error };
    struct PricingOptions {
        PricingBackend backend{PricingBackend::PlainLabeling};
        double timeLimitSeconds{60}; // ALL starts together, including graph construction.
        std::size_t maxLabels{200000}; // Retained records per start, including dead predecessors.
        std::size_t bucketCount{25};
        std::size_t maxGraphNodes{100000};
        std::size_t maxGraphArcs{5000000}; // Successor + dependency/transpose adjacency entries.
    };
    struct PricedRoute {
        StageRoute route;
        double cost{}, reducedCost{};
    };
    struct StartPricingResult {
        int startKey{};
        bool hasFeasibleRoute{};
        std::optional<double> minimumReducedCost;
        std::optional<PricedRoute> bestRoute;
    };
    struct PricingStatistics {
        std::size_t generatedLabels{}, extendedLabels{}, dominanceChecks{}, boundSkips{};
        std::size_t peakRetainedLabels{}, bucketNodes{}, bucketArcs{}, dependencyArcs{};
        std::size_t sccPasses{}, crossBinExtensions{}, crossBinDominated{};
        double graphBuildSeconds{}, searchSeconds{};
    };
    struct PricingResult {
        std::size_t stageId{}, modelRevision{};
        RmpPhase phase{RmpPhase::Feasibility};
        PricingBackend backend{PricingBackend::PlainLabeling};
        PricingStatus status{PricingStatus::Error};
        // Empty on interruption/error: no partial minima or no-negative-column certificate.
        std::vector<StartPricingResult> starts;
        std::vector<PricedRoute> candidates;
        PricingStatistics statistics;
        double seconds{};
        std::string message;
        BranchContext context;
    };
    class DVRP_PricingController {
    public:
        explicit DVRP_PricingController(const StageProblem& stage); // Invalid stage throws.
        DVRP_PricingController(const StageProblem& stage, const BranchContext& context);
        ~DVRP_PricingController();
        DVRP_PricingController(const DVRP_PricingController&) = delete;
        DVRP_PricingController& operator=(const DVRP_PricingController&) = delete;
        PricingResult price(const RmpDuals& duals, const PricingOptions& options) const;
    private:
        struct Impl;
        std::unique_ptr<Impl> impl_;
    };
}
#endif

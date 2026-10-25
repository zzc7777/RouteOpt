/* Copyright (c) 2025 Zhengzhong (Ricky) You. RouteOpt, GPL-3.0.
 * DVRP copy/adaptation sources (packages/application/cvrp/src/pricing/include):
 * label.hpp, pricing_functors.hpp, dominance.hpp, run_labeling.hpp,
 * set_resource_in_bucket_graph.hpp, get_topological_order.hpp,
 * write_columns_from_pricing.hpp. See each implementation for deviations. */
#ifndef ROUTEOPT_DVRP_PRICING_INTERNAL_HPP
#define ROUTEOPT_DVRP_PRICING_INTERNAL_HPP
#include "pricing_controller.hpp"
#include "route_opt_macro.hpp"
#include <chrono>
#include <limits>

namespace RouteOpt::Application::DVRP::PricingDetail {
    using Clock = std::chrono::steady_clock;
    inline constexpr std::size_t NO_LABEL = std::numeric_limits<std::size_t>::max();
    inline constexpr double INF = std::numeric_limits<double>::infinity();
    struct Interrupted { PricingStatus status; const char* message; };
    struct Budget {
        Clock::time_point began;
        const PricingOptions& options;
        void check() const;
    };
    struct Timer {
        Clock::time_point began{Clock::now()};
        double& total;
        explicit Timer(double& seconds) : total(seconds) {}
        ~Timer() { total += std::chrono::duration<double>(Clock::now()-began).count(); }
    };
    struct PreparedStage {
        StageProblem stage;
        BranchContext context;
        std::map<int,int> index;
        std::vector<std::vector<double>> distances; // Customers 1..n, return n+1. Row 0 unused.
        explicit PreparedStage(const StageProblem& source, const BranchContext& context = {});
        int n() const { return static_cast<int>(stage.customers.size()); }
    };
    struct Label {
        int vertex{};
        double time{}, load{}, rc{}, cost{};
        routeOptLong visited;
        std::size_t predecessor{NO_LABEL};
        bool active{true}, extended{false};
    };
    struct Work {
        const PreparedStage& data;
        const StartSpec& start;
        const RmpDuals& duals;
        const Budget& budget;
        PricingStatistics& stats;
        double weight{};
        std::vector<double> sourceDistances;
        std::vector<Label> labels;
        std::size_t bestLabel{NO_LABEL};
        double bestRc{INF}, bestCost{};
        // Compiled per-start rules. RF external truth is constant (-1=true,-2=false).
        struct RfRule { int first, second; bool together; };
        std::vector<RfRule> rfRules;
        routeOptLong rfMask, bannedCustomers;
        std::vector<int> forcedNext, forcedPrevious; // -1=none, -2=conflict
        std::vector<std::pair<int,int>> forbiddenArcs;
        bool rfAllowed(const routeOptLong& visited, bool finishing) const;
        bool arcAllowed(int from, int to) const;
        Work(const PreparedStage&, const StartSpec&, const RmpDuals&, const Budget&, PricingStatistics&);
        double arc(int from, int to) const;
        std::optional<Label> extend(std::size_t parent, int customer);
        bool dominates(const Label& a, const Label& b);
        std::size_t retain(Label label);
        std::optional<std::size_t> insert(std::vector<std::size_t>& atVertex, Label label);
        void complete(std::size_t id);
        StartPricingResult result() const;
    };
    struct Bucket {
        std::vector<int> bucketArcs;
        std::vector<std::size_t> labels, pending;
        std::size_t cursor{};
        double minimumRc{INF}, prefixMinimumRc{INF};
    };
    struct BucketGraph {
        int customers{};
        std::vector<double> lower;
        std::vector<Bucket> buckets; // bin-major, customer index is 1-based.
        std::vector<std::vector<std::vector<int>>> order;
        std::vector<std::vector<int>> componentRank;
        std::size_t bin(double time) const;
        Bucket& at(int i, std::size_t b) { return buckets.at(b*customers+i-1); }
        const Bucket& at(int i, std::size_t b) const { return buckets.at(b*customers+i-1); }
    };
    BucketGraph buildBucketGraph(Work& work);
    void runPlain(Work& work);
    void runBucket(Work& work);
}
#endif

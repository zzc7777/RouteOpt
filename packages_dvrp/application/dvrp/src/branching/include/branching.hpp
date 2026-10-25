/* DVRP branching over elementary route columns. RouteOpt, GPL-3.0. */
#ifndef ROUTEOPT_DVRP_BRANCHING_HPP
#define ROUTEOPT_DVRP_BRANCHING_HPP
#include "dynamic_data.hpp"
#include <compare>
#include <optional>
#include <variant>

namespace RouteOpt::Application::DVRP {
    enum class BranchStrategy { None, RyanFoster, StartDirectedArc };
    enum class RfElementKind { Customer, ExternalStart };
    struct RfElement {
        RfElementKind kind{RfElementKind::Customer};
        int key{};
        auto operator<=>(const RfElement&) const = default;
    };
    enum class ArcEndpointKind { Source, Customer, Return };
    struct ArcEndpoint {
        ArcEndpointKind kind{ArcEndpointKind::Source};
        int customerId{}; // Zero for Source/Return, NOT an external vehicle key.
        auto operator<=>(const ArcEndpoint&) const = default;
    };
    struct DirectedArc {
        int startKey{};
        ArcEndpoint from, to;
        auto operator<=>(const DirectedArc&) const = default;
    };
    struct RfDecision {
        RfElement first, second;
        bool together{};
        auto operator<=>(const RfDecision&) const = default;
    };
    struct ArcDecision {
        DirectedArc arc;
        bool use{};
        auto operator<=>(const ArcDecision&) const = default;
    };
    using BranchDecision = std::variant<RfDecision, ArcDecision>;
    struct BranchContext {
        std::size_t nodeId{};
        BranchStrategy strategy{BranchStrategy::None};
        std::vector<BranchDecision> decisions;
        bool operator==(const BranchContext&) const = default;
    };
    struct BranchCandidate {
        BranchDecision decision; // Boolean is false; children set false / true.
        double flow{};
    };
    enum class BranchSelectionStatus { CandidateFound, NoFractionalCandidate, NumericalUnresolved, Error };
    struct BranchSelection {
        std::size_t stageId{}, modelRevision{};
        BranchContext context;
        BranchSelectionStatus status{BranchSelectionStatus::Error};
        std::vector<BranchCandidate> candidates;
        std::optional<BranchCandidate> selected;
        std::string message;
    };
    struct RmpLpResult;
    struct RmpColumn;
    BranchContext normalizeBranches(const StageProblem&, const BranchContext&);
    // Requires a normalized context and a structurally valid elementary route.
    // Does not replace capacity/time-window validation.
    bool routeAllowed(const StageRoute&, const BranchContext&);
    // Caller must first establish CG closure. NoFractionalCandidate is NOT integer optimality.
    BranchSelection selectCandidate(const StageProblem&, const BranchContext&,
                                    const RmpLpResult&, const std::vector<RmpColumn>&);
    std::pair<BranchContext, BranchContext> makeChildren(const StageProblem&, const BranchContext&,
        const BranchSelection&, std::size_t leftId, std::size_t rightId);
}
#endif

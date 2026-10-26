/* DVRP adaptation of RouteOpt. Copyright (c) 2025 Zhengzhong (Ricky) You.
 * License: GPL-3.0. Single-stage BP, no dynamic simulation or CLI. */
#ifndef ROUTEOPT_DVRP_BP_CONTROLLER_HPP
#define ROUTEOPT_DVRP_BP_CONTROLLER_HPP
#include "root_cg_controller.hpp"

namespace RouteOpt::Application::DVRP {
enum class BpStatus { Optimal, Infeasible, TimeLimit, NodeLimit, ResourceLimit,
                      IterationLimit, NumericalUnresolved, Error };
struct BpOptions {
    BpOptions(BranchStrategy strategy, PricingBackend backend) : strategy(strategy), nodeCg(backend) {}
    BranchStrategy strategy;
    RootCgOptions nodeCg;
    double timeLimitSeconds{60};
    std::size_t maxNodes{10000}; // Started node CG calls; bound-only pruning does not count.
};
struct IntegerWitness {
    std::size_t stageId{};
    BranchContext context;
    std::vector<StageRoute> routes;
    double routeCost{}, totalPlannedCost{};
};
enum class IntegerRecoveryStatus { Recovered, NotRecovered, NumericalUnresolved, Error };
struct IntegerRecoveryResult {
    IntegerRecoveryStatus status{IntegerRecoveryStatus::Error};
    std::optional<IntegerWitness> witness;
    std::string message;
};
struct BpNodeRecord {
    BranchContext context;
    std::optional<std::size_t> parentId;
    std::size_t depth{};
    double inheritedLowerBound{};
    std::optional<double> nodeLowerBound, lpObjective, incumbentCost;
    std::optional<RootCgStatus> cgStatus;
    std::optional<BranchCandidate> candidate;
    std::vector<RootCgIteration> iterations;
    std::string action;
    double seconds{};
};
struct BpResult {
    std::size_t stageId{};
    BranchStrategy strategy{BranchStrategy::None};
    PricingBackend backend{PricingBackend::PlainLabeling};
    BpStatus status{BpStatus::Error};
    std::optional<IntegerWitness> incumbent;
    std::optional<double> lowerBound, upperBound, totalLowerBound, totalUpperBound, absoluteGap, relativeGap;
    double absoluteGapTolerance{1e-6}, relativeGapTolerance{1e-8};
    std::size_t nodesCreated{}, nodesSolved{}, nodesSplit{}, nodesPruned{}, nodesInfeasible{}, remainingNodes{};
    std::size_t pricingCalls{};
    PricingStatistics pricingStatistics;
    std::vector<BpNodeRecord> nodes;
    std::optional<BranchContext> stoppedNode;
    double seconds{};
    std::string message;
};
IntegerWitness validateIntegerRoutes(const StageProblem&, const BranchContext&, const std::vector<StageRoute>&);
IntegerRecoveryResult recoverIntegerRoutes(const StageProblem&, const RootCgResult&, const BranchSelection&);
StageSolution toStageSolution(const BpResult&);
const char* bpStatusName(BpStatus);
class DVRP_BpController {
public:
    BpResult solve(const StageProblem&, const BpOptions&) const;
};
// Shared value checks, not a solver injection interface.
namespace BpDetail {
    void validateStage(const StageProblem&);
    void validateConverged(const StageProblem&, const RootCgResult&);
}
}
#endif

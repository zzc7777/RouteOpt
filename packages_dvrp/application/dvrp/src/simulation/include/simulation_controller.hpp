/* DVRP adaptation of RouteOpt. Copyright (c) 2025 Zhengzhong (Ricky) You.
 * License: GPL-3.0. Event-loop integration; no new optimization algorithm. */
#ifndef ROUTEOPT_DVRP_SIMULATION_CONTROLLER_HPP
#define ROUTEOPT_DVRP_SIMULATION_CONTROLLER_HPP

#include "bp_controller.hpp"
#include "dynamic_controller.hpp"
#include <functional>
#include <iosfwd>

namespace RouteOpt::Application::DVRP {
struct StageBpSummary {
    std::size_t stageId{};
    BranchStrategy strategy{BranchStrategy::None};
    PricingBackend backend{PricingBackend::PlainLabeling};
    BpStatus status{BpStatus::Error};
    bool accepted{}; // Plans accepted, NOT a claim that all future travel occurred.
    std::optional<IntegerWitness> incumbent;
    std::optional<double> lowerBound, upperBound, totalLowerBound, totalUpperBound, absoluteGap, relativeGap;
    std::size_t nodesCreated{}, nodesSolved{}, nodesSplit{}, nodesPruned{}, nodesInfeasible{}, remainingNodes{}, pricingCalls{};
    PricingStatistics pricingStatistics;
    double seconds{};
    std::string message;
};
struct SimulationResult {
    SimulationStatus status{SimulationStatus::StoppedError};
    DynamicState finalState; // Last committed snapshot; may still be Running on an integration error.
    std::vector<StageRecord> stages;
    std::vector<StageBpSummary> solves;
    std::optional<StageProblem> stoppedStage;
    std::optional<BpResult> stoppedBp; // Full diagnostics only for the last failed stage.
    std::optional<BpStatus> stoppedByBp; // Empty on integration/IO error, even if stoppedBp exists.
    double seconds{};
    std::string message;
};
const char* simulationStatusName(SimulationStatus);
int simulationExitCode(const SimulationResult&);
class DVRP_SimulationController {
public:
    SimulationResult run(DynamicController dynamic, const BpOptions& options, std::ostream* log = nullptr) const;
};
namespace SimulationDetail {
    // Deterministic fault injection only. The production entry always supplies real BP.
    using StageSolver = std::function<BpResult(const StageProblem&, const BpOptions&)>;
    SimulationResult runWithSolver(DynamicController dynamic, const BpOptions& options,
                                   const StageSolver& solve, std::ostream* log = nullptr);
}
}
#endif

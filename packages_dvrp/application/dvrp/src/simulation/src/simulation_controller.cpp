/* DVRP adaptation of RouteOpt. Copyright (c) 2025 Zhengzhong (Ricky) You.
 * License: GPL-3.0. Reuses DynamicController and DVRP_BpController unchanged. */
#include "simulation_controller.hpp"
#include "route_opt_macro.hpp"
#include <algorithm>
#include <chrono>
#include <cmath>
#include <iomanip>
#include <limits>
#include <locale>
#include <sstream>
#include <stdexcept>
#include <type_traits>

namespace RouteOpt::Application::DVRP {
namespace {
using Clock = std::chrono::steady_clock;
void require(bool ok, const char* why) { if (!ok) throw std::runtime_error(why); }
bool near(double a, double b) {
    return std::isfinite(a) && std::isfinite(b)
        && std::abs(a-b) <= TOLERANCE * std::max({1.,std::abs(a),std::abs(b)});
}
std::ostringstream textStream() {
    std::ostringstream out; out.imbue(std::locale::classic());
    out << std::setprecision(std::numeric_limits<double>::max_digits10); return out;
}
std::string quoted(const std::string& value) {
    auto out = textStream(); out << '"';
    for (unsigned char c : value) {
        if (c == '"' || c == '\\') out << '\\' << char(c);
        else if (c < 32) out << "\\u" << std::hex << std::setw(4) << std::setfill('0') << unsigned(c) << std::dec;
        else out << char(c);
    }
    out << '"'; return out.str();
}
std::string number(const std::optional<double>& value) {
    if (!value) return "NA";
    auto out = textStream(); out << *value; return out.str();
}
void ids(std::ostream& out, const std::vector<int>& values) {
    out << '['; bool first = true;
    for (int id : values) { if (!first) out << ','; out << id; first = false; }
    out << ']';
}
void emit(std::ostream* log, const std::ostringstream& out) {
    if (!log) return;
    *log << out.str(); log->flush();
    require(bool(*log), "simulation log write failed");
}
const char* strategyName(BranchStrategy s) {
    return s == BranchStrategy::RyanFoster ? "rf" : s == BranchStrategy::StartDirectedArc ? "arc" : "invalid";
}
const char* backendName(PricingBackend b) {
    return b == PricingBackend::PlainLabeling ? "plain" : b == PricingBackend::BucketGraph ? "bucket" : "invalid";
}
void runLog(std::ostream* log, const BpOptions& o) {
    const auto& p = o.nodeCg.pricing; auto out = textStream();
    out << "RUN branching=" << strategyName(o.strategy) << " pricing=" << backendName(p.backend)
        << " stage_seconds=" << o.timeLimitSeconds << " max_nodes=" << o.maxNodes
        << " node_seconds=" << o.nodeCg.timeLimitSeconds << " lp_seconds=" << o.nodeCg.rmp.timeLimitSeconds
        << " pricing_seconds=" << p.timeLimitSeconds << " max_iterations=" << o.nodeCg.maxIterations
        << " max_labels_per_start=" << p.maxLabels << " bucket_count=" << p.bucketCount
        << " max_graph_nodes=" << p.maxGraphNodes << " max_graph_arcs=" << p.maxGraphArcs
        << " abs_gap_tolerance=" << o.nodeCg.absoluteGapTolerance << " rel_gap_tolerance=" << o.nodeCg.relativeGapTolerance
        << " initial_routes=" << (o.nodeCg.addInitialRoutes ? "true" : "false") << "\n";
    emit(log,out);
}
void stageLog(std::ostream* log, const StageProblem& s, std::optional<double> next) {
    auto out = textStream(); std::vector<int> customers;
    for (const auto& c : s.customers) customers.push_back(c.id);
    out << "STAGE id=" << s.stageId << " instance=" << quoted(s.instanceName) << " time=" << s.currentTime
        << " next_event=" << number(next) << " customers="; ids(out,customers);
    out << " external_starts=" << s.externalStarts.size() << " fixed_cost=" << s.fixedCostAfterTau
        << " return_x=" << s.returnDepot.location.x << " return_y=" << s.returnDepot.location.y
        << " return_deadline=" << s.returnDepot.deadline << '\n';
    auto start = [&](const StartSpec& k) {
        out << "START stage=" << s.stageId << " key=" << k.startKey << " kind="
            << (k.kind == StartKind::Origin ? "origin" : "external") << " customer=" << k.locationCustomerId
            << " x=" << k.location.x << " y=" << k.location.y << " time=" << k.startTime
            << " remaining_capacity=" << k.remainingCapacity << '\n';
    };
    start(s.originStart); for (const auto& k : s.externalStarts) start(k); emit(log,out);
}
StageBpSummary summarize(const BpResult& b) {
    StageBpSummary s; s.stageId=b.stageId; s.strategy=b.strategy; s.backend=b.backend; s.status=b.status;
    s.incumbent=b.incumbent; s.lowerBound=b.lowerBound; s.upperBound=b.upperBound;
    s.totalLowerBound=b.totalLowerBound; s.totalUpperBound=b.totalUpperBound;
    s.absoluteGap=b.absoluteGap; s.relativeGap=b.relativeGap;
    s.nodesCreated=b.nodesCreated; s.nodesSolved=b.nodesSolved; s.nodesSplit=b.nodesSplit;
    s.nodesPruned=b.nodesPruned; s.nodesInfeasible=b.nodesInfeasible; s.remainingNodes=b.remainingNodes;
    s.pricingCalls=b.pricingCalls; s.pricingStatistics=b.pricingStatistics; s.seconds=b.seconds; s.message=b.message;
    return s;
}
void solveLog(std::ostream* log, const BpResult& b, bool accepted) {
    auto out = textStream(); const auto& p = b.pricingStatistics;
    out << "SOLVE stage=" << b.stageId << " status=" << bpStatusName(b.status)
        << " executed=" << (accepted ? "true" : "false") << " lb=" << number(b.lowerBound)
        << " ub=" << number(b.upperBound) << " total_lb=" << number(b.totalLowerBound)
        << " total_ub=" << number(b.totalUpperBound) << " abs_gap=" << number(b.absoluteGap)
        << " rel_gap=" << number(b.relativeGap) << " candidate_route_cost="
        << number(b.incumbent ? std::optional<double>(b.incumbent->routeCost) : std::nullopt)
        << " candidate_total_cost=" << number(b.incumbent ? std::optional<double>(b.incumbent->totalPlannedCost) : std::nullopt)
        << " nodes_created=" << b.nodesCreated << " nodes_solved=" << b.nodesSolved << " nodes_split=" << b.nodesSplit
        << " nodes_pruned=" << b.nodesPruned << " nodes_infeasible=" << b.nodesInfeasible << " remaining_nodes=" << b.remainingNodes
        << " pricing_calls=" << b.pricingCalls << " labels=" << p.generatedLabels << " extended_labels=" << p.extendedLabels
        << " peak_labels=" << p.peakRetainedLabels << " dominance_checks=" << p.dominanceChecks
        << " bound_skips=" << p.boundSkips << " bucket_nodes=" << p.bucketNodes << " bucket_arcs=" << p.bucketArcs
        << " dependency_arcs=" << p.dependencyArcs << " scc_passes=" << p.sccPasses
        << " cross_bin_extensions=" << p.crossBinExtensions << " cross_bin_dominated=" << p.crossBinDominated
        << " graph_seconds=" << p.graphBuildSeconds << " search_seconds=" << p.searchSeconds
        << " seconds=" << b.seconds << " message=" << quoted(b.message) << '\n';
    if (b.incumbent) for (const auto& r : b.incumbent->routes) {
        out << "ROUTE stage=" << b.stageId << " start=" << r.startKey << " executed=" << (accepted ? "true" : "false")
            << " customers="; ids(out,r.customerSequence); out << '\n';
    }
    emit(log,out);
}
void summaryLog(std::ostream* log, const SimulationResult& r) {
    auto out = textStream();
    out << "SUMMARY status=" << simulationStatusName(r.status) << " complete="
        << (r.status == SimulationStatus::Completed ? "true" : "false")
        << " stage=" << r.finalState.stageId << " time=" << r.finalState.currentTime
        << " snapshot_status=" << simulationStatusName(r.finalState.status)
        << " actual_distance=" << r.finalState.cumulativeActualDistance
        << " served=" << r.finalState.servedCustomerIds.size() << " vehicles=" << r.finalState.vehicles.size()
        << " solve_calls=" << r.solves.size() << " seconds=" << r.seconds
        << " bp_status=" << (r.stoppedBp ? bpStatusName(r.stoppedBp->status) : "NA")
        << " message=" << quoted(r.message) << '\n'; emit(log,out);
}
void validateConversion(const BpResult& b, const StageSolution& s) {
    switch (b.status) {
        case BpStatus::Optimal: require(s.status==SolveStatus::Optimal,"BP Optimal result failed conversion"); break;
        case BpStatus::Infeasible: require(s.status==SolveStatus::Infeasible,"BP Infeasible result failed conversion"); break;
        case BpStatus::TimeLimit:
            require(s.status==(b.incumbent ? SolveStatus::TimeLimitWithSolution : SolveStatus::TimeLimitWithoutSolution),
                    "BP TimeLimit result failed conversion"); break;
        case BpStatus::NodeLimit: case BpStatus::ResourceLimit: case BpStatus::IterationLimit:
        case BpStatus::NumericalUnresolved: case BpStatus::Error:
            require(s.status==SolveStatus::Error,"BP error result failed conversion"); break;
        default: throw std::runtime_error("unknown BP status");
    }
}
}
const char* simulationStatusName(SimulationStatus s) {
    switch(s) {
        case SimulationStatus::Running:return "Running";
        case SimulationStatus::Completed:return "Completed";
        case SimulationStatus::StoppedInfeasible:return "StoppedInfeasible";
        case SimulationStatus::StoppedTimeLimit:return "StoppedTimeLimit";
        default:return "StoppedError";
    }
}
int simulationExitCode(const SimulationResult& r) {
    if (r.status==SimulationStatus::Completed) return 0;
    if (r.status==SimulationStatus::StoppedInfeasible) return 3;
    if (r.status==SimulationStatus::StoppedTimeLimit) return 4;
    if (r.stoppedByBp && (*r.stoppedByBp==BpStatus::NodeLimit || *r.stoppedByBp==BpStatus::ResourceLimit
                         || *r.stoppedByBp==BpStatus::IterationLimit)) return 5;
    return 1;
}
SimulationResult DVRP_SimulationController::run(DynamicController dynamic, const BpOptions& options, std::ostream* log) const {
    return SimulationDetail::runWithSolver(std::move(dynamic),options,
        [](const StageProblem& s,const BpOptions& o) { return DVRP_BpController{}.solve(s,o); },log);
}
SimulationResult SimulationDetail::runWithSolver(DynamicController dynamic, const BpOptions& options,
                                                 const StageSolver& solve, std::ostream* log) {
    const auto began = Clock::now(); SimulationResult result;
    const BpOptions fixedOptions = options; // No cross-stage options mutation.
    std::optional<StageProblem> stage;
    std::optional<BpResult> bp;
    static_assert(std::is_nothrow_move_assignable_v<DynamicController>);
    try {
        const auto initial = dynamic.state();
        require(initial.status==SimulationStatus::Running && initial.awaitingSolution && initial.stageId==0
                && initial.currentTime==0 && dynamic.records().empty(),"simulation requires a fresh dynamic controller");
        require(bool(solve),"missing stage solver");
        runLog(log,fixedOptions);
        for (;;) {
            stage = dynamic.buildStageProblem(); const auto next = dynamic.nextEventTime();
            stageLog(log,*stage,next);
            bp = solve(*stage,fixedOptions);
            result.solves.push_back(summarize(*bp));
            require(bp->stageId==stage->stageId && bp->strategy==fixedOptions.strategy
                    && bp->backend==fixedOptions.nodeCg.pricing.backend,"BP result identity mismatch");
            const auto solution = toStageSolution(*bp); validateConversion(*bp,solution);
            auto pending = dynamic;
            pending.acceptSolution(solution);
            const bool accepted = bp->status==BpStatus::Optimal;
            if (accepted) {
                const auto records = pending.records(); const auto& record = records.back();
                require(bp->incumbent && record.executed && near(record.routeCost,bp->incumbent->routeCost)
                        && near(record.totalPlannedCost,bp->incumbent->totalPlannedCost),"dynamic/BP cost mismatch");
            }
            solveLog(log,*bp,accepted);
            dynamic = std::move(pending);
            result.solves.back().accepted = accepted;
            if (!accepted) {
                result.status = dynamic.state().status; result.stoppedByBp = bp->status;
                result.message = solution.message; break;
            }
            const auto before = dynamic.state(); pending = dynamic;
            if (next) pending.advanceTo(*next); else pending.finish();
            const auto after = pending.state();
            require(std::isfinite(after.cumulativeActualDistance) && after.cumulativeActualDistance>=before.cumulativeActualDistance
                    && std::isfinite(after.currentTime) && after.currentTime>=before.currentTime,"invalid actual distance/time accounting");
            auto out = textStream();
            out << "ADVANCE stage=" << stage->stageId << " operation=" << (next ? "event" : "finish")
                << " from=" << before.currentTime << " to=" << after.currentTime
                << " distance=" << after.cumulativeActualDistance-before.cumulativeActualDistance
                << " cumulative=" << after.cumulativeActualDistance << '\n'; emit(log,out);
            dynamic = std::move(pending);
            bp.reset();
            if (!next) {
                require(after.status==SimulationStatus::Completed,"finish did not complete simulation");
                result.status=SimulationStatus::Completed;
                result.message="all disclosure stages accepted and all vehicles returned; not a clairvoyant global optimum";
                break;
            }
        }
    } catch (const std::exception& e) {
        result.status=SimulationStatus::StoppedError; result.stoppedByBp.reset(); result.message=e.what();
    } catch (...) {
        result.status=SimulationStatus::StoppedError; result.stoppedByBp.reset(); result.message="unknown simulation exception";
    }
    result.finalState=dynamic.state(); result.stages=dynamic.records();
    if (result.status!=SimulationStatus::Completed) { result.stoppedStage=stage; result.stoppedBp=std::move(bp); }
    result.seconds=std::chrono::duration<double>(Clock::now()-began).count();
    try { summaryLog(log,result); }
    catch (...) {
        result.status=SimulationStatus::StoppedError; result.stoppedByBp.reset();
        result.message += "; final summary output failed";
        if (!result.stoppedStage) result.stoppedStage=std::move(stage);
    }
    return result;
}
}

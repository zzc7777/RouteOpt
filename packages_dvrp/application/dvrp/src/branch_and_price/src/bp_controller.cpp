/* Copyright (c) 2025 Zhengzhong (Ricky) You. RouteOpt, GPL-3.0.
 * Adapted from packages/branching/bbt/include/workflow.hpp: pricing, branch,
 * children, bounds. No cuts/BKF/MIP. Node LPs use the existing DVRP CG service. */
#include "bp_controller.hpp"
#include "bbt_controller.hpp"
#include "route_opt_macro.hpp"
#include <algorithm>
#include <chrono>
#include <cmath>
#include <limits>
#include <stdexcept>

namespace RouteOpt::Application::DVRP {
namespace {
using Clock=std::chrono::steady_clock;
using Tree=RouteOpt::Branching::DVRPBBT::BBTController<BranchContext>;
struct Stop { BpStatus status; std::string why; };
void require(bool b,const char* why) { if(!b)throw std::invalid_argument(std::string("BP: ")+why); }
double finite(double x) { if(!std::isfinite(x))throw Stop{BpStatus::NumericalUnresolved,"nonfinite BP arithmetic"};return x; }
void nonnegative(double x) { require(std::isfinite(x)&&x>=0,"invalid budget or tolerance"); }
double tolerance(double u,const BpOptions& o) {
    return finite(o.nodeCg.absoluteGapTolerance+o.nodeCg.relativeGapTolerance*std::max(1.,std::abs(u)));
}
BpStatus cgStop(RootCgStatus s) {
    switch(s) {
        case RootCgStatus::TimeLimit:return BpStatus::TimeLimit;
        case RootCgStatus::ResourceLimit:return BpStatus::ResourceLimit;
        case RootCgStatus::IterationLimit:return BpStatus::IterationLimit;
        case RootCgStatus::NumericalUnresolved:return BpStatus::NumericalUnresolved;
        default:return BpStatus::Error;
    }
}
void add(std::size_t& a,std::size_t b) {
    if(b>std::numeric_limits<std::size_t>::max()-a)throw Stop{BpStatus::ResourceLimit,"statistics counter overflow"}; a+=b;
}
void accumulate(PricingStatistics& a,const PricingStatistics& b) {
    add(a.generatedLabels,b.generatedLabels); add(a.extendedLabels,b.extendedLabels);
    add(a.dominanceChecks,b.dominanceChecks); add(a.boundSkips,b.boundSkips);
    a.peakRetainedLabels=std::max(a.peakRetainedLabels,b.peakRetainedLabels);
    add(a.bucketNodes,b.bucketNodes); add(a.bucketArcs,b.bucketArcs); add(a.dependencyArcs,b.dependencyArcs);
    add(a.sccPasses,b.sccPasses); add(a.crossBinExtensions,b.crossBinExtensions); add(a.crossBinDominated,b.crossBinDominated);
    a.graphBuildSeconds=finite(a.graphBuildSeconds+b.graphBuildSeconds); a.searchSeconds=finite(a.searchSeconds+b.searchSeconds);
}
bool sameVariable(BranchDecision a,BranchDecision b) {
    auto zero=[](auto& d){if constexpr(requires{d.together;})d.together=false;else d.use=false;};
    std::visit(zero,a); std::visit(zero,b); return a==b;
}
void infeasibilityCertificate(const StageProblem& s,const RootCgResult& r) {
    require(r.stageId==s.stageId&&r.snapshot&&r.snapshot->bound,"missing infeasibility snapshot");
    const auto& p=r.snapshot->lp; const auto& b=*r.snapshot->bound;
    require(p.stageId==s.stageId&&b.stageId==s.stageId&&p.context==r.context&&b.context==r.context
        &&p.modelRevision==b.modelRevision&&p.phase==RmpPhase::Feasibility&&b.phase==p.phase
        &&p.status==RmpLpStatus::OptimalRmp&&p.artificialSum&&std::isfinite(*p.artificialSum)
        &&*p.artificialSum>TOLERANCE&&std::isfinite(b.lowerBound)&&b.lowerBound>TOLERANCE,"invalid Phase-I proof");
}
}
BpResult DVRP_BpController::solve(const StageProblem& s,const BpOptions& o) const {
    const auto began=Clock::now(); BpResult result;
    result.stageId=s.stageId; result.strategy=o.strategy; result.backend=o.nodeCg.pricing.backend;
    result.absoluteGapTolerance=o.nodeCg.absoluteGapTolerance; result.relativeGapTolerance=o.nodeCg.relativeGapTolerance;
    std::optional<Tree> tree;
    auto elapsed=[&]{return std::chrono::duration<double>(Clock::now()-began).count();};
    auto remaining=[&] {
        double left=o.timeLimitSeconds-elapsed();
        if(left<=0)throw Stop{BpStatus::TimeLimit,"stage BP time budget exhausted"}; return left;
    };
    auto updateBounds=[&] {
        result.upperBound=result.incumbent?std::optional<double>(result.incumbent->routeCost):std::nullopt;
        result.lowerBound=tree?tree->globalLowerBound(result.upperBound):std::nullopt;
        result.totalLowerBound=result.lowerBound?std::optional<double>(finite(*result.lowerBound+s.fixedCostAfterTau)):std::nullopt;
        result.totalUpperBound=result.upperBound?std::optional<double>(finite(*result.upperBound+s.fixedCostAfterTau)):std::nullopt;
        result.absoluteGap.reset(); result.relativeGap.reset();
        if(result.lowerBound&&result.upperBound) {
            double gap=finite(*result.upperBound-*result.lowerBound);
            if(gap<0)throw Stop{BpStatus::NumericalUnresolved,"global bounds reversed"};
            result.absoluteGap=gap; result.relativeGap=gap/std::max(1.,std::abs(*result.upperBound));
        }
    };
    try {
        BpDetail::validateStage(s); nonnegative(o.timeLimitSeconds); nonnegative(o.nodeCg.timeLimitSeconds);
        nonnegative(o.nodeCg.rmp.timeLimitSeconds); nonnegative(o.nodeCg.pricing.timeLimitSeconds);
        nonnegative(o.nodeCg.absoluteGapTolerance); nonnegative(o.nodeCg.relativeGapTolerance);
        require(o.strategy==BranchStrategy::RyanFoster||o.strategy==BranchStrategy::StartDirectedArc,"choose RF or start-specific directed arcs");
        require(result.backend==PricingBackend::PlainLabeling||result.backend==PricingBackend::BucketGraph,"unknown pricing backend");
        require(result.backend!=PricingBackend::BucketGraph||o.nodeCg.pricing.bucketCount>0,"bucketCount must be positive");
        tree.emplace(); tree->addRoot(BranchContext{0,o.strategy,{}},0); result.nodesCreated=1;
        for(;;) {
            remaining(); updateBounds();
            if(result.absoluteGap&&*result.absoluteGap<=tolerance(*result.upperBound,o)) {
                result.status=BpStatus::Optimal; result.message="integer witness and global numerical gap closed"; break;
            }
            if(tree->empty()) {
                result.status=result.incumbent?BpStatus::NumericalUnresolved:BpStatus::Infeasible;
                result.message=result.incumbent?"tree exhausted, but certified global gap is not closed":"all integer partitions proved infeasible"; break;
            }
            const auto& node=tree->startNext();
            require(node.id==node.payload.nodeId,"tree/context node identity mismatch");
            auto& log=result.nodes.emplace_back(); log.context=node.payload; log.parentId=node.parentId;
            log.depth=node.depth; log.inheritedLowerBound=node.lowerBound;
            const auto nodeBegan=Clock::now();
            auto logTime=[&]{log.seconds=std::chrono::duration<double>(Clock::now()-nodeBegan).count();};
            if(result.upperBound&&node.lowerBound>=*result.upperBound-tolerance(*result.upperBound,o)) {
                log.nodeLowerBound=node.lowerBound; log.incumbentCost=result.upperBound; log.action="prune inherited bound";
                tree->closeActive(false); ++result.nodesPruned; logTime(); continue;
            }
            if(result.nodesSolved>=o.maxNodes)throw Stop{BpStatus::NodeLimit,"maximum node CG calls reached"};
            auto options=o.nodeCg; options.timeLimitSeconds=std::min(options.timeLimitSeconds,remaining());
            ++result.nodesSolved;
            auto cg=DVRP_RootCgController{}.solve(s,node.payload,options);
            log.cgStatus=cg.status; log.iterations=std::move(cg.iterations); logTime();
            for(const auto& it:log.iterations)if(it.pricingStatistics) {
                add(result.pricingCalls,1); accumulate(result.pricingStatistics,*it.pricingStatistics);
            }
            require(cg.stageId==s.stageId&&cg.context==node.payload&&cg.backend==result.backend,"node CG identity mismatch");
            remaining();
            if(cg.status==RootCgStatus::LpInfeasible) {
                infeasibilityCertificate(s,cg); log.action="prune proven Phase-I infeasibility";
                tree->closeActive(true); ++result.nodesInfeasible; logTime(); continue;
            }
            if(cg.status!=RootCgStatus::LpConverged)throw Stop{cgStop(cg.status),cg.message};
            BpDetail::validateConverged(s,cg);
            auto selection=selectCandidate(s,cg.context,cg.snapshot->lp,cg.snapshot->columns);
            if(selection.status==BranchSelectionStatus::NumericalUnresolved)throw Stop{BpStatus::NumericalUnresolved,selection.message};
            require(selection.status!=BranchSelectionStatus::Error,selection.message.c_str());
            const double lpObjective=*cg.snapshot->lp.objective;
            require(cg.snapshot->bound->gap<=tolerance(lpObjective,o),"node reported convergence outside gap tolerance");
            tree->updateActiveBound(cg.snapshot->bound->lowerBound);
            const double bound=tree->active()->lowerBound;
            log.nodeLowerBound=bound; log.lpObjective=lpObjective;
            auto recovered=recoverIntegerRoutes(s,cg,selection);
            if(recovered.status==IntegerRecoveryStatus::Error)throw Stop{BpStatus::Error,recovered.message};
            if(recovered.status==IntegerRecoveryStatus::NumericalUnresolved)throw Stop{BpStatus::NumericalUnresolved,recovered.message};
            if(recovered.witness) {
                if(recovered.witness->routeCost<bound)throw Stop{BpStatus::NumericalUnresolved,"node witness is below its certified bound"};
                if(!result.incumbent||recovered.witness->routeCost<result.incumbent->routeCost)
                    result.incumbent=std::move(recovered.witness);
            }
            updateBounds(); log.incumbentCost=result.upperBound;
            logTime(); remaining(); // Retain a fully verified witness even if verification used the last time slice.
            if(result.upperBound&&bound>=*result.upperBound-tolerance(*result.upperBound,o)) {
                log.action="close node by validated incumbent and bound";
                tree->closeActive(false); ++result.nodesPruned; logTime(); continue;
            }
            if(selection.status==BranchSelectionStatus::NoFractionalCandidate)
                throw Stop{BpStatus::NumericalUnresolved,"no fractional candidate, but integer witness/bound does not close node"};
            require(selection.selected.has_value(),"missing selected branch");
            for(const auto& old:node.payload.decisions)
                require(!sameVariable(old,selection.selected->decision),"branching again on an already fixed variable");
            const auto [leftId,rightId]=tree->nextChildIds();
            auto children=makeChildren(s,node.payload,selection,leftId,rightId);
            log.candidate=selection.selected; remaining();
            tree->replaceActive(std::move(children.first),std::move(children.second));
            add(result.nodesCreated,2); ++result.nodesSplit; log.action="branch into two restricted CG nodes"; logTime();
        }
    } catch(const Stop& e) { result.status=e.status; result.message=e.why; }
    catch(const std::bad_alloc&) { result.status=BpStatus::ResourceLimit; result.message="BP allocation failed"; }
    catch(const std::exception& e) { result.status=BpStatus::Error; result.message=e.what(); }
    try {
        if(tree) {
            result.remainingNodes=tree->pendingCount()+(tree->active()?1:0);
            if(tree->active()) {
                result.stoppedNode=tree->active()->payload;
                if(!result.nodes.empty())result.nodes.back().action=result.message;
            }
        }
        updateBounds();
    } catch(const Stop& e) { result.status=e.status; result.message=e.why; result.lowerBound.reset(); result.absoluteGap.reset(); result.relativeGap.reset(); result.totalLowerBound.reset(); }
    catch(const std::bad_alloc&) { result.status=BpStatus::ResourceLimit; }
    tree.reset(); // Include destruction of remaining queued payloads in the total budget.
    result.seconds=elapsed();
    if(std::isfinite(o.timeLimitSeconds)&&o.timeLimitSeconds>=0&&result.seconds>=o.timeLimitSeconds
        &&(result.status==BpStatus::Optimal||result.status==BpStatus::Infeasible)) {
        result.status=BpStatus::TimeLimit; result.message="BP time exhausted during finalization";
    }
    return result;
}
}

/* RouteOpt DVRP, GPL-3.0. Resource propagation follows the independent checks in
 * src/node/src/rmp_controller.cpp and src/dynamic/src/dynamic_controller.cpp.
 * No solver, pricing search, test enumeration or mutable dynamic state. */
#include "bp_controller.hpp"
#include "dynamic_controller.hpp"
#include "route_opt_macro.hpp"
#include <algorithm>
#include <cmath>
#include <limits>
#include <set>
#include <stdexcept>
#include <tuple>

namespace RouteOpt::Application::DVRP {
namespace {
void require(bool ok,const char* msg) { if (!ok) throw std::invalid_argument(std::string("BP verification: ")+msg); }
double finite(double x) { require(std::isfinite(x),"nonfinite arithmetic"); return x; }
bool near(double a,double b) { return std::isfinite(a)&&std::isfinite(b)&&std::abs(a-b)<=TOLERANCE*std::max({1.,std::abs(a),std::abs(b)}); }
void loc(Location p) { finite(p.x); finite(p.y); }
double routeCost(const StageProblem& s,const BranchContext& ctx,const StageRoute& r) {
    const StartSpec* start=&s.originStart;
    if (r.startKey) {
        auto it=std::find_if(s.externalStarts.begin(),s.externalStarts.end(),[&](const auto& k){return k.startKey==r.startKey;});
        require(it!=s.externalStarts.end(),"unknown start"); start=&*it;
    }
    require(r.startKey!=0 || !r.customerSequence.empty(),"empty origin route");
    std::set<int> seen; double time=start->startTime,load=0,cost=0; auto previous=start->location;
    for (int id:r.customerSequence) {
        auto it=std::find_if(s.customers.begin(),s.customers.end(),[&](const auto& c){return c.id==id;});
        require(it!=s.customers.end()&&seen.insert(id).second,"unknown/repeated customer");
        double d=routeOptDistance(previous,it->location);
        cost=finite(cost+d); load=finite(load+it->demand); time=std::max(finite(time+d),it->readyTime);
        require(load<=start->remainingCapacity+TOLERANCE && time<=it->dueDate+TOLERANCE,"capacity/time window violation");
        time=finite(time+it->serviceTime); previous=it->location;
    }
    double d=routeOptDistance(previous,s.returnDepot.location); cost=finite(cost+d);
    require(finite(time+d)<=s.returnDepot.deadline+TOLERANCE,"late return");
    require(routeAllowed(r,ctx),"route violates node restrictions"); return cost;
}
bool sameCandidate(const BranchCandidate& a,const BranchCandidate& b) { return a.decision==b.decision && a.flow==b.flow; }
bool sameSelection(const BranchSelection& a,const BranchSelection& b) {
    if(a.stageId!=b.stageId||a.modelRevision!=b.modelRevision||a.context!=b.context||a.status!=b.status
       ||a.selected.has_value()!=b.selected.has_value()||a.candidates.size()!=b.candidates.size())return false;
    if(a.selected&&!sameCandidate(*a.selected,*b.selected))return false;
    return std::equal(a.candidates.begin(),a.candidates.end(),b.candidates.begin(),sameCandidate);
}
std::vector<DirectedArc> routeArcs(const StageRoute& r) {
    std::vector<DirectedArc> arcs; ArcEndpoint previous{ArcEndpointKind::Source,0};
    for(int id:r.customerSequence) { ArcEndpoint next{ArcEndpointKind::Customer,id}; arcs.push_back({r.startKey,previous,next}); previous=next; }
    arcs.push_back({r.startKey,previous,{ArcEndpointKind::Return,0}}); return arcs;
}
}
namespace BpDetail {
void validateStage(const StageProblem& s) {
    require(s.customers.size()+s.externalStarts.size()<=std::size_t(std::numeric_limits<int>::max()),"row capacity exceeded");
    require(finite(s.currentTime)>=0 && finite(s.fixedCostAfterTau)>=0 && finite(s.returnDepot.deadline)>=0,"negative stage value");
    loc(s.returnDepot.location); const auto& o=s.originStart; loc(o.location);
    require(o.kind==StartKind::Origin&&o.startKey==0&&o.locationCustomerId==0,"invalid origin identity");
    require(finite(o.startTime)==s.currentTime&&finite(o.remainingCapacity)>0&&std::floor(o.remainingCapacity)==o.remainingCapacity,"invalid origin resources");
    require(o.location.x==s.returnDepot.location.x&&o.location.y==s.returnDepot.location.y,"origin/return positions differ");
    std::set<int> customers,starts;
    for(const auto& c:s.customers) {
        require(c.id>0&&customers.insert(c.id).second,"invalid customer ID"); loc(c.location);
        require(finite(c.demand)>=0&&c.demand<=o.remainingCapacity&&std::floor(c.demand)==c.demand,"invalid demand");
        require(finite(c.readyTime)<=finite(c.dueDate)&&finite(c.serviceTime)>=0,"invalid time resources");
    }
    for(const auto& k:s.externalStarts) {
        require(k.kind==StartKind::External&&k.startKey>0&&starts.insert(k.startKey).second,"invalid external identity"); loc(k.location);
        require(k.locationCustomerId>0&&!customers.contains(k.locationCustomerId),"invalid external anchor");
        require(finite(k.startTime)>=s.currentTime&&finite(k.remainingCapacity)>=0&&k.remainingCapacity<=o.remainingCapacity
                &&std::floor(k.remainingCapacity)==k.remainingCapacity,"invalid external resources");
    }
}
void validateConverged(const StageProblem& s,const RootCgResult& r) {
    require(r.status==RootCgStatus::LpConverged&&r.stageId==s.stageId&&r.snapshot&&r.snapshot->bound,"missing closed node LP");
    require(r.context==normalizeBranches(s,r.context),"noncanonical context");
    const auto& p=r.snapshot->lp; const auto& b=*r.snapshot->bound;
    require(p.stageId==s.stageId&&p.context==r.context&&p.phase==RmpPhase::Cost&&p.status==RmpLpStatus::OptimalRmp
        &&p.objective&&p.artificialSum&&std::abs(*p.artificialSum)<=TOLERANCE,"invalid real LP");
    require(b.stageId==s.stageId&&b.context==r.context&&b.modelRevision==p.modelRevision&&b.phase==RmpPhase::Cost
        &&b.rowCount==s.customers.size()+s.externalStarts.size(),"stale node bound");
    require(near(b.rmpObjective,*p.objective)&&finite(b.gap)>=0&&finite(b.lowerBound)<=b.rmpObjective
        &&near(b.gap,b.rmpObjective-b.lowerBound),"inconsistent node bound");
}
}
IntegerWitness validateIntegerRoutes(const StageProblem& s,const BranchContext& context,const std::vector<StageRoute>& routes) {
    BpDetail::validateStage(s); const auto ctx=normalizeBranches(s,context);
    IntegerWitness w{s.stageId,ctx,routes,0,0}; std::map<int,int> customers,starts;
    for(const auto& c:s.customers)customers[c.id]=0;
    for(const auto& k:s.externalStarts)starts[k.startKey]=0;
    for(const auto& r:routes) {
        w.routeCost=finite(w.routeCost+routeCost(s,ctx,r));
        for(int id:r.customerSequence)require(++customers.at(id)==1,"duplicate customer coverage");
        if(r.startKey)require(++starts.at(r.startKey)==1,"duplicate external coverage");
    }
    for(const auto& [id,n]:customers)require(n==1,"missing customer");
    for(const auto& [id,n]:starts)require(n==1,"missing external continuation");
    w.totalPlannedCost=finite(w.routeCost+s.fixedCostAfterTau); return w;
}
IntegerRecoveryResult recoverIntegerRoutes(const StageProblem& s,const RootCgResult& r,const BranchSelection& selection) {
    IntegerRecoveryResult out;
    try {
        BpDetail::validateStage(s); BpDetail::validateConverged(s,r);
        const auto& snap=*r.snapshot; const auto& lp=snap.lp;
        auto checked=selectCandidate(s,r.context,lp,snap.columns);
        require(sameSelection(checked,selection),"stale or altered candidate selection");
        if(checked.status==BranchSelectionStatus::NumericalUnresolved) {
            out.status=IntegerRecoveryStatus::NumericalUnresolved; out.message=checked.message; return out;
        }
        require(checked.status==BranchSelectionStatus::CandidateFound||checked.status==BranchSelectionStatus::NoFractionalCandidate,"invalid candidate snapshot");
        std::vector<double> actual(snap.columns.size());
        std::vector<StageRoute> tentative;
        for(std::size_t j=0;j<snap.columns.size();++j) {
            if(lp.lambda[j]>0) {
                actual[j]=routeCost(s,r.context,snap.columns[j].route);
                require(near(actual[j],snap.columns[j].cost),"cached column cost mismatch");
            }
            if(lp.lambda[j]>=1-TOLERANCE)tentative.push_back(snap.columns[j].route);
        }
        // A proposal only: exact integer coverage and resource checks decide validity.
        try { out.witness=validateIntegerRoutes(s,r.context,tentative); }
        catch(const std::invalid_argument&) { /* incomplete/overlapping tentative selection */ }
        if(!out.witness && checked.status==BranchSelectionStatus::NoFractionalCandidate) {
            tentative.clear();
            if(r.context.strategy==BranchStrategy::RyanFoster) {
                std::map<std::set<RfElement>,std::size_t> blocks;
                for(std::size_t j=0;j<snap.columns.size();++j)if(lp.lambda[j]>0) {
                    const auto& route=snap.columns[j].route; std::set<RfElement> rows;
                    for(int id:route.customerSequence)rows.insert({RfElementKind::Customer,id});
                    if(route.startKey)rows.insert({RfElementKind::ExternalStart,route.startKey});
                    auto [it,inserted]=blocks.emplace(rows,j);
                    auto key=[&](std::size_t k){return std::tuple{actual[k],snap.columns[k].route.startKey,snap.columns[k].route.customerSequence};};
                    if(!inserted&&key(j)<key(it->second))it->second=j;
                }
                std::set<RfElement> covered; bool overlap=false;
                for(const auto& [rows,j]:blocks) {
                    for(auto e:rows)if(!covered.insert(e).second)overlap=true;
                    tentative.push_back(snap.columns[j].route);
                }
                if(overlap)tentative.clear();
            } else {
                std::map<DirectedArc,double> flows;
                std::set<std::pair<int,std::vector<int>>> unique;
                for(std::size_t j=0;j<snap.columns.size();++j)if(lp.lambda[j]>0) {
                    const auto& route=snap.columns[j].route;
                    unique.emplace(route.startKey,route.customerSequence);
                    for(auto a:routeArcs(route))flows[a]+=lp.lambda[j];
                }
                bool integral=true;
                for(const auto& [a,f]:flows)if(!std::isfinite(f)||std::abs(f-1)>TOLERANCE)integral=false;
                if(integral)for(const auto& [key,seq]:unique)tentative.push_back({key,seq});
            }
            try { out.witness=validateIntegerRoutes(s,r.context,tentative); }
            catch(const std::invalid_argument&) { /* no integer cover reconstructed */ }
        }
        out.status=out.witness?IntegerRecoveryStatus::Recovered:
            checked.status==BranchSelectionStatus::CandidateFound?IntegerRecoveryStatus::NotRecovered:IntegerRecoveryStatus::NumericalUnresolved;
        out.message=out.witness?"validated integer route witness (not an optimality certificate)":
            out.status==IntegerRecoveryStatus::NotRecovered?"fractional LP: branch required":"no candidate, but integer recovery failed";
    } catch(const std::bad_alloc&) { throw; }
    catch(const std::exception& e) { out.status=IntegerRecoveryStatus::Error; out.witness.reset(); out.message=e.what(); }
    return out;
}
const char* bpStatusName(BpStatus s) {
    switch(s) {
        case BpStatus::Optimal:return "Optimal"; case BpStatus::Infeasible:return "Infeasible";
        case BpStatus::TimeLimit:return "TimeLimit"; case BpStatus::NodeLimit:return "NodeLimit";
        case BpStatus::ResourceLimit:return "ResourceLimit"; case BpStatus::IterationLimit:return "IterationLimit";
        case BpStatus::NumericalUnresolved:return "NumericalUnresolved"; default:return "Error";
    }
}
StageSolution toStageSolution(const BpResult& r) {
    StageSolution out; out.stageId=r.stageId; out.message=std::string("BP ")+bpStatusName(r.status)+": "+r.message;
    if(r.incumbent) {
        if(r.incumbent->stageId!=r.stageId||!std::isfinite(r.incumbent->routeCost)||r.incumbent->routeCost<0
            ||!std::isfinite(r.incumbent->totalPlannedCost)||r.incumbent->totalPlannedCost<r.incumbent->routeCost) {
            out.message="inconsistent BP witness"; return out;
        }
        out.routes=r.incumbent->routes;
    }
    switch(r.status) {
        case BpStatus::Optimal:
            if(r.incumbent&&r.upperBound&&r.lowerBound&&r.absoluteGap&&near(*r.upperBound,r.incumbent->routeCost)
                &&std::isfinite(*r.lowerBound)&&std::isfinite(*r.absoluteGap)&&*r.absoluteGap>=0
                &&*r.lowerBound<=*r.upperBound
                &&std::isfinite(r.absoluteGapTolerance)&&r.absoluteGapTolerance>=0
                &&std::isfinite(r.relativeGapTolerance)&&r.relativeGapTolerance>=0
                &&near(*r.absoluteGap,*r.upperBound-*r.lowerBound)
                &&*r.absoluteGap<=r.absoluteGapTolerance+r.relativeGapTolerance*std::max(1.,std::abs(*r.upperBound)))
                out.status=SolveStatus::Optimal;
            else out.message="inconsistent BP Optimal result";
            break;
        case BpStatus::Infeasible:
            if(!r.incumbent)out.status=SolveStatus::Infeasible; else out.message="infeasible result contains an incumbent";
            break;
        case BpStatus::TimeLimit:out.status=r.incumbent?SolveStatus::TimeLimitWithSolution:SolveStatus::TimeLimitWithoutSolution;break;
        default:out.status=SolveStatus::Error;break;
    }
    return out;
}
}

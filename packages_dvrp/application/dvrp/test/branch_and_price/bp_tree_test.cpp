// Deterministic, solver-free tests of tree bookkeeping and integer recovery.
#include "bp_controller.hpp"
#include "bbt_controller.hpp"
#include <cmath>
#include <iostream>
#include <stdexcept>

using namespace RouteOpt::Application::DVRP;
using RouteOpt::Branching::DVRPBBT::BBTController;
namespace {
void check(bool b, const char* msg) { if (!b) throw std::runtime_error(msg); }
template<class F> void rejects(F f) { bool bad=false; try { f(); } catch (const std::exception&) { bad=true; } check(bad,"expected rejection"); }
StageProblem sample() {
    StageProblem s; s.stageId=9; s.originStart={0,StartKind::Origin,0,{0,0},0,2};
    s.returnDepot={{0,0},100}; s.customers={{3,{10,0},1,0,90,0},{8,{10,0},1,0,90,0}}; return s;
}
RootCgResult snapshot(const StageProblem& s, BranchStrategy strategy, std::vector<StageRoute> routes, std::vector<double> x) {
    RootCgResult r; r.stageId=s.stageId; r.status=RootCgStatus::LpConverged;
    r.context={0,strategy,{}}; r.snapshot=RootCgSnapshot{}; auto& p=r.snapshot->lp;
    p.stageId=s.stageId; p.context=r.context; p.modelRevision=12;
    p.phase=RmpPhase::Cost; p.status=RmpLpStatus::OptimalRmp; p.artificialSum=0; p.lambda=x;
    p.duals=RmpDuals{s.stageId,12,RmpPhase::Cost,{},{},r.context};
    for (const auto& c:s.customers) p.duals->customers[c.id]=0;
    for (const auto& k:s.externalStarts) p.duals->externalStarts[k.startKey]=0;
    double cost=0;
    for (std::size_t j=0;j<routes.size();++j) {
        // Fixtures below use only these geometries; this is deliberately not a production evaluator.
        double c=routes[j].startKey==42?0:20;
        r.snapshot->columns.push_back({j,routes[j],c,int(j)}); cost+=c*x[j];
    }
    p.objective=p.routeCost=cost; p.totalPlannedCost=cost+s.fixedCostAfterTau;
    RootCgBound b; b.stageId=s.stageId; b.modelRevision=12; b.context=r.context; b.phase=RmpPhase::Cost;
    b.lowerBound=cost-1e-10; b.rmpObjective=cost; b.gap=cost-b.lowerBound;
    b.rowCount=s.customers.size()+s.externalStarts.size(); r.snapshot->bound=b; return r;
}
IntegerRecoveryResult recover(const StageProblem& s, const RootCgResult& r) {
    auto pick=selectCandidate(s,r.context,r.snapshot->lp,r.snapshot->columns);
    return recoverIntegerRoutes(s,r,pick);
}
void tree() {
    BBTController<int> t; t.addRoot(7,0); check(*t.globalLowerBound({})==0,"root bound");
    check(t.startNext().id==0 && t.pendingCount()==0 && t.active(),"active lost");
    t.updateActiveBound(30); check(*t.globalLowerBound(40)==30,"active bound omitted");
    auto ids=t.nextChildIds(); check(ids.first==1&&ids.second==2,"ids"); t.replaceActive(1,2);
    check(t.startNext().id==1,"tie ordering"); t.updateActiveBound(40-1e-7); t.closeActive(false);
    check(*t.globalLowerBound(40)==30,"pending omitted");
    check(t.startNext().id==2,"sibling missing"); t.closeActive(true);
    check(t.empty() && *t.globalLowerBound(40)<40,"tolerance leaf falsely closed gap");
    rejects([&]{t.startNext();}); rejects([&]{t.addRoot(0,0);});
}
void rf() {
    auto s=sample(); auto r=snapshot(s,BranchStrategy::RyanFoster,{{0,{3,8}},{0,{8,3}}},{.5,.5});
    auto p=selectCandidate(s,r.context,r.snapshot->lp,r.snapshot->columns);
    check(p.status==BranchSelectionStatus::NoFractionalCandidate,"RF fixture");
    auto out=recoverIntegerRoutes(s,r,p); check(out.witness && out.witness->routes.size()==1,"RF recovery");
    check(out.witness->routeCost==20,"RF cost");
    s.externalStarts={{42,StartKind::External,99,{0,0},0,0}};
    r=snapshot(s,BranchStrategy::RyanFoster,{{0,{3,8}},{0,{8,3}},{42,{}}},{.5,.5,1});
    out=recover(s,r); check(out.witness&&out.witness->routes.size()==2,"external row lost");
}
void arc() {
    auto s=sample(); auto r=snapshot(s,BranchStrategy::StartDirectedArc,{{0,{3,8}},{0,{3,8}}},{.5,.5});
    check(recover(s,r).witness.has_value(),"arc duplicate recovery");
    r=snapshot(s,BranchStrategy::StartDirectedArc,{{0,{3,8}},{0,{8,3}}},{.5,.5});
    check(recover(s,r).status==IntegerRecoveryStatus::NotRecovered,"direction falsely integral");
}
void validation() {
    auto s=sample(); BranchContext c{0,BranchStrategy::RyanFoster,{}};
    check(validateIntegerRoutes(s,c,{{0,{3,8}}}).routeCost==20,"valid");
    rejects([&]{validateIntegerRoutes(s,c,{{0,{3}},{0,{3,8}}});});
    rejects([&]{validateIntegerRoutes(s,c,{{0,{3}}});});
    rejects([&]{validateIntegerRoutes(s,c,{{0,{3,3,8}}});});
    rejects([&]{validateIntegerRoutes(s,c,{{4,{3,8}}});});
    rejects([&]{validateIntegerRoutes(s,c,{{0,{}}});});
    c.decisions={RfDecision{{RfElementKind::Customer,3},{RfElementKind::Customer,8},false}};
    rejects([&]{validateIntegerRoutes(s,c,{{0,{3,8}}});});
    s.returnDepot.deadline=19; rejects([&]{validateIntegerRoutes(s,{},{{0,{3,8}}});});
}
void stale() {
    auto s=sample(); auto r=snapshot(s,BranchStrategy::RyanFoster,{{0,{3,8}}},{1});
    auto p=selectCandidate(s,r.context,r.snapshot->lp,r.snapshot->columns);
    r.snapshot->bound->context.nodeId=7; check(recoverIntegerRoutes(s,r,p).status==IntegerRecoveryStatus::Error,"stale bound");
    r=snapshot(s,BranchStrategy::RyanFoster,{{0,{3,8}}},{1}); r.status=RootCgStatus::TimeLimit;
    check(recoverIntegerRoutes(s,r,p).status==IntegerRecoveryStatus::Error,"interrupted snapshot consumed");
    r.status=RootCgStatus::LpConverged; p.modelRevision++;
    check(recoverIntegerRoutes(s,r,p).status==IntegerRecoveryStatus::Error,"stale candidate");
}
void status() {
    BpResult r; r.stageId=5; r.status=BpStatus::TimeLimit;
    check(toStageSolution(r).status==SolveStatus::TimeLimitWithoutSolution,"timeout without");
    r.incumbent=IntegerWitness{5,{},{},0,0};
    check(toStageSolution(r).status==SolveStatus::TimeLimitWithSolution,"empty witness misclassified");
    for (auto st:{BpStatus::NodeLimit,BpStatus::ResourceLimit,BpStatus::IterationLimit,BpStatus::NumericalUnresolved,BpStatus::Error}) {
        r.status=st; check(toStageSolution(r).status==SolveStatus::Error,"incomplete mapped executable");
    }
    r.status=BpStatus::Optimal; r.upperBound=0; r.lowerBound=0; r.absoluteGap=0;
    check(toStageSolution(r).status==SolveStatus::Optimal,"optimal empty");
    r.incumbent.reset(); check(toStageSolution(r).status==SolveStatus::Error,"optimal without witness");
    r.status=BpStatus::Infeasible; check(toStageSolution(r).status==SolveStatus::Infeasible,"infeasible");
}
void empty() {
    auto s=sample(); s.customers.clear(); s.fixedCostAfterTau=7;
    auto r=snapshot(s,BranchStrategy::RyanFoster,{},{}); r.snapshot->bound->lowerBound=0; r.snapshot->bound->gap=0;
    auto out=recover(s,r); check(out.witness&&out.witness->routes.empty()&&out.witness->totalPlannedCost==7,"empty stage");
    BBTController<int> t; t.addRoot(0,0); t.startNext(); t.closeActive(true);
    check(t.empty()&&!t.globalLowerBound({}),"infeasible fabricated zero bound");
}
void numeric() {
    auto s=sample(); s.originStart.remainingCapacity=3; s.customers.push_back({17,{10,0},1,0,90,0});
    const double eps=1e-8;
    auto r=snapshot(s,BranchStrategy::RyanFoster,
        {{0,{3,8,17}},{0,{17,8,3}},{0,{3,8}},{0,{3,17}},{0,{8,17}}},
        {(1-eps)/2,(1-eps)/2,eps/2,eps/2,eps/2});
    check(selectCandidate(s,r.context,r.snapshot->lp,r.snapshot->columns).status==BranchSelectionStatus::NoFractionalCandidate,"dust fixture");
    check(recover(s,r).status==IntegerRecoveryStatus::NumericalUnresolved,"small positive overlapping blocks dropped");
    r=snapshot(sample(),BranchStrategy::RyanFoster,{{0,{3,8}}},{1}); r.snapshot->columns[0].cost=21;
    check(!recover(sample(),r).witness,"inconsistent LP objective accepted");
    // Make cached LP metadata self-consistent: only independent geometry can catch this.
    r.snapshot->lp.objective=r.snapshot->lp.routeCost=21; r.snapshot->lp.totalPlannedCost=21;
    r.snapshot->bound->rmpObjective=21; r.snapshot->bound->lowerBound=21-1e-10;
    r.snapshot->bound->gap=21-r.snapshot->bound->lowerBound;
    check(recover(sample(),r).status==IntegerRecoveryStatus::Error,"corrupt cost accepted");
}
struct ThrowingPayload {
    int value{};
    static inline bool fail{};
    explicit ThrowingPayload(int v):value(v){}
    ThrowingPayload(const ThrowingPayload&)=default;
    ThrowingPayload& operator=(const ThrowingPayload&)=default;
    ThrowingPayload(ThrowingPayload&& o):value(o.value){if(fail&&value==2)throw std::bad_alloc();}
};
void atomic() {
    BBTController<ThrowingPayload> t; t.addRoot(ThrowingPayload{0},17); t.startNext();
    ThrowingPayload::fail=true; rejects([&]{t.replaceActive(ThrowingPayload{1},ThrowingPayload{2});});
    ThrowingPayload::fail=false;
    check(t.active()&&t.pendingCount()==0&&*t.globalLowerBound({})==17,"partial split lost parent or left orphan");
    check(t.nextChildIds().first==1,"failed split consumed IDs");
    t.replaceActive(ThrowingPayload{1},ThrowingPayload{2});check(t.pendingCount()==2,"split retry");
}
}
int main(int argc,char** argv) {
    try { check(argc==2,"case required"); std::string c=argv[1];
        if(c=="tree")tree(); else if(c=="rf")rf(); else if(c=="arc")arc(); else if(c=="validation")validation();
        else if(c=="stale")stale(); else if(c=="status")status(); else if(c=="empty")empty();
        else if(c=="numeric")numeric(); else if(c=="atomic")atomic();
        else throw std::runtime_error("unknown case"); std::cout<<"PASS "<<c<<'\n'; return 0;
    } catch(const std::exception& e) { std::cerr<<e.what()<<'\n'; return 1; }
}

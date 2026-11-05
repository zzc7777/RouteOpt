// Real BP drives execution; complete MIP is an independent per-snapshot oracle.
#include "simulation_controller.hpp"
#include "stage_reference.hpp"
#include <algorithm>
#include <cmath>
#include <iostream>
#include <limits>
#include <map>
#include <random>
#include <set>
#include <sstream>
#include <stdexcept>
using namespace RouteOpt::Application::DVRP;
using namespace RouteOpt::Application::DVRP::Testing;
namespace {
using Input = std::vector<std::vector<double>>;
constexpr BranchStrategy strategies[]{BranchStrategy::RyanFoster, BranchStrategy::StartDirectedArc};
constexpr PricingBackend backends[]{PricingBackend::PlainLabeling, PricingBackend::BucketGraph};
void check(bool ok, const std::string& message) { if (!ok) throw std::runtime_error(message); }
void close(double a, double b, const char* message) {
    check(std::isfinite(a) && std::isfinite(b) && std::abs(a-b) < 1e-6, message);
}
Input three() { return {{0,0,0,0,0,1000,0,0}, {1,10,0,10,0,900,20,0},
                       {2,12,0,10,0,900,0,5}, {3,14,0,10,0,900,0,15}}; }
void completed(const SimulationResult& result, std::size_t customers) {
    check(result.status == SimulationStatus::Completed, result.message);
    check(simulationExitCode(result) == 0 && result.finalState.status == SimulationStatus::Completed,
          "false completion");
    check(result.finalState.servedCustomerIds.size() == customers, "missing service");
    check(!result.stoppedBp && !result.stoppedStage && !result.stoppedByBp, "stale stopped diagnostics");
    double actual = 0;
    for (const auto& stage : result.stages) { check(stage.executed, "unexecuted successful stage"); actual += stage.actualDistanceToNextEvent; }
    close(actual, result.finalState.cumulativeActualDistance, "actual distance sum");
    for (const auto& v : result.finalState.vehicles) check(v.phase == VehiclePhase::Finished, "not returned");
    check(result.solves.size() == result.stages.size(), "lost BP summary");
}
void events() {
    for (auto strategy : strategies) for (auto backend : backends) {
        BpOptions options(strategy, backend); DynamicController original(three(),100,"three_events");
        std::ostringstream log;
        auto result = DVRP_SimulationController{}.run(original, options, &log);
        completed(result, 3); close(result.finalState.cumulativeActualDistance, 28, "hand cost");
        check(original.records().empty() && original.state().currentTime == 0, "mutated caller");
        check(result.stages.size() == 3, "event count");
        close(result.stages[1].problem.currentTime,5,"second event");
        close(result.stages[2].problem.currentTime,15,"third event");
        for (std::size_t j = 1; j < 3; ++j) {
            const auto& s = result.stages[j].problem;
            check(s.externalStarts.size()==1 && s.externalStarts[0].startKey==1
                  && s.externalStarts[0].locationCustomerId==1,"lost commitment/vehicle");
            close(s.externalStarts[0].startTime,30,"external time");
            close(s.externalStarts[0].remainingCapacity,90,"external capacity");
        }
        close(result.stages[0].actualDistanceToNextEvent,5,"first increment");
        close(result.stages[1].actualDistanceToNextEvent,5,"second increment");
        close(result.stages[2].actualDistanceToNextEvent,18,"final increment");
        check(log.str().find("SUMMARY status=Completed") != std::string::npos, "missing summary");
    }
}
void empty() {
    auto data=three(); for(std::size_t i=1;i<data.size();++i)data[i][7]=20;
    for(auto strategy:strategies)for(auto backend:backends) {
        auto r=DVRP_SimulationController{}.run(DynamicController(data,100),BpOptions(strategy,backend));
        completed(r,3); check(r.stages.size()==2 && r.stages[0].problem.customers.empty(),"empty initial stage lost");
        check(r.stages[0].solution.routes.empty() && r.solves[0].incumbent,"empty witness is still a witness");
        close(r.stages[0].actualDistanceToNextEvent,0,"empty stage travel");
    }
}
void completion() {
    auto data=three();data[2][7]=data[3][7]=30;
    for(auto strategy:strategies)for(auto backend:backends) {
        auto r=DVRP_SimulationController{}.run(DynamicController(data,100),BpOptions(strategy,backend));completed(r,3);
        check(r.stages.size()==2,"same-time disclosure not merged");const auto& s=r.stages[1].problem;
        check(s.customers.size()==2 && s.externalStarts.size()==1 && s.externalStarts[0].locationCustomerId==1,"completion boundary");
        close(s.externalStarts[0].startTime,30,"completion start time");close(s.fixedCostAfterTau,0,"completion fixed cost");
        close(r.finalState.cumulativeActualDistance,28,"completion distance");
    }
}
void waiting() {
    auto data=three();data[1][4]=20;data[1][6]=10;data[2][7]=15;data[3][7]=25;
    for(auto strategy:strategies)for(auto backend:backends) {
        auto r=DVRP_SimulationController{}.run(DynamicController(data,100),BpOptions(strategy,backend));completed(r,3);
        for(std::size_t i=1;i<3;++i) {
            check(r.stages[i].problem.externalStarts.size()==1,"waiting/serving lost commitment");
            close(r.stages[i].problem.externalStarts[0].startTime,30,"waiting/serving start time");
            close(r.stages[i].problem.fixedCostAfterTau,0,"waiting/serving travel");
        }
        close(r.stages[1].actualDistanceToNextEvent,0,"waiting/service charged distance");
        close(r.finalState.cumulativeActualDistance,28,"waiting total");
    }
}
void returning() {
    auto data=three();data[1][6]=0;data[2][1]=20;data[2][7]=15;data[3][1]=1;data[3][7]=30;
    for(auto strategy:strategies)for(auto backend:backends) {
        auto r=DVRP_SimulationController{}.run(DynamicController(data,100),BpOptions(strategy,backend));completed(r,3);
        check(r.stages[1].problem.externalStarts.empty(),"returning vehicle rerouted");
        close(r.stages[1].problem.fixedCostAfterTau,5,"remaining return cost");
        check(r.stages[2].problem.externalStarts.size()==1 && r.stages[2].problem.externalStarts[0].startKey==2,"finished vehicle reused");
        close(r.finalState.cumulativeActualDistance,60,"return total");
    }
}
void external() {
    auto data=three();data[1][3]=100;
    for(auto strategy:strategies)for(auto backend:backends) {
        BpOptions o(strategy,backend);auto r=DVRP_SimulationController{}.run(DynamicController(data,100),o);completed(r,3);
        const auto& routes=r.stages[1].solution.routes;
        check(std::any_of(routes.begin(),routes.end(),[](const StageRoute& a){return a.startKey==1 && a.customerSequence.empty();}),"missing mandatory empty return");
        close(r.finalState.cumulativeActualDistance,48,"empty return distance");
        // A no-customer/external-only snapshot is an adjacent BP contract test,
        // not a naturally new disclosure event (new disclosures add customers).
        auto stage=r.stages[1].problem;stage.customers.clear();
        auto b=DVRP_BpController{}.solve(stage,o);
        check(b.status==BpStatus::Optimal && b.incumbent && b.incumbent->routes.size()==1
              && b.incumbent->routes[0].customerSequence.empty(),"external-only contract");
    }
}
void infeasible() {
    auto data=three();data[2][5]=6; // Revealed at 5, unreachable from origin or the committed start.
    for(auto strategy:strategies)for(auto backend:backends) {
        auto r=DVRP_SimulationController{}.run(DynamicController(data,100),BpOptions(strategy,backend));
        check(r.status==SimulationStatus::StoppedInfeasible && simulationExitCode(r)==3,"real infeasibility not stopped");
        check(r.solves.size()==2 && r.stoppedBp && r.stoppedBp->status==BpStatus::Infeasible,"missing infeasibility report");
        close(r.finalState.currentTime,5,"infeasible advanced");close(r.finalState.cumulativeActualDistance,5,"infeasible cost");
        check(r.stages.size()==2 && !r.stages.back().executed,"infeasible dispatched");
    }
}
void stops() {
    for(auto strategy:strategies)for(auto backend:backends)
    for(auto stop:{BpStatus::Infeasible,BpStatus::TimeLimit,BpStatus::NodeLimit,BpStatus::ResourceLimit,
                   BpStatus::IterationLimit,BpStatus::NumericalUnresolved,BpStatus::Error})
    for(bool withWitness:{false,true}) {
        if(stop==BpStatus::Infeasible && withWitness)continue;
        BpOptions o(strategy,backend);int calls=0;std::ostringstream log;
        auto r=SimulationDetail::runWithSolver(DynamicController(three(),100),o,
            [&](const StageProblem& s,const BpOptions& options) {
                ++calls;auto b=DVRP_BpController{}.solve(s,options);
                check(b.status==BpStatus::Optimal,"injection precondition");
                if(calls==2) {b.status=stop;b.message="injected stop";
                    if(!withWitness) {b.incumbent.reset();b.upperBound.reset();b.totalUpperBound.reset();b.absoluteGap.reset();b.relativeGap.reset();}}
                return b;
            },&log);
        check(calls==2 && r.stoppedByBp==stop && r.stoppedBp && r.stoppedBp->status==stop,"lost stop reason or called next stage");
        check(bool(r.stoppedBp->incumbent)==withWitness,"lost diagnostic witness");
        check(!r.solves.back().accepted && !r.stages.back().executed,"executed failed result");
        close(r.finalState.currentTime,5,"stopped advanced");close(r.finalState.cumulativeActualDistance,5,"stopped added future cost");
        check(r.finalState.vehicles.size()==1 && r.finalState.vehicles[0].committedCustomerId==1,"replaced stopped plan");
        check(log.str().find("ADVANCE stage=1")==std::string::npos && log.str().find("SUMMARY status=Completed")==std::string::npos,"false advance/completion log");
        const int code=stop==BpStatus::Infeasible?3:stop==BpStatus::TimeLimit?4:
            (stop==BpStatus::NodeLimit||stop==BpStatus::ResourceLimit||stop==BpStatus::IterationLimit)?5:1;
        check(simulationExitCode(r)==code,"wrong stop exit code");
    }
}
void malformed() {
    for(int fault=0;fault<12;++fault) {
        int calls=0;BpOptions o(BranchStrategy::RyanFoster,PricingBackend::BucketGraph);
        auto r=SimulationDetail::runWithSolver(DynamicController(three(),100),o,
            [&](const StageProblem& s,const BpOptions& options) {
                ++calls; auto b=DVRP_BpController{}.solve(s,options); check(b.incumbent.has_value(),"fault precondition");
                if(calls==2) switch(fault) {
                    case 0: ++b.stageId; break;
                    case 1: b.strategy=BranchStrategy::StartDirectedArc; break;
                    case 2: b.backend=PricingBackend::PlainLabeling; break;
                    case 3: ++b.incumbent->stageId; break;
                    case 4: b.incumbent->totalPlannedCost+=1; break;
                    case 5: // A self-consistent fake certificate, but costs disagree with actual routes.
                        b.incumbent->routeCost+=1;b.incumbent->totalPlannedCost+=1;
                        *b.upperBound+=1;*b.lowerBound+=1;*b.totalUpperBound+=1;*b.totalLowerBound+=1;break;
                    case 6: b.incumbent->routes[0].customerSequence.push_back(999); break;
                    case 7: b.incumbent.reset(); break;
                    case 8: b.lowerBound.reset(); break;
                    case 9: b.status=static_cast<BpStatus>(999); break;
                    case 10: throw std::runtime_error("injected callback exception");
                    case 11: b.incumbent->routeCost=std::numeric_limits<double>::quiet_NaN();break;
                }
                return b;
            });
        check(calls==2 && r.status==SimulationStatus::StoppedError && !r.stoppedByBp && simulationExitCode(r)==1,"malformed result accepted");
        close(r.finalState.currentTime,5,"malformed advanced"); close(r.finalState.cumulativeActualDistance,5,"malformed cost");
        check(r.stages.size()==1 && r.finalState.awaitingSolution && r.finalState.vehicles.size()==1,"partial acceptance/ID leak");
        check(r.stoppedStage && r.stoppedStage->stageId==1,"missing attempted stage");
    }
}
class RejectBuffer : public std::stringbuf {
public:
    explicit RejectBuffer(std::string token):token_(std::move(token)){}
protected:
    std::streamsize xsputn(const char* p,std::streamsize n) override {
        if(std::string(p,static_cast<std::size_t>(n)).find(token_)!=std::string::npos) return 0;
        return std::stringbuf::xsputn(p,n);
    }
private: std::string token_;
};
void io() {
    for(bool throwing:{false,true})for(const auto* token:{"RUN ","STAGE id=1 ","SOLVE stage=1 ","ADVANCE stage=1 ","SUMMARY "}) {
        RejectBuffer buffer(token);std::ostream log(&buffer);if(throwing)log.exceptions(std::ios::badbit|std::ios::failbit);
        int calls=0;BpOptions o(BranchStrategy::RyanFoster,PricingBackend::PlainLabeling);
        auto r=SimulationDetail::runWithSolver(DynamicController(three(),100),o,
            [&](const StageProblem& s,const BpOptions& options){++calls;return DVRP_BpController{}.solve(s,options);},&log);
        check(r.status==SimulationStatus::StoppedError && simulationExitCode(r)==1,"IO failure reported success");
        const std::string at=token;
        if(at=="RUN ")check(calls==0 && r.stages.empty(),"ran after RUN failure");
        else if(at=="SUMMARY ")check(calls==3 && r.finalState.status==SimulationStatus::Completed,"summary failed before finish");
        else {
            check(calls==(at=="STAGE id=1 "?1:2),"ran after output failure");
            close(r.finalState.currentTime,5,"output failure advanced");close(r.finalState.cumulativeActualDistance,5,"IO partial distance");
            check(r.stages.size()==(at=="ADVANCE stage=1 "?2:1),"IO atomicity");
        }
    }
    std::ostringstream log;auto r=DVRP_SimulationController{}.run(DynamicController(three(),100,"x\nSTAGE fake\t\"\\"),
        BpOptions(BranchStrategy::RyanFoster,PricingBackend::PlainLabeling),&log);completed(r,3);
    check(log.str().find("\nSTAGE fake")==std::string::npos && log.str().find("\\u000a")!=std::string::npos,"unescaped log string");
}
void budgets() {
    for(auto strategy:strategies)for(auto backend:backends)for(int limit=0;limit<5;++limit) {
        BpOptions o(strategy,backend);
        if(limit==0)o.timeLimitSeconds=0;
        if(limit==1)o.maxNodes=0;
        if(limit==2)o.nodeCg.pricing.maxLabels=0;
        if(limit==3)o.nodeCg.maxIterations=0;
        if(limit==4)o.nodeCg.pricing.timeLimitSeconds=0;
        auto r=DVRP_SimulationController{}.run(DynamicController(three(),100),o);
        check(r.status!=SimulationStatus::Completed && r.solves.size()==1 && r.stoppedByBp,"real limit did not stop");
        close(r.finalState.currentTime,0,"budget advanced");close(r.finalState.cumulativeActualDistance,0,"budget travelled");
        check(r.finalState.vehicles.empty() && !r.stages.back().executed,"budget dispatched");
        check(simulationExitCode(r)==((limit==0||limit==4)?4:5),"real budget exit code");
    }
}
void compareSnapshot(const StageProblem& s,const BpResult& b,const EnumeratedStage& all,const MipReferenceResult& mip) {
    check(mip.solution.status==SolveStatus::Optimal && b.status==BpStatus::Optimal && b.incumbent,"snapshot solve incomplete");
    close(b.incumbent->routeCost,*mip.routeCost,"snapshot BP != MIP");
    close(b.incumbent->totalPlannedCost,*mip.totalPlannedCost,"snapshot fixed cost mismatch");
    check(b.lowerBound && *b.lowerBound<=*mip.routeCost+1e-7,"unsafe BP bound");
    std::map<int,int> customers,starts;double cost=0;
    for(const auto& c:s.customers)customers[c.id]=0;
    for(const auto& k:s.externalStarts)starts[k.startKey]=0;
    for(const auto& route:b.incumbent->routes) {
        auto it=std::find_if(all.routes().begin(),all.routes().end(),[&](const EnumeratedRoute& e){
            return route.startKey==e.route.startKey && route.customerSequence==e.route.customerSequence;});
        check(it!=all.routes().end(),"route outside independent feasible universe");cost+=it->cost;
        for(int id:route.customerSequence)++customers.at(id);if(route.startKey)++starts.at(route.startKey);
    }
    for(auto [id,count]:customers)check(count==1,"oracle customer coverage");
    for(auto [id,count]:starts)check(count==1,"oracle start coverage");close(cost,*mip.routeCost,"oracle selected cost");
}
void oracle() {
    std::mt19937 rng(20261027);std::size_t snapshots=0,comparisons=0;
    for(int trial=0;trial<16;++trial) {
        Input data{{0,0,0,0,0,1000,0,0}};const double cap=2+rng()%3;
        for(int i=1;i<=5;++i)data.push_back({double(i),double(rng()%8),double(rng()%6),1,0,900,double(rng()%8),i==1?0.:double(5*(rng()%5))});
        for(auto strategy:strategies)for(auto backend:backends) {
            BpOptions o(strategy,backend);
            auto r=SimulationDetail::runWithSolver(DynamicController(data,cap),o,
                [&](const StageProblem& s,const BpOptions& options) {
                    ++snapshots;
                    for(const auto& c:s.customers)check(data[c.id][7]<=s.currentTime,"future leaked into BP");
                    auto b=DVRP_BpController{}.solve(s,options);
                    auto all=enumerateStage(s);auto mip=solveFullMip(all);
                    compareSnapshot(s,b,all,mip);++comparisons;
                    for(auto other:strategies)for(auto engine:backends)if(other!=options.strategy||engine!=options.nodeCg.pricing.backend) {
                        auto alternate=DVRP_BpController{}.solve(s,BpOptions(other,engine));
                        compareSnapshot(s,alternate,all,mip);++comparisons;
                    }
                    return b; // Oracle and alternative solves never supply columns/UB/routes to this result.
                });completed(r,5);
        }
    }
    std::cout << "random_simulations=64 snapshots=" << snapshots << " BP_MIP_comparisons=" << comparisons << '\n';
}
void lifecycle() {
    DVRP_SimulationController controller;BpOptions options(BranchStrategy::RyanFoster,PricingBackend::PlainLabeling);
    for(int i=0;i<3;++i){auto r=controller.run(DynamicController(three(),100),options);completed(r,3);close(r.finalState.cumulativeActualDistance,28,"stale state");}
    DynamicController d(three(),100);auto b=DVRP_BpController{}.solve(d.buildStageProblem(),options);d.acceptSolution(toStageSolution(b));
    int calls=0;auto r=SimulationDetail::runWithSolver(d,options,[&](const StageProblem&,const BpOptions&){++calls;return BpResult{};});
    check(r.status==SimulationStatus::StoppedError && calls==0,"accepted resume unexpectedly");
    options.strategy=BranchStrategy::None;r=controller.run(DynamicController(three(),100),options);
    check(r.status==SimulationStatus::StoppedError && r.finalState.vehicles.empty(),"invalid strategy ran");
}
void size() {
    Input data{{0,0,0,0,0,1000,0,0}};
    for(int i=1;i<=20;++i)data.push_back({double(i),1,0,1,0,900,0,double(5*((i-1)%4))});
    for(auto strategy:strategies)for(auto backend:backends) {
        auto r=DVRP_SimulationController{}.run(DynamicController(data,1),BpOptions(strategy,backend));completed(r,20);
        close(r.finalState.cumulativeActualDistance,40,"20-customer simulation cost");
        check(r.finalState.vehicles.size()==20 && r.stages.size()==4,"vehicle reuse or customer truncation");
    }
}
void fractional() {
    Input data{{0,0,0,0,0,1000,0,0},{1,10,0,1,0,900,0,0},{2,10,0,1,0,900,0,0},{3,10,0,1,0,900,0,0}};
    for(auto strategy:strategies)for(auto backend:backends) {
        auto r=DVRP_SimulationController{}.run(DynamicController(data,2),BpOptions(strategy,backend));completed(r,3);
        check(r.solves.size()==1 && r.solves[0].nodesSplit>0,"integration never branched");
        close(r.finalState.cumulativeActualDistance,40,"fractional root LP30 is not integer cost40");
    }
}
void deepStop() {
    Input data{{0,0,0,0,0,1000,0,0},{1,10,0,2,0,900,0,0}};
    for(int i=2;i<=6;++i)data.push_back({double(i),10,0,1,0,900,0,5});
    for(auto strategy:strategies)for(auto backend:backends) {
        BpOptions o(strategy,backend);auto full=DVRP_SimulationController{}.run(DynamicController(data,2),o);completed(full,6);
        check(full.solves.size()==2 && full.solves[1].nodesSplit>1,"no deep second stage");
        close(full.finalState.cumulativeActualDistance,80,"deep full distance");
        bool sawWitness=false;
        // The BP module already exhausts every budget. Here verify loop integration
        // at early/middle/late cutoffs, including a real retained incumbent.
        const std::set<std::size_t> budgets{1,full.solves[1].nodesSolved/2,full.solves[1].nodesSolved-1};
        for(std::size_t n:budgets) {
            o.maxNodes=n;auto cut=DVRP_SimulationController{}.run(DynamicController(data,2),o);
            check(cut.stoppedByBp==BpStatus::NodeLimit && cut.stoppedBp && simulationExitCode(cut)==5,"real node limit lost");
            close(cut.finalState.currentTime,5,"real partial tree advanced time");close(cut.finalState.cumulativeActualDistance,5,"real partial tree travelled");
            check(cut.stages.size()==2 && !cut.stages.back().executed && cut.finalState.vehicles.size()==1,"real partial dispatch");
            if(cut.stoppedBp->incumbent) {
                sawWitness=true;
                check(!cut.solves.back().accepted && cut.stoppedBp->incumbent->routeCost>=full.solves[1].incumbent->routeCost-1e-7,"bad interrupted witness");
            }
        }
        check(sawWitness,"did not observe a REAL stop with an incumbent");
    }
}
}
int main(int argc, char** argv) {
    try { check(argc==2,"case required"); const std::string name=argv[1];
        if(name=="events")events();else if(name=="empty")empty();else if(name=="completion")completion();
        else if(name=="waiting")waiting();else if(name=="returning")returning();else if(name=="external")external();
        else if(name=="infeasible")infeasible();else if(name=="stops")stops();else if(name=="malformed")malformed();
        else if(name=="io")io();else if(name=="budgets")budgets();else if(name=="oracle")oracle();
        else if(name=="lifecycle")lifecycle();else if(name=="size")size();else if(name=="fractional")fractional();
        else if(name=="deep_stop")deepStop();else throw std::runtime_error("unknown case");
        std::cout << "PASS " << name << '\n'; return 0;
    } catch(const std::exception& error) { std::cerr << error.what() << '\n'; return 1; }
}

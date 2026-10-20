#include "pricing_controller.hpp"
#include "stage_reference.hpp"
#include <algorithm>
#include <cmath>
#include <functional>
#include <iostream>
#include <limits>
#include <random>
#include <stdexcept>
using namespace RouteOpt::Application::DVRP;
using namespace RouteOpt::Application::DVRP::Testing;
namespace {
    void check(bool ok,const std::string& why){if(!ok)throw std::runtime_error(why);}
    void close(double a,double b,const std::string& why){check(std::isfinite(a)&&std::isfinite(b)&&std::abs(a-b)<1e-6,why);}
    StageProblem sample() {
        StageProblem s;s.stageId=7;s.currentTime=10;s.fixedCostAfterTau=7;
        s.originStart={0,StartKind::Origin,0,{0,0},10,2};s.returnDepot={{0,0},100};
        s.customers={{5,{2,0},1,0,100,0},{8,{4,0},1,0,100,0}};
        s.externalStarts={{42,StartKind::External,3,{3,0},10,2}};return s;
    }
    RmpLpResult columnGeneration(const StageProblem& s,PricingBackend backend,bool initial,
                                 bool alternating=false) {
        const auto all=enumerateStage(s);
        DVRP_RmpController rmp(s);DVRP_PricingController pricing(s);
        if(initial)rmp.addInitialRoutes();
        PricingOptions options;options.backend=backend;
        for(std::size_t iteration=0;iteration<all.routes().size()+5;++iteration) {
            const auto lp=rmp.solveLp();
            check(lp.status==RmpLpStatus::OptimalRmp && lp.duals && lp.artificialSum,lp.message);
            if(lp.phase==RmpPhase::Feasibility && *lp.artificialSum<=1e-6) {rmp.enterCostPhase();continue;}
            if(alternating)options.backend=iteration%2?PricingBackend::BucketGraph:PricingBackend::PlainLabeling;
            options.bucketCount=iteration%2?3:25;
            const auto p=pricing.price(*lp.duals,options);
            check(p.status==PricingStatus::Complete,p.message);
            check(p.stageId==lp.stageId && p.modelRevision==lp.modelRevision && p.phase==lp.phase,"stale pricing result");
            // Oracle only AUDITS the pricer; it never supplies the production columns.
            for(const auto& best:p.starts) {
                double minimum=std::numeric_limits<double>::infinity();
                for(const auto& r:all.routes())if(r.route.startKey==best.startKey) {
                    double rc=lp.phase==RmpPhase::Cost?r.cost:0;
                    for(int id:r.route.customerSequence)rc-=lp.duals->customers.at(id);
                    if(r.route.startKey)rc-=lp.duals->externalStarts.at(r.route.startKey);
                    minimum=std::min(minimum,rc);
                }
                check(best.hasFeasibleRoute==std::isfinite(minimum),"pricing existence mismatch");
                if(std::isfinite(minimum))close(*best.minimumReducedCost,minimum,"pricing minimum mismatch");
            }
            if(p.candidates.empty()) {
                const auto full=solveFullLp(all);
                if(lp.phase==RmpPhase::Feasibility) {
                    check(*lp.artificialSum>1e-6 && full.status==LpReferenceStatus::Infeasible,"Phase-I false infeasibility");
                } else {
                    check(full.status==LpReferenceStatus::Optimal,full.message);
                    close(*lp.routeCost,*full.routeCost,"CG differs from complete LP");
                    close(*lp.totalPlannedCost,*full.totalPlannedCost,"fixed cost accounting");
                }
                std::cout<<(alternating?"alternating":backend==PricingBackend::PlainLabeling?"plain":"bucket")
                    <<" iterations="<<iteration+1<<" real_columns="<<rmp.columns().size()
                    <<" phase="<<(lp.phase==RmpPhase::Cost?2:1)<<" objective="<<*lp.objective<<'\n';
                return lp;
            }
            std::vector<StageRoute> routes;
            for(const auto& r:p.candidates) {
                double expected=lp.phase==RmpPhase::Cost?r.cost:0;
                for(int id:r.route.customerSequence)expected-=lp.duals->customers.at(id);
                if(r.route.startKey)expected-=lp.duals->externalStarts.at(r.route.startKey);
                close(r.reducedCost,expected,"candidate phase/dual accounting");
                routes.push_back(r.route);
            }
            const auto before=rmp.columns().size();const auto ids=rmp.addRoutes(routes);
            check(rmp.columns().size()>before,"negative pricing candidates are all existing columns");
            for(std::size_t i=0;i<ids.size();++i)close(rmp.columns()[ids[i]].cost,p.candidates[i].cost,"RMP independent route cost");
        }
        throw std::runtime_error("CG did not converge within finite universe bound");
    }
    void both(const StageProblem& s,bool initial=false) {
        const auto a=columnGeneration(s,PricingBackend::PlainLabeling,initial);
        const auto b=columnGeneration(s,PricingBackend::BucketGraph,initial);
        check(a.phase==b.phase,"two backends disagree about feasibility");close(*a.objective,*b.objective,"two backend LP objectives");
    }
    void basic(){both(sample());}
    void initial(){both(sample(),true);}
    void infeasible() {
        auto s=sample();s.customers[0].dueDate=0;both(s);
        s=sample();s.customers.clear();s.returnDepot.deadline=1;both(s);
        s.currentTime=0;s.originStart.startTime=0;
        s.customers={{5,{0.55,0},1,0,100,0}};
        s.externalStarts={{42,StartKind::External,99,{1.1,0},0,2},{43,StartKind::External,98,{1.1,0},0,2}};
        both(s); // Both required vehicles need the same customer.
    }
    void fractional() {
        auto s=sample();s.currentTime=0;s.originStart.startTime=0;s.externalStarts.clear();
        s.customers={{1,{10,0},1,0,100,0},{2,{10,0},1,0,100,0},{3,{10,0},1,0,100,0}};
        both(s);
        const auto all=enumerateStage(s);const auto integer=solveFullMip(all);
        check(integer.solution.status==SolveStatus::Optimal,integer.solution.message);close(*integer.routeCost,40,"MIP40");
        close(*columnGeneration(s,PricingBackend::BucketGraph,false).routeCost,30,"LP30 is not an integer solution");
    }
    void randomCases() {
        std::mt19937 gen(8391);
        for(int t=0;t<12;++t) {
            auto s=sample();s.currentTime=0;s.originStart.startTime=0;s.originStart.remainingCapacity=3;
            s.customers.clear();s.externalStarts.clear();s.returnDepot.deadline=t%3?100:12;
            for(int j=0;j<3+t%3;++j)s.customers.push_back({5+7*j,{double(gen()%8),double(gen()%8)},1,0,100,double(gen()%3)});
            for(int k=0;k<t%3;++k)s.externalStarts.push_back({42+k,StartKind::External,900+k,{double(gen()%6),0},double(gen()%10),double(gen()%4)});
            both(s,t%2==0);
        }
    }
    void empty(){auto s=sample();s.customers.clear();both(s);s.externalStarts.clear();both(s);}
    void interrupted() {
        auto s=sample();DVRP_RmpController rmp(s);DVRP_PricingController p(s);
        const auto lp=rmp.solveLp();check(lp.duals.has_value(),"initial dual");
        for(auto backend:{PricingBackend::PlainLabeling,PricingBackend::BucketGraph}) {
            PricingOptions o;o.backend=backend;o.timeLimitSeconds=0;
            const auto result=p.price(*lp.duals,o);
            check(result.status==PricingStatus::TimeLimit && result.candidates.empty() && result.starts.empty(),"interruption certificate");
            check(rmp.columns().empty(),"pricing mutated RMP");
            check(rmp.solveLp().modelRevision==lp.modelRevision,"pricing changed model version");
        }
        both(s);
    }
    void stress() {
        std::mt19937 gen(20261015);
        int feasible=0,infeasible=0;
        for(int t=0;t<100;++t) {
            auto s=sample();s.stageId=t+1;s.currentTime=double(gen()%20)/8.;
            s.originStart.startTime=s.currentTime;s.originStart.remainingCapacity=1+gen()%6;
            s.returnDepot.deadline=s.currentTime+(t%4?100:double(gen()%100)/8.);
            s.customers.clear();s.externalStarts.clear();
            const int n=gen()%6, external=gen()%6;
            for(int i=0;i<n;++i) {
                const double ready=double(gen()%40)/8.;
                s.customers.push_back({11+19*i,{double(gen()%80)/8.,double(gen()%80)/8.},
                    double(gen()%(1+int(s.originStart.remainingCapacity))),ready,
                    ready+(t%3?100:double(gen()%40)/8.),double(gen()%12)/8.});
            }
            for(int k=0;k<external;++k)s.externalStarts.push_back({11+19*k,StartKind::External,900+k,
                {double(gen()%40)/8.,double(gen()%40)/8.},s.currentTime+double(gen()%80)/8.,
                double(gen()%(1+int(s.originStart.remainingCapacity)))});
            try {
                const auto a=columnGeneration(s,PricingBackend::PlainLabeling,t%2);
                const auto b=columnGeneration(s,PricingBackend::BucketGraph,t%2);
                const auto mixed=columnGeneration(s,PricingBackend::PlainLabeling,t%2,true);
                check(a.phase==b.phase && a.phase==mixed.phase,"stress feasibility disagreement");
                close(*a.objective,*b.objective,"stress two backends");
                close(*a.objective,*mixed.objective,"stress alternating backend");
                if(a.phase==RmpPhase::Cost)++feasible;else ++infeasible;
            } catch(const std::exception& e) {
                throw std::runtime_error("CG seed=20261015 case="+std::to_string(t)+": "+e.what());
            }
        }
        check(feasible>0 && infeasible>0,"stress must cover feasible and infeasible full LPs");
        std::cout<<"stress: "<<feasible<<" feasible, "<<infeasible
                 <<" infeasible stages, 300 CG runs PASS\n";
    }
}
int main(int argc,char** argv) {
    const std::map<std::string,std::function<void()>> tests={{"basic",basic},{"initial",initial},{"infeasible",infeasible},
        {"fractional",fractional},{"random",randomCases},{"empty",empty},{"interrupted",interrupted},{"stress",stress}};
    try {if(argc==2)tests.at(argv[1])();else for(const auto& [name,f]:tests)f();return 0;}
    catch(const std::exception& e){std::cerr<<e.what()<<'\n';return 1;}
}

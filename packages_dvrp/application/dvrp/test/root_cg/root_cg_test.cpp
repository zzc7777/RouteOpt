#include "root_cg_controller.hpp"
#include "stage_reference.hpp"
#include "route_opt_macro.hpp"
#include <algorithm>
#include <cmath>
#include <functional>
#include <iostream>
#include <limits>
#include <random>
#include <stdexcept>

// Internal policies used by the production loop, deliberately not a public mock API.
namespace RouteOpt::Application::DVRP::RootCgDetail {
    double remainingTime(double,double);
    std::optional<RootCgStatus> classifyPricingStatus(PricingStatus);
    void requireColumnProgress(std::size_t,std::size_t);
    void validatePricingResult(const StageProblem&,const RmpLpResult&,const PricingResult&,PricingBackend);
    RootCgBound computeBound(const StageProblem&,const RmpLpResult&,const PricingResult&);
    RootCgStatus classifyBound(const RmpLpResult&,const RootCgBound&,double,double);
}
using namespace RouteOpt::Application::DVRP;
using namespace RouteOpt::Application::DVRP::Testing;
namespace {
    constexpr PricingBackend backends[]={PricingBackend::PlainLabeling,PricingBackend::BucketGraph};
    void check(bool ok,const std::string& why) { if(!ok) throw std::runtime_error(why); }
    void close(double a,double b,const std::string& why) { check(std::isfinite(a)&&std::isfinite(b)&&std::abs(a-b)<1e-6,why); }
    template<class F> void rejects(F action,const char* why) {
        bool threw=false; try {action();} catch(const std::exception&) {threw=true;} check(threw,why);
    }
    bool same(const StageRoute& a,const StageRoute& b) {return a.startKey==b.startKey && a.customerSequence==b.customerSequence;}
    StageProblem sample() {
        StageProblem s;s.stageId=7;s.currentTime=10;s.fixedCostAfterTau=7;
        s.originStart={0,StartKind::Origin,0,{0,0},10,2};s.returnDepot={{0,0},100};
        s.customers={{5,{2,0},1,0,100,0},{8,{4,0},1,0,100,0}};
        s.externalStarts={{42,StartKind::External,3,{3,0},10,2}};return s;
    }
    StageProblem fractionalStage() {
        auto s=sample();s.currentTime=0;s.originStart.startTime=0;s.externalStarts.clear();s.fixedCostAfterTau=0;
        s.customers={{1,{10,0},1,0,100,0},{2,{10,0},1,0,100,0},{3,{10,0},1,0,100,0}};return s;
    }
    void auditSnapshot(const StageProblem& s,const RootCgSnapshot& snap,const EnumeratedStage* all=nullptr) {
        const auto& lp=snap.lp; check(lp.stageId==s.stageId && lp.status==RmpLpStatus::OptimalRmp,"snapshot context");
        check(snap.columns.size()==lp.lambda.size(),"old lambda with new pool");
        std::map<int,double> cover,starts;double cost=0;
        for(const auto& c:s.customers)cover[c.id]=0;
        for(const auto& k:s.externalStarts)starts[k.startKey]=0;
        for(std::size_t i=0;i<snap.columns.size();++i) {
            const auto& c=snap.columns[i];check(c.id==i,"unstable column ID");
            if(all) {
                const auto it=std::find_if(all->routes().begin(),all->routes().end(),[&](const auto& r){return same(r.route,c.route);});
                check(it!=all->routes().end(),"root column outside independent feasible universe");close(c.cost,it->cost,"root cost");
            }
            check(lp.lambda[i]>=-1e-8,"negative lambda"); cost+=lp.lambda[i]*c.cost;
            for(int id:c.route.customerSequence)cover.at(id)+=lp.lambda[i];
            if(c.route.startKey)starts.at(c.route.startKey)+=lp.lambda[i];
        }
        if(lp.phase==RmpPhase::Cost) {
            check(lp.routeCost.has_value() && lp.totalPlannedCost.has_value(),"missing Phase-II costs");
            close(cost,*lp.routeCost,"route cost from snapshot");
            for(const auto& [id,x]:cover)close(x,1,"customer coverage");
            for(const auto& [id,x]:starts)close(x,1,"external equality");
            close(*lp.totalPlannedCost,cost+s.fixedCostAfterTau,"fixed cost once");
        } else {
            check(!lp.routeCost && !lp.totalPlannedCost,"Phase-I must not report distance objective");
        }
        if(snap.bound) {
            check(snap.bound->modelRevision==lp.modelRevision && snap.bound->phase==lp.phase,"stale bound snapshot");
            check(snap.bound->gap>=0,"bound inversion");
        }
    }
    RootCgResult run(const StageProblem& s,PricingBackend b,bool initial=true) {
        RootCgOptions o(b);o.addInitialRoutes=initial;
        return DVRP_RootCgController{}.solve(s,o);
    }
    RootCgResult compare(const StageProblem& s,PricingBackend b,bool initial) {
        const auto all=enumerateStage(s); const auto reference=solveFullLp(all);
        const auto r=run(s,b,initial);
        check(r.snapshot.has_value(),r.message);auditSnapshot(s,*r.snapshot,&all);
        check(r.snapshot->bound.has_value(),r.message);
        const auto& bound=*r.snapshot->bound;
        if(reference.status==LpReferenceStatus::Infeasible) {
            check(r.status==RootCgStatus::LpInfeasible,r.message);check(bound.lowerBound>1e-6,"false Phase-I certificate");
            DVRP_RmpController complete(s);std::vector<StageRoute> routes;
            for(const auto& c:all.routes())routes.push_back(c.route);
            complete.addRoutes(routes);const auto full=complete.solveLp();
            check(full.status==RmpLpStatus::OptimalRmp,full.message);
            check(bound.lowerBound<=*full.artificialSum+1e-8,"Phase-I lower bound above full objective");
        } else {
            check(reference.status==LpReferenceStatus::Optimal,reference.message);
            check(r.status==RootCgStatus::LpConverged,r.message);
            close(*r.snapshot->lp.routeCost,*reference.routeCost,"root LP != complete LP");
            check(bound.lowerBound<=*reference.routeCost+1e-8,"unsafe root lower bound");
            check(bound.gap<=1e-6+1e-8*std::max(1.0,std::abs(bound.rmpObjective)),"gap not closed");
            close(*bound.totalRmpObjective,*reference.totalPlannedCost,"bound fixed cost");
        }
        check(r.backend==b && r.stageId==s.stageId,"result identity");
        for(const auto& i:r.iterations) {
            check(i.columnsAfter==i.columnsBefore+i.addedColumns,"column accounting");
            check(i.lpTimeLimit<=60 && i.pricingTimeLimit<=60,"per-call budget");
            if(i.pricingStatus==PricingStatus::Complete)check(i.minimumReducedCosts.size()==s.externalStarts.size()+1,"complete start log");
        }
        return r;
    }
    void both(const StageProblem& s,bool initial=true) {for(auto b:backends)compare(s,b,initial);}
    void hand() {
        for(auto b:backends) {
            const auto r=compare(sample(),b,true);close(*r.snapshot->lp.routeCost,5,"hand5");
            close(*r.snapshot->lp.totalPlannedCost,12,"hand total12");
            check(r.iterations.front().phase==RmpPhase::Feasibility && !r.iterations.front().pricingStatus,
                  "zero artificials should switch without Phase-I pricing");
            check(r.iterations.back().pricingStatus==PricingStatus::Complete,"integer lambda must still be priced");
        }
    }
    void phases() {
        for(auto b:backends) {
            const auto r=compare(sample(),b,false);close(*r.iterations.front().artificialSum,3,"initial artificial mass");
            check(r.iterations.front().pricingStatus==PricingStatus::Complete && r.iterations.front().addedColumns>0,"Phase-I did not price");
            bool switched=false;
            for(std::size_t i=1;i<r.iterations.size();++i) {
                const auto& a=r.iterations[i-1];const auto& c=r.iterations[i];
                check(c.modelRevision>a.modelRevision,"phase/columns must revise model");
                if(a.phase==c.phase)check(*c.objective<=*a.objective+1e-6,"LP worsened after columns");
                else {switched=true;check(c.phase==RmpPhase::Cost && *a.artificialSum<=1e-6,"bad Phase switch");}
            }
            check(switched,"never entered Phase-II");
        }
    }
    void fractional() {
        auto s=fractionalStage();for(auto b:backends) {
            const auto r=compare(s,b,false);close(*r.snapshot->lp.routeCost,30,"LP30");
            check(std::any_of(r.snapshot->lp.lambda.begin(),r.snapshot->lp.lambda.end(),[](double v){return v>1e-6&&v<1-1e-6;}),"expected fractions");
            const auto fromSingletons=compare(s,b,true);
            bool plateau=false;
            for(std::size_t i=1;i<fromSingletons.iterations.size();++i) {
                const auto& prev=fromSingletons.iterations[i-1];const auto& now=fromSingletons.iterations[i];
                if(prev.phase==RmpPhase::Cost && now.phase==RmpPhase::Cost && prev.addedColumns>0
                    && std::abs(*prev.objective-*now.objective)<1e-8) plateau=true;
            }
            check(plateau,"degenerate objective plateau was not exercised");
        }
        const auto mip=solveFullMip(enumerateStage(s));check(mip.solution.status==SolveStatus::Optimal,mip.solution.message);
        close(*mip.routeCost,40,"MIP40");std::cout<<"production root LP=30, independent MIP=40\n";
    }
    void infeasible() {
        auto s=sample();s.customers[0].dueDate=0;both(s);both(s,false);
        s=sample();s.customers.clear();s.returnDepot.deadline=1;both(s);
        s.currentTime=0;s.originStart.startTime=0;s.customers={{5,{0.55,0},1,0,100,0}};
        s.externalStarts={{42,StartKind::External,99,{1.1,0},0,2},{43,StartKind::External,98,{1.1,0},0,2}};
        both(s);both(s,false); // Two required starts can return only via the ONE customer.
    }
    void empty() {
        auto s=sample();s.customers.clear();both(s);both(s,false);
        s.externalStarts.clear();both(s);both(s,false);
        const auto r=run(s,backends[0]);check(r.snapshot->columns.empty(),"empty origin route was invented");
        close(r.snapshot->bound->lowerBound,0,"analytic empty bound");
    }
    void randomCases() {
        std::mt19937 gen(20261025);
        for(int trial=0;trial<100;++trial) {
            auto s=sample();s.stageId=1000+trial;s.currentTime=s.originStart.startTime=0;
            s.originStart.remainingCapacity=2+gen()%4;s.customers.clear();s.externalStarts.clear();
            s.returnDepot.deadline=trial%7?80:8;s.fixedCostAfterTau=trial%9;
            for(int i=0,n=gen()%6;i<n;++i) {
                const double ready=gen()%8;
                s.customers.push_back({10+3*i,{double(gen()%9),double(gen()%9)},double(gen()%3),ready,ready+double(2+gen()%40),double(gen()%3)});
            }
            for(int k=0,n=gen()%3;k<n;++k)
                s.externalStarts.push_back({900+7*k,StartKind::External,800+k,{double(gen()%8),double(gen()%8)},double(gen()%5),double(gen()%3)});
            both(s,trial%2==0);
        }
        std::cout<<"100 fixed-seed stages, 200 production root solves + independent complete LP audits\n";
    }
    struct PricedLp {RmpLpResult lp;PricingResult price;};
    PricedLp priced(const StageProblem& s,RmpPhase phase=RmpPhase::Cost) {
        DVRP_RmpController r(s);
        if(phase==RmpPhase::Cost) {r.addInitialRoutes();check(r.solveLp().status==RmpLpStatus::OptimalRmp,"prepare LP");r.enterCostPhase();}
        auto lp=r.solveLp();check(lp.status==RmpLpStatus::OptimalRmp,lp.message);
        auto p=DVRP_PricingController(s).price(*lp.duals,PricingOptions{});
        check(p.status==PricingStatus::Complete,p.message);return {lp,p};
    }
    void bounds() {
        for(auto phase:{RmpPhase::Feasibility,RmpPhase::Cost}) {
            const auto s=sample();const auto x=priced(s,phase);
            const auto b=RootCgDetail::computeBound(s,x.lp,x.price);const auto all=enumerateStage(s);
            double minimum=0;
            for(const auto& c:all.routes()) {
                double rc=phase==RmpPhase::Cost?c.cost:0;
                for(int id:c.route.customerSequence)rc-=x.lp.duals->customers.at(id);
                if(c.route.startKey)rc-=x.lp.duals->externalStarts.at(c.route.startKey);
                minimum=std::min(minimum,rc);
                check(rc>=b.minimumReducedCost-b.rcRoundingGuard-1e-9,"rc correction missed a full-universe column");
            }
            if(phase==RmpPhase::Feasibility) {
                for(const auto& [id,y]:x.lp.duals->customers)minimum=std::min(minimum,1-y);
                for(const auto& [id,y]:x.lp.duals->externalStarts)minimum=std::min(minimum,1-y);
                check(b.lowerBound<=0 && !b.totalLowerBound,"Phase-I is not distance");
            } else check(b.lowerBound<=*solveFullLp(all).routeCost,"bound versus full LP");
            close(b.minimumReducedCost,minimum,"delta across all columns");
            check(b.rcRoundingGuard>0 && b.sumRoundingGuard>0,"unrecorded rounding guards");
        }
    }
    void numeric() {
        auto s=sample();s.originStart.remainingCapacity=1;s.customers.clear();s.externalStarts.clear();
        for(int i=1;i<=200;++i)s.customers.push_back({i,{1,0},1,0,100,0});
        auto x=priced(s);x.lp.duals->customers.at(1)+=5e-6;
        x.price=DVRP_PricingController(s).price(*x.lp.duals,PricingOptions{});
        check(x.price.status==PricingStatus::Complete && x.price.candidates.empty(),"near-threshold setup");
        const auto b=RootCgDetail::computeBound(s,x.lp,x.price);
        close(b.minimumReducedCost,-5e-6,"tiny negative not erased");
        check(b.dualSum-b.lowerBound>=0.001 && b.gap>0.00099,"200-row correction lost");
        check(RootCgDetail::classifyBound(x.lp,b,1e-6,1e-8)==RootCgStatus::NumericalUnresolved,"false threshold convergence");
        // Phase-I artificial rc must be included even when no real route exists.
        auto one=sample();one.customers.resize(1);one.customers[0].dueDate=0;one.externalStarts.clear();
        auto p=priced(one,RmpPhase::Feasibility);p.lp.duals->customers.begin()->second=1+1e-7;
        p.price=DVRP_PricingController(one).price(*p.lp.duals,PricingOptions{});
        const auto a=RootCgDetail::computeBound(one,p.lp,p.price);
        close(a.minimumReducedCost,-1e-7,"artificial rc omitted");check(a.lowerBound<=1,"unsafe Phase-I bound");
        auto weak=a;weak.lowerBound=0;weak.gap=1;
        check(RootCgDetail::classifyBound(p.lp,weak,1e-6,1e-8)==RootCgStatus::NumericalUnresolved,"unproven infeasibility");
        x.lp.duals->customers.at(1)=std::numeric_limits<double>::quiet_NaN();
        rejects([&]{RootCgDetail::computeBound(s,x.lp,x.price);},"NaN certificate accepted");
        auto bad=a;bad.gap=-1;rejects([&]{RootCgDetail::classifyBound(p.lp,bad,1e-6,1e-8);},"negative gap hidden");
        auto mismatch=priced(sample());mismatch.lp.objective=100;
        rejects([&]{RootCgDetail::computeBound(sample(),mismatch.lp,mismatch.price);},"objective/dual disagreement");
    }
    void context() {
        const auto s=sample();const auto x=priced(s);
        const auto validate=[&](const PricingResult& p){RootCgDetail::validatePricingResult(s,x.lp,p,PricingBackend::PlainLabeling);};
        validate(x.price);
        auto bad=[&](auto change){auto p=x.price;change(p);rejects([&]{validate(p);},"bad pricing context accepted");};
        bad([](auto& p){++p.stageId;});bad([](auto& p){++p.modelRevision;});
        bad([](auto& p){p.phase=RmpPhase::Feasibility;});bad([](auto& p){p.backend=PricingBackend::BucketGraph;});
        bad([](auto& p){p.status=PricingStatus::TimeLimit;});bad([](auto& p){p.starts.pop_back();});
        bad([](auto& p){p.starts.push_back(p.starts.front());});bad([](auto& p){p.starts.front().startKey=99;});
        bad([](auto& p){p.starts.front().bestRoute.reset();});bad([](auto& p){*p.starts.front().minimumReducedCost+=1;});
        bad([](auto& p){*p.starts.front().minimumReducedCost+=1e-7;});
        bad([](auto& p){p.starts.front().bestRoute->cost+=1;});bad([](auto& p){p.candidates.clear();});
        bad([](auto& p){p.candidates.push_back(p.candidates.front());});
        bad([](auto& p){p.candidates.front().route.customerSequence.push_back(999);});
    }
    void duplicate() {
        DVRP_RmpController r(sample());r.addRoutes({{42,{8,5}}});const auto before=r.columns().size();
        r.addRoutes({{42,{8,5}}});rejects([&]{RootCgDetail::requireColumnProgress(before,r.columns().size());},"duplicate negative not stopped");
        RootCgDetail::requireColumnProgress(before,before+1);
    }
    void limits() {
        const auto s=sample();
        for(auto b:backends) {
            RootCgOptions o(b);o.timeLimitSeconds=0;
            auto r=DVRP_RootCgController{}.solve(s,o);check(r.status==RootCgStatus::TimeLimit && !r.snapshot,"zero total budget");
            o.timeLimitSeconds=60;o.maxIterations=0;
            r=DVRP_RootCgController{}.solve(s,o);check(r.status==RootCgStatus::IterationLimit && !r.snapshot,"zero iterations");
            o.maxIterations=1;o.addInitialRoutes=false;
            r=DVRP_RootCgController{}.solve(s,o);check(r.status==RootCgStatus::IterationLimit && r.snapshot,"iteration stop snapshot");
            check(r.snapshot->columns.empty() && r.snapshot->lp.lambda.empty() && r.iterations.back().addedColumns>0,"mixed old/new snapshot");
            o.maxIterations=100;o.pricing.timeLimitSeconds=0;
            r=DVRP_RootCgController{}.solve(s,o);check(r.status==RootCgStatus::TimeLimit && r.snapshot && !r.snapshot->bound,"pricing zero time");
            check(r.iterations.back().minimumReducedCosts.empty(),"partial minima consumed");
            o.pricing.timeLimitSeconds=60;o.pricing.maxLabels=0;
            r=DVRP_RootCgController{}.solve(s,o);check(r.status==RootCgStatus::ResourceLimit && !r.snapshot->bound,"label resource stop");
        }
        RootCgOptions o(PricingBackend::BucketGraph);o.pricing.maxGraphNodes=1;o.addInitialRoutes=false;
        check(DVRP_RootCgController{}.solve(s,o).status==RootCgStatus::ResourceLimit,"graph resource stop");
        close(RootCgDetail::remainingTime(60,20),40,"budget not shared");close(RootCgDetail::remainingTime(60,70),0,"negative remaining");
        o=RootCgOptions(backends[0]);o.rmp.timeLimitSeconds=30;o.pricing.timeLimitSeconds=20;
        const auto limited=DVRP_RootCgController{}.solve(s,o);check(limited.status==RootCgStatus::LpConverged,limited.message);
        for(const auto& log:limited.iterations)check(log.lpTimeLimit<=30 && log.pricingTimeLimit<=20,"single-call caps ignored");
    }
    void status() {
        check(!RootCgDetail::classifyPricingStatus(PricingStatus::Complete),"complete mapping");
        check(RootCgDetail::classifyPricingStatus(PricingStatus::TimeLimit)==RootCgStatus::TimeLimit,"time mapping");
        check(RootCgDetail::classifyPricingStatus(PricingStatus::ResourceLimit)==RootCgStatus::ResourceLimit,"resource mapping");
        check(RootCgDetail::classifyPricingStatus(PricingStatus::Error)==RootCgStatus::Error,"error mapping");
        check(RootCgDetail::classifyPricingStatus(static_cast<PricingStatus>(-123))==RootCgStatus::Error,"unknown mapping");
    }
    void validation() {
        RootCgOptions o(backends[0]);auto s=sample();
        for(double bad:{-1.0,std::numeric_limits<double>::infinity(),std::numeric_limits<double>::quiet_NaN()}) {
            auto c=o;c.timeLimitSeconds=bad;check(DVRP_RootCgController{}.solve(s,c).status==RootCgStatus::Error,"invalid total limit");
            c=o;c.relativeGapTolerance=bad;check(DVRP_RootCgController{}.solve(s,c).status==RootCgStatus::Error,"invalid gap tolerance");
        }
        o.pricing.backend=static_cast<PricingBackend>(-1);check(DVRP_RootCgController{}.solve(s,o).status==RootCgStatus::Error,"invalid backend");
        o=RootCgOptions(backends[1]);o.pricing.bucketCount=0;check(DVRP_RootCgController{}.solve(s,o).status==RootCgStatus::Error,"invalid buckets");
        o=RootCgOptions(backends[0]);s.customers.push_back(s.customers[0]);
        check(DVRP_RootCgController{}.solve(s,o).status==RootCgStatus::Error,"duplicate customer accepted");
    }
    void lifecycle() {
        DVRP_RootCgController controller;RootCgOptions o(backends[0]);auto s=sample();
        const auto first=controller.solve(s,o);check(first.status==RootCgStatus::LpConverged,first.message);
        for(int i=0;i<12;++i) {
            s=sample();s.stageId=i+40;s.externalStarts.clear();s.fixedCostAfterTau=i;
            const auto r=controller.solve(s,o);check(r.status==RootCgStatus::LpConverged && r.stageId==s.stageId,r.message);
            close(*r.snapshot->lp.routeCost,8,"new stage contaminated");check(s.customers[0].id==5 && s.customers.size()==2,"input changed");
        }
        close(*first.snapshot->lp.routeCost,5,"old result invalid after controller reuse");auditSnapshot(sample(),*first.snapshot);
    }
    void size() {
        auto s=sample();s.originStart.remainingCapacity=1;s.externalStarts.clear();s.customers.clear();
        for(int i=0;i<20;++i)s.customers.push_back({100+2*i,{1,0},1,0,100,0});
        for(int k=0;k<6;++k)s.externalStarts.push_back({200+k,StartKind::External,900+k,{0,0},10,0});
        for(auto b:backends) {
            const auto r=run(s,b);check(r.status==RootCgStatus::LpConverged,r.message);auditSnapshot(s,*r.snapshot);
            close(*r.snapshot->lp.routeCost,40,"production has no 5-customer/5-start limit");
        }
    }
}
int main(int argc,char** argv) {
    const std::vector<std::pair<std::string,std::function<void()>>> cases={
        {"hand",hand},{"phases",phases},{"fractional",fractional},{"infeasible",infeasible},
        {"empty",empty},{"random",randomCases},{"bounds",bounds},{"numeric",numeric},{"context",context},
        {"duplicate",duplicate},{"limits",limits},{"status",status},{"validation",validation},{"lifecycle",lifecycle},{"size",size}};
    try {
        bool ran=false;for(const auto& [name,test]:cases)if(argc==1 || name==argv[1]){test();ran=true;std::cout<<"PASS root CG "<<name<<'\n';}
        check(ran,"unknown test case");return 0;
    } catch(const std::exception& e){std::cerr<<e.what()<<'\n';return 1;}
}

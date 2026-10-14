#include "rmp_controller.hpp"
#include "stage_reference.hpp"
#include "solver.hpp"
#include <algorithm>
#include <cmath>
#include <functional>
#include <iostream>
#include <limits>
#include <numeric>
#include <random>
#include <stdexcept>

namespace RouteOpt::Application::DVRP::RmpDetail {
    RmpLpStatus classifyLpStatus(int rawStatus); // Internal production policy, no fake solver.
}

using namespace RouteOpt::Application::DVRP;
using namespace RouteOpt::Application::DVRP::Testing;

namespace {
    void check(bool ok, const std::string& message) {
        if (!ok) throw std::runtime_error(message);
    }
    void close(double a, double b, const std::string& message) {
        check(std::isfinite(a) && std::isfinite(b) && std::abs(a-b) <= 1e-6, message);
    }
    template<class F> void rejects(F operation, const std::string& message) {
        bool rejected = false;
        try { operation(); } catch (const std::exception&) { rejected = true; }
        check(rejected,message);
    }
    RmpLpResult optimal(DVRP_RmpController& rmp) {
        auto r = rmp.solveLp();
        check(r.status == RmpLpStatus::OptimalRmp,r.message);
        check(r.objective && r.artificialSum && r.duals,"missing optimal LP data");
        if (r.phase==RmpPhase::Cost) check(r.routeCost && r.totalPlannedCost,"missing cost-phase objectives");
        else check(!r.routeCost && !r.totalPlannedCost,"Phase-I exposed a distance objective");
        check(r.duals->stageId == r.stageId && r.duals->modelRevision == r.modelRevision
              && r.duals->phase == r.phase,"dual context mismatch");
        return r;
    }
    double coefficient(const RmpSnapshotColumn& c, int row) {
        for (const auto& [h,value] : c.coefficients) if (h == row) return value;
        return 0;
    }
    StageProblem sample() {
        StageProblem s;
        s.stageId = 7; s.instanceName = "rmp-hand-check"; s.currentTime = 10;
        s.originStart = {0,StartKind::Origin,0,{0,0},10,2};
        s.returnDepot = {{0,0},100};
        s.customers = {{5,{2,0},1,0,100,0}, {8,{4,0},1,0,100,0}};
        s.externalStarts = {{42,StartKind::External,3,{3,0},10,2}};
        return s;
    }
    void phases() {
        DVRP_RmpController rmp(sample());
        rejects([&]{rmp.enterCostPhase();},"switch before solve");
        close(*optimal(rmp).artificialSum,3,"initial artificial mass");
        rejects([&]{rmp.enterCostPhase();},"switch with positive artificial");
        rmp.addRoutes({{0,{5}}});
        close(*optimal(rmp).artificialSum,2,"first customer");
        rmp.addRoutes({{42,{}}});
        close(*optimal(rmp).artificialSum,1,"external return");
        rmp.addRoutes({{42,{8}}});
        auto feasible = optimal(rmp);
        close(*feasible.artificialSum,0,"feasibility restored");
        check(!feasible.routeCost && !feasible.totalPlannedCost,"Phase-I must not report distance objective");
        rmp.enterCostPhase();
        auto before = optimal(rmp);
        close(*before.routeCost,9,"restricted cost");
        check(before.modelRevision > feasible.modelRevision,"phase switch must invalidate old result");
        rejects([&]{rmp.enterCostPhase();},"double phase switch");
        rmp.addRoutes({{42,{8,5}}});
        auto after = optimal(rmp);
        close(*after.routeCost,5,"improved cost");
        check(after.modelRevision > before.modelRevision,"new columns must invalidate old result");
        const auto model = rmp.inspectModel();
        for (int h=0; h<3; ++h) close(model.columns[h].upper,0,"artificial must stay fixed");
        close(model.columns.back().objective,5,"new cost-phase column must have real objective");
        std::cout << "artificial=3,2,1,0 routeCost=9,5\n";
    }

    void matrix() {
        DVRP_RmpController rmp(sample());
        auto s = rmp.inspectModel();
        check(s.continuousLp && s.rows.size()==3 && s.columns.size()==3,"root dimensions/types");
        check(s.rows[0].key==5 && s.rows[1].key==8 && s.rows[2].key==42,"row order");
        check(s.rows[2].kind==RmpRowKind::ExternalStart,"external row kind");
        for (int h=0; h<3; ++h) {
            close(s.rhs[h],1,"RHS"); check(s.senses[h]=='E',"equality");
            const auto& a=s.columns[h];
            check(!a.routeId && a.coefficients.size()==1,"artificial column identity");
            close(a.objective,1,"Phase-I artificial cost"); close(a.lower,0,"artificial lower");
            check(a.upper>1e10,"artificial upper bound");
            for (int k=0; k<3; ++k) close(coefficient(a,k),h==k ? 1 : 0,"artificial identity matrix");
        }
        rmp.addRoutes({{0,{5}},{42,{}},{42,{8}},{42,{8,5}}});
        s = rmp.inspectModel();
        const int expected[4][3]={{1,0,0},{0,0,1},{0,1,1},{1,1,1}};
        const double costs[4]={4,3,5,5};
        for (int c=0; c<4; ++c) {
            const auto& column=s.columns[3+c];
            check(column.routeId==static_cast<std::size_t>(c),"stable ID vs solver index");
            for(int h=0;h<3;++h) close(coefficient(column,h),expected[c][h],"real column coefficient");
            close(column.objective,0,"Phase-I real cost weight zero");
            close(column.lower,0,"route lower"); check(column.upper>1e10,"no redundant upper=1");
            close(rmp.columns()[c].cost,costs[c],"real cost cache");
        }
        optimal(rmp); rmp.enterCostPhase();
        s = rmp.inspectModel();
        for (int c=0;c<4;++c) close(s.columns[3+c].objective,costs[c],"Phase-II cost restoration");
    }

    std::vector<StageRoute> routesOf(const EnumeratedStage& all) {
        std::vector<StageRoute> routes;
        for (const auto& r:all.routes()) routes.push_back(r.route);
        return routes;
    }
    double reducedCost(const EnumeratedRoute& r, const RmpLpResult& lp) {
        double value=lp.phase==RmpPhase::Cost ? r.cost : 0;
        for(int id:r.route.customerSequence) value-=lp.duals->customers.at(id);
        if(r.route.startKey) value-=lp.duals->externalStarts.at(r.route.startKey);
        return value;
    }
    void certifyAgainstAll(const EnumeratedStage& all, const RmpLpResult& lp) {
        for(const auto& route:all.routes()) check(reducedCost(route,lp)>=-1e-6,"negative RC in full universe");
        const auto full=solveFullLp(all);
        if(lp.phase==RmpPhase::Feasibility) {
            check(*lp.artificialSum>1e-6 && full.status==LpReferenceStatus::Infeasible,"full Phase-I infeasibility disagreement");
        } else {
            check(full.status==LpReferenceStatus::Optimal,full.message);
            close(*lp.routeCost,*full.routeCost,"full LP route cost");
            close(*lp.totalPlannedCost,*full.totalPlannedCost,"full LP total cost");
        }
    }
    RmpLpResult solveWithAll(const StageProblem& stage) {
        const auto all=enumerateStage(stage);
        DVRP_RmpController rmp(stage);
        rmp.addRoutes(routesOf(all));
        for (std::size_t i=0;i<all.routes().size();++i) close(rmp.columns()[i].cost,all.routes()[i].cost,"independent route cost");
        auto lp=optimal(rmp);
        if(*lp.artificialSum<=1e-6) { rmp.enterCostPhase(); lp=optimal(rmp); }
        certifyAgainstAll(all,lp);
        return lp;
    }
    RmpLpResult scanPricing(const StageProblem& stage, bool initial) {
        const auto all=enumerateStage(stage);
        DVRP_RmpController rmp(stage);
        if(initial) rmp.addInitialRoutes();
        for(std::size_t iter=0;iter<all.routes().size()+4;++iter) {
            auto lp=optimal(rmp);
            if(lp.phase==RmpPhase::Feasibility && *lp.artificialSum<=1e-6) { rmp.enterCostPhase(); continue; }
            std::vector<StageRoute> negative;
            for(const auto& r:all.routes()) if(reducedCost(r,lp)<-1e-6) negative.push_back(r.route);
            if(negative.empty()) { certifyAgainstAll(all,lp); return lp; }
            const auto before=rmp.columns().size();
            rmp.addRoutes(negative);
            check(rmp.columns().size()>before,"pricing made no progress / existing negative column");
        }
        throw std::runtime_error("test pricing did not close finite universe");
    }
    void initial() {
        DVRP_RmpController rmp(sample());
        check(rmp.addInitialRoutes().size()==5 && rmp.columns().size()==5,"initial singleton/return routes");
        for(const auto& c:rmp.columns()) check(c.route.customerSequence.size()<=1,"unexpected multi-customer seed");
        close(*optimal(rmp).artificialSum,0,"initial feasible LP");
        rmp.enterCostPhase(); close(*optimal(rmp).routeCost,9,"initial cost");
        const auto version=rmp.inspectModel().modelRevision;
        rmp.addInitialRoutes();
        check(rmp.columns().size()==5 && rmp.inspectModel().modelRevision==version,"initial routes idempotent");
        auto s=sample(); s.externalStarts[0].remainingCapacity=0;
        DVRP_RmpController emptyLoad(s);
        check(emptyLoad.addInitialRoutes().size()==3,"zero remaining capacity still allows empty return");
    }
    void resources() {
        auto s=sample(); s.externalStarts[0].remainingCapacity=1;
        DVRP_RmpController capacity(s);
        rejects([&]{capacity.addRoutes({{42,{5,8}}});},"external capacity");
        capacity.addRoutes({{0,{5,8}}}); // Origin uses its own capacity.
        s=sample(); s.customers[1].dueDate=20; s.externalStarts[0].startTime=30;
        DVRP_RmpController time(s);
        rejects([&]{time.addRoutes({{42,{8}}});},"external start time");
        time.addRoutes({{0,{8}}});
        s=sample(); s.customers[0].readyTime=20; s.customers[0].dueDate=20; s.customers[0].serviceTime=7;
        s.returnDepot.deadline=29;
        DVRP_RmpController boundary(s); boundary.addRoutes({{0,{5}}}); // wait to20, finish27, return29
        s.returnDepot.deadline=28.9;
        DVRP_RmpController late(s); rejects([&]{late.addRoutes({{0,{5}}});},"waiting/service/return propagation");
        s=sample(); s.customers[0].dueDate=12-0.5e-6;
        DVRP_RmpController tolerance(s); tolerance.addRoutes({{0,{5}}});
        s.customers[0].dueDate=12-2e-6;
        DVRP_RmpController missed(s); rejects([&]{missed.addRoutes({{0,{5}}});},"time-window tolerance");
        // Truncation permits an indirect return that is shorter than a direct one.
        s=sample(); s.currentTime=0; s.originStart.startTime=0; s.returnDepot.deadline=1;
        s.customers={{5,{0.55,0},1,0,100,0}};
        s.externalStarts={{42,StartKind::External,3,{1.1,0},0,2}};
        DVRP_RmpController nonmetric(s);
        rejects([&]{nonmetric.addRoutes({{42,{}}});},"direct return must be late");
        nonmetric.addInitialRoutes();
        check(std::any_of(nonmetric.columns().begin(),nonmetric.columns().end(),[](const auto& c){return c.route.startKey==42;}),"failed direct return must not suppress singleton");
        close(*solveWithAll(s).routeCost,1,"nonmetric feasible continuation");
    }
    void validation() {
        auto s=sample();
        auto invalid=[&](StageProblem bad){rejects([&]{DVRP_RmpController r(bad);},"invalid stage accepted");};
        s.customers.push_back(s.customers.front()); invalid(s);
        s=sample(); s.externalStarts.push_back(s.externalStarts.front()); invalid(s);
        s=sample(); s.externalStarts[0].locationCustomerId=5; invalid(s);
        s=sample(); s.externalStarts[0].startTime=9; invalid(s);
        s=sample(); s.originStart.startTime=9; invalid(s);
        s=sample(); s.originStart.remainingCapacity=1.5; invalid(s);
        s=sample(); s.customers[0].demand=3; invalid(s);
        s=sample(); s.externalStarts[0].remainingCapacity=-1; invalid(s);
        s=sample(); s.customers[0].location.x=std::numeric_limits<double>::quiet_NaN(); invalid(s);
        s=sample(); s.returnDepot.location.x=1; invalid(s);
        s=sample(); s.fixedCostAfterTau=-1; invalid(s);
        DVRP_RmpController rmp(sample());
        for(const auto& route:std::vector<StageRoute>{{0,{}},{43,{5}},{42,{99}},{42,{5,5}}})
            rejects([&]{rmp.addRoutes({route});},"invalid route accepted");
        auto before=rmp.inspectModel();
        rejects([&]{rmp.addRoutes({{0,{5}},{42,{999}}});},"whole invalid batch");
        auto after=rmp.inspectModel();
        check(after.modelRevision==before.modelRevision && after.columns.size()==before.columns.size(),"invalid batch partly modified LP");
        close(*optimal(rmp).artificialSum,3,"valid model survives bad input");
        rejects([&]{DVRP_RmpController r(sample(),{-1});},"negative time limit");
        s=sample(); s.customers[0].location.x=std::numeric_limits<double>::max();
        DVRP_RmpController overflow(s);
        rejects([&]{overflow.addRoutes({{0,{5}}});},"finite coordinates overflow distance arithmetic");
        close(*optimal(overflow).artificialSum,3,"arithmetic input rejection must not partly add a column");
    }
    void identity() {
        auto s=sample(); auto second=s.externalStarts[0]; second.startKey=5; second.locationCustomerId=4;
        s.externalStarts.push_back(second);
        DVRP_RmpController rmp(s); rmp.addRoutes({{5,{5}},{42,{5}}});
        const auto snap=rmp.inspectModel();
        check(snap.rows.size()==4,"customer and external key collision");
        close(coefficient(snap.columns[4],0),1,"customer 5 coefficient");
        close(coefficient(snap.columns[4],3),1,"external 5 coefficient");
        close(coefficient(snap.columns[4],2),0,"other colocated external not covered");
        solveWithAll(s);
    }
    void duplicate() {
        DVRP_RmpController rmp(sample());
        auto ids=rmp.addRoutes({{42,{5,8}},{42,{5,8}},{42,{8,5}},{0,{5,8}}});
        check(ids==std::vector<std::size_t>({0,0,1,2}),"dedup by start AND ordered sequence");
        optimal(rmp);
        const auto before=rmp.inspectModel();
        check(rmp.addRoutes({{42,{5,8}}})==std::vector<std::size_t>{0},"reuse stable ID");
        check(rmp.inspectModel().modelRevision==before.modelRevision,"no-op duplicate invalidates result");
        rmp.enterCostPhase(); optimal(rmp);
        DVRP_RmpController stale(sample()); stale.addInitialRoutes(); optimal(stale);
        stale.addRoutes({{0,{5,8}}});
        rejects([&]{stale.enterCostPhase();},"stale Phase-I result authorized switch");
        optimal(stale); stale.enterCostPhase(); optimal(stale);
    }
    void empty() {
        auto s=sample(); s.customers.clear(); s.externalStarts.clear(); s.fixedCostAfterTau=7;
        DVRP_RmpController rmp(s);
        check(rmp.addInitialRoutes().empty(),"empty seeds");
        auto r=optimal(rmp); check(!r.rawStatus,"empty case faked solver status");
        close(*r.artificialSum,0,"empty artificial mass"); rmp.enterCostPhase();
        r=optimal(rmp); close(*r.routeCost,0,"empty cost"); close(*r.totalPlannedCost,7,"empty fixed cost");
        s.externalStarts=sample().externalStarts;
        auto second=s.externalStarts[0]; second.startKey=43; second.locationCustomerId=4; s.externalStarts.push_back(second);
        auto result=solveWithAll(s); close(*result.routeCost,6,"two external empty returns");
        s.returnDepot.deadline=12;
        auto bad=solveWithAll(s); close(*bad.artificialSum,2,"two missing return columns");
    }
    void infeasible() {
        auto s=sample(); s.customers[0].dueDate=0;
        auto lp=solveWithAll(s); check(lp.phase==RmpPhase::Feasibility,"uncovered customer should remain Phase-I");
        // Each customer has a feasible external singleton, but one vehicle cannot select both.
        s=sample(); s.customers[0].location={20,0}; s.customers[1].location={21,0};
        for(auto& c:s.customers) c.dueDate=15;
        s.externalStarts[0].location={20,0}; s.externalStarts[0].remainingCapacity=1;
        const auto all=enumerateStage(s);
        check(all.routes().size()==3,"conflict fixture must have return plus two singleton columns");
        lp=solveWithAll(s); close(*lp.artificialSum,1,"each row has a column but no joint solution");
        scanPricing(s,false);
    }
    void fullLp() {
        auto s=sample(); close(*solveWithAll(s).routeCost,5,"hand full LP");
        s.fixedCostAfterTau=7; auto fixed=solveWithAll(s); close(*fixed.totalPlannedCost,12,"fixed cost once");
        std::mt19937 gen(20261014);
        for(int trial=0;trial<12;++trial) {
            s=sample(); s.currentTime=0; s.originStart.startTime=0; s.originStart.remainingCapacity=2+gen()%3;
            s.customers.clear(); s.externalStarts.clear();
            const int n=1+trial%5;
            for(int i=0;i<n;++i) s.customers.push_back({10+3*i,{double(gen()%9),double(gen()%9)},1,0,100,0});
            for(int k=0;k<trial%3;++k) s.externalStarts.push_back({101+k,StartKind::External,999+k,{double(gen()%5),0},double(gen()%5),double(gen()%3)});
            s.fixedCostAfterTau=trial; solveWithAll(s);
        }
        s=sample(); s.currentTime=0; s.originStart.startTime=0; s.originStart.remainingCapacity=10;
        s.customers.clear(); s.externalStarts.clear();
        for(int i=1;i<=5;++i) {
            s.customers.push_back({i,{double(i),0},1,0,100,0});
            s.externalStarts.push_back({100+i,StartKind::External,200+i,{double(i),1},0,10});
        }
        check(enumerateStage(s).routes().size()==1955,"full reference limit fixture");
        solveWithAll(s);
    }
    StageProblem fractionalStage() {
        auto s=sample(); s.currentTime=0; s.originStart.startTime=0; s.externalStarts.clear(); s.customers.clear();
        for(int i=1;i<=3;++i) s.customers.push_back({i,{10,0},1,0,100,0});
        return s;
    }
    void fractional() {
        const auto s=fractionalStage();
        const auto lp=solveWithAll(s);
        close(*lp.routeCost,30,"LP 30, not MIP 40");
        check(std::any_of(lp.lambda.begin(),lp.lambda.end(),[](double x){return x>1e-6 && x<1-1e-6;}),"expected fractional LP");
        const auto mip=solveFullMip(enumerateStage(s));
        check(mip.solution.status==SolveStatus::Optimal,mip.solution.message); close(*mip.routeCost,40,"reference integer optimum");
        std::cout<<"LP=30 MIP=40\n";
    }
    void pricing() {
        close(*scanPricing(sample(),false).routeCost,5,"scan from only artificials");
        close(*scanPricing(sample(),true).routeCost,5,"scan from singleton pool");
        close(*scanPricing(fractionalStage(),true).routeCost,30,"scan to fractional optimum");
        auto s=sample(); s.externalStarts[0].remainingCapacity=1; solveWithAll(s); scanPricing(s,false);
    }
    void size() {
        auto s=sample(); s.originStart.remainingCapacity=1; s.customers.clear(); s.externalStarts.clear();
        for(int i=1;i<=200;++i) s.customers.push_back({i,{1,0},1,0,100,0});
        for(int k=1;k<=20;++k) s.externalStarts.push_back({1000+k,StartKind::External,9000+k,{0,0},10,0});
        DVRP_RmpController rmp(s);
        close(*optimal(rmp).artificialSum,220,"200+20 artificial variables");
        check(rmp.inspectModel().rows.size()==220,"not 220 additional constraints");
        check(rmp.addInitialRoutes().size()==220,"200 singletons plus20 returns");
        optimal(rmp); rmp.enterCostPhase(); close(*optimal(rmp).routeCost,400,"no reference-tool size cap in production");
    }
    void lifecycle() {
        auto s=sample(); DVRP_RmpController owned(s);
        s.customers[0].location.x=999; s.externalStarts.clear(); // Must not mutate the owned snapshot.
        owned.addRoutes({{42,{8,5}}}); optimal(owned); owned.enterCostPhase(); close(*optimal(owned).routeCost,5,"owned stage snapshot");
        for(int i=0;i<20;++i) {
            auto stage=sample(); stage.stageId=i;
            DVRP_RmpController rmp(stage); rmp.addInitialRoutes(); auto r=optimal(rmp);
            check(r.stageId==static_cast<std::size_t>(i),"stage identity isolation");
            rmp.enterCostPhase(); close(*optimal(rmp).routeCost,9,"repeat model lifecycle");
        }
    }
    void status() {
        using RmpDetail::classifyLpStatus;
        check(classifyLpStatus(CPX_STAT_OPTIMAL)==RmpLpStatus::OptimalRmp,"optimal mapping");
        check(classifyLpStatus(CPX_STAT_ABORT_TIME_LIM)==RmpLpStatus::TimeLimit,"time limit mapping");
        check(classifyLpStatus(CPX_STAT_ABORT_DETTIME_LIM)==RmpLpStatus::TimeLimit,"deterministic time limit mapping");
        for(int raw:{CPX_STAT_INFEASIBLE,CPX_STAT_UNBOUNDED,CPX_STAT_INForUNBD,-123})
            check(classifyLpStatus(raw)==RmpLpStatus::Error,"unexpected auxiliary status cannot prove stage infeasibility");
        // Real API error after opening an environment: verifies constructor cleanup.
        rejects([&]{DVRP_RmpController bad(sample(),{1e100});},"CPLEX parameter error expected");
        DVRP_RmpController good(sample()); close(*optimal(good).artificialSum,3,"new environment after failure");
    }
    void timeLimit() {
        DVRP_RmpController rmp(fractionalStage(),{0}); rmp.addInitialRoutes();
        const auto result=rmp.solveLp();
        check(result.status==RmpLpStatus::OptimalRmp || result.status==RmpLpStatus::TimeLimit,result.message);
        if(result.status==RmpLpStatus::TimeLimit) {
            check(!result.duals && !result.objective && result.lambda.empty(),"timeout must not expose proof-quality data");
            rejects([&]{rmp.enterCostPhase();},"timeout cannot authorize phase switch");
        }
        std::cout<<"zero-second LP status="<<(result.rawStatus ? std::to_string(*result.rawStatus) : "analytic")<<'\n';
    }
}
int main(int argc, char** argv) {
    const std::vector<std::pair<std::string,std::function<void()>>> cases={
        {"matrix",matrix},{"phases",phases},{"initial",initial},{"resources",resources},
        {"validation",validation},{"identity",identity},{"duplicate",duplicate},{"empty",empty},
        {"infeasible",infeasible},{"full_lp",fullLp},{"fractional",fractional},{"pricing",pricing},
        {"size",size},{"lifecycle",lifecycle},{"status",status},{"time_limit",timeLimit}};
    try {
        bool ran=false;
        for(const auto& [name,run]:cases) if(argc==1 || name==argv[1]) { run(); ran=true; std::cout<<"PASS rmp "<<name<<'\n'; }
        check(ran,"unknown test case"); return 0;
    }
    catch (const std::exception& e) { std::cerr << e.what() << '\n'; return 1; }
}

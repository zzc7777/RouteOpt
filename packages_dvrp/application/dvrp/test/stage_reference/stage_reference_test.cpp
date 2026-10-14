#include "stage_reference.hpp"
#include "dynamic_controller.hpp"
#include "solver.hpp"
#include <algorithm>
#include <cmath>
#include <functional>
#include <iostream>
#include <limits>
#include <set>
#include <stdexcept>

using namespace RouteOpt::Application::DVRP;
using namespace RouteOpt::Application::DVRP::Testing;
namespace {
    void check(bool value, const std::string& message) {
        if (!value) throw std::runtime_error(message);
    }
    void close(double a, double b, const std::string& message) {
        check(std::isfinite(a) && std::isfinite(b) && std::abs(a-b) <= 1e-6, message);
    }
    template<class F> void rejects(F operation, const std::string& message) {
        bool rejected = false;
        try { operation(); } catch (const std::invalid_argument&) { rejected = true; }
        check(rejected, message);
    }
    StageProblem sample() {
        StageProblem s;
        s.stageId = 7;
        s.currentTime = 100;
        s.originStart = {0, StartKind::Origin, 0, {0,0}, 100, 100};
        s.externalStarts = {{42, StartKind::External, 3, {10,0}, 130, 40}};
        s.returnDepot = {{0,0}, 200};
        s.customers = {{5,{12,0},10,0,180,0}, {8,{14,0},20,0,180,0}};
        s.fixedCostAfterTau = 7;
        return s;
    }
    void enumeration() {
        auto s = sample();
        const auto all = enumerateStage(s);
        check(all.routes().size() == 9, "two customers: expected four origin and five external routes");
        std::set<std::pair<int,std::vector<int>>> expected;
        for (int start : {0,42})
            for (const auto& seq : std::vector<std::vector<int>>{{5},{8},{5,8},{8,5}})
                expected.insert({start,seq});
        expected.insert({42,{}});
        for (const auto& r : all.routes()) check(expected.erase({r.route.startKey,r.route.customerSequence}) == 1, "unexpected/duplicate route");
        check(expected.empty(), "missing route");
        s.customers[0].id = 999;
        check(all.problem().customers[0].id == 5, "snapshot ownership");
    }
    bool contains(const EnumeratedStage& all, int key, const std::vector<int>& seq) {
        return std::any_of(all.routes().begin(),all.routes().end(),[&](const auto& r) {
            return r.route.startKey == key && r.route.customerSequence == seq;
        });
    }
    double costOf(const EnumeratedStage& all, int key, const std::vector<int>& seq) {
        for (const auto& r : all.routes())
            if (r.route.startKey == key && r.route.customerSequence == seq) return r.cost;
        throw std::runtime_error("route not found");
    }
    void resources() {
        auto s = sample();
        s.externalStarts[0].remainingCapacity = 10;
        auto all = enumerateStage(s);
        check(contains(all,42,{5}) && !contains(all,42,{8}) && !contains(all,42,{5,8}),"start-specific capacity");
        check(contains(all,0,{5,8}),"origin keeps full capacity");
        s = sample(); s.externalStarts[0].startTime = 179;
        all = enumerateStage(s);
        check(contains(all,42,{}) && !contains(all,42,{5}),"external departure time");
        s = sample(); s.customers[0].readyTime = 150; s.customers[0].dueDate = 150; s.customers[0].serviceTime = 20;
        all = enumerateStage(s);
        check(contains(all,42,{5,8}),"waiting and service completion later than dueDate are allowed");
        s.customers[1].dueDate = 171;
        check(!contains(enumerateStage(s),42,{5,8}),"service time must propagate to next customer");
        s = sample(); s.returnDepot.deadline = 147.9;
        check(!contains(enumerateStage(s),42,{5,8}),"H checked after last service");
        s.returnDepot.deadline = 148;
        check(contains(enumerateStage(s),42,{5,8}),"return at H allowed");
        s = sample(); s.externalStarts[0].startKey = 5;
        all = enumerateStage(s);
        check(contains(all,5,{5,8}),"vehicle key and customer ID have different namespaces");
        close(costOf(all,5,{5,8}),18,"route endpoints and cost");
    }
    void nonmetric() {
        auto s = sample(); s.currentTime = 0;
        s.originStart.startTime = 0;
        s.externalStarts = {{42,StartKind::External,99,{1.1,0},0,100}};
        s.customers = {{5,{0.55,0},1,0,100,0}};
        s.returnDepot.deadline = 1.0;
        const auto all = enumerateStage(s);
        check(!contains(all,42,{}) && contains(all,42,{5}),"infeasible direct return must not prune feasible extension");
        close(costOf(all,42,{5}),1.0,"truncated two-arc route cost");
    }
    void validation() {
        auto bad = [](auto change) {
            auto s = sample(); change(s);
            rejects([&] { (void)enumerateStage(s); },"invalid stage was accepted");
        };
        bad([](auto& s){s.customers[1].id = 5;});
        bad([](auto& s){s.customers[0].id = 0;});
        bad([](auto& s){s.customers[0].location.x = std::numeric_limits<double>::quiet_NaN();});
        bad([](auto& s){s.customers[0].demand = 0.5;});
        bad([](auto& s){s.customers[0].serviceTime = -1;});
        bad([](auto& s){s.customers[0].readyTime = 181;});
        bad([](auto& s){s.externalStarts.push_back(s.externalStarts[0]);});
        bad([](auto& s){s.externalStarts[0].startKey = 0;});
        bad([](auto& s){s.externalStarts[0].locationCustomerId = 5;});
        bad([](auto& s){s.externalStarts[0].remainingCapacity = -1;});
        bad([](auto& s){s.externalStarts[0].remainingCapacity = 101;});
        bad([](auto& s){s.externalStarts[0].startTime = 99;});
        bad([](auto& s){s.originStart.startTime = 101;});
        bad([](auto& s){s.returnDepot.location.x = 1;});
        bad([](auto& s){s.fixedCostAfterTau = -1;});
        bad([](auto& s){s.customers[0].location.x = 1e300;});
    }
    void limits() {
        auto s = sample(); s.customers.clear(); s.externalStarts.clear();
        for (int i = 1; i <= 5; ++i) {
            s.customers.push_back({i,{0,0},0,0,1000,0});
            s.externalStarts.push_back({i*10,StartKind::External,100+i,{0,0},100,100});
        }
        const auto all = enumerateStage(s);
        check(all.routes().size() == 325 + 5*326,"full five-customer universe");
        const auto l = solveFullLp(all); const auto m = solveFullMip(all);
        check(l.status == LpReferenceStatus::Optimal,l.message);
        check(m.solution.status == SolveStatus::Optimal,m.solution.message);
        check(l.routeCost && m.routeCost,"maximum-size objectives missing");
        close(*l.routeCost,0,"maximum-size LP"); close(*m.routeCost,0,"maximum-size MIP");
        s.customers.push_back({6,{0,0},0,0,1000,0});
        rejects([&]{ (void)enumerateStage(s); },"six customers not refused");
        s.customers.pop_back();
        s.externalStarts.push_back({60,StartKind::External,106,{0,0},100,100});
        rejects([&]{ (void)enumerateStage(s); },"six external starts not refused");
    }
    void duals() {
        const auto all = enumerateStage(sample());
        StageDuals d{{{5,20},{8,20}},{{42,5}}};
        const auto rc = computeReducedCosts(all,d);
        close(*std::min_element(rc.begin(),rc.end()),-27,"minimum reduced cost");
        for (std::size_t i = 0; i < rc.size(); ++i) {
            const auto& r = all.routes()[i];
            if (r.route.customerSequence.empty()) close(rc[i],5,"external empty route dual");
            if (r.route.startKey == 0 && r.route.customerSequence.size() == 2) close(rc[i],-12,"no origin vehicle dual");
        }
        d = {{{5,0},{8,0}},{{42,0}}};
        const auto nonnegative = computeReducedCosts(all,d);
        check(*std::min_element(nonnegative.begin(),nonnegative.end()) >= 0,"zero duals must have no negative route");
        d.externalStarts.clear();
        rejects([&]{ (void)computeReducedCosts(all,d); },"missing dual accepted");
        d.externalStarts.emplace(0,5);
        rejects([&]{ (void)computeReducedCosts(all,d); },"wrong dual key accepted");
        d.externalStarts = {{42,std::numeric_limits<double>::infinity()}};
        rejects([&]{ (void)computeReducedCosts(all,d); },"infinite dual accepted");
    }
    void lp() {
        const auto all = enumerateStage(sample());
        const auto result = solveFullLp(all);
        check(result.status == LpReferenceStatus::Optimal,result.message);
        check(result.routeCost && result.totalPlannedCost && result.duals,"missing LP fields");
        close(*result.routeCost,18,"LP objective"); close(*result.totalPlannedCost,25,"fixed cost once");
        check(result.lambda.size() == 9 && result.reducedCosts.size() == 9,"LP column values");
        double dualObjective = 0;
        for (const auto& [id,v] : result.duals->customers) dualObjective += v;
        for (const auto& [id,v] : result.duals->externalStarts) dualObjective += v;
        close(dualObjective,18,"dual objective");
        for (std::size_t i = 0; i < result.lambda.size(); ++i) {
            check(result.reducedCosts[i] >= -1e-6,"full LP has negative reduced cost");
            close(result.lambda[i]*result.reducedCosts[i],0,"complementarity");
        }
    }
    void mip() {
        const auto result = solveFullMip(enumerateStage(sample()));
        check(result.solution.status == SolveStatus::Optimal,result.solution.message);
        check(result.routeCost && result.bestBound && result.totalPlannedCost,"missing MIP objectives");
        close(*result.routeCost,18,"MIP objective"); close(*result.bestBound,18,"MIP bound");
        close(*result.totalPlannedCost,25,"MIP fixed cost");
        check(result.solution.stageId == 7 && result.solution.routes.size() == 1,"MIP stage identity/routes");
        const auto& r = result.solution.routes.front();
        check(r.startKey == 42 && std::set<int>(r.customerSequence.begin(),r.customerSequence.end()) == std::set<int>({5,8}),"wrong chosen route");
    }
    StageProblem fractionalStage() {
        auto s = sample(); s.stageId = 0; s.currentTime = 0; s.fixedCostAfterTau = 0;
        s.originStart = {0,StartKind::Origin,0,{0,0},0,2};
        s.externalStarts.clear(); s.customers.clear(); s.returnDepot.deadline = 100;
        for (int id = 1; id <= 3; ++id) s.customers.push_back({id,{10,0},1,0,100,0});
        return s;
    }
    void fractional() {
        const auto all = enumerateStage(fractionalStage());
        check(all.routes().size() == 9,"three singleton and six ordered pair routes");
        const auto l = solveFullLp(all); const auto m = solveFullMip(all);
        check(l.status == LpReferenceStatus::Optimal,l.message);
        check(m.solution.status == SolveStatus::Optimal,m.solution.message);
        close(*l.routeCost,30,"pair cover LP = 1.5 routes * 20");
        close(*m.routeCost,40,"integer cover = two routes * 20");
        check(std::any_of(l.lambda.begin(),l.lambda.end(),[](double x){return x > 1e-6 && x < 1-1e-6;}),"expected fractional LP");
        check(m.solution.routes.size() == 2,"origin not restricted to one vehicle");
    }
    void empty() {
        auto s = sample(); s.customers.clear(); s.externalStarts.clear();
        auto all = enumerateStage(s);
        auto l = solveFullLp(all); auto m = solveFullMip(all);
        check(l.status == LpReferenceStatus::Optimal && m.solution.status == SolveStatus::Optimal,"empty stage optimum");
        check(!l.rawStatus && !m.rawStatus && m.solution.routes.empty(),"analytic empty case should not fake solver status");
        close(*m.routeCost,0,"empty route cost"); close(*m.totalPlannedCost,7,"empty fixed cost");
        s.externalStarts = sample().externalStarts;
        auto other = s.externalStarts[0]; other.startKey = 100; other.locationCustomerId = 4;
        s.externalStarts.push_back(other);
        all = enumerateStage(s); m = solveFullMip(all); l = solveFullLp(all);
        check(m.solution.status == SolveStatus::Optimal && l.status == LpReferenceStatus::Optimal,"external empty return");
        close(*m.routeCost,20,"two colocated external vehicles separately return");
        check(m.solution.routes.size() == 2,"each external used exactly once");
        for (const auto& r : m.solution.routes) check(r.customerSequence.empty(),"empty returns must remain empty");
    }
    void infeasible() {
        auto s = sample(); s.customers[0].dueDate = 100;
        auto all = enumerateStage(s);
        auto l = solveFullLp(all); auto m = solveFullMip(all);
        check(l.status == LpReferenceStatus::Infeasible,"uncovered customer LP: " + l.message);
        check(m.solution.status == SolveStatus::Infeasible,"uncovered customer MIP: " + m.solution.message);
        check(!l.routeCost && !l.duals && !m.routeCost && m.solution.routes.empty(),"infeasible results should not have objectives");
        s = sample(); s.customers.clear(); s.returnDepot.deadline = 139;
        all = enumerateStage(s);
        check(all.routes().empty(),"external direct return after H");
        check(solveFullMip(all).solution.status == SolveStatus::Infeasible,"required external row cannot disappear");
        // Every required row has a column, but two external vehicles both need the same customer.
        s.currentTime = 0; s.originStart.startTime = 0; s.returnDepot.deadline = 1;
        s.customers = {{5,{0.55,0},1,0,100,0}};
        s.externalStarts = {{42,StartKind::External,99,{1.1,0},0,100},
                            {43,StartKind::External,98,{1.1,0},0,100}};
        all = enumerateStage(s);
        l = solveFullLp(all); m = solveFullMip(all);
        check(l.status == LpReferenceStatus::Infeasible,"conflicting required starts LP: " + l.message);
        check(m.solution.status == SolveStatus::Infeasible,"conflicting required starts MIP: " + m.solution.message);
    }
    void status() {
        using RouteOpt::Solver; using RouteOpt::SolverTermination;
        check(Solver::classifyStatus(CPX_STAT_OPTIMAL,false).termination == SolverTermination::Optimal,"LP optimal");
        check(Solver::classifyStatus(CPXMIP_OPTIMAL,true).termination == SolverTermination::Optimal,"MIP optimal");
        check(Solver::classifyStatus(CPX_STAT_OPTIMAL,true).termination == SolverTermination::Error,"LP status not MIP optimal");
        check(Solver::classifyStatus(CPXMIP_OPTIMAL_TOL,true).termination == SolverTermination::Feasible,"gap stop not proof of exact optimum");
        check(Solver::classifyStatus(CPXMIP_INFEASIBLE,true).termination == SolverTermination::Infeasible,"proven infeasible");
        for (int raw : {CPXMIP_TIME_LIM_FEAS,CPXMIP_DETTIME_LIM_FEAS}) {
            const auto info = Solver::classifyStatus(raw,true);
            check(info.termination == SolverTermination::TimeLimit && info.hasPrimal,"timeout with incumbent");
        }
        for (int raw : {CPXMIP_TIME_LIM_INFEAS,CPXMIP_DETTIME_LIM_INFEAS}) {
            const auto info = Solver::classifyStatus(raw,true);
            check(info.termination == SolverTermination::TimeLimit && !info.hasPrimal,"no incumbent is not infeasibility");
        }
        check(Solver::classifyStatus(CPX_STAT_INForUNBD,false).termination == SolverTermination::Error,"ambiguous status must not be infeasible");
        check(Solver::classifyStatus(-123,true).termination == SolverTermination::Error,"unknown status");
        const auto all = enumerateStage(sample());
        check(solveFullLp(all,{-1}).status == LpReferenceStatus::Error,"invalid LP option");
        const auto m = solveFullMip(all,{std::numeric_limits<double>::quiet_NaN()});
        check(m.solution.status == SolveStatus::Error && !m.routeCost && !m.rawStatus,"invalid MIP option");
        // Finite input passes our guard but exceeds CPLEX's parameter range.
        // Exercise actual API-error reporting and owner cleanup, not just input validation.
        const auto apiLp = solveFullLp(all,{1e300}); const auto apiMip = solveFullMip(all,{1e300});
        check(apiLp.status == LpReferenceStatus::Error && !apiLp.routeCost && !apiLp.rawStatus
              && apiLp.message.find("solver API code=") != std::string::npos,"LP API error handling");
        check(apiMip.solution.status == SolveStatus::Error && !apiMip.routeCost && !apiMip.rawStatus
              && apiMip.solution.message.find("solver API code=") != std::string::npos,"MIP API error handling");
    }
    using Input = std::vector<std::vector<double>>;
    Input dynamicInput() {
        return {{0,0,0,0,0,1000,0,0}, {1,10,0,10,0,900,20,0},
                {2,12,0,10,0,900,0,5}, {3,14,0,10,0,900,0,15}};
    }
    void dynamic() {
        DynamicController controller(dynamicInput(),100,"reference_dynamic");
        std::size_t stages = 0;
        while (controller.state().status == SimulationStatus::Running) {
            const auto stage = controller.buildStageProblem();
            const auto result = solveFullMip(enumerateStage(stage));
            check(result.solution.status == SolveStatus::Optimal,result.solution.message);
            if (stage.currentTime > 0) {
                check(stage.externalStarts.size() == 1 && stage.externalStarts[0].startKey == 1,"stable vehicle identity");
                check(stage.externalStarts[0].locationCustomerId == 1,"commitment retained");
                close(stage.externalStarts[0].startTime,30,"commitment completion projection");
                close(stage.externalStarts[0].remainingCapacity,90,"projected remaining capacity");
            }
            controller.acceptSolution(result.solution);
            close(controller.records().back().routeCost,*result.routeCost,"independent dynamic route validation");
            close(controller.records().back().totalPlannedCost,*result.totalPlannedCost,"fixed cost comparison");
            ++stages;
            if (const auto next = controller.nextEventTime()) controller.advanceTo(*next);
            else controller.finish();
        }
        check(stages == 3 && controller.state().status == SimulationStatus::Completed,"three-stage automatic MIP loop");
        close(controller.state().cumulativeActualDistance,28,"actual distance is not sum of stage plans");
        double sum = 0;
        for (const auto& record : controller.records()) sum += record.actualDistanceToNextEvent;
        close(sum,28,"stage distance increments");
        std::cout << "dynamic stages=3 actual_distance=28\n";
        // Deterministic simulated termination: even an incumbent must not be executed.
        for (auto stop : {SolveStatus::TimeLimitWithSolution,SolveStatus::TimeLimitWithoutSolution}) {
            DynamicController stopped(dynamicInput(),100);
            auto result = solveFullMip(enumerateStage(stopped.buildStageProblem()));
            check(result.solution.status == SolveStatus::Optimal,result.solution.message);
            result.solution.status = stop;
            if (stop == SolveStatus::TimeLimitWithoutSolution) result.solution.routes.clear();
            stopped.acceptSolution(result.solution);
            check(stopped.state().status == SimulationStatus::StoppedTimeLimit && stopped.state().vehicles.empty(),"timeout must not dispatch");
        }
    }
    void lifecycle() {
        const auto all = enumerateStage(sample());
        for (int i = 0; i < 20; ++i) {
            const auto l = solveFullLp(all); const auto m = solveFullMip(all);
            check(l.status == LpReferenceStatus::Optimal,l.message);
            check(m.solution.status == SolveStatus::Optimal,m.solution.message);
        }
        auto s = sample(); s.fixedCostAfterTau = 100;
        const auto m = solveFullMip(enumerateStage(s));
        check(m.solution.status == SolveStatus::Optimal && m.routeCost && m.totalPlannedCost,m.solution.message);
        close(*m.routeCost,18,"constant does not change optimized route cost");
        close(*m.totalPlannedCost,118,"new stage value not shared");
    }
    void timeLimit() {
        const auto all = enumerateStage(fractionalStage());
        const auto l = solveFullLp(all,{0}); const auto m = solveFullMip(all,{0});
        check(l.status == LpReferenceStatus::Optimal || l.status == LpReferenceStatus::TimeLimit,"LP zero time limit: " + l.message);
        check(m.solution.status == SolveStatus::Optimal || m.solution.status == SolveStatus::TimeLimitWithSolution
              || m.solution.status == SolveStatus::TimeLimitWithoutSolution,"MIP zero time limit: " + m.solution.message);
        if (l.status == LpReferenceStatus::TimeLimit) check(!l.routeCost && !l.duals,"timed out LP is not reference optimum");
        if (m.solution.status == SolveStatus::TimeLimitWithoutSolution) check(!m.routeCost && m.solution.routes.empty(),"no incumbent queried");
        std::cout << "real time-limit smoke: LP raw=" << l.rawStatus.value_or(-1)
                  << " MIP raw=" << m.rawStatus.value_or(-1) << '\n';
    }
}
int main(int argc, char* argv[]) {
    const std::vector<std::pair<std::string,std::function<void()>>> tests{
        {"enumeration",enumeration},{"resources",resources},{"nonmetric",nonmetric},
        {"validation",validation},{"limits",limits},{"duals",duals},{"lp",lp},{"mip",mip},
        {"fractional",fractional},{"empty",empty},{"infeasible",infeasible},{"status",status},
        {"dynamic",dynamic},{"lifecycle",lifecycle},{"time_limit",timeLimit}};
    try {
        int count = 0;
        for (const auto& [name,test] : tests) {
            if (argc == 2 && name != argv[1]) continue;
            test(); ++count; std::cout << "PASS stage_reference " << name << '\n';
        }
        check(count > 0 && argc <= 2, "unknown test or invalid arguments");
        return 0;
    } catch (const std::exception& e) {
        std::cerr << "FAIL stage_reference: " << e.what() << '\n'; return 1;
    }
}

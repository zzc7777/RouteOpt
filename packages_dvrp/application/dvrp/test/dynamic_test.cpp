#include <cmath>
#include <algorithm>
#include <functional>
#include <iostream>
#include <limits>
#include <stdexcept>
#include <string>
#include <vector>
#include "dynamic_controller.hpp"
#include "read_data_controller.hpp"

namespace {
    using namespace RouteOpt::Application::DVRP;
    using Input = std::vector<std::vector<double>>;

    void check(bool value, const std::string& message) {
        if (!value) throw std::runtime_error(message);
    }
    void close(double actual, double expected, const std::string& message) {
        check(std::isfinite(actual) && std::abs(actual - expected) < 1e-8,
              message + ": actual=" + std::to_string(actual) + " expected=" + std::to_string(expected));
    }
    template<class F> void rejects(F operation, const std::string& message) {
        bool rejected = false;
        try { operation(); } catch (const std::invalid_argument&) { rejected = true; }
        catch (const std::logic_error&) { rejected = true; }
        check(rejected, message);
    }
    Input base(double horizon = 200) { return {{0, 0, 0, 0, 0, horizon, 0, 0}}; }
    void customer(Input& data, double x, double demand = 10, double ready = 0,
                  double due = 150, double service = 0, double disclosure = 0, double y = 0) {
        data.push_back({static_cast<double>(data.size()), x, y, demand, ready, due, service, disclosure});
    }
    void accept(DynamicController& controller, std::vector<StageRoute> routes) {
        controller.acceptSolution({controller.state().stageId, SolveStatus::Feasible, std::move(routes), "hand route"});
    }
    const StartSpec& external(const StageProblem& stage, int key = 1) {
        for (const auto& start : stage.externalStarts) if (start.startKey == key) return start;
        throw std::runtime_error("missing external start");
    }

    void eventsAndOwnership() {
        auto data = base();
        customer(data, 10);
        customer(data, 20, 10, 0, 150, 0, 5);
        customer(data, 30, 10, 0, 150, 0, 5);
        customer(data, 40, 10, 0, 150, 0, 5.0000001);
        DynamicController c(data, 100, "events");
        check(c.eventTimes() == std::vector<double>({0, 5, 5.0000001}), "exact event grouping");
        const auto snapshot = c.buildStageProblem();
        check(snapshot.customers.size() == 1, "future customers leaked");
        data[1][1] = 999; // Controller must own its inputs.
        accept(c, {{0, {1}}});
        rejects([&] { c.advanceTo(5.0000001); }, "must not skip an event");
        c.advanceTo(5);
        check(c.state().remainingCustomerIds == std::vector<int>({2, 3}), "same-time disclosure");
        check(snapshot.customers.size() == 1 && snapshot.currentTime == 0, "snapshot mutated");
        close(c.state().vehicles[0].actualPosition.x, 5, "owned input");
        rejects([&] { c.finish(); }, "finish before result");
        rejects([&] { c.acceptSolution({0, SolveStatus::Feasible, {{0,{2,3}}}, {}}); }, "stale result");
    }

    void commitmentAndLoad() {
        auto data = base();
        customer(data, 10, 20, 20, 150, 10);
        customer(data, 15, 10);
        customer(data, 0, 0, 0, 150, 0, 5);
        customer(data, 0, 0, 0, 150, 0, 15);
        customer(data, 0, 0, 0, 150, 0, 25);
        customer(data, 0, 0, 0, 150, 0, 30);
        DynamicController c(data, 100);
        accept(c, {{0, {1,2}}});
        const std::vector<VehiclePhase> phases{VehiclePhase::Driving, VehiclePhase::Waiting, VehiclePhase::Serving};
        int eventCustomer = 3;
        for (int index = 0; index < 3; ++index) {
            c.advanceTo(*c.nextEventTime());
            const auto stage = c.buildStageProblem();
            const auto vehicle = c.state().vehicles[0];
            check(vehicle.phase == phases[index] && vehicle.committedCustomerId == 1, "commitment phase");
            close(vehicle.actualLoad, 0, "must not collect before service completion");
            close(external(stage).startTime, 30, "fixed completion");
            close(external(stage).remainingCapacity, 80, "projected load, not repeated deduction");
            close(external(stage).location.x, 10, "external at customer, not interpolated position");
            close(stage.fixedCostAfterTau, index == 0 ? 5 : 0, "fixed driving only");
            accept(c, {{1, {2}}, {0, {eventCustomer++}}});
        }
        c.advanceTo(30);
        check(c.state().servedCustomerIds == std::vector<int>({1,3,4,5}), "completed before disclosure");
        check(c.state().vehicles[0].phase == VehiclePhase::ReadyForReplan, "hold same-time departure");
        check(c.state().vehicles[0].committedCustomerId == 0, "old next customer not committed at tie");
        close(c.state().vehicles[0].actualLoad, 20, "collect once at completion");
        accept(c, {{1, {}}, {0, {2,6}}});
        c.finish();
        check(c.state().status == SimulationStatus::Completed, "finish status");
        close(c.state().cumulativeActualDistance, 50, "reassigned continuation actual total");
        check(c.state().servedCustomerIds.size() == 6, "all customers served");
    }

    void fixedReturnAndNoReuse() {
        auto data = base();
        customer(data, 10);
        customer(data, 0, 0, 0, 150, 0, 15);
        customer(data, 0, 0, 0, 150, 0, 20);
        DynamicController c(data, 100);
        accept(c, {{0,{1}}});
        c.advanceTo(15);
        auto stage = c.buildStageProblem();
        check(stage.externalStarts.empty(), "returning vehicle must not reoptimize");
        check(c.state().vehicles[0].phase == VehiclePhase::Returning, "return phase");
        close(c.state().vehicles[0].actualPosition.x, 5, "return interpolation");
        close(stage.fixedCostAfterTau, 5, "remaining return fixed");
        accept(c, {{0,{2}}});
        c.advanceTo(20);
        check(c.state().vehicles[0].phase == VehiclePhase::Finished, "exact return boundary");
        check(c.buildStageProblem().externalStarts.empty(), "finished vehicle no external");
        accept(c, {{0,{3}}});
        c.finish();
        check(c.state().vehicles.size() == 3 && c.state().vehicles[2].vehicleId == 3, "unlimited origin, no recycled ID");
        close(c.state().cumulativeActualDistance, 20, "fixed return accounted once");
    }

    void distancePartitionAndTail() {
        auto data = base();
        customer(data, 1, 1, 0, 150, 0, 0, 1); // sqrt(2) -> 1.4
        for (double t : {0.15, 0.55, 1.1}) customer(data, 0, 0, 0, 150, 0, t);
        DynamicController c(data, 100);
        close(routeOptDistance({0,0},{1,1}), 1.4, "VRPTW truncation");
        accept(c, {{0,{1}}});
        int id = 2;
        while (auto next = c.nextEventTime()) {
            c.advanceTo(*next);
            close(c.state().cumulativeActualDistance, *next, "partial arc not rounded");
            close(c.state().vehicles[0].actualPosition.x, *next / 1.4, "geometric interpolation");
            accept(c, {{1,{}}, {0,{id++}}});
        }
        c.finish();
        close(c.state().cumulativeActualDistance, 2.8, "tail execution");
        double intervals = 0;
        for (const auto& record : c.records()) intervals += record.actualDistanceToNextEvent;
        close(intervals, 2.8, "recorded intervals telescope");
        close(c.state().currentTime, 2.8, "tail ends at actual last return");
    }

    void atomicValidation() {
        auto data = base(30);
        customer(data, 10, 60, 0, 10, 0);
        customer(data, 11, 60, 0, 20, 0);
        DynamicController c(data, 100);
        rejects([&] { accept(c, {{0,{1}}}); }, "missing customer");
        rejects([&] { accept(c, {{0,{1}}, {0,{1,2}}}); }, "duplicate customer");
        rejects([&] { accept(c, {{0,{1,2}}}); }, "capacity overflow");
        rejects([&] { accept(c, {{0,{1}}, {9,{2}}}); }, "unknown start");
        rejects([&] { accept(c, {{0,{}}, {0,{1}}, {0,{2}}}); }, "empty origin");
        rejects([&] { accept(c, {{0,{99}}, {0,{2}}}); }, "unknown customer");
        check(c.state().vehicles.empty() && c.records().empty() && c.state().awaitingSolution,
              "failed validation partially changed state");
        accept(c, {{0,{1}}, {0,{2}}});
        check(c.state().vehicles[0].vehicleId == 1, "invalid plan consumed IDs");
        rejects([&] { accept(c, {{0,{1}}, {0,{2}}}); }, "double result");
        c.finish();
        close(c.state().cumulativeActualDistance, 42, "two physical routes");
    }

    void deadlinesAndZeroTime() {
        auto data = base(20);
        customer(data, 10, 0, 10, 10, 0);
        DynamicController exact(data, 10);
        accept(exact, {{0,{1}}});
        exact.finish();
        close(exact.state().currentTime, 20, "H equality feasible");
        data[0][5] = 19.999;
        DynamicController late(data, 10);
        rejects([&] { accept(late, {{0,{1}}}); }, "late return");
        data = base();
        customer(data, 10, 0, 0, 9.99);
        DynamicController window(data, 10);
        rejects([&] { accept(window, {{0,{1}}}); }, "late service start");
        data = base();
        customer(data, 0, 0, 0, 0, 0);
        customer(data, 0, 0, 0, 0, 0);
        DynamicController zero(data, 10);
        accept(zero, {{0,{1,2}}});
        zero.finish();
        check(zero.state().servedCustomerIds.size() == 2, "zero-duration chain");
        close(zero.state().cumulativeActualDistance, 0, "zero-duration distance");
    }

    void stopStatuses() {
        for (auto status : {SolveStatus::Infeasible, SolveStatus::TimeLimitWithoutSolution,
                            SolveStatus::TimeLimitWithSolution, SolveStatus::Error}) {
            auto data = base();
            customer(data, 10);
            DynamicController c(data, 100);
            const auto routes = status == SolveStatus::TimeLimitWithSolution
                ? std::vector<StageRoute>{{0,{1}}} : std::vector<StageRoute>{};
            c.acceptSolution({0, status, routes, "mock solver stop"});
            check(c.state().status != SimulationStatus::Running && c.state().status != SimulationStatus::Completed,
                  "failure must not continue or complete");
            if (status == SolveStatus::TimeLimitWithSolution || status == SolveStatus::TimeLimitWithoutSolution)
                check(c.state().status == SimulationStatus::StoppedTimeLimit, "timeout is not infeasible");
            check(c.state().vehicles.empty() && !c.records()[0].executed, "timeout incumbent executed");
            check(c.records()[0].solution.routes.size() == routes.size(), "incumbent diagnostics lost");
            rejects([&] { c.finish(); }, "cannot finish stopped simulation");
        }
    }

    void externalValidation() {
        auto data = base();
        customer(data, 10, 70, 20, 150, 10);
        customer(data, 10, 20, 20, 150, 10); // Same coordinates, distinct physical vehicles.
        customer(data, 12, 40, 0, 150, 0, 5);
        DynamicController c(data, 100);
        accept(c, {{0,{1}}, {0,{2}}});
        c.advanceTo(5);
        const auto stage = c.buildStageProblem();
        check(stage.externalStarts.size() == 2, "coincident external starts merged");
        close(external(stage, 1).remainingCapacity, 30, "first vehicle capacity");
        close(external(stage, 2).remainingCapacity, 80, "second vehicle capacity");
        close(stage.fixedCostAfterTau, 10, "multiple fixed arcs");
        rejects([&] { accept(c, {{1,{}}, {0,{3}}}); }, "missing external route");
        rejects([&] { accept(c, {{1,{}}, {1,{}}, {2,{3}}}); }, "duplicate external route");
        rejects([&] { accept(c, {{1,{3}}, {2,{}}}); }, "external capacity not standard Q");
        rejects([&] { accept(c, {{1,{1}}, {2,{3}}}); }, "committed customer replanned");
        check(c.records().size() == 1 && c.state().awaitingSolution, "rejection changed stage");
        accept(c, {{1,{}}, {2,{3}}});
        close(c.records().back().routeCost, 24, "external routes start at customers");
        close(c.records().back().totalPlannedCost, 34, "fixed + selected cost");
        c.finish();
        close(c.state().cumulativeActualDistance, 44, "multi-vehicle accounting");
        check(c.state().vehicles.size() == 2, "external starts allocated new vehicles");
    }

    void loadedVehicleProjection() {
        auto data = base(1000);
        customer(data, 40, 40);
        customer(data, 120, 20, 0, 200, 10);
        customer(data, 140, 10, 0, 200);
        for (double t : {100., 110., 130.}) customer(data, 0, 0, 0, 200, 0, t);
        DynamicController c(data, 100);
        accept(c, {{0,{1,2,3}}});
        int fresh = 4;
        for (double t : {100., 110.}) {
            c.advanceTo(t);
            const auto stage = c.buildStageProblem();
            close(c.state().vehicles[0].actualLoad, 40, "previous pickups remain loaded");
            close(external(stage).remainingCapacity, 40, "project Q minus old and committed pickup");
            close(external(stage).startTime, 130, "projected availability");
            close(external(stage).location.x, 120, "commitment location");
            close(stage.fixedCostAfterTau, 120-t, "remaining committed leg");
            accept(c, {{1,{3}}, {0,{fresh++}}});
        }
        c.advanceTo(130);
        close(c.state().vehicles[0].actualLoad, 60, "load updated only once at completion");
        close(external(c.buildStageProblem()).remainingCapacity, 40, "tie capacity unchanged");
        accept(c, {{1,{3}}, {0,{6}}});
        c.finish();
        close(c.state().cumulativeActualDistance, 280, "full route with repeated projection");
        close(c.state().vehicles[0].actualLoad, 70, "final collected load");
    }

    void externalReturnDeadline() {
        // Truncated distances need not obey triangle inequality: .25 -> 0 is .2,
        // but .25 -> .16 -> 0 is .0 + .1. Direct return is NOT always feasible.
        auto data = base(1.35);
        customer(data, .25, 0, 0, 1.3, 1);
        customer(data, .16, 0, 0, 1.3);
        customer(data, 0, 0, 0, 1.3, 0, 1.2);
        DynamicController c(data, 10);
        accept(c, {{0,{1,2}}});
        c.advanceTo(1.2);
        check(c.state().remainingCustomerIds == std::vector<int>({2,3}), "zero-time next visit executed before replan");
        rejects([&] { accept(c, {{1,{}}, {0,{2,3}}}); }, "external direct return after H");
        accept(c, {{1,{2}}, {0,{3}}});
        c.finish();
        close(c.state().currentTime, 1.3, "indirect feasible return");
        close(c.state().cumulativeActualDistance, .3, "zero-travel arc distance");
    }

    void emptyAndFutureStages() {
        auto data = base();
        customer(data, 10, 1, 0, 150, 0, 10);
        DynamicController c(data, 100);
        check(c.buildStageProblem().customers.empty(), "initial stage should be empty");
        rejects([&] { accept(c, {{0,{1}}}); }, "unreleased customer accepted");
        accept(c, {});
        rejects([&] { c.finish(); }, "future disclosure skipped by empty stage");
        c.advanceTo(10);
        check(c.state().vehicles.empty(), "undispatched vehicle became external");
        close(c.buildStageProblem().originStart.startTime, 10, "origin earliest departure");
        accept(c, {{0,{1}}});
        close(c.records().back().validatedRoutes[0].visits[0].departureTime, 10, "must depart immediately");
        c.finish();
        close(c.state().cumulativeActualDistance, 20, "empty initial phase");
        close(c.state().currentTime, 30, "wall clock ignored");
    }

    void arithmeticBoundaries() {
        auto data = base();
        customer(data, .1, 1, 0, 1, .2);
        customer(data, 0, 0, 0, 1, 0, .3);
        customer(data, 0, 0, 0, 1, 0, std::nextafter(.3, 1.0));
        DynamicController c(data, 100);
        accept(c, {{0,{1}}});
        c.advanceTo(.3);
        check(c.state().vehicles[0].phase == VehiclePhase::ReadyForReplan, ".1+.2 completion tie");
        check(c.state().remainingCustomerIds == std::vector<int>({2}), "nextafter disclosure released too soon");
        accept(c, {{1,{}}, {0,{2}}});
        c.advanceTo(*c.nextEventTime());
        check(c.buildStageProblem().externalStarts.empty(), "already departed vehicle resurrected at close event");
        check(c.state().vehicles[0].phase == VehiclePhase::Returning, "return retained after close event");
        accept(c, {{0,{3}}});
        c.finish();
        close(c.state().cumulativeActualDistance, .2, "floating boundary distance");

        data = base(30);
        customer(data, 10, 1, 0, 10, 5); // Service may FINISH after dueDate.
        DynamicController starts(data, 100);
        accept(starts, {{0,{1}}});
        starts.finish();
        close(starts.state().currentTime, 25, "window constrains service start only");
        data[1][5] = 10 - .5e-6;
        DynamicController within(data, 100);
        accept(within, {{0,{1}}}); // RouteOpt's feasibility tolerance.
        data[1][5] = 10 - 2e-6;
        DynamicController outside(data, 100);
        rejects([&] { accept(outside, {{0,{1}}}); }, "feasibility tolerance too loose");
    }

    void stopAtLaterStage() {
        auto data = base();
        customer(data, 10);
        customer(data, 20, 1, 0, 150, 0, 5);
        DynamicController c(data, 100);
        accept(c, {{0,{1}}});
        c.advanceTo(5);
        c.acceptSolution({1, SolveStatus::TimeLimitWithSolution, {{1,{2}}}, "mock timeout with feasible incumbent"});
        close(c.state().cumulativeActualDistance, 5, "stop must not execute old or incumbent plan");
        close(c.state().currentTime, 5, "stop simulation clock");
        check(c.state().servedCustomerIds.empty(), "stop must not finish commitment");
        check(c.records().back().problem.externalStarts.size() == 1, "stop snapshot missing");
        rejects([&] { c.advanceTo(10); }, "advance after timeout");
        rejects([&] { accept(c, {{1,{2}}}); }, "resume after timeout");
    }

    void partitionProperty() {
        // Independent unchanged-route oracle: all later requests are at the real depot,
        // so the total is always 0->A->B->0 regardless of event partitioning.
        for (int sample = 1; sample <= 60; ++sample) {
            auto data = base(1000);
            const double a = 1 + sample % 13, b = 15 + sample % 11;
            const double ready = sample % 20, service = sample % 7;
            customer(data, a, 10, ready, 900, service);
            customer(data, b, 10, 0, 900, service);
            const double end = std::max(a, ready) + service + (b-a) + service + b;
            for (int e = 1; e <= 8; ++e) customer(data, 0, 0, 0, 900, 0, end * e / 9);
            DynamicController c(data, 100);
            accept(c, {{0,{1,2}}});
            while (auto next = c.nextEventTime()) {
                c.advanceTo(*next);
                const auto stage = c.buildStageProblem();
                std::vector<int> continuation;
                std::vector<StageRoute> routes;
                for (const auto& node : stage.customers) {
                    if (node.id <= 2) continuation.push_back(node.id);
                    else routes.push_back({0,{node.id}});
                }
                for (const auto& start : stage.externalStarts)
                    routes.push_back({start.startKey, continuation});
                accept(c, routes);
                close(c.state().cumulativeActualDistance + c.records().back().totalPlannedCost,
                      2*b, "actual + remaining conservation");
            }
            c.finish();
            close(c.state().cumulativeActualDistance, 2*b, "partition-independent total");
            close(c.state().currentTime, end, "unchanged-route end time");
            check(c.state().servedCustomerIds.size() == 10, "property coverage");
        }
    }

    void badInput() {
        auto data = base(); customer(data, 1);
        rejects([&] { DynamicController c(data, 0); }, "zero capacity");
        rejects([&] { DynamicController c(data, std::numeric_limits<double>::infinity()); }, "infinite capacity");
        auto broken = data; broken[1].pop_back();
        rejects([&] { DynamicController c(broken, 100); }, "missing input field");
        broken = data; broken[1][1] = std::numeric_limits<double>::quiet_NaN();
        rejects([&] { DynamicController c(broken, 100); }, "NaN coordinates");
        broken = data; broken[1][0] = 0;
        rejects([&] { DynamicController c(broken, 100); }, "duplicate input ID");
        rejects([&] { routeOptDistance({1e300,0},{0,0}); }, "distance overflow");
    }

    void readerIntegration(const std::string& path) {
        std::string program = "dvrp_dynamic_test", file = path, fName, instance;
        char* args[]{program.data(), file.data()};
        int vehicles = 0, dim = 0;
        double capacity = 0;
        Input data;
        DVRP_ReadDataController reader(2, args, fName, instance, vehicles, capacity, dim, data);
        DynamicController c(data, capacity, instance);
        check(c.eventTimes() == std::vector<double>({0,25,100}), "reader disclosure events");
        accept(c, {});
        c.advanceTo(25);
        accept(c, {{0,{1}}});
        c.advanceTo(100);
        check(c.state().vehicles[0].phase == VehiclePhase::Finished, "first trip before second request");
        accept(c, {{0,{2}}});
        c.finish();
        close(c.state().cumulativeActualDistance, 89.2, "reader-to-execution total");
        check(c.records()[0].problem.instanceName == "minimal_valid", "instance identity");
        std::cout << "stage\ttime\tcustomers\texternal_starts\tfixed_cost\troute_cost\tactual_to_next\n";
        for (const auto& record : c.records()) {
            std::cout << record.problem.stageId << '\t' << record.problem.currentTime << '\t'
                      << record.problem.customers.size() << '\t' << record.problem.externalStarts.size() << '\t'
                      << record.problem.fixedCostAfterTau << '\t' << record.routeCost << '\t'
                      << record.actualDistanceToNextEvent << '\n';
        }
        std::cout << "completed_actual_distance=" << c.state().cumulativeActualDistance << '\n';
    }
}

int main(int argc, char* argv[]) {
    const std::vector<std::pair<std::string, std::function<void()>>> tests{
        {"events", eventsAndOwnership}, {"commitment", commitmentAndLoad},
        {"return", fixedReturnAndNoReuse}, {"distance", distancePartitionAndTail},
        {"validation", atomicValidation}, {"time", deadlinesAndZeroTime}, {"stop", stopStatuses},
        {"external", externalValidation}, {"loaded", loadedVehicleProjection}, {"external_deadline", externalReturnDeadline},
        {"empty", emptyAndFutureStages}, {"arithmetic", arithmeticBoundaries},
        {"stop_later", stopAtLaterStage}, {"partition", partitionProperty}, {"input", badInput}
    };
    try {
        if (argc == 3 && std::string(argv[1]) == "reader") {
            readerIntegration(argv[2]);
            std::cout << "PASS dynamic reader integration\n";
            return 0;
        }
        int executed = 0;
        for (const auto& [name, test] : tests) {
            if (argc == 2 && name != argv[1]) continue;
            test();
            ++executed;
            std::cout << "PASS dynamic " << name << '\n';
        }
        check(executed > 0, "unknown test name");
        return 0;
    } catch (const std::exception& error) {
        std::cerr << "FAIL dynamic: " << error.what() << '\n';
        return 1;
    }
}

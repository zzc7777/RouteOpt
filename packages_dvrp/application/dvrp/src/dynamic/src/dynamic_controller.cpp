#include "dynamic_controller.hpp"
#include "route_opt_macro.hpp"

#include <algorithm>
#include <cmath>
#include <limits>
#include <stdexcept>
#include <utility>

namespace RouteOpt::Application::DVRP {
    namespace {
        // Match floating arithmetic at event boundaries, not the much wider feasibility
        // tolerance. Disclosure timestamps themselves are compared/sorted exactly.
        bool sameExecutionTime(double a, double b) {
            return std::abs(a - b) <= 8 * std::numeric_limits<double>::epsilon()
                   * std::max({1.0, std::abs(a), std::abs(b)});
        }
        bool before(double a, double b) { return a < b && !sameExecutionTime(a, b); }

        double travelledOnArc(double departure, double arrival, double length, double time) {
            if (time <= departure) return 0;
            if (time >= arrival) return length;
            return length * ((time - departure) / (arrival - departure));
        }
        Location interpolate(Location from, Location to, double ratio) {
            ratio = std::clamp(ratio, 0.0, 1.0);
            return {from.x + ratio * (to.x - from.x), from.y + ratio * (to.y - from.y)};
        }
        bool isExternal(VehiclePhase phase) {
            return phase != VehiclePhase::Returning && phase != VehiclePhase::Finished;
        }
        void requireFinite(double value, const char* name) {
            if (!std::isfinite(value)) throw std::invalid_argument(std::string(name) + " must be finite");
        }
    }

    double routeOptDistance(Location from, Location to) {
        // Adapted from packages/application/cvrp/src/initial/src/initial_controller.cpp,
        // Copyright (c) 2025 Zhengzhong (Ricky) You, RouteOpt, GPL-3.0.
        constexpr double distanceTolerance = 1e-6; // original VRPTW_DISTANCE_TOLERANCE
        const double dx = from.x - to.x;
        const double dy = from.y - to.y;
        const double euclidean = std::sqrt(static_cast<float>(dx * dx + dy * dy));
        const double distance = std::floor(10 * (euclidean + distanceTolerance)) / 10;
        requireFinite(distance, "arc distance");
        return distance;
    }

    DynamicController::DynamicController(const std::vector<std::vector<double>>& infoVertex,
                                         double capacity, std::string instanceName)
        : capacity_(capacity), instanceName_(std::move(instanceName)) {
        requireFinite(capacity_, "capacity");
        if (capacity_ <= 0 || std::floor(capacity_) != capacity_)
            throw std::invalid_argument("capacity must be a positive integer");
        if (infoVertex.size() < 2 || infoVertex.size() > MAX_NUM_CUSTOMERS)
            throw std::invalid_argument("expected depot and customers within MAX_NUM_CUSTOMERS");
        events_.push_back(0);
        for (std::size_t i = 0; i < infoVertex.size(); ++i) {
            const auto& row = infoVertex[i];
            if (row.size() != 8) throw std::invalid_argument("dynamic input must have eight fields");
            for (double value : row) requireFinite(value, "node field");
            if (row[0] != static_cast<double>(i) || std::floor(row[3]) != row[3]
                || row[3] < 0 || row[3] > capacity_ || row[4] > row[5]
                || row[6] < 0 || row[7] < 0 || row[7] > row[5])
                throw std::invalid_argument("invalid dynamic node " + std::to_string(i));
            nodes_.push_back({static_cast<int>(i), {row[1], row[2]}, row[3], row[4], row[5], row[6]});
            disclosureTimes_.push_back(row[7]);
            if (i > 0) events_.push_back(row[7]);
        }
        if (infoVertex[0][3] != 0 || infoVertex[0][6] != 0 || infoVertex[0][7] != 0)
            throw std::invalid_argument("depot demand, service and disclosure must be zero");
        std::sort(events_.begin(), events_.end());
        events_.erase(std::unique(events_.begin(), events_.end()), events_.end());
        refreshState(true);
    }

    void DynamicController::requireRunning() const {
        if (state_.status != SimulationStatus::Running)
            throw std::logic_error("simulation has already stopped or completed");
    }

    void DynamicController::refreshState(bool holdCompletionAtEvent) {
        const auto previouslyServed = state_.servedCustomerIds;
        state_.vehicles.clear();
        state_.servedCustomerIds.clear();
        state_.releasedCustomerIds.clear();
        state_.remainingCustomerIds.clear();
        std::vector<bool> served(nodes_.size(), false), committed(nodes_.size(), false);
        const double time = state_.currentTime;
        for (const auto& plan : plans_) {
            VehicleState vehicle;
            vehicle.vehicleId = plan.vehicleId;
            bool atCustomer = false;
            for (std::size_t index = 0; index < plan.visits.size(); ++index) {
                const auto& visit = plan.visits[index];
                const auto& customer = nodes_[visit.customerId];
                vehicle.retainedVisits = index + 1;
                if (!before(time, visit.completionTime)) {
                    served[visit.customerId] = true;
                    vehicle.actualLoad += customer.demand;
                    const bool alreadyExecuted = std::binary_search(previouslyServed.begin(), previouslyServed.end(),
                                                                    visit.customerId);
                    if (!(holdCompletionAtEvent && !alreadyExecuted && sameExecutionTime(time, visit.completionTime))) continue;
                    vehicle.phase = VehiclePhase::ReadyForReplan;
                    vehicle.actualPosition = customer.location;
                    vehicle.anchorCustomerId = customer.id;
                    vehicle.availableTime = time;
                    vehicle.remainingCapacityAfterCommitment = capacity_ - vehicle.actualLoad;
                } else {
                    committed[visit.customerId] = true;
                    vehicle.committedCustomerId = customer.id;
                    vehicle.anchorCustomerId = customer.id;
                    vehicle.availableTime = visit.completionTime;
                    vehicle.remainingCapacityAfterCommitment = capacity_ - vehicle.actualLoad - customer.demand;
                    if (before(time, visit.arrivalTime)) {
                        vehicle.phase = VehiclePhase::Driving;
                        const double distance = travelledOnArc(visit.departureTime, visit.arrivalTime,
                                                               visit.incomingDistance, time);
                        vehicle.fixedRemainingDistance = visit.incomingDistance - distance;
                        vehicle.actualPosition = interpolate(nodes_[visit.fromCustomerId].location, customer.location,
                                                             distance / visit.incomingDistance);
                    } else {
                        vehicle.phase = before(time, visit.serviceStartTime) ? VehiclePhase::Waiting : VehiclePhase::Serving;
                        vehicle.actualPosition = customer.location;
                    }
                }
                atCustomer = true;
                break;
            }
            if (!atCustomer) {
                const int last = plan.visits.back().customerId;
                vehicle.anchorCustomerId = last;
                vehicle.availableTime = plan.returnArrivalTime;
                vehicle.remainingCapacityAfterCommitment = capacity_ - vehicle.actualLoad;
                if (before(time, plan.returnArrivalTime)) {
                    vehicle.phase = VehiclePhase::Returning;
                    const double distance = travelledOnArc(plan.returnDepartureTime, plan.returnArrivalTime,
                                                           plan.returnDistance, time);
                    vehicle.fixedRemainingDistance = plan.returnDistance - distance;
                    vehicle.actualPosition = interpolate(nodes_[last].location, nodes_[0].location,
                                                         distance / plan.returnDistance);
                } else {
                    vehicle.phase = VehiclePhase::Finished;
                    vehicle.actualPosition = nodes_[0].location;
                }
            }
            state_.vehicles.push_back(vehicle);
        }
        for (std::size_t i = 1; i < nodes_.size(); ++i) {
            if (served[i]) state_.servedCustomerIds.push_back(static_cast<int>(i));
            if (disclosureTimes_[i] <= time) {
                state_.releasedCustomerIds.push_back(static_cast<int>(i));
                if (!served[i] && !committed[i]) state_.remainingCustomerIds.push_back(static_cast<int>(i));
            }
        }
    }

    StageProblem DynamicController::buildStageProblem() const {
        requireRunning();
        if (!state_.awaitingSolution) throw std::logic_error("stage already has a result");
        StageProblem stage;
        stage.stageId = state_.stageId;
        stage.instanceName = instanceName_;
        stage.currentTime = state_.currentTime;
        stage.originStart = {0, StartKind::Origin, 0, nodes_[0].location, state_.currentTime, capacity_};
        stage.returnDepot = {nodes_[0].location, nodes_[0].dueDate};
        for (int id : state_.remainingCustomerIds) stage.customers.push_back(nodes_[id]);
        for (const auto& vehicle : state_.vehicles) {
            stage.fixedCostAfterTau += vehicle.fixedRemainingDistance;
            if (isExternal(vehicle.phase)) {
                stage.externalStarts.push_back({vehicle.vehicleId, StartKind::External,
                    vehicle.anchorCustomerId, nodes_[vehicle.anchorCustomerId].location,
                    vehicle.availableTime, vehicle.remainingCapacityAfterCommitment});
            }
        }
        return stage;
    }

    RouteExecution DynamicController::validateRoute(const StageProblem& stage, const StageRoute& route) const {
        const StartSpec* start = &stage.originStart;
        if (route.startKey != 0) {
            const auto found = std::find_if(stage.externalStarts.begin(), stage.externalStarts.end(),
                                           [&](const StartSpec& s) { return s.startKey == route.startKey; });
            if (found == stage.externalStarts.end()) throw std::invalid_argument("unknown/inactive route startKey");
            start = &*found;
        } else if (route.customerSequence.empty()) {
            throw std::invalid_argument("an origin route must serve at least one customer");
        }
        RouteExecution execution;
        execution.selection = route;
        double time = start->startTime;
        double demand = 0;
        int previous = start->locationCustomerId;
        std::vector<bool> seen(nodes_.size(), false);
        for (int id : route.customerSequence) {
            const auto found = std::find_if(stage.customers.begin(), stage.customers.end(),
                                           [&](const StageCustomer& c) { return c.id == id; });
            if (found == stage.customers.end())
                throw std::invalid_argument("route customer is not in the current planning set: " + std::to_string(id));
            if (seen[id]) throw std::invalid_argument("repeated route customer: " + std::to_string(id));
            seen[id] = true;
            const auto& customer = *found;
            demand += customer.demand;
            if (demand > start->remainingCapacity + TOLERANCE)
                throw std::invalid_argument("route exceeds start remaining capacity");
            const double length = routeOptDistance(nodes_[previous].location, customer.location);
            const double arrival = time + length;
            const double serviceStart = std::max(arrival, customer.readyTime);
            const double completion = serviceStart + customer.serviceTime;
            requireFinite(completion, "service completion");
            if (serviceStart > customer.dueDate + TOLERANCE)
                throw std::invalid_argument("route violates customer time window: " + std::to_string(id));
            execution.visits.push_back({id, previous, time, arrival, serviceStart, completion, length});
            execution.travelCost += length;
            time = completion;
            previous = id;
        }
        execution.returnDistance = routeOptDistance(nodes_[previous].location, stage.returnDepot.location);
        execution.returnDepartureTime = time;
        execution.returnArrivalTime = time + execution.returnDistance;
        requireFinite(execution.returnArrivalTime, "return time");
        if (execution.returnArrivalTime > stage.returnDepot.deadline + TOLERANCE)
            throw std::invalid_argument("route violates return deadline H");
        execution.travelCost += execution.returnDistance;
        requireFinite(execution.travelCost, "route cost");
        return execution;
    }

    void DynamicController::acceptSolution(const StageSolution& solution) {
        const auto stage = buildStageProblem();
        if (solution.stageId != stage.stageId) throw std::invalid_argument("stale stage solution");
        StageRecord record;
        record.problem = stage;
        record.solution = solution;
        switch (solution.status) {
            case SolveStatus::Infeasible:
            case SolveStatus::TimeLimitWithSolution:
            case SolveStatus::TimeLimitWithoutSolution:
            case SolveStatus::Error:
                records_.push_back(std::move(record));
                state_.status = solution.status == SolveStatus::Infeasible ? SimulationStatus::StoppedInfeasible
                    : solution.status == SolveStatus::Error ? SimulationStatus::StoppedError : SimulationStatus::StoppedTimeLimit;
                state_.awaitingSolution = false;
                return; // Retain diagnostic incumbent; do not execute it or the previous plan.
            case SolveStatus::Optimal:
            case SolveStatus::Feasible: break;
            default: throw std::invalid_argument("invalid solve status");
        }

        std::vector<int> coverage(nodes_.size(), 0), startCoverage(plans_.size() + 1, 0);
        for (const auto& route : solution.routes) {
            auto execution = validateRoute(stage, route);
            for (int id : route.customerSequence)
                if (++coverage[id] != 1) throw std::invalid_argument("customer covered by multiple routes");
            if (route.startKey != 0 && ++startCoverage.at(static_cast<std::size_t>(route.startKey)) != 1)
                throw std::invalid_argument("external start selected multiple times");
            record.routeCost += execution.travelCost;
            record.validatedRoutes.push_back(std::move(execution));
        }
        for (const auto& customer : stage.customers)
            if (coverage[customer.id] != 1) throw std::invalid_argument("missing customer coverage");
        for (const auto& start : stage.externalStarts)
            if (startCoverage[start.startKey] != 1) throw std::invalid_argument("missing external continuation");
        for (const auto& plan : plans_)
            if (plan.returnArrivalTime > stage.returnDepot.deadline + TOLERANCE)
                throw std::logic_error("stored vehicle plan violates H");

        // Prepare replacement plans locally. Invalid input must never partly dispatch vehicles.
        auto nextPlans = plans_;
        int nextId = nextVehicleId_;
        for (const auto& execution : record.validatedRoutes) {
            VehiclePlan* plan;
            const int key = execution.selection.startKey;
            if (key == 0) {
                nextPlans.push_back({});
                plan = &nextPlans.back();
                plan->vehicleId = nextId++;
            } else {
                plan = &nextPlans.at(static_cast<std::size_t>(key - 1));
                plan->visits.resize(state_.vehicles.at(static_cast<std::size_t>(key - 1)).retainedVisits);
            }
            plan->visits.insert(plan->visits.end(), execution.visits.begin(), execution.visits.end());
            plan->returnDepartureTime = execution.returnDepartureTime;
            plan->returnArrivalTime = execution.returnArrivalTime;
            plan->returnDistance = execution.returnDistance;
        }
        record.executed = true;
        record.totalPlannedCost = stage.fixedCostAfterTau + record.routeCost;
        records_.push_back(std::move(record));
        plans_ = std::move(nextPlans);
        nextVehicleId_ = nextId;
        state_.awaitingSolution = false;
        refreshState(false); // Dispatch immediately, including finite zero-time chains.
    }

    double DynamicController::travelledUntil(const VehiclePlan& plan, double time) const {
        double distance = 0;
        for (const auto& visit : plan.visits)
            distance += travelledOnArc(visit.departureTime, visit.arrivalTime, visit.incomingDistance, time);
        return distance + travelledOnArc(plan.returnDepartureTime, plan.returnArrivalTime, plan.returnDistance, time);
    }

    void DynamicController::settleDistance(double time) {
        double increment = 0;
        for (const auto& plan : plans_)
            increment += travelledUntil(plan, time) - travelledUntil(plan, state_.currentTime);
        state_.cumulativeActualDistance += increment;
        if (!records_.empty()) records_.back().actualDistanceToNextEvent += increment;
        state_.currentTime = time;
    }

    std::optional<double> DynamicController::nextEventTime() const {
        if (state_.status != SimulationStatus::Running || eventIndex_ + 1 == events_.size()) return std::nullopt;
        return events_[eventIndex_ + 1];
    }

    void DynamicController::advanceTo(double time) {
        requireRunning();
        if (state_.awaitingSolution) throw std::logic_error("accept a stage result before advancing");
        const auto next = nextEventTime();
        if (!next || time != *next) throw std::invalid_argument("advanceTo requires the next disclosure event time");
        settleDistance(time);
        ++eventIndex_;
        ++state_.stageId;
        state_.awaitingSolution = true;
        refreshState(true);
    }

    void DynamicController::finish() {
        requireRunning();
        if (state_.awaitingSolution || nextEventTime())
            throw std::logic_error("cannot finish before every disclosure stage has an accepted solution");
        double time = state_.currentTime;
        for (const auto& plan : plans_) time = std::max(time, plan.returnArrivalTime);
        settleDistance(time);
        refreshState(false);
        if (state_.servedCustomerIds.size() + 1 != nodes_.size()
            || std::any_of(state_.vehicles.begin(), state_.vehicles.end(), [](const VehicleState& v) {
                return v.phase != VehiclePhase::Finished;
            })) throw std::logic_error("incomplete execution at finish");
        state_.status = SimulationStatus::Completed;
    }
}

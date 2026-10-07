#ifndef ROUTEOPT_DVRP_DYNAMIC_CONTROLLER_HPP
#define ROUTEOPT_DVRP_DYNAMIC_CONTROLLER_HPP

#include <optional>
#include "dynamic_data.hpp"

namespace RouteOpt::Application::DVRP {
    // Matches original VRPTW initial_controller.cpp (including its float sqrt).
    double routeOptDistance(Location from, Location to);

    class DynamicController {
    public:
        DynamicController(const std::vector<std::vector<double>>& infoVertex,
                          double capacity, std::string instanceName = {});

        StageProblem buildStageProblem() const;
        void acceptSolution(const StageSolution& solution);
        std::optional<double> nextEventTime() const;
        void advanceTo(double nextEventTime);
        void finish();

        DynamicState state() const { return state_; }
        std::vector<double> eventTimes() const { return events_; }
        std::vector<StageRecord> records() const { return records_; }

    private:
        struct VehiclePlan {
            int vehicleId{};
            std::vector<VisitSchedule> visits;
            double returnDepartureTime{};
            double returnArrivalTime{};
            double returnDistance{};
        };

        std::vector<StageCustomer> nodes_;
        std::vector<double> disclosureTimes_;
        double capacity_{};
        std::string instanceName_;
        std::vector<double> events_;
        std::size_t eventIndex_{};
        int nextVehicleId_{1};
        DynamicState state_;
        std::vector<VehiclePlan> plans_;
        std::vector<StageRecord> records_;

        void requireRunning() const;
        void refreshState(bool holdCompletionAtEvent);
        double travelledUntil(const VehiclePlan& plan, double time) const;
        void settleDistance(double time);
        RouteExecution validateRoute(const StageProblem& stage, const StageRoute& route) const;
    };
}
#endif

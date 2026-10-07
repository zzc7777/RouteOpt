#ifndef ROUTEOPT_DVRP_DYNAMIC_DATA_HPP
#define ROUTEOPT_DVRP_DYNAMIC_DATA_HPP

#include <cstddef>
#include <string>
#include <vector>

namespace RouteOpt::Application::DVRP {
    struct Location {
        double x{};
        double y{};
    };

    // Original input IDs are preserved; local pricing indices are a downstream concern.
    struct StageCustomer {
        int id{};
        Location location;
        double demand{};
        double readyTime{};
        double dueDate{};
        double serviceTime{};
    };

    enum class StartKind { Origin, External };

    struct StartSpec {
        int startKey{}; // 0 = unlimited origin; positive = stable physical vehicle ID.
        StartKind kind{StartKind::Origin};
        int locationCustomerId{}; // A location, NOT the start's identity.
        Location location;
        double startTime{};
        double remainingCapacity{};
    };

    struct ReturnDepot {
        Location location;
        double deadline{};
    };

    struct StageProblem {
        std::size_t stageId{};
        std::string instanceName;
        double currentTime{};
        std::vector<StageCustomer> customers;
        StartSpec originStart;
        std::vector<StartSpec> externalStarts;
        ReturnDepot returnDepot; // Separate from the origin even at the same coordinates.
        double fixedCostAfterTau{};
    };

    // Only an integer selection is accepted. Timing/cost are independently reconstructed.
    struct StageRoute {
        int startKey{};
        std::vector<int> customerSequence;
    };

    enum class SolveStatus {
        Optimal, Feasible, Infeasible, TimeLimitWithSolution, TimeLimitWithoutSolution, Error
    };

    struct StageSolution {
        std::size_t stageId{};
        SolveStatus status{SolveStatus::Error};
        std::vector<StageRoute> routes;
        std::string message;
    };

    struct VisitSchedule {
        int customerId{};
        int fromCustomerId{}; // Internal physical location ID; 0 is the real depot.
        double departureTime{};
        double arrivalTime{};
        double serviceStartTime{};
        double completionTime{};
        double incomingDistance{};
    };

    struct RouteExecution {
        StageRoute selection;
        std::vector<VisitSchedule> visits;
        double returnDepartureTime{};
        double returnArrivalTime{};
        double returnDistance{};
        double travelCost{};
    };

    enum class VehiclePhase { Driving, Waiting, Serving, ReadyForReplan, Returning, Finished };

    struct VehicleState {
        int vehicleId{};
        VehiclePhase phase{VehiclePhase::Finished};
        Location actualPosition;
        int committedCustomerId{}; // 0 means no committed customer.
        int anchorCustomerId{};
        double availableTime{}; // Completion of the commitment, or the held boundary time.
        double actualLoad{}; // Updated at service completion, not when projecting a start.
        double remainingCapacityAfterCommitment{};
        double fixedRemainingDistance{};
        std::size_t retainedVisits{}; // Executed prefix plus the current commitment.
    };

    enum class SimulationStatus { Running, Completed, StoppedInfeasible, StoppedTimeLimit, StoppedError };

    struct DynamicState {
        double currentTime{};
        std::size_t stageId{};
        bool awaitingSolution{true};
        SimulationStatus status{SimulationStatus::Running};
        std::vector<int> releasedCustomerIds;
        std::vector<int> servedCustomerIds;
        std::vector<int> remainingCustomerIds;
        std::vector<VehicleState> vehicles;
        double cumulativeActualDistance{};
    };

    struct StageRecord {
        StageProblem problem;
        StageSolution solution;
        std::vector<RouteExecution> validatedRoutes;
        bool executed{};
        double routeCost{}; // Cost fields are validated only when executed == true.
        double totalPlannedCost{}; // fixed + routes; NEVER sum this across stages.
        double actualDistanceToNextEvent{};
    };
}
#endif

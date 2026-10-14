#include "stage_reference.hpp"
#include "dynamic_controller.hpp"
#include "route_opt_macro.hpp"
#include <algorithm>
#include <chrono>
#include <cmath>
#include <functional>
#include <set>
#include <stdexcept>

namespace RouteOpt::Application::DVRP::Testing {
    namespace {
        void require(bool value, const std::string& reason) {
            if (!value) throw std::invalid_argument("stage reference: " + reason);
        }
        void finite(double value, const char* field) {
            require(std::isfinite(value), std::string(field) + " must be finite");
        }
        void location(Location p) { finite(p.x,"x"); finite(p.y,"y"); }
        void validateStage(const StageProblem& s) {
            require(s.customers.size() <= MAX_REFERENCE_CUSTOMERS, "at most 5 customers supported (not infeasibility)");
            require(s.externalStarts.size() <= MAX_REFERENCE_EXTERNAL_STARTS, "at most 5 external starts supported (not infeasibility)");
            finite(s.currentTime,"currentTime"); finite(s.fixedCostAfterTau,"fixedCostAfterTau");
            finite(s.returnDepot.deadline,"return deadline"); location(s.returnDepot.location);
            require(s.currentTime >= 0 && s.fixedCostAfterTau >= 0 && s.returnDepot.deadline >= 0, "negative time/cost/deadline");
            const auto& o = s.originStart;
            location(o.location); finite(o.startTime,"origin start time"); finite(o.remainingCapacity,"capacity");
            require(o.kind == StartKind::Origin && o.startKey == 0 && o.locationCustomerId == 0, "invalid origin identity");
            require(o.startTime == s.currentTime, "origin must depart at currentTime");
            require(o.remainingCapacity > 0 && std::floor(o.remainingCapacity) == o.remainingCapacity, "invalid origin capacity");
            require(o.location.x == s.returnDepot.location.x && o.location.y == s.returnDepot.location.y, "return location differs from physical depot");
            std::set<int> ids, starts;
            for (const auto& c : s.customers) {
                require(c.id > 0 && ids.insert(c.id).second, "customer IDs must be unique and positive");
                location(c.location); finite(c.demand,"demand"); finite(c.readyTime,"ready time");
                finite(c.dueDate,"due date"); finite(c.serviceTime,"service time");
                require(c.demand >= 0 && c.demand <= o.remainingCapacity && std::floor(c.demand) == c.demand, "invalid demand");
                require(c.readyTime <= c.dueDate && c.serviceTime >= 0, "invalid customer time window/service");
            }
            for (const auto& k : s.externalStarts) {
                location(k.location); finite(k.startTime,"external start time"); finite(k.remainingCapacity,"remaining capacity");
                require(k.kind == StartKind::External && k.startKey > 0 && starts.insert(k.startKey).second, "invalid/duplicate external startKey");
                require(k.locationCustomerId > 0 && !ids.contains(k.locationCustomerId), "external anchor cannot be a remaining customer");
                require(k.startTime >= s.currentTime, "external start precedes currentTime");
                require(k.remainingCapacity >= 0 && k.remainingCapacity <= o.remainingCapacity
                        && std::floor(k.remainingCapacity) == k.remainingCapacity, "invalid external remaining capacity");
            }
        }
        std::optional<double> routeCost(const StageProblem& s, const StartSpec& start,
                                        const std::vector<std::size_t>& sequence) {
            double time = start.startTime, cost = 0, demand = 0;
            Location previous = start.location;
            for (std::size_t index : sequence) {
                const auto& c = s.customers[index];
                demand += c.demand;
                if (demand > start.remainingCapacity + TOLERANCE) return std::nullopt;
                const double distance = routeOptDistance(previous,c.location);
                const double serviceStart = std::max(time + distance,c.readyTime);
                time = serviceStart + c.serviceTime;
                finite(time,"service completion");
                if (serviceStart > c.dueDate + TOLERANCE) return std::nullopt;
                cost += distance;
                previous = c.location;
            }
            const double distance = routeOptDistance(previous,s.returnDepot.location);
            finite(time + distance,"return time"); finite(cost + distance,"route cost");
            if (time + distance > s.returnDepot.deadline + TOLERANCE) return std::nullopt;
            return cost + distance;
        }
    }

    EnumeratedStage enumerateStage(const StageProblem& stage) {
        const auto began = std::chrono::steady_clock::now();
        validateStage(stage);
        std::vector<StartSpec> starts{stage.originStart};
        starts.insert(starts.end(),stage.externalStarts.begin(),stage.externalStarts.end());
        std::vector<EnumeratedRoute> routes;
        std::vector<std::size_t> sequence;
        std::vector<bool> seen(stage.customers.size(),false);
        for (const auto& start : starts) {
            std::function<void()> visit = [&] {
                if (start.kind == StartKind::External || !sequence.empty()) {
                    if (const auto cost = routeCost(stage,start,sequence)) {
                        StageRoute r{start.startKey,{}};
                        for (auto index : sequence) r.customerSequence.push_back(stage.customers[index].id);
                        routes.push_back({std::move(r),*cost});
                    }
                }
                // Exhaust ALL ordered subsets. A failed direct return does not prune extensions.
                for (std::size_t i = 0; i < seen.size(); ++i) {
                    if (seen[i]) continue;
                    seen[i] = true; sequence.push_back(i);
                    visit();
                    sequence.pop_back(); seen[i] = false;
                }
            };
            visit();
        }
        const double elapsed = std::chrono::duration<double>(std::chrono::steady_clock::now()-began).count();
        return EnumeratedStage(stage,std::move(routes),elapsed);
    }

    std::vector<double> computeReducedCosts(const EnumeratedStage& all, const StageDuals& duals) {
        const auto& s = all.problem();
        require(duals.customers.size() == s.customers.size()
                && duals.externalStarts.size() == s.externalStarts.size(), "dual key count mismatch");
        for (const auto& c : s.customers) {
            require(duals.customers.contains(c.id), "missing customer dual"); finite(duals.customers.at(c.id),"customer dual");
        }
        for (const auto& k : s.externalStarts) {
            require(duals.externalStarts.contains(k.startKey), "missing external dual"); finite(duals.externalStarts.at(k.startKey),"external dual");
        }
        std::vector<double> costs;
        for (const auto& r : all.routes()) {
            double rc = r.cost;
            for (int id : r.route.customerSequence) rc -= duals.customers.at(id);
            if (r.route.startKey != 0) rc -= duals.externalStarts.at(r.route.startKey);
            finite(rc,"reduced cost");
            costs.push_back(rc);
        }
        return costs;
    }
}

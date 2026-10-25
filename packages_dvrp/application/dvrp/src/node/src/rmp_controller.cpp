/*
 * Copyright (c) 2025 Zhengzhong (Ricky) You. RouteOpt, GPL-3.0.
 * DVRP adaptation: sparse row/column construction follows
 * packages/application/cvrp/src/node/src/node.cpp and
 * packages/application/cvrp/src/add_column/src/add_column.cpp.
 * Unlike the original single Big-M column, each equality has a Phase-I variable.
 */
#include "rmp_controller.hpp"
#include "dynamic_controller.hpp"
#include "solver.hpp"
#include "route_opt_macro.hpp"
#include <algorithm>
#include <chrono>
#include <cmath>
#include <limits>
#include <numeric>
#include <set>
#include <stdexcept>

namespace RouteOpt::Application::DVRP {
    namespace {
        void checked(int code, const char* operation) {
            if (code) throw std::runtime_error(std::string(operation) + ": solver API code=" + std::to_string(code));
        }
        void input(bool ok, const std::string& why) {
            if (!ok) throw std::invalid_argument("DVRP RMP: " + why);
        }
        void verify(bool ok, const char* why) {
            if (!ok) throw std::runtime_error(std::string("DVRP RMP verification: ") + why);
        }
        bool near(double a, double b) {
            return std::isfinite(a) && std::isfinite(b)
                && std::abs(a-b) <= TOLERANCE * std::max({1.0,std::abs(a),std::abs(b)});
        }
        void finite(double x, const char* field) { input(std::isfinite(x), std::string(field)+" must be finite"); }
        void location(Location p) { finite(p.x,"x"); finite(p.y,"y"); }
        int count(std::size_t size) {
            input(size <= static_cast<std::size_t>(std::numeric_limits<int>::max()),"solver index capacity exceeded");
            return static_cast<int>(size);
        }
        // Solver itself owns raw handles and is copyable; keep ownership here instead.
        struct ModelOwner {
            Solver solver{};
            ModelOwner() { solver.env = nullptr; solver.model = nullptr; }
            ModelOwner(const ModelOwner&) = delete;
            ModelOwner& operator=(const ModelOwner&) = delete;
            ~ModelOwner() {
                if (solver.model) solver.freeModel();
                if (solver.env) solver.freeEnv();
            }
        };
    }

    namespace RmpDetail {
        // Internal policy, shared by solveLp and deterministic status tests.
        RmpLpStatus classifyLpStatus(int rawStatus) {
            switch (Solver::classifyStatus(rawStatus,false).termination) {
                case SolverTermination::Optimal: return RmpLpStatus::OptimalRmp;
                case SolverTermination::TimeLimit: return RmpLpStatus::TimeLimit;
                default: return RmpLpStatus::Error; // Auxiliary LP infeasibility is NOT a stage proof.
            }
        }
    }

    struct DVRP_RmpController::Impl {
        StageProblem stage;
        RmpOptions options;
        ModelOwner owner;
        std::vector<RmpRow> rows;
        std::map<int,int> customerRows, externalRows;
        std::vector<RmpColumn> columns;
        using RouteKey = std::pair<int,std::vector<int>>;
        std::map<RouteKey,std::size_t> columnIds;
        RmpPhase phase{RmpPhase::Feasibility};
        std::size_t revision{};
        bool usable{true};
        std::optional<RmpLpResult> last;

        Impl(const StageProblem& source, RmpOptions opts) : stage(source), options(opts) {
            validateStage();
            auto& solver = owner.solver;
            checked(solver.loadEnv(nullptr),"load CPLEX environment/license");
            checked(solver.setEnvOutputFlag(0,true),"set output flag");
            checked(solver.setEnvThreads(1,true),"set threads");
            checked(solver.setEnvFeasibilityTol(TOLERANCE,true),"set feasibility tolerance");
            checked(solver.setEnvTimeLimit(options.timeLimitSeconds),"set LP time limit");
            checked(solver.newModel("dvrp_rmp",0,nullptr,nullptr,nullptr,nullptr,nullptr),"new model");
            checked(solver.setModelSense(SOLVER_MIN_SENSE),"minimize");
            const int nr = count(rows.size());
            if (nr) {
                std::vector<int> emptyBeg(nr,0), indices(nr);
                std::iota(indices.begin(),indices.end(),0);
                std::vector<char> senses(nr,SOLVER_EQUAL);
                std::vector<double> ones(nr,1), lower(nr,0), upper(nr,SOLVER_INFINITY);
                checked(solver.addConstraints(nr,0,emptyBeg.data(),nullptr,nullptr,senses.data(),ones.data(),nullptr),"add equality rows");
                // Artificial solver column h belongs ONLY to row h.
                checked(solver.addVars(nr,nr,indices.data(),indices.data(),ones.data(),ones.data(),
                                       lower.data(),upper.data(),nullptr,nullptr),"add artificial columns");
            }
            checked(solver.updateModel(),"update model");
        }
        void validateStage() {
            finite(options.timeLimitSeconds,"timeLimitSeconds");
            input(options.timeLimitSeconds >= 0,"timeLimitSeconds must be nonnegative");
            count(stage.customers.size()+stage.externalStarts.size());
            finite(stage.currentTime,"currentTime"); finite(stage.fixedCostAfterTau,"fixedCostAfterTau");
            finite(stage.returnDepot.deadline,"return deadline"); location(stage.returnDepot.location);
            input(stage.currentTime >= 0 && stage.fixedCostAfterTau >= 0 && stage.returnDepot.deadline >= 0,"negative time/cost/deadline");
            const auto& origin = stage.originStart;
            location(origin.location); finite(origin.startTime,"origin startTime"); finite(origin.remainingCapacity,"capacity");
            input(origin.kind == StartKind::Origin && origin.startKey == 0 && origin.locationCustomerId == 0,"invalid origin identity");
            input(origin.startTime == stage.currentTime,"origin startTime must equal currentTime");
            input(origin.remainingCapacity > 0 && std::floor(origin.remainingCapacity) == origin.remainingCapacity,"invalid origin capacity");
            input(origin.location.x == stage.returnDepot.location.x && origin.location.y == stage.returnDepot.location.y,"origin and return physical locations differ");
            for (const auto& c : stage.customers) {
                const int row = count(rows.size());
                input(c.id > 0 && customerRows.emplace(c.id,row).second,"duplicate/nonpositive customer ID");
                rows.push_back({RmpRowKind::Customer,c.id,row});
                location(c.location); finite(c.demand,"demand"); finite(c.readyTime,"readyTime");
                finite(c.dueDate,"dueDate"); finite(c.serviceTime,"serviceTime");
                input(c.demand >= 0 && c.demand <= origin.remainingCapacity && std::floor(c.demand) == c.demand,"invalid demand");
                input(c.readyTime <= c.dueDate && c.serviceTime >= 0,"invalid time window/service");
            }
            for (const auto& start : stage.externalStarts) {
                const int row = count(rows.size());
                input(start.kind == StartKind::External && start.startKey > 0
                      && externalRows.emplace(start.startKey,row).second,"invalid/duplicate external startKey");
                rows.push_back({RmpRowKind::ExternalStart,start.startKey,row});
                location(start.location); finite(start.startTime,"external startTime"); finite(start.remainingCapacity,"remainingCapacity");
                input(start.locationCustomerId > 0 && !customerRows.contains(start.locationCustomerId),"external anchor is not a remaining customer");
                input(start.startTime >= stage.currentTime,"external startTime precedes currentTime");
                input(start.remainingCapacity >= 0 && start.remainingCapacity <= origin.remainingCapacity
                      && std::floor(start.remainingCapacity) == start.remainingCapacity,"invalid external remainingCapacity");
            }
        }
        void requireUsable() const {
            if (!usable) throw std::logic_error("DVRP RMP invalidated by solver/numerical failure; rebuild the model");
        }
        void invalidateResult() { ++revision; last.reset(); }
        const StartSpec& startFor(int key) const {
            if (key == 0) return stage.originStart;
            const auto found = externalRows.find(key);
            input(found != externalRows.end(),"unknown startKey="+std::to_string(key));
            return stage.externalStarts[found->second-stage.customers.size()];
        }
        std::vector<int> rowIndices(const StageRoute& route) const {
            std::vector<int> result;
            for (int id : route.customerSequence) result.push_back(customerRows.at(id));
            if (route.startKey) result.push_back(externalRows.at(route.startKey));
            return result;
        }
        // Independent of test/stage_reference: never trust a caller-supplied route cost.
        std::optional<double> evaluate(const StageRoute& route) const {
            const auto& start = startFor(route.startKey);
            input(start.kind == StartKind::External || !route.customerSequence.empty(),"origin route must visit a customer");
            std::set<int> visited;
            double load = 0, time = start.startTime, cost = 0;
            Location position = start.location;
            bool feasible = true;
            for (int id : route.customerSequence) {
                const auto it = customerRows.find(id);
                input(it != customerRows.end(),"unknown customer ID="+std::to_string(id));
                input(visited.insert(id).second,"repeated customer ID="+std::to_string(id));
                const auto& c = stage.customers[it->second];
                const double length = routeOptDistance(position,c.location);
                load += c.demand; cost += length;
                const double begin = std::max(time+length,c.readyTime);
                time = begin+c.serviceTime;
                finite(load,"route load"); finite(cost,"route cost"); finite(time,"service completion");
                if (load > start.remainingCapacity+TOLERANCE || begin > c.dueDate+TOLERANCE) feasible = false;
                position = c.location;
            }
            const double length = routeOptDistance(position,stage.returnDepot.location);
            cost += length; time += length;
            finite(cost,"route cost"); finite(time,"return arrival");
            if (time > stage.returnDepot.deadline+TOLERANCE) feasible = false;
            return feasible ? std::optional<double>(cost) : std::nullopt;
        }
        RmpLpResult resultHeader() const {
            RmpLpResult r;
            r.stageId = stage.stageId; r.modelRevision = revision; r.phase = phase;
            return r;
        }
        void readOptimal(RmpLpResult& r) {
            auto& solver = owner.solver;
            const int nr = count(rows.size()), nc = count(columns.size()), total = nr+nc;
            std::vector<double> x(total), dual(nr), rc(total);
            double objective = 0;
            if (total) {
                checked(solver.getX(0,total,x.data()),"read primal");
                checked(solver.getRC(0,total,rc.data()),"read reduced costs");
                checked(solver.getObjVal(&objective),"read objective");
            }
            if (nr) checked(solver.getDual(0,nr,dual.data()),"read dual");
            std::vector<double> activity(nr,0);
            double actualObjective = 0, actualCost = 0, artificialMass = 0;
            for (double value : x) verify(std::isfinite(value) && value >= -TOLERANCE && value <= 1+TOLERANCE,"invalid primal variable");
            for (double value : dual) verify(std::isfinite(value),"nonfinite dual");
            for (int h = 0; h < nr; ++h) {
                activity[h] = x[h]; artificialMass += std::max(0.0,x[h]);
                if (phase == RmpPhase::Feasibility) {
                    actualObjective += x[h];
                    verify(near(rc[h],1-dual[h]) && rc[h] >= -TOLERANCE,"artificial dual/reduced cost");
                    verify(near(x[h]*rc[h],0),"artificial complementarity");
                } else verify(std::abs(x[h]) <= TOLERANCE,"nonzero fixed artificial variable");
            }
            for (const auto& col : columns) {
                const double value = x[col.lpColumn];
                const double objectiveCoefficient = phase == RmpPhase::Cost ? col.cost : 0;
                double expectedRc = objectiveCoefficient;
                for (int h : rowIndices(col.route)) { activity[h] += value; expectedRc -= dual[h]; }
                verify(near(rc[col.lpColumn],expectedRc) && expectedRc >= -TOLERANCE,"route dual/reduced cost");
                verify(near(value*expectedRc,0),"route complementarity");
                actualCost += value*col.cost; actualObjective += value*objectiveCoefficient;
            }
            for (double value : activity) verify(std::abs(value-1) <= TOLERANCE,"coverage/start equality residual");
            verify(near(objective,actualObjective),"primal objective mismatch");
            verify(near(objective,std::accumulate(dual.begin(),dual.end(),0.0)),"primal/dual objective gap");
            RmpDuals d{stage.stageId,revision,phase,{},{}};
            for (const auto& [id,h] : customerRows) d.customers.emplace(id,dual[h]);
            for (const auto& [key,h] : externalRows) d.externalStarts.emplace(key,dual[h]);
            r.lambda.assign(x.begin()+nr,x.end()); r.artificial.assign(x.begin(),x.begin()+nr);
            r.reducedCosts.assign(rc.begin()+nr,rc.end()); r.duals = std::move(d);
            r.objective = objective; r.artificialSum = artificialMass;
            if (phase == RmpPhase::Cost) {
                verify(std::isfinite(actualCost+stage.fixedCostAfterTau),"reported cost overflow");
                r.routeCost = actualCost; r.totalPlannedCost = actualCost+stage.fixedCostAfterTau;
            }
        }
    };

    DVRP_RmpController::DVRP_RmpController(const StageProblem& stage, RmpOptions options)
        : impl_(std::make_unique<Impl>(stage,options)) {}
    DVRP_RmpController::~DVRP_RmpController() = default;

    const std::vector<RmpColumn>& DVRP_RmpController::columns() const { impl_->requireUsable(); return impl_->columns; }

    std::vector<std::size_t> DVRP_RmpController::addRoutes(const std::vector<StageRoute>& routes) {
        auto& p = *impl_; p.requireUsable();
        std::vector<std::size_t> ids;
        std::vector<RmpColumn> pending;
        std::map<Impl::RouteKey,std::size_t> pendingIds;
        // Validate the WHOLE batch and allocate metadata before touching the solver.
        for (const auto& route : routes) {
            const auto cost = p.evaluate(route);
            input(cost.has_value(),"route violates capacity, time window or return deadline");
            Impl::RouteKey key{route.startKey,route.customerSequence};
            if (const auto found = p.columnIds.find(key); found != p.columnIds.end()) ids.push_back(found->second);
            else if (const auto added = pendingIds.find(key); added != pendingIds.end()) ids.push_back(added->second);
            else {
                const auto id = p.columns.size()+pending.size();
                const int lp = count(p.rows.size()+id);
                pending.push_back({id,route,*cost,lp}); pendingIds.emplace(std::move(key),id); ids.push_back(id);
            }
        }
        if (pending.empty()) return ids;
        count(p.rows.size()+p.columns.size()+pending.size());
        std::vector<int> begins, indices;
        std::vector<double> values, objective, lower(pending.size(),0), upper(pending.size(),SOLVER_INFINITY);
        for (const auto& col : pending) {
            begins.push_back(count(indices.size()));
            auto rows = p.rowIndices(col.route);
            indices.insert(indices.end(),rows.begin(),rows.end()); values.insert(values.end(),rows.size(),1);
            objective.push_back(p.phase == RmpPhase::Cost ? col.cost : 0);
        }
        count(indices.size()); p.columns.reserve(p.columns.size()+pending.size());
        p.invalidateResult();
        try {
            checked(p.owner.solver.addVars(count(pending.size()),count(indices.size()),begins.data(),indices.data(),
                                           values.data(),objective.data(),lower.data(),upper.data(),nullptr,nullptr),"add real columns");
            checked(p.owner.solver.updateModel(),"update after columns");
            for (auto& col : pending) p.columns.push_back(std::move(col));
            p.columnIds.merge(pendingIds);
        } catch (...) { p.usable = false; throw; }
        return ids;
    }

    std::vector<std::size_t> DVRP_RmpController::addInitialRoutes() {
        auto& p = *impl_; p.requireUsable();
        std::vector<StageRoute> routes;
        auto tryRoute = [&](StageRoute route) { if (p.evaluate(route)) routes.push_back(std::move(route)); };
        for (const auto& start : p.stage.externalStarts) tryRoute({start.startKey,{}});
        for (const auto& c : p.stage.customers) {
            tryRoute({0,{c.id}});
            for (const auto& start : p.stage.externalStarts) tryRoute({start.startKey,{c.id}});
        }
        return addRoutes(routes);
    }

    RmpLpResult DVRP_RmpController::solveLp() {
        return solveLp(impl_->options.timeLimitSeconds);
    }

    RmpLpResult DVRP_RmpController::solveLp(double timeLimitSeconds) {
        if (!std::isfinite(timeLimitSeconds) || timeLimitSeconds < 0)
            throw std::invalid_argument("LP time limit must be finite and nonnegative");
        auto& p = *impl_;
        const auto begin = std::chrono::steady_clock::now();
        auto result = p.resultHeader(); p.last.reset();
        try {
            p.requireUsable();
            checked(p.owner.solver.setEnvTimeLimit(timeLimitSeconds),"LP time limit");
            if (p.rows.empty()) {
                result.status = RmpLpStatus::OptimalRmp;
                result.message = "Empty RMP: analytic optimum (no solver status)";
            } else {
                checked(p.owner.solver.optimize(),"LP optimize");
                int raw = 0; checked(p.owner.solver.getStatus(&raw),"LP status");
                result.rawStatus = raw; result.status = RmpDetail::classifyLpStatus(raw);
                result.message = "CPLEX LP status="+std::to_string(raw);
            }
            if (result.status == RmpLpStatus::OptimalRmp) { p.readOptimal(result); p.last = result; }
            else if (result.status == RmpLpStatus::Error) {
                p.usable = false;
                result.message += "; unexpected auxiliary LP failure, NOT proof of stage infeasibility";
            }
        } catch (const std::bad_alloc&) {
            p.usable = false; p.last.reset(); throw; // Let the root caller classify resource exhaustion.
        } catch (const std::exception& e) {
            const auto raw = result.rawStatus;
            result = p.resultHeader(); result.rawStatus = raw; result.message = e.what();
            p.usable = false; p.last.reset();
        }
        result.solveSeconds = std::chrono::duration<double>(std::chrono::steady_clock::now()-begin).count();
        return result;
    }

    void DVRP_RmpController::enterCostPhase() {
        auto& p = *impl_; p.requireUsable();
        if (p.phase != RmpPhase::Feasibility || !p.last || !p.last->artificialSum
            || *p.last->artificialSum > TOLERANCE)
            throw std::logic_error("enterCostPhase requires a CURRENT optimal Phase-I result with zero artificial mass");
        const int nr = count(p.rows.size()), total = count(p.rows.size()+p.columns.size());
        std::vector<double> objective(total,0);
        for (const auto& c : p.columns) objective[c.lpColumn] = c.cost;
        p.invalidateResult();
        try {
            for (int h = 0; h < nr; ++h) checked(p.owner.solver.setColUpper(h,0),"fix artificial to zero");
            if (total) checked(p.owner.solver.changeObj(0,total,objective.data()),"restore route costs");
            checked(p.owner.solver.updateModel(),"update cost phase");
            p.phase = RmpPhase::Cost;
        } catch (...) { p.usable = false; throw; }
    }

    RmpModelSnapshot DVRP_RmpController::inspectModel() const {
        auto& p = *impl_; p.requireUsable();
        auto& solver = p.owner.solver;
        RmpModelSnapshot s;
        s.stageId = p.stage.stageId; s.modelRevision = p.revision; s.phase = p.phase; s.rows = p.rows;
        try {
            int nr = 0, nc = 0;
            checked(solver.getNumRow(&nr),"inspect rows"); checked(solver.getNumCol(&nc),"inspect columns");
            verify(nr == count(p.rows.size()) && nc == count(p.rows.size()+p.columns.size()),"model dimensions");
            // Backend-specific inspection stays private; the public header has no CPLEX types.
            s.continuousLp = CPXgetprobtype(solver.env,solver.model) == CPXPROB_LP;
            verify(s.continuousLp,"expected continuous LP");
            s.rhs.resize(nr); s.senses.resize(nr);
            if (nr) { checked(solver.getRhs(0,nr,s.rhs.data()),"inspect RHS"); checked(solver.getSense(0,nr,s.senses.data()),"inspect sense"); }
            for (int col = 0; col < nc; ++col) {
                RmpSnapshotColumn c; c.lpColumn = col;
                if (col >= nr) c.routeId = static_cast<std::size_t>(col-nr);
                checked(solver.getObj(col,1,&c.objective),"inspect objective");
                checked(solver.getColLower(col,&c.lower),"inspect lower"); checked(solver.getColUpper(col,&c.upper),"inspect upper");
                for (int h = 0; h < nr; ++h) {
                    double a = 0; checked(solver.getCoeff(h,col,a),"inspect coefficient");
                    if (a != 0) c.coefficients.emplace_back(h,a);
                }
                s.columns.push_back(std::move(c));
            }
        } catch (...) { p.usable = false; p.last.reset(); throw; }
        return s;
    }
}

#include "stage_reference.hpp"
#include "solver.hpp"
#include "route_opt_macro.hpp"
#include <algorithm>
#include <chrono>
#include <cmath>
#include <numeric>
#include <stdexcept>

namespace RouteOpt::Application::DVRP::Testing {
    namespace {
        using Clock = std::chrono::steady_clock;
        void checked(int code, const char* operation) {
            if (code != 0) throw std::runtime_error(std::string(operation) + " failed, solver API code=" + std::to_string(code));
        }
        void require(bool value, const char* reason) {
            if (!value) throw std::runtime_error(reason);
        }
        bool equal(double a, double b) {
            return std::isfinite(a) && std::isfinite(b)
                && std::abs(a-b) <= TOLERANCE * std::max({1.0,std::abs(a),std::abs(b)});
        }
        void validateOptions(const ReferenceOptions& options) {
            require(std::isfinite(options.timeLimitSeconds) && options.timeLimitSeconds >= 0,
                    "timeLimitSeconds must be finite and nonnegative");
        }
        // Solver is a raw handle wrapper; this local owner must never be shallow-copied.
        struct ModelOwner {
            Solver solver{};
            ModelOwner() { solver.env = nullptr; solver.model = nullptr; }
            ModelOwner(const ModelOwner&) = delete;
            ModelOwner& operator=(const ModelOwner&) = delete;
            ~ModelOwner() {
                if (solver.model) solver.freeModel();
                if (solver.env) solver.freeEnv();
            }
            void open(const ReferenceOptions& options) {
                checked(solver.loadEnv(nullptr),"loadEnv (check CPLEX installation/license)");
                checked(solver.setEnvOutputFlag(0,true),"output flag");
                checked(solver.setEnvThreads(1,true),"threads");
                checked(solver.setEnvFeasibilityTol(TOLERANCE,true),"feasibility tolerance");
                checked(solver.setEnvIntFeasTol(TOLERANCE,true),"integrality tolerance");
                checked(solver.setEnvMIPGap(0,true),"relative MIP gap");
                checked(solver.setEnvMIPAbsGap(0),"absolute MIP gap");
                checked(solver.setEnvTimeLimit(options.timeLimitSeconds),"time limit");
            }
        };

        struct RowMap {
            std::map<int,int> customers, external;
            explicit RowMap(const StageProblem& s) {
                int row = 0;
                for (const auto& c : s.customers) customers.emplace(c.id,row++);
                for (const auto& k : s.externalStarts) external.emplace(k.startKey,row++);
            }
            int size() const { return static_cast<int>(customers.size()+external.size()); }
            std::vector<int> indices(const StageRoute& r) const {
                std::vector<int> rows;
                for (int id : r.customerSequence) rows.push_back(customers.at(id));
                if (r.startKey != 0) rows.push_back(external.at(r.startKey));
                return rows;
            }
        };

        void buildModel(Solver& solver, const EnumeratedStage& all, const RowMap& rows, bool mip) {
            checked(solver.newModel(mip ? "dvrp_full_mip" : "dvrp_full_lp",0,nullptr,nullptr,nullptr,nullptr,nullptr),"newModel");
            checked(solver.setModelSense(SOLVER_MIN_SENSE),"minimize");
            const int nr = rows.size(), nc = static_cast<int>(all.routes().size());
            std::vector<int> rb(nr,0);
            std::vector<char> senses(nr,SOLVER_EQUAL);
            std::vector<double> rhs(nr,1.0);
            checked(solver.addConstraints(nr,0,rb.data(),nullptr,nullptr,senses.data(),rhs.data(),nullptr),"add equality rows");
            std::vector<int> begins, indices;
            std::vector<double> values, objective;
            for (const auto& r : all.routes()) {
                begins.push_back(static_cast<int>(indices.size()));
                const auto ri = rows.indices(r.route);
                indices.insert(indices.end(),ri.begin(),ri.end());
                values.insert(values.end(),ri.size(),1.0);
                objective.push_back(r.cost);
            }
            std::vector<double> lower(nc,0), upper(nc,mip ? 1.0 : SOLVER_INFINITY);
            checked(solver.addVars(nc,static_cast<int>(indices.size()),begins.data(),indices.data(),values.data(),
                                   objective.data(),lower.data(),upper.data(),nullptr,nullptr),"add route columns");
            if (mip) {
                std::vector<char> types(nc,SOLVER_BINARY), actual(nc);
                // CPLEX addVars ignores vtype: explicitly change types, then verify.
                checked(solver.setVTypeArray(0,nc,types.data()),"set binary types");
                checked(solver.getVTypeArray(0,nc,actual.data()),"read variable types");
                require(actual == types,"MIP columns are not binary");
            }
            checked(solver.updateModel(),"updateModel");
            // This small reference tool checks what actually reached the solver, not just our arrays.
            int actualRows = 0, actualCols = 0;
            checked(solver.getNumRow(&actualRows),"row count"); checked(solver.getNumCol(&actualCols),"column count");
            require(actualRows == nr && actualCols == nc,"unexpected model dimensions");
            std::vector<double> actualRhs(nr), actualObj(nc);
            std::vector<char> actualSenses(nr);
            checked(solver.getRhs(0,nr,actualRhs.data()),"read rhs");
            checked(solver.getSense(0,nr,actualSenses.data()),"read senses");
            checked(solver.getObj(0,nc,actualObj.data()),"read objective");
            require(actualRhs == rhs && actualSenses == senses && actualObj == objective,"incorrect model data");
            for (int col = 0; col < nc; ++col) {
                const auto ri = rows.indices(all.routes()[col].route);
                for (int row = 0; row < nr; ++row) {
                    double a = 0;
                    checked(solver.getCoeff(row,col,a),"read coefficient");
                    const double expected = std::find(ri.begin(),ri.end(),row) != ri.end() ? 1.0 : 0.0;
                    require(a == expected,"incorrect coverage/start coefficient");
                }
            }
        }

        double validatePrimal(const EnumeratedStage& all, const RowMap& rows,
                              const std::vector<double>& x, double objective, bool integer) {
            require(x.size() == all.routes().size(),"solution size mismatch");
            std::vector<double> coverage(rows.size(),0);
            double cost = 0;
            for (std::size_t i = 0; i < x.size(); ++i) {
                require(std::isfinite(x[i]) && x[i] >= -TOLERANCE && x[i] <= 1+TOLERANCE,"invalid route variable value");
                if (integer) require(std::abs(x[i]-std::round(x[i])) <= TOLERANCE,"fractional MIP result");
                const double value = integer ? std::round(x[i]) : x[i];
                for (int row : rows.indices(all.routes()[i].route)) coverage[row] += value;
                cost += value * all.routes()[i].cost;
            }
            for (double a : coverage) require(std::abs(a-1.0) <= TOLERANCE,"solution violates a coverage/start equality");
            require(equal(cost,objective),"recomputed route cost differs from solver objective");
            require(std::isfinite(cost + all.problem().fixedCostAfterTau),"total planned cost overflow");
            return cost;
        }
        std::string statusMessage(int raw) { return "CPLEX raw status=" + std::to_string(raw); }
    }

    LpReferenceResult solveFullLp(const EnumeratedStage& all, const ReferenceOptions& options) {
        const auto began = Clock::now();
        LpReferenceResult result;
        try {
            validateOptions(options);
            const RowMap rows(all.problem());
            if (all.routes().empty()) {
                result.status = rows.size() ? LpReferenceStatus::Infeasible : LpReferenceStatus::Optimal;
                result.message = rows.size() ? "Complete route universe empty with required rows" : "Empty stage: analytic optimum";
                if (!rows.size()) {
                    result.routeCost = 0; result.totalPlannedCost = all.problem().fixedCostAfterTau;
                    result.duals = StageDuals{};
                }
            } else {
                ModelOwner owner; owner.open(options);
                auto& solver = owner.solver;
                buildModel(solver,all,rows,false);
                checked(solver.optimize(),"LP optimize");
                int raw = 0; checked(solver.getStatus(&raw),"LP status");
                result.rawStatus = raw; result.message = statusMessage(raw);
                const auto status = Solver::classifyStatus(raw,false);
                if (status.termination == SolverTermination::Infeasible) result.status = LpReferenceStatus::Infeasible;
                else if (status.termination == SolverTermination::TimeLimit) result.status = LpReferenceStatus::TimeLimit;
                else if (status.termination == SolverTermination::Optimal) {
                    std::vector<double> x(all.routes().size()), dual(rows.size()), rc(x.size());
                    double obj = 0;
                    checked(solver.getX(0,static_cast<int>(x.size()),x.data()),"LP primal");
                    checked(solver.getObjVal(&obj),"LP objective");
                    const double cost = validatePrimal(all,rows,x,obj,false);
                    checked(solver.getDual(0,rows.size(),dual.data()),"LP dual");
                    checked(solver.getRC(0,static_cast<int>(rc.size()),rc.data()),"LP reduced costs");
                    StageDuals d;
                    for (const auto& [id,row] : rows.customers) d.customers.emplace(id,dual[row]);
                    for (const auto& [key,row] : rows.external) d.externalStarts.emplace(key,dual[row]);
                    const auto expected = computeReducedCosts(all,d);
                    require(equal(std::accumulate(dual.begin(),dual.end(),0.0),cost),"LP primal/dual objective mismatch");
                    for (std::size_t i = 0; i < rc.size(); ++i) {
                        require(equal(rc[i],expected[i]),"solver reduced cost mismatch");
                        require(expected[i] >= -TOLERANCE,"negative reduced cost at complete LP optimum");
                        require(equal(x[i]*expected[i],0),"LP complementarity failure");
                    }
                    result.status = LpReferenceStatus::Optimal;
                    result.lambda = std::move(x); result.duals = std::move(d);
                    result.reducedCosts = expected; result.routeCost = cost;
                    result.totalPlannedCost = cost + all.problem().fixedCostAfterTau;
                }
            }
        } catch (const std::exception& e) {
            result.status = LpReferenceStatus::Error; result.message = e.what();
            result.lambda.clear(); result.duals.reset(); result.reducedCosts.clear();
            result.routeCost.reset(); result.totalPlannedCost.reset();
        }
        result.solveSeconds = std::chrono::duration<double>(Clock::now()-began).count();
        return result;
    }

    MipReferenceResult solveFullMip(const EnumeratedStage& all, const ReferenceOptions& options) {
        const auto began = Clock::now();
        MipReferenceResult result;
        result.solution.stageId = all.problem().stageId;
        try {
            validateOptions(options);
            const RowMap rows(all.problem());
            if (all.routes().empty()) {
                result.solution.status = rows.size() ? SolveStatus::Infeasible : SolveStatus::Optimal;
                result.solution.message = rows.size() ? "Complete route universe empty with required rows" : "Empty stage: analytic optimum";
                if (!rows.size()) {
                    result.routeCost = 0; result.bestBound = 0;
                    result.totalPlannedCost = all.problem().fixedCostAfterTau;
                }
            } else {
                ModelOwner owner; owner.open(options);
                auto& solver = owner.solver;
                buildModel(solver,all,rows,true);
                checked(solver.mipOptimize(),"MIP optimize");
                int raw = 0; checked(solver.getStatus(&raw),"MIP status");
                result.rawStatus = raw; result.solution.message = statusMessage(raw);
                const auto status = Solver::classifyStatus(raw,true);
                switch (status.termination) {
                    case SolverTermination::Optimal: result.solution.status = SolveStatus::Optimal; break;
                    case SolverTermination::Feasible: result.solution.status = SolveStatus::Feasible; break;
                    case SolverTermination::Infeasible: result.solution.status = SolveStatus::Infeasible; break;
                    case SolverTermination::TimeLimit:
                        result.solution.status = status.hasPrimal ? SolveStatus::TimeLimitWithSolution : SolveStatus::TimeLimitWithoutSolution;
                        break;
                    default: result.solution.status = SolveStatus::Error; break;
                }
                if (status.termination == SolverTermination::Optimal || status.termination == SolverTermination::Feasible
                    || status.termination == SolverTermination::TimeLimit) {
                    double bound = 0;
                    const int code = solver.getMipBestBound(&bound);
                    if (!code && std::isfinite(bound) && std::abs(bound) < SOLVER_INFINITY) result.bestBound = bound;
                }
                if (status.hasPrimal) {
                    std::vector<double> x(all.routes().size());
                    double obj = 0;
                    checked(solver.getX(0,static_cast<int>(x.size()),x.data()),"MIP incumbent");
                    checked(solver.getObjVal(&obj),"MIP objective");
                    const double cost = validatePrimal(all,rows,x,obj,true);
                    if (status.termination == SolverTermination::Optimal)
                        require(result.bestBound && equal(*result.bestBound,cost),"optimal MIP has no matching bound");
                    for (std::size_t i = 0; i < x.size(); ++i)
                        if (x[i] > 0.5) result.solution.routes.push_back(all.routes()[i].route);
                    result.routeCost = cost; result.totalPlannedCost = cost + all.problem().fixedCostAfterTau;
                }
            }
        } catch (const std::exception& e) {
            result.solution.status = SolveStatus::Error; result.solution.message = e.what();
            result.solution.routes.clear(); result.routeCost.reset(); result.totalPlannedCost.reset(); result.bestBound.reset();
        }
        result.solveSeconds = std::chrono::duration<double>(Clock::now()-began).count();
        return result;
    }
}

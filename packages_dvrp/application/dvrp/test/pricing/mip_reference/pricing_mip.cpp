// Independent test formulation: optional customer path, time windows, capacity,
// explicit MTZ order (also eliminates disconnected zero-time cycles).
// Uses only the DVRP Solver wrapper; no label/bucket/production distance helpers.
#include "pricing_mip.hpp"
#include "solver.hpp"
#include <algorithm>
#include <chrono>
#include <cmath>
#include <set>
#include <stdexcept>

namespace RouteOpt::Application::DVRP::Testing {
    namespace {
        constexpr double EPS = 1e-6;
        using Clock = std::chrono::steady_clock;
        void require(bool ok, const std::string& reason) {
            if (!ok) throw std::runtime_error("pricing MIP: " + reason);
        }
        void checked(int code, const char* name) {
            require(code == 0, std::string(name) + " API code=" + std::to_string(code));
        }
        bool finite(double x) { return std::isfinite(x); }
        bool located(Location p) { return finite(p.x) && finite(p.y); }
        double distance(Location a, Location b) {
            const double dx = a.x-b.x, dy = a.y-b.y;
            const float square = static_cast<float>(dx*dx + dy*dy);
            const double value = std::floor((static_cast<double>(std::sqrt(square))+1e-6)*10.)/10.;
            require(finite(value), "nonfinite distance");
            return value;
        }
        const StartSpec& getStart(const StageProblem& s, int key) {
            if (key == 0) return s.originStart;
            for (const auto& k : s.externalStarts) if (k.startKey == key) return k;
            throw std::runtime_error("pricing MIP: unknown start key");
        }
        void validate(const StageProblem& s, const RmpDuals& d) {
            require(s.customers.size() <= MAX_PRICING_MIP_CUSTOMERS, "test-only 50 customer guard");
            require(d.stageId == s.stageId && (d.phase == RmpPhase::Cost || d.phase == RmpPhase::Feasibility), "dual context");
            require(finite(s.currentTime) && s.currentTime >= 0 && finite(s.returnDepot.deadline)
                && s.returnDepot.deadline >= 0 && located(s.returnDepot.location), "invalid stage time/location");
            const auto& o=s.originStart;
            require(o.kind==StartKind::Origin && o.startKey==0 && o.locationCustomerId==0
                && o.startTime==s.currentTime && located(o.location)
                && o.location.x==s.returnDepot.location.x && o.location.y==s.returnDepot.location.y
                && finite(o.remainingCapacity) && o.remainingCapacity>0
                && std::floor(o.remainingCapacity)==o.remainingCapacity, "invalid origin");
            require(finite(s.fixedCostAfterTau) && s.fixedCostAfterTau>=0, "invalid fixed cost");
            std::set<int> ids, keys;
            require(d.customers.size()==s.customers.size() && d.externalStarts.size()==s.externalStarts.size(), "dual key count");
            for (const auto& c:s.customers) {
                require(c.id>0 && ids.insert(c.id).second && located(c.location), "invalid customer ID/location");
                require(finite(c.demand) && c.demand>=0 && c.demand<=o.remainingCapacity
                    && std::floor(c.demand)==c.demand && finite(c.readyTime) && finite(c.dueDate)
                    && c.readyTime<=c.dueDate && finite(c.serviceTime) && c.serviceTime>=0, "invalid customer resources");
                require(d.customers.contains(c.id) && finite(d.customers.at(c.id)), "invalid customer dual");
            }
            for (const auto& k:s.externalStarts) {
                require(k.kind==StartKind::External && k.startKey>0 && keys.insert(k.startKey).second
                    && k.locationCustomerId>0 && !ids.contains(k.locationCustomerId) && located(k.location)
                    && finite(k.startTime) && k.startTime>=s.currentTime && finite(k.remainingCapacity)
                    && k.remainingCapacity>=0 && k.remainingCapacity<=o.remainingCapacity
                    && std::floor(k.remainingCapacity)==k.remainingCapacity, "invalid external start");
                require(d.externalStarts.contains(k.startKey) && finite(d.externalStarts.at(k.startKey)), "invalid external dual");
            }
        }
        struct Owner {
            Solver solver{};
            Owner() { solver.model=nullptr; solver.env=nullptr; }
            Owner(const Owner&)=delete;
            Owner& operator=(const Owner&)=delete;
            ~Owner() { if(solver.model)solver.freeModel(); if(solver.env)solver.freeEnv(); }
            void open(double seconds) {
                checked(solver.loadEnv(nullptr), "loadEnv");
                checked(solver.setEnvOutputFlag(0,true), "output");
                checked(solver.setEnvThreads(1,true), "threads");
                checked(solver.setEnvFeasibilityTol(1e-9,true), "feasibility tolerance");
                checked(solver.setEnvIntFeasTol(1e-9,true), "integrality tolerance");
                checked(solver.setEnvMIPGap(0,true), "relative gap");
                checked(solver.setEnvMIPAbsGap(0), "absolute gap");
                checked(solver.setEnvTimeLimit(seconds), "time limit");
            }
        };
        struct Arc { int from, to, variable; double length; };
        struct Model {
            Solver& solver;
            std::vector<double> obj, lower, upper;
            std::vector<char> types;
            std::vector<Arc> arcs;
            std::vector<int> y, time, order;
            int sinkTime{};
            int variable(double cost, double lo, double hi, char type=SOLVER_CONTINUOUS) {
                require(finite(cost) && finite(lo) && finite(hi) && lo<=hi, "invalid variable bounds/objective");
                const int v=static_cast<int>(obj.size());
                obj.push_back(cost); lower.push_back(lo); upper.push_back(hi); types.push_back(type);
                return v;
            }
            void row(const std::vector<std::pair<int,double>>& terms, char sense, double rhs) {
                std::vector<int> indices; std::vector<double> values;
                sense=sense=='='?SOLVER_EQUAL:sense=='<'?SOLVER_LESS_EQUAL:SOLVER_GREATER_EQUAL;
                require(finite(rhs), "nonfinite RHS");
                for(const auto& [i,v]:terms) if(v!=0) {
                    require(finite(v), "nonfinite coefficient"); indices.push_back(i); values.push_back(v);
                }
                checked(solver.addConstraint(static_cast<int>(indices.size()),indices.data(),values.data(),sense,rhs,nullptr), "add row");
            }
            void build(const StageProblem& s, const RmpDuals& d, const StartSpec& k) {
                const int n=static_cast<int>(s.customers.size()), sink=n+1;
                const double horizon=s.returnDepot.deadline+EPS;
                const double w=d.phase==RmpPhase::Cost?1.:0.;
                // Times are bounded by H+EPS even for unselected nodes, which can take 0.
                // Earliest execution is nonnegative even if a customer's readyTime is negative.
                for(const auto& c:s.customers) {
                    y.push_back(variable(-d.customers.at(c.id),0,1,SOLVER_BINARY));
                    time.push_back(variable(0,0,horizon));
                    order.push_back(variable(0,0,n));
                }
                sinkTime=variable(0,0,horizon);
                auto point=[&](int v) { return v==0?k.location:v==sink?s.returnDepot.location:s.customers[v-1].location; };
                for(int i=0;i<=n;++i) for(int j=1;j<=sink;++j) {
                    if(i==j || (i==0 && j==sink && k.kind==StartKind::Origin))continue;
                    const double length=distance(point(i),point(j));
                    arcs.push_back({i,j,variable(w*length,0,1,SOLVER_BINARY),length});
                }
                checked(solver.newModel("independent_single_path_pricing",0,nullptr,nullptr,nullptr,nullptr,nullptr), "newModel");
                checked(solver.setModelSense(SOLVER_MIN_SENSE), "minimize");
                std::vector<int> begins(obj.size(),0);
                checked(solver.addVars(static_cast<int>(obj.size()),0,begins.data(),nullptr,nullptr,
                    obj.data(),lower.data(),upper.data(),nullptr,nullptr), "variables");
                // The copied CPLEX addVars ignores vtype. Set and verify explicitly.
                checked(solver.setVTypeArray(0,static_cast<int>(types.size()),types.data()), "types");
                std::vector<char> actual(types.size());
                checked(solver.getVTypeArray(0,static_cast<int>(actual.size()),actual.data()), "verify types");
                require(actual==types, "variable type mismatch");
                for(int v=0;v<=sink;++v) {
                    if(v!=sink) {
                        std::vector<std::pair<int,double>> terms;
                        for(const auto& a:arcs)if(a.from==v)terms.emplace_back(a.variable,1);
                        if(v!=0)terms.emplace_back(y[v-1],-1);
                        row(terms,'=',v==0?1:0);
                    }
                    if(v!=0) {
                        std::vector<std::pair<int,double>> terms;
                        for(const auto& a:arcs)if(a.to==v)terms.emplace_back(a.variable,1);
                        if(v!=sink)terms.emplace_back(y[v-1],-1);
                        row(terms,'=',v==sink?1:0);
                    }
                }
                std::vector<std::pair<int,double>> capacity;
                for(int i=0;i<n;++i) {
                    const auto& c=s.customers[i]; capacity.emplace_back(y[i],c.demand);
                    row({{time[i],1},{y[i],-c.readyTime}},'>',0);
                    row({{time[i],1},{y[i],horizon-c.dueDate-EPS}},'<',horizon);
                    row({{order[i],1},{y[i],-1}},'>',0);
                    row({{order[i],1},{y[i],-double(n)}},'<',0);
                }
                row(capacity,'<',k.remainingCapacity+EPS);
                for(const auto& a:arcs) {
                    const int destination=a.to==sink?sinkTime:time[a.to-1];
                    if(a.from==0) {
                        row({{destination,1},{a.variable,-(k.startTime+a.length)}},'>',0);
                    } else {
                        const int i=a.from-1;
                        const double delta=s.customers[i].serviceTime+a.length;
                        const double M=horizon+delta; // Derived from t_i <= horizon, t_j >= 0.
                        row({{destination,1},{time[i],-1},{a.variable,-M}},'>',-horizon);
                        if(a.to!=sink) {
                            // If x_ij=1 then u_j >= u_i+1. Summing around ANY cycle is impossible.
                            row({{order[a.to-1],1},{order[i],-1},{a.variable,-double(n+1)}},'>',-double(n));
                        }
                    }
                }
                checked(solver.updateModel(), "update");
            }
            StageRoute extract(const StageProblem& s, int key, const std::vector<double>& x) const {
                const int sink=static_cast<int>(s.customers.size())+1;
                std::map<int,int> successor; std::set<int> selected;
                for(std::size_t j=0;j<types.size();++j) if(types[j]==SOLVER_BINARY)
                    require(finite(x[j]) && x[j]>=-EPS && x[j]<=1+EPS
                        && std::abs(x[j]-std::round(x[j]))<=EPS, "invalid binary incumbent");
                for(const auto& a:arcs)if(x[a.variable]>0.5)
                    require(successor.emplace(a.from,a.to).second, "multiple outgoing incumbent arcs");
                StageRoute route{key,{}}; int v=0;
                for(int count=0;v!=sink && count<=sink;++count) {
                    require(successor.contains(v), "broken incumbent path"); v=successor.at(v);
                    if(v!=sink) {
                        require(selected.insert(v).second, "incumbent cycle");
                        route.customerSequence.push_back(s.customers.at(v-1).id);
                    }
                }
                require(v==sink && successor.size()==route.customerSequence.size()+1, "disconnected incumbent component");
                for(std::size_t i=0;i<y.size();++i)
                    require((x[y[i]]>0.5)==selected.contains(static_cast<int>(i)+1), "visit/arc mismatch");
                return route;
            }
        };
    }
    PricingMipWitness checkPricingRoute(const StageProblem& s, const RmpDuals& d, const StageRoute& r) {
        validate(s,d); const auto& k=getStart(s,r.startKey);
        require(k.kind==StartKind::External || !r.customerSequence.empty(), "empty origin route");
        double t=k.startTime, load=0, cost=0, dual=k.kind==StartKind::External?d.externalStarts.at(k.startKey):0;
        Location pos=k.location; std::set<int> seen;
        for(int id:r.customerSequence) {
            require(seen.insert(id).second, "repeated witness customer");
            const auto it=std::find_if(s.customers.begin(),s.customers.end(),[&](const auto& c){return c.id==id;});
            require(it!=s.customers.end(), "unknown witness customer");
            const double length=distance(pos,it->location); cost+=length; t=std::max(t+length,it->readyTime);
            load+=it->demand;
            require(t<=it->dueDate+EPS && load<=k.remainingCapacity+EPS, "witness time/capacity");
            t+=it->serviceTime; dual+=d.customers.at(id); pos=it->location;
        }
        const double home=distance(pos,s.returnDepot.location); cost+=home;
        require(t+home<=s.returnDepot.deadline+EPS && finite(t+home) && finite(cost) && finite(dual), "witness return/arithmetic");
        const double rc=(d.phase==RmpPhase::Cost?cost:0)-dual;
        require(finite(rc), "nonfinite witness rc");
        return {r,cost,rc};
    }
    PricingMipResult solvePricingMip(const StageProblem& s, const RmpDuals& d, int key, double limit) {
        const auto began=Clock::now();
        PricingMipResult r; r.stageId=s.stageId; r.modelRevision=d.modelRevision; r.phase=d.phase; r.startKey=key;
        try {
            validate(s,d); require(finite(limit) && limit>=0, "invalid time limit");
            const auto& k=getStart(s,key);
            if(s.customers.empty() && k.kind==StartKind::Origin) {
                r.status=PricingMipStatus::Infeasible; r.message="empty origin path forbidden (analytic)";
            } else {
                Owner owner; owner.open(limit); Model m{owner.solver}; m.build(s,d,k);
                checked(owner.solver.mipOptimize(), "mipOptimize");
                int raw=0; checked(owner.solver.getStatus(&raw), "status"); r.rawStatus=raw;
                const auto status=Solver::classifyStatus(raw,true);
                r.message="CPLEX status="+std::to_string(raw);
                if(status.termination==SolverTermination::Infeasible)r.status=PricingMipStatus::Infeasible;
                else if(status.termination==SolverTermination::Optimal)r.status=PricingMipStatus::Optimal;
                else if(status.termination==SolverTermination::TimeLimit || status.termination==SolverTermination::Feasible)
                    r.status=PricingMipStatus::Unfinished;
                else throw std::runtime_error(r.message);
                const double offset=k.kind==StartKind::External?d.externalStarts.at(key):0;
                if(r.status!=PricingMipStatus::Infeasible) {
                    double lb=0;
                    if(owner.solver.getMipBestBound(&lb)==0 && finite(lb) && std::abs(lb)<SOLVER_INFINITY) {
                        require(finite(lb-offset), "nonfinite bound"); r.lowerBound=lb-offset;
                    }
                }
                if(status.hasPrimal) {
                    std::vector<double> x(m.obj.size()); double objective=0;
                    checked(owner.solver.getX(0,static_cast<int>(x.size()),x.data()), "incumbent");
                    checked(owner.solver.getObjVal(&objective), "objective");
                    r.incumbent=checkPricingRoute(s,d,m.extract(s,key,x));
                    require(std::abs(r.incumbent->reducedCost-(objective-offset))<=EPS, "objective/witness mismatch");
                    require(!r.lowerBound || *r.lowerBound<=r.incumbent->reducedCost+EPS, "bound above incumbent");
                }
                if(r.status==PricingMipStatus::Optimal)
                    require(r.incumbent && r.lowerBound && r.incumbent->reducedCost-*r.lowerBound<=EPS, "optimal status without closed absolute gap");
            }
        } catch(const std::exception& e) {
            r.status=PricingMipStatus::Error; r.message=e.what(); r.incumbent.reset(); r.lowerBound.reset();
        }
        r.seconds=std::chrono::duration<double>(Clock::now()-began).count(); return r;
    }
    MinimumAudit certifyPricingMinimum(const PricingMipResult& r, std::optional<double> claimed, double eps) {
        if(!finite(eps) || eps<0 || (claimed && !finite(*claimed)) || r.status==PricingMipStatus::Error)
            return MinimumAudit::Mismatch;
        if(!claimed) {
            if(r.incumbent)return MinimumAudit::Mismatch;
            return r.status==PricingMipStatus::Infeasible?MinimumAudit::Certified:MinimumAudit::Unresolved;
        }
        if(r.status==PricingMipStatus::Infeasible)return MinimumAudit::Mismatch;
        if(r.incumbent && r.incumbent->reducedCost<*claimed-eps)return MinimumAudit::Mismatch;
        if(r.lowerBound) {
            if(!finite(*r.lowerBound) || *r.lowerBound>*claimed+eps)return MinimumAudit::Mismatch;
            if(*claimed-*r.lowerBound<=eps)return MinimumAudit::Certified;
        }
        return MinimumAudit::Unresolved;
    }
}

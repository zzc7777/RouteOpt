/* Copyright (c) 2025 Zhengzhong (Ricky) You. RouteOpt, GPL-3.0.
 * DVRP adaptation of the LP -> pricing -> add-column loop in
 * packages/application/cvrp/src/main/src/cg.cpp. No cuts, ng, or tree state.
 * The independent test oracle is NOT a dependency of this production file. */
#include "root_cg_controller.hpp"
#include "dynamic_controller.hpp"
#include "route_opt_macro.hpp"
#include <algorithm>
#include <chrono>
#include <cmath>
#include <limits>
#include <set>
#include <stdexcept>

namespace RouteOpt::Application::DVRP {
    namespace {
        using Clock = std::chrono::steady_clock;
        struct NumericalFailure : std::runtime_error { using std::runtime_error::runtime_error; };
        struct Stop { RootCgStatus status; std::string message; };
        void require(bool ok, const char* why) {
            if (!ok) throw std::runtime_error(std::string("root CG: ")+why);
        }
        bool near(double a, double b) {
            return std::isfinite(a) && std::isfinite(b)
                && std::abs(a-b) <= TOLERANCE*std::max({1.0,std::abs(a),std::abs(b)});
        }
        double finite(double x) {
            if (!std::isfinite(x)) throw NumericalFailure("root CG: nonfinite arithmetic");
            return x;
        }
        void nonnegative(double x) { require(std::isfinite(x) && x>=0,"invalid time limit or gap tolerance"); }
        bool sameRoute(const StageRoute& a, const StageRoute& b) {
            return a.startKey==b.startKey && a.customerSequence==b.customerSequence;
        }
        void validateLp(const StageProblem& s, const RmpLpResult& lp) {
            require(lp.status==RmpLpStatus::OptimalRmp && lp.duals && lp.objective && lp.artificialSum,
                    "missing optimal RMP data");
            require(lp.phase==RmpPhase::Cost || lp.phase==RmpPhase::Feasibility,"unknown LP phase");
            const auto& d=*lp.duals;
            require(lp.stageId==s.stageId && d.stageId==lp.stageId
                    && d.modelRevision==lp.modelRevision && d.phase==lp.phase,"stale LP/dual context");
            require(d.customers.size()==s.customers.size() && d.externalStarts.size()==s.externalStarts.size(),
                    "dual key count mismatch");
            for (const auto& c:s.customers) {
                require(d.customers.contains(c.id),"missing customer dual"); finite(d.customers.at(c.id));
            }
            for (const auto& k:s.externalStarts) {
                require(d.externalStarts.contains(k.startKey),"missing external dual"); finite(d.externalStarts.at(k.startKey));
            }
            require(finite(*lp.objective)>=-TOLERANCE && finite(*lp.artificialSum)>=-TOLERANCE,"negative LP objective/artificial mass");
            if (lp.phase==RmpPhase::Cost)
                require(*lp.artificialSum<=TOLERANCE && lp.routeCost && near(*lp.routeCost,*lp.objective),
                        "cost phase not a verified real-route LP");
        }
        void validateWitness(const StageProblem& s, const RmpDuals& duals, const PricedRoute& r) {
            const StartSpec* start=&s.originStart;
            if (r.route.startKey!=0) {
                const auto it=std::find_if(s.externalStarts.begin(),s.externalStarts.end(),
                    [&](const auto& k){return k.startKey==r.route.startKey;});
                require(it!=s.externalStarts.end(),"unknown route start"); start=&*it;
            }
            require(start->kind==StartKind::External || !r.route.customerSequence.empty(),"empty origin route");
            std::set<int> seen;
            double cost=0, time=start->startTime, load=0, dualSum=0;
            auto previous=start->location;
            for (int id:r.route.customerSequence) {
                require(seen.insert(id).second,"repeated customer in pricing witness");
                const auto it=std::find_if(s.customers.begin(),s.customers.end(),[&](const auto& c){return c.id==id;});
                require(it!=s.customers.end(),"unknown customer in witness");
                const auto& c=*it;
                const double distance=routeOptDistance(previous,c.location);
                cost=finite(cost+distance); load=finite(load+c.demand);
                time=std::max(finite(time+distance),c.readyTime);
                require(time<=c.dueDate+TOLERANCE && load<=start->remainingCapacity+TOLERANCE,"infeasible witness");
                time=finite(time+c.serviceTime); previous=c.location;
                dualSum=finite(dualSum+duals.customers.at(id));
            }
            const double last=routeOptDistance(previous,s.returnDepot.location);
            cost=finite(cost+last);
            require(finite(time+last)<=s.returnDepot.deadline+TOLERANCE,"infeasible witness return");
            double rc=finite((duals.phase==RmpPhase::Cost?cost:0)-dualSum);
            if (r.route.startKey) rc=finite(rc-duals.externalStarts.at(r.route.startKey));
            require(near(cost,r.cost) && near(rc,r.reducedCost),"witness cost/reduced cost mismatch");
        }
    }

    // Narrow internal policies are also called directly by deterministic tests.
    // Not an injectable solver framework, and not part of the public header/API.
    namespace RootCgDetail {
        double remainingTime(double total, double elapsed) {
            nonnegative(total); nonnegative(elapsed); return std::max(0.0,total-elapsed);
        }
        std::optional<RootCgStatus> classifyPricingStatus(PricingStatus status) {
            switch (status) {
                case PricingStatus::Complete: return {};
                case PricingStatus::TimeLimit: return RootCgStatus::TimeLimit;
                case PricingStatus::ResourceLimit: return RootCgStatus::ResourceLimit;
                default: return RootCgStatus::Error;
            }
        }
        void requireColumnProgress(std::size_t before, std::size_t after) {
            require(after>before,"all negative candidates already exist; no column progress");
        }
        void validatePricingResult(const StageProblem& s, const RmpLpResult& lp,
                                   const PricingResult& p, PricingBackend backend) {
            validateLp(s,lp);
            require(p.status==PricingStatus::Complete,"incomplete pricing cannot supply a certificate");
            require(p.stageId==lp.stageId && p.modelRevision==lp.modelRevision && p.phase==lp.phase
                    && p.backend==backend,"pricing context/backend mismatch");
            std::set<int> expected{0}, actual, candidateKeys;
            for (const auto& k:s.externalStarts) expected.insert(k.startKey);
            std::map<int,const PricedRoute*> negative;
            for (const auto& k:p.starts) {
                require(expected.contains(k.startKey) && actual.insert(k.startKey).second,"unknown/duplicate priced start");
                require(k.hasFeasibleRoute==k.minimumReducedCost.has_value()
                        && k.hasFeasibleRoute==k.bestRoute.has_value(),"inconsistent feasible/minimum/witness flags");
                if (!k.hasFeasibleRoute) continue;
                require(k.bestRoute->route.startKey==k.startKey,"witness start mismatch");
                validateWitness(s,*lp.duals,*k.bestRoute);
                // These values are copies of the SAME result, not independently summed values.
                require(*k.minimumReducedCost==k.bestRoute->reducedCost,"minimum/witness rc mismatch");
                if (*k.minimumReducedCost<RC_TOLERANCE) negative.emplace(k.startKey,&*k.bestRoute);
            }
            require(actual==expected,"not all starts were priced");
            for (const auto& r:p.candidates) {
                require(candidateKeys.insert(r.route.startKey).second && negative.contains(r.route.startKey),
                        "unexpected/duplicate pricing candidate");
                const auto& best=*negative.at(r.route.startKey);
                require(sameRoute(r.route,best.route) && r.cost==best.cost
                        && r.reducedCost==best.reducedCost && r.reducedCost<RC_TOLERANCE,
                        "candidate does not match negative minimum witness");
                validateWitness(s,*lp.duals,r);
            }
            require(candidateKeys.size()==negative.size(),"negative minimum without candidate");
        }

        RootCgBound computeBound(const StageProblem& s, const RmpLpResult& lp, const PricingResult& p) {
            validatePricingResult(s,lp,p,p.backend);
            RootCgBound b; b.stageId=s.stageId; b.modelRevision=lp.modelRevision; b.phase=lp.phase;
            b.rowCount=s.customers.size()+s.externalStarts.size(); b.rmpObjective=*lp.objective;
            double absDualSum=0, witnessScale=0;
            auto addDual=[&](double y) {
                b.dualSum=finite(b.dualSum+y); absDualSum=finite(absDualSum+std::abs(y));
                if (lp.phase==RmpPhase::Feasibility) b.minimumReducedCost=std::min(b.minimumReducedCost,finite(1-y));
            };
            for (const auto& [id,y]:lp.duals->customers) addDual(y);
            for (const auto& [key,y]:lp.duals->externalStarts) addDual(y);
            for (const auto& k:p.starts) if (k.minimumReducedCost) {
                b.minimumReducedCost=std::min(b.minimumReducedCost,finite(*k.minimumReducedCost));
                witnessScale=std::max({witnessScale,std::abs(*k.minimumReducedCost),k.bestRoute->cost});
            }
            if (!near(b.dualSum,b.rmpObjective)) throw NumericalFailure("root CG: RMP objective/dual sum mismatch");
            // In exact arithmetic sum(z)<=m since every column covers >=1 RHS-one row.
            // Use a conservative floating allowance, NOT an interval/rational certificate.
            const double m=static_cast<double>(b.rowCount), eps=std::numeric_limits<double>::epsilon();
            if (b.rowCount) {
                const double scale=finite(1+std::abs(b.rmpObjective)+absDualSum+witnessScale);
                b.rcRoundingGuard=finite(64*eps*(static_cast<double>(s.customers.size())+2)*scale);
                const double correction=finite(m*finite(b.minimumReducedCost-b.rcRoundingGuard));
                b.sumRoundingGuard=finite(64*eps*(m+2)*finite(1+absDualSum+std::abs(correction)));
                b.lowerBound=finite(std::nextafter(finite(b.dualSum+correction-b.sumRoundingGuard),
                                                  -std::numeric_limits<double>::infinity()));
            }
            b.gap=finite(b.rmpObjective-b.lowerBound);
            if (b.gap<0) throw NumericalFailure("root CG: lower bound exceeds feasible RMP objective");
            if (lp.phase==RmpPhase::Cost) {
                b.totalLowerBound=finite(b.lowerBound+s.fixedCostAfterTau);
                b.totalRmpObjective=finite(b.rmpObjective+s.fixedCostAfterTau);
            }
            return b;
        }
        RootCgStatus classifyBound(const RmpLpResult& lp, const RootCgBound& b, double absolute, double relative) {
            nonnegative(absolute); nonnegative(relative);
            require(b.stageId==lp.stageId && b.modelRevision==lp.modelRevision && b.phase==lp.phase,
                    "stale bound");
            finite(b.lowerBound); finite(b.gap); finite(b.rmpObjective);
            require(b.gap>=0,"negative gap");
            if (lp.phase==RmpPhase::Feasibility) {
                require(lp.artificialSum && *lp.artificialSum>TOLERANCE,"zero artificial mass must switch phase");
                return b.lowerBound>TOLERANCE ? RootCgStatus::LpInfeasible : RootCgStatus::NumericalUnresolved;
            }
            const double tolerance=finite(absolute+relative*std::max(1.0,std::abs(b.rmpObjective)));
            return b.gap<=tolerance ? RootCgStatus::LpConverged : RootCgStatus::NumericalUnresolved;
        }
    }

    RootCgResult DVRP_RootCgController::solve(const StageProblem& s, const RootCgOptions& o) const {
        const auto began=Clock::now();
        RootCgResult result; result.stageId=s.stageId; result.backend=o.pricing.backend;
        auto elapsed=[&]{return std::chrono::duration<double>(Clock::now()-began).count();};
        auto remaining=[&] {
            const double left=RootCgDetail::remainingTime(o.timeLimitSeconds,elapsed());
            if (left<=0) throw Stop{RootCgStatus::TimeLimit,"root total time budget exhausted"};
            return left;
        };
        try {
            nonnegative(o.timeLimitSeconds); nonnegative(o.rmp.timeLimitSeconds); nonnegative(o.pricing.timeLimitSeconds);
            nonnegative(o.absoluteGapTolerance); nonnegative(o.relativeGapTolerance);
            require(o.pricing.backend==PricingBackend::PlainLabeling || o.pricing.backend==PricingBackend::BucketGraph,
                    "unknown pricing backend");
            require(o.pricing.backend!=PricingBackend::BucketGraph || o.pricing.bucketCount>0,"bucketCount must be positive");
            remaining();
            DVRP_RmpController rmp(s,o.rmp); remaining();
            DVRP_PricingController pricing(s); remaining();
            if (o.addInitialRoutes) { rmp.addInitialRoutes(); remaining(); }
            for (;;) {
                remaining();
                if (result.iterations.size()>=o.maxIterations)
                    throw Stop{RootCgStatus::IterationLimit,"maximum number of root LP solves reached"};
                auto& log=result.iterations.emplace_back();
                log.iteration=result.iterations.size(); log.stageId=s.stageId;
                log.columnsBefore=log.columnsAfter=rmp.columns().size();
                log.lpTimeLimit=std::min(o.rmp.timeLimitSeconds,remaining());
                const auto lp=rmp.solveLp(log.lpTimeLimit);
                log.phase=lp.phase; log.modelRevision=lp.modelRevision; log.lpStatus=lp.status;
                log.lpSeconds=lp.solveSeconds; log.objective=lp.objective; log.artificialSum=lp.artificialSum;
                if (lp.status==RmpLpStatus::OptimalRmp) {
                    validateLp(s,lp);
                    require(lp.lambda.size()==rmp.columns().size(),"LP/column snapshot size mismatch");
                    result.snapshot=RootCgSnapshot{lp,rmp.columns(),{}};
                }
                remaining();
                if (lp.status!=RmpLpStatus::OptimalRmp)
                    throw Stop{lp.status==RmpLpStatus::TimeLimit?RootCgStatus::TimeLimit:RootCgStatus::Error,lp.message};
                if (lp.phase==RmpPhase::Feasibility && *lp.artificialSum<=TOLERANCE) {
                    log.action="enter cost phase"; rmp.enterCostPhase();
                    log.elapsedSeconds=elapsed(); remaining(); continue;
                }
                auto po=o.pricing; po.timeLimitSeconds=std::min(po.timeLimitSeconds,remaining());
                log.pricingTimeLimit=po.timeLimitSeconds;
                const auto p=pricing.price(*lp.duals,po);
                log.pricingStatus=p.status; log.pricingSeconds=p.seconds;
                remaining();
                if (const auto stop=RootCgDetail::classifyPricingStatus(p.status)) throw Stop{*stop,p.message};
                RootCgDetail::validatePricingResult(s,lp,p,o.pricing.backend);
                for (const auto& k:p.starts) log.minimumReducedCosts.emplace(k.startKey,k.minimumReducedCost);
                remaining();
                if (!p.candidates.empty()) {
                    std::vector<StageRoute> routes;
                    for (const auto& c:p.candidates) routes.push_back(c.route);
                    const auto ids=rmp.addRoutes(routes);
                    log.columnsAfter=rmp.columns().size(); log.addedColumns=log.columnsAfter-log.columnsBefore;
                    RootCgDetail::requireColumnProgress(log.columnsBefore,log.columnsAfter);
                    require(ids.size()==p.candidates.size(),"added column ID count mismatch");
                    for (std::size_t i=0;i<ids.size();++i)
                        require(near(rmp.columns().at(ids[i]).cost,p.candidates[i].cost),"RMP/pricing column cost mismatch");
                    log.action="add columns"; log.elapsedSeconds=elapsed(); remaining(); continue;
                }
                auto bound=RootCgDetail::computeBound(s,lp,p);
                log.bound=bound; result.snapshot->bound=bound;
                result.status=RootCgDetail::classifyBound(lp,bound,o.absoluteGapTolerance,o.relativeGapTolerance);
                result.message=result.status==RootCgStatus::LpConverged?"root LP gap closed (not an integer solution)":
                    result.status==RootCgStatus::LpInfeasible?"positive full Phase-I lower bound: real-route LP infeasible":
                    "complete pricing, but numerical bound does not certify termination";
                log.action=result.message; log.elapsedSeconds=elapsed(); remaining(); break;
            }
        } catch (const Stop& e) { result.status=e.status; result.message=e.message; }
        catch (const NumericalFailure& e) { result.status=RootCgStatus::NumericalUnresolved; result.message=e.what(); }
        catch (const std::bad_alloc&) { result.status=RootCgStatus::ResourceLimit; result.message="root CG allocation failed"; }
        catch (const std::exception& e) { result.status=RootCgStatus::Error; result.message=e.what(); }
        // Includes destruction of the local pricing/RMP. Cooperative budget, not hard real-time.
        result.seconds=elapsed();
        if (std::isfinite(o.timeLimitSeconds) && o.timeLimitSeconds>=0 && result.seconds>=o.timeLimitSeconds
            && (result.status==RootCgStatus::LpConverged || result.status==RootCgStatus::LpInfeasible)) {
            result.status=RootCgStatus::TimeLimit; result.message="root time budget exhausted during result finalization";
        }
        if (!result.iterations.empty()) {
            auto& last=result.iterations.back(); last.elapsedSeconds=result.seconds;
            if (result.status!=RootCgStatus::LpConverged && result.status!=RootCgStatus::LpInfeasible) last.action=result.message;
        }
        return result;
    }
}

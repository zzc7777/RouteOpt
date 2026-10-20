// Bounded real-instance PRICING experiment, not a stage solver or a CG loop.
// No exhaustive enumerator, future customer disclosure, or known upper bound.
#include "pricing_controller.hpp"
#include "dynamic_controller.hpp"
#include "read_data_controller.hpp"
#include <algorithm>
#include <chrono>
#include <cmath>
#include <iomanip>
#include <iostream>
#include <set>
#include <stdexcept>

using namespace RouteOpt::Application::DVRP;
namespace {
    using Clock = std::chrono::steady_clock;
    void require(bool ok, const std::string& why) {
        if (!ok) throw std::runtime_error(why);
    }
    void close(double a, double b, const char* why) {
        require(std::isfinite(a) && std::isfinite(b) && std::abs(a-b)<=1e-6, why);
    }
    double distance(Location a, Location b) {
        const double dx=a.x-b.x,dy=a.y-b.y;
        const float root=std::sqrt(static_cast<float>(dx*dx+dy*dy));
        return std::floor((static_cast<double>(root)+1e-6)*10.)/10.;
    }
    std::size_t sizeArg(const char* arg) {
        const std::string s=arg;
        if(s.empty() || s.find_first_not_of("0123456789")!=std::string::npos)
            throw std::invalid_argument("size argument must be a nonnegative integer");
        return std::stoull(s);
    }
    const char* statusName(PricingStatus status) {
        switch(status) {
        case PricingStatus::Complete:return "Complete";
        case PricingStatus::TimeLimit:return "TimeLimit";
        case PricingStatus::ResourceLimit:return "ResourceLimit";
        default:return "Error";
        }
    }
    // Independent full-route resource/cost check; does not use pricing internals.
    void checkRoute(const StageProblem& s,const RmpDuals& d,const PricedRoute& r) {
        const StartSpec* k=&s.originStart;
        if(r.route.startKey!=0) {
            const auto it=std::find_if(s.externalStarts.begin(),s.externalStarts.end(),
                [&](const auto& start){return start.startKey==r.route.startKey;});
            require(it!=s.externalStarts.end(),"unknown witness start");k=&*it;
        }
        require(k->kind==StartKind::External || !r.route.customerSequence.empty(),"empty origin route");
        double time=k->startTime,load=0,cost=0,dual=0;
        Location pos=k->location;std::set<int> seen;
        for(int id:r.route.customerSequence) {
            require(seen.insert(id).second,"repeated witness customer");
            const auto c=std::find_if(s.customers.begin(),s.customers.end(),[&](const auto& v){return v.id==id;});
            require(c!=s.customers.end(),"unknown witness customer");
            const double arc=distance(pos,c->location);
            cost+=arc;time=std::max(time+arc,c->readyTime);load+=c->demand;
            require(time<=c->dueDate+1e-6 && load<=k->remainingCapacity+1e-6,"witness time/capacity");
            time+=c->serviceTime;pos=c->location;dual+=d.customers.at(id);
        }
        const double home=distance(pos,s.returnDepot.location);cost+=home;
        require(time+home<=s.returnDepot.deadline+1e-6,"witness return deadline");
        if(k->kind==StartKind::External)dual+=d.externalStarts.at(k->startKey);
        close(r.cost,cost,"independent witness cost");
        close(r.reducedCost,(d.phase==RmpPhase::Cost?cost:0)-dual,"independent witness rc");
    }
    void checkResult(const StageProblem& s,const RmpDuals& d,const PricingResult& p) {
        require(p.stageId==s.stageId && p.modelRevision==d.modelRevision && p.phase==d.phase,"stale result");
        require(p.status!=PricingStatus::Error,p.message);
        if(p.status!=PricingStatus::Complete) {
            require(p.starts.empty() && p.candidates.empty(),"partial results leaked");return;
        }
        require(p.starts.size()==1+s.externalStarts.size(),"missing starts");
        std::set<int> expected,actual,keys;
        for(const auto& start:p.starts) {
            require(keys.insert(start.startKey).second,"duplicate start result");
            require(start.hasFeasibleRoute==start.bestRoute.has_value()
                && start.hasFeasibleRoute==start.minimumReducedCost.has_value(),"invalid witness presence");
            if(start.bestRoute) {
                require(start.bestRoute->route.startKey==start.startKey,"wrong witness key");
                checkRoute(s,d,*start.bestRoute);
                close(*start.minimumReducedCost,start.bestRoute->reducedCost,"witness not minimum");
                if(*start.minimumReducedCost < -1e-5)expected.insert(start.startKey);
            }
        }
        require(keys.contains(0),"missing origin result");
        for(const auto& k:s.externalStarts)require(keys.contains(k.startKey),"missing external result");
        for(const auto& r:p.candidates) {
            require(actual.insert(r.route.startKey).second,"duplicate candidate");
            checkRoute(s,d,r);
            const auto best=std::find_if(p.starts.begin(),p.starts.end(),
                [&](const auto& v){return v.startKey==r.route.startKey;});
            require(best!=p.starts.end() && best->minimumReducedCost,"candidate without minimum");
            close(r.reducedCost,*best->minimumReducedCost,"candidate not minimum");
        }
        require(expected==actual,"negative candidates differ from minima");
    }
    bool probe(const StageProblem& s,const RmpLpResult& lp,const PricingOptions& limits) {
        require(lp.status==RmpLpStatus::OptimalRmp && lp.duals && lp.objective,lp.message);
        const auto began=Clock::now();
        DVRP_PricingController pricing(s);
        std::cout<<"DUAL phase="<<(lp.phase==RmpPhase::Cost?2:1)<<" revision="<<lp.modelRevision
            <<" rmp_objective="<<*lp.objective<<" prepare_seconds="
            <<std::chrono::duration<double>(Clock::now()-began).count()<<'\n';
        std::vector<PricingResult> results;
        for(auto backend:{PricingBackend::PlainLabeling,PricingBackend::BucketGraph}) {
            auto options=limits;options.backend=backend;
            const auto p=pricing.price(*lp.duals,options);
            const auto& st=p.statistics;
            std::cout<<"RESULT backend="<<(backend==PricingBackend::PlainLabeling?"plain":"bucket")
                <<" phase="<<(lp.phase==RmpPhase::Cost?2:1)<<" status="<<statusName(p.status)
                <<" seconds="<<p.seconds<<" labels="<<st.generatedLabels
                <<" retained_peak="<<st.peakRetainedLabels<<" extended="<<st.extendedLabels
                <<" dominance="<<st.dominanceChecks<<" graph_seconds="<<st.graphBuildSeconds
                <<" graph_nodes="<<st.bucketNodes<<" candidates="<<p.candidates.size();
            if(p.status==PricingStatus::Complete && !p.starts.empty() && p.starts.front().minimumReducedCost)
                std::cout<<" origin_min_rc="<<*p.starts.front().minimumReducedCost;
            std::cout<<" message="<<std::quoted(p.message)<<'\n';
            checkResult(s,*lp.duals,p);
            for(const auto& best:p.starts)if(best.bestRoute) {
                std::cout<<"WITNESS start="<<best.startKey<<" cost="<<best.bestRoute->cost<<" customers=";
                for(int id:best.bestRoute->route.customerSequence)std::cout<<id<<',';
                std::cout<<" verified=true\n";
            }
            results.push_back(p);
        }
        if(results[0].status!=PricingStatus::Complete || results[1].status!=PricingStatus::Complete) {
            std::cout<<"COMPARISON unavailable_due_to_budget; no_optimality_claim\n";return false;
        }
        for(const auto& a:results[0].starts) {
            const auto b=std::find_if(results[1].starts.begin(),results[1].starts.end(),
                [&](const auto& v){return v.startKey==a.startKey;});
            require(b!=results[1].starts.end() && a.hasFeasibleRoute==b->hasFeasibleRoute,"backend feasibility mismatch");
            if(a.minimumReducedCost)close(*a.minimumReducedCost,*b->minimumReducedCost,"backend minimum mismatch");
        }
        std::cout<<"COMPARISON same_minima=true independently_checked_routes=true exhaustive_oracle=false\n";
        return true;
    }
}
int main(int argc,char** argv) {
    if(argc<2 || argc>5) {
        std::cerr<<"Usage: dvrp_pricing_instance_test instance.txt [customer_limit=0] [seconds=15] [max_labels=200000]\n";
        return 2;
    }
    try {
        std::cout<<std::unitbuf<<std::setprecision(9);
        const auto limit=argc>2?sizeArg(argv[2]):0;
        PricingOptions options;options.timeLimitSeconds=15;
        if(argc>3) {
            std::size_t used=0;options.timeLimitSeconds=std::stod(argv[3],&used);
            if(used!=std::string(argv[3]).size() || !std::isfinite(options.timeLimitSeconds) || options.timeLimitSeconds<0)
                throw std::invalid_argument("invalid seconds");
        }
        if(argc>4)options.maxLabels=sizeArg(argv[4]);
        std::string path,name;int vehicles=0,dim=0;double cap=0;
        std::vector<std::vector<double>> vertices;
        DVRP_ReadDataController reader(2,argv,path,name,vehicles,cap,dim,vertices);
        DynamicController dynamic(vertices,cap,name);
        auto stage=dynamic.buildStageProblem();const auto released=stage.customers.size();
        if(limit && limit<released)stage.customers.resize(limit);
        std::cout<<"STAGE instance="<<name<<" input_customers="<<dim-1<<" released="<<released
            <<" tested="<<stage.customers.size()<<" selection="<<(stage.customers.size()==released?"full_initial":"prefix_subset")
            <<" tau="<<stage.currentTime<<" external="<<stage.externalStarts.size()
            <<" capacity="<<cap<<" H="<<stage.returnDepot.deadline
            <<" seconds_per_price="<<options.timeLimitSeconds<<" label_limit="<<options.maxLabels
            <<" buckets="<<options.bucketCount<<" solver=CPLEX enumeration=false\n";
        DVRP_RmpController rmp(stage);
        bool complete=probe(stage,rmp.solveLp(),options); // Actual empty-RMP Phase-I dual.
        rmp.addInitialRoutes();const auto initial=rmp.solveLp();
        require(initial.status==RmpLpStatus::OptimalRmp && initial.artificialSum,initial.message);
        if(*initial.artificialSum>1e-6) {
            std::cout<<"PHASE_II skipped_initial_real_columns_insufficient; not_a_full_infeasibility_proof\n";complete=false;
        } else {
            rmp.enterCostPhase();
            complete=probe(stage,rmp.solveLp(),options) && complete; // Singleton-RMP cost dual, not CG optimum.
        }
        std::cout<<"SUMMARY "<<(complete?"COMPLETED_AND_AGREED":"INCOMPLETE")
            <<" scope=two_fixed_dual_pricing_snapshots not_CG_not_BP\n";
        return complete?0:3;
    }catch(const std::invalid_argument& e){std::cerr<<e.what()<<'\n';return 2;}
    catch(const std::exception& e){std::cerr<<e.what()<<'\n';return 1;}
}

// Independent exhaustive/property checks. No production extension, dominance,
// bucket internals, or existing five-customer oracle is used to compute answers.
#include "pricing_controller.hpp"
#include <algorithm>
#include <cmath>
#include <functional>
#include <iomanip>
#include <iostream>
#include <limits>
#include <random>
#include <set>
#include <stdexcept>

using namespace RouteOpt::Application::DVRP;
namespace {
    constexpr double EPS = 1e-6, NEGATIVE_RC = -1e-5;
    std::size_t calls = 0, permutations = 0;
    std::string context;
    void check(bool ok, const std::string& message) {
        if (!ok) throw std::runtime_error(context + ": " + message);
    }
    void close(double a, double b, const std::string& message) {
        check(std::isfinite(a) && std::isfinite(b) && std::abs(a-b) <= 1e-7,
              message + " got=" + std::to_string(a) + " expected=" + std::to_string(b));
    }
    double distance(Location a, Location b) {
        // Independently implement the documented float-sqrt / one-decimal rule.
        const double dx=a.x-b.x, dy=a.y-b.y;
        const float square=static_cast<float>(dx*dx+dy*dy);
        const float root=std::sqrt(square);
        return std::floor((static_cast<double>(root)+1e-6)*10.0)/10.0;
    }
    std::vector<StartSpec> starts(const StageProblem& s) {
        std::vector<StartSpec> ks{s.originStart};
        ks.insert(ks.end(),s.externalStarts.begin(),s.externalStarts.end());
        return ks;
    }
    struct Route { StageRoute route; double cost; };
    std::optional<double> evaluate(const StageProblem& s, const StartSpec& k,
                                   const std::vector<int>& ids) {
        if (ids.empty() && k.kind==StartKind::Origin) return {};
        std::set<int> seen;
        double time=k.startTime, demand=0, cost=0;
        auto pos=k.location;
        for (int id:ids) {
            if (!seen.insert(id).second) return {};
            const auto it=std::find_if(s.customers.begin(),s.customers.end(),
                                      [&](const auto& c){return c.id==id;});
            if (it==s.customers.end()) return {};
            const double arc=distance(pos,it->location);
            cost+=arc; time=std::max(time+arc,it->readyTime); demand+=it->demand;
            if (time>it->dueDate+EPS || demand>k.remainingCapacity+EPS) return {};
            time+=it->serviceTime; pos=it->location;
        }
        const double home=distance(pos,s.returnDepot.location);
        if (time+home>s.returnDepot.deadline+EPS) return {};
        return cost+home;
    }
    std::vector<Route> exhaustive(const StageProblem& s) {
        check(s.customers.size()<=7,"test-only exhaustive size guard");
        std::vector<Route> all;
        // Every subset and every ordering, evaluated from scratch. No feasibility
        // pruning across prefixes: even a failed return cannot suppress a longer route.
        for (const auto& k:starts(s)) for (unsigned mask=0;mask<(1u<<s.customers.size());++mask) {
            std::vector<int> ids;
            for (std::size_t i=0;i<s.customers.size();++i) if (mask&(1u<<i)) ids.push_back(s.customers[i].id);
            std::sort(ids.begin(),ids.end());
            do {
                ++permutations;
                if (auto cost=evaluate(s,k,ids)) all.push_back({{k.startKey,ids},*cost});
            } while (std::next_permutation(ids.begin(),ids.end()));
        }
        return all;
    }
    double rc(const Route& r,const RmpDuals& d) {
        double sum=0;
        for (int id:r.route.customerSequence) sum+=d.customers.at(id);
        return (d.phase==RmpPhase::Cost?r.cost:0)-sum
            -(r.route.startKey?d.externalStarts.at(r.route.startKey):0);
    }
    void dump(const StageProblem& s,const RmpDuals& d) {
        std::cerr<<std::setprecision(17)<<"stage="<<s.stageId<<" tau="<<s.currentTime
                 <<" H="<<s.returnDepot.deadline<<" phase="<<int(d.phase)<<'\n';
        for (const auto& k:starts(s))
            std::cerr<<"start "<<k.startKey<<" anchor "<<k.locationCustomerId<<" xy "<<k.location.x<<' '
                     <<k.location.y<<" T "<<k.startTime<<" R "<<k.remainingCapacity
                     <<" dual "<<(k.startKey?d.externalStarts.at(k.startKey):0)<<'\n';
        for (const auto& c:s.customers)
            std::cerr<<"customer "<<c.id<<' '<<c.location.x<<' '<<c.location.y<<' '<<c.demand
                     <<' '<<c.readyTime<<' '<<c.dueDate<<' '<<c.serviceTime<<" dual "<<d.customers.at(c.id)<<'\n';
    }
    PricingResult audit(const StageProblem& s,const RmpDuals& d,const std::vector<Route>& all,
                        DVRP_PricingController& controller,PricingBackend backend,std::size_t bins) {
        PricingOptions o; o.backend=backend; o.bucketCount=bins;
        auto p=controller.price(d,o); ++calls;
        try {
            check(p.status==PricingStatus::Complete,p.message);
            check(p.stageId==s.stageId && p.modelRevision==d.modelRevision && p.phase==d.phase
                  && p.backend==backend,"result context");
            check(p.starts.size()==1+s.externalStarts.size(),"start count");
            std::set<int> expectedNegative, actualNegative, keys;
            for (const auto& k:starts(s)) {
                const auto it=std::find_if(p.starts.begin(),p.starts.end(),[&](const auto& v){return v.startKey==k.startKey;});
                check(it!=p.starts.end() && keys.insert(it->startKey).second,"missing/duplicate start");
                double best=std::numeric_limits<double>::infinity();
                for (const auto& r:all) if(r.route.startKey==k.startKey) best=std::min(best,rc(r,d));
                check(it->hasFeasibleRoute==std::isfinite(best),"route existence");
                if (!std::isfinite(best)) {
                    check(!it->minimumReducedCost && !it->bestRoute,"invented minimum"); continue;
                }
                check(it->minimumReducedCost && it->bestRoute,"missing minimum/witness");
                close(*it->minimumReducedCost,best,"minimum rc");
                const auto& w=*it->bestRoute;
                check(w.route.startKey==k.startKey,"witness start");
                const auto cost=evaluate(s,k,w.route.customerSequence);
                check(cost.has_value(),"witness infeasible");
                close(w.cost,*cost,"witness distance");
                close(w.reducedCost,rc({w.route,*cost},d),"witness rc");
                close(w.reducedCost,best,"witness optimality");
                if (best<NEGATIVE_RC) expectedNegative.insert(k.startKey);
            }
            for (const auto& r:p.candidates) {
                check(actualNegative.insert(r.route.startKey).second,"duplicate candidate");
                const auto it=std::find_if(p.starts.begin(),p.starts.end(),
                    [&](const auto& v){return v.startKey==r.route.startKey;});
                check(it!=p.starts.end() && it->bestRoute,"candidate without start witness");
                check(r.route.customerSequence==it->bestRoute->route.customerSequence,"candidate not witness");
                close(r.reducedCost,it->bestRoute->reducedCost,"candidate rc");
                close(r.cost,it->bestRoute->cost,"candidate cost");
            }
            check(actualNegative==expectedNegative,"negative-column certificate");
            if (backend==PricingBackend::PlainLabeling)
                check(p.statistics.bucketNodes==0 && p.statistics.dependencyArcs==0,"plain constructed graph");
        } catch (...) {
            std::cerr<<"backend="<<int(backend)<<" bins="<<bins<<'\n'; dump(s,d); throw;
        }
        return p;
    }
    void allBackends(const StageProblem& s,const RmpDuals& d,const std::vector<Route>& all) {
        DVRP_PricingController controller(s);
        audit(s,d,all,controller,PricingBackend::PlainLabeling,0);
        for(std::size_t bins:{1,2,7,25,64}) audit(s,d,all,controller,PricingBackend::BucketGraph,bins);
    }
    StageProblem base() {
        StageProblem s; s.stageId=1;
        s.originStart={0,StartKind::Origin,0,{0,0},0,10}; s.returnDepot={{0,0},100};
        return s;
    }
    RmpDuals duals(const StageProblem& s,RmpPhase phase=RmpPhase::Cost) {
        RmpDuals d{s.stageId,73,phase,{},{}};
        for(const auto& c:s.customers)d.customers[c.id]=8;
        for(const auto& k:s.externalStarts)d.externalStarts[k.startKey]=4;
        return d;
    }
    StageProblem randomStage(std::mt19937& gen,int n,int external,int serial) {
        auto s=base();s.stageId=serial+1;s.currentTime=double(gen()%25)/8.;
        s.originStart.startTime=s.currentTime;s.originStart.remainingCapacity=1+gen()%9;
        s.returnDepot.deadline=s.currentTime+double(gen()%400)/8.;
        for(int i=0;i<n;++i) {
            const double ready=double(gen()%200)/8.;
            s.customers.push_back({11+19*i,{double(int(gen()%101)-50)/16.,double(int(gen()%101)-50)/16.},
                double(gen()%(1+int(s.originStart.remainingCapacity))),ready,
                ready+double(gen()%240)/8.,double(gen()%25)/16.});
        }
        for(int k=0;k<external;++k)
            s.externalStarts.push_back({11+19*k,StartKind::External,2000+k,
                {double(gen()%25)/16.,double(gen()%25)/16.},s.currentTime+double(gen()%250)/8.,
                double(gen()%(1+int(s.originStart.remainingCapacity)))});
        // Deliberately include fully feasible dense graphs and zero-resource cycles.
        if(serial%4==0) {
            s.returnDepot.deadline=s.currentTime+100;
            for(auto& c:s.customers){c.readyTime=0;c.dueDate=200;c.demand=0;}
        }
        if(serial%13==0) {
            for(auto& c:s.customers){c.location={0,0};c.serviceTime=0;}
            for(auto& k:s.externalStarts)k.location={0,0};
        }
        return s;
    }
    void fuzz(unsigned seed) {
        std::mt19937 gen(seed);
        for(int t=0;t<250;++t) {
            context="seed="+std::to_string(seed)+" case="+std::to_string(t);
            const int n=gen()%6, external=gen()%6;
            auto s=randomStage(gen,n,external,t);
            const auto all=exhaustive(s);
            for(auto phase:{RmpPhase::Feasibility,RmpPhase::Cost}) {
                auto d=duals(s,phase);
                for(auto& [id,v]:d.customers)v=double(int(gen()%1601)-400)/16.;
                for(auto& [id,v]:d.externalStarts)v=double(int(gen()%801)-400)/16.;
                allBackends(s,d,all);
            }
        }
    }
    void seven() {
        for(int t=0;t<8;++t) {
            context="seven case="+std::to_string(t);
            auto s=base();
            for(int i=0;i<7;++i) s.customers.push_back({11+19*i,
                {t<2?0.:double(i%3),t<2?0.:double(i%2)},t%2?1.:0.,0,100,t%3?0.:0.125});
            for(int k=0;k<3;++k)s.externalStarts.push_back({42+k,StartKind::External,900+k,{double(k),0},double(k),double(3+k)});
            if(t>3)s.returnDepot.deadline=6.125;
            const auto all=exhaustive(s);
            if(t==0) check(all.size()==54799,"seven-customer universe count");
            for(auto phase:{RmpPhase::Feasibility,RmpPhase::Cost}) {
                auto d=duals(s,phase);
                for(auto& [id,v]:d.customers)v=t%2 ? double(id%9)-3 : 8;
                allBackends(s,d,all);
            }
        }
    }
    std::map<int,std::optional<double>> minima(const PricingResult& p) {
        std::map<int,std::optional<double>> m;
        for(const auto& r:p.starts)m[r.startKey]=r.minimumReducedCost;
        return m;
    }
    void metamorphic() {
        std::mt19937 gen(8171);
        for(int t=0;t<100;++t) {
            context="metamorphic case="+std::to_string(t);
            auto s=randomStage(gen,4,3,t);const auto all=exhaustive(s);
            auto d=duals(s,t%2?RmpPhase::Cost:RmpPhase::Feasibility);
            DVRP_PricingController p(s);
            const auto original=minima(audit(s,d,all,p,PricingBackend::PlainLabeling,0));
            // Reorder arrays, rename customer IDs/vehicle keys, reflect coordinates,
            // and change fixed cost. None of these changes the pricing problem.
            std::reverse(s.customers.begin(),s.customers.end());
            std::reverse(s.externalStarts.begin(),s.externalStarts.end());
            s.fixedCostAfterTau=123456;
            RmpDuals changed=d;changed.customers.clear();changed.externalStarts.clear();
            for(auto& c:s.customers){int old=c.id;c.id+=5000;c.location.x=-c.location.x;
                changed.customers[c.id]=d.customers.at(old);}
            for(auto& k:s.externalStarts){int old=k.startKey;k.startKey+=8000;k.location.x=-k.location.x;
                changed.externalStarts[k.startKey]=d.externalStarts.at(old);}
            DVRP_PricingController transformed(s);const auto universe=exhaustive(s);
            for(auto backend:{PricingBackend::PlainLabeling,PricingBackend::BucketGraph}) {
                const auto answer=audit(s,changed,universe,transformed,backend,7);
                for(const auto& r:answer.starts) {
                    const auto expected=original.at(r.startKey?r.startKey-8000:0);
                    check(expected.has_value()==r.minimumReducedCost.has_value(),"renaming changed feasibility");
                    if(expected)close(*expected,*r.minimumReducedCost,"renaming/reordering changed rc");
                }
            }
            // External dual shifts must change ONLY that start's min rc by -delta.
            auto shifted=changed; shifted.externalStarts.begin()->second+=3.25;
            const int key=shifted.externalStarts.begin()->first;
            const auto a=minima(audit(s,changed,universe,transformed,PricingBackend::PlainLabeling,0));
            const auto b=minima(audit(s,shifted,universe,transformed,PricingBackend::BucketGraph,25));
            for(const auto& [k,v]:a)if(v)close(*b.at(k),*v-(k==key?3.25:0),"external dual shifted other start");
        }
    }
    void numeric() {
        auto s=base();s.customers={{11,{0,0},0,0,100,0}};
        for(double v:{0.,-1e-5,1e-5,std::nextafter(1e-5,0.),std::nextafter(1e-5,1.)}) {
            context="negative rc threshold";auto d=duals(s);d.customers[11]=v;
            allBackends(s,d,exhaustive(s));
        }
        const double boundary=(100.+EPS)/7.*3.;
        for(double time:{std::nextafter(boundary,0.),boundary,std::nextafter(boundary,100.)}) {
            context="actual bucket boundary";s.currentTime=time;s.originStart.startTime=time;
            s.customers={{11,{0,0},0,time,time,0},{30,{0,0},0,time,time,0}};
            allBackends(s,duals(s),exhaustive(s));
        }
        // Equal begin/due is feasible even if service finishes after customer due.
        s=base();s.customers={{11,{0,0},1,2,2,5}};s.returnDepot.deadline=7;
        allBackends(s,duals(s),exhaustive(s));
        for(double slack:{0.5e-6,2e-6}) {
            context="return tolerance";s.returnDepot.deadline=7-slack;
            allBackends(s,duals(s),exhaustive(s));
        }
    }
    void bitset() {
        for(int n:{63,64,65,1001}) {
            context="bitset boundary n="+std::to_string(n);
            auto s=base();s.originStart.remainingCapacity=1;
            for(int i=0;i<n;++i)s.customers.push_back({10000+i,{1,0},1,0,100,0});
            auto d=duals(s);for(auto& [id,v]:d.customers)v=0;
            d.customers[10000+n-1]=8;
            DVRP_PricingController p(s);
            for(auto backend:{PricingBackend::PlainLabeling,PricingBackend::BucketGraph}) {
                PricingOptions o;o.backend=backend;o.bucketCount=1;
                const auto answer=p.price(d,o);++calls;
                check(answer.status==PricingStatus::Complete,answer.message);
                check(answer.starts.size()==1 && answer.starts[0].minimumReducedCost
                      && answer.candidates.size()==1,"analytic minimum/candidate missing");
                close(*answer.starts.at(0).minimumReducedCost,-6,"capacity-one analytic optimum");
                check(answer.candidates.at(0).route.customerSequence==std::vector<int>{10000+n-1},
                      "high customer bit was lost");
            }
        }
        auto invalid=base();
        for(int i=0;i<1002;++i)invalid.customers.push_back({i+1,{0,0},1,0,100,0});
        bool rejected=false;
        try{DVRP_PricingController p(invalid);}catch(const std::invalid_argument&){rejected=true;}
        check(rejected,"bitset overflow must be rejected before indexing");
    }
    void limits() {
        context="partial-start interruption";
        // Origin cannot reach the tight window; external start can. Interrupt
        // only the latter, after an origin result has already been collected.
        auto s=base();s.originStart.remainingCapacity=2;
        s.customers={{11,{50,0},1,0,1,0}};
        s.externalStarts={{42,StartKind::External,99,{50,0},0,2}};
        DVRP_PricingController p(s);auto d=duals(s);
        for(auto backend:{PricingBackend::PlainLabeling,PricingBackend::BucketGraph}) {
            PricingOptions o;o.backend=backend;o.maxLabels=1;
            auto r=p.price(d,o);++calls;
            check(r.status==PricingStatus::ResourceLimit && r.starts.empty() && r.candidates.empty(),
                  "partial origin result leaked after external interruption");
            o.maxLabels=200000;check(p.price(d,o).status==PricingStatus::Complete,"failed call poisoned recovery");++calls;
            o.timeLimitSeconds=1e-12;r=p.price(d,o);++calls;
            check(r.status==PricingStatus::TimeLimit && r.starts.empty() && r.candidates.empty(),"positive time limit");
        }
        PricingOptions o;o.backend=PricingBackend::BucketGraph;
        o.bucketCount=std::numeric_limits<std::size_t>::max();
        check(p.price(d,o).status==PricingStatus::ResourceLimit,"overflow graph allocation");++calls;
    }
}
int main(int argc,char** argv) {
    try {
        check(argc==2,"expected test group");
        const std::string group=argv[1];
        if(group.rfind("fuzz_",0)==0) fuzz(static_cast<unsigned>(std::stoul(group.substr(5))));
        else if(group=="seven") seven();
        else if(group=="metamorphic") metamorphic();
        else if(group=="numeric") numeric();
        else if(group=="bitset") bitset();
        else if(group=="limits") limits();
        else throw std::runtime_error("unknown group");
        std::cout<<group<<": "<<calls<<" pricing calls, "<<permutations<<" independently evaluated permutations PASS\n";
        return 0;
    } catch(const std::exception& e){std::cerr<<e.what()<<'\n';return 1;}
}

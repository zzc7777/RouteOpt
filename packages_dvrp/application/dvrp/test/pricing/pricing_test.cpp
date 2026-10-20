#include "pricing_controller.hpp"
#include "pricing_internal.hpp" // Structural tests; not a public algorithm API.
#include "stage_reference.hpp"
#include <algorithm>
#include <cmath>
#include <functional>
#include <iostream>
#include <random>
#include <set>
#include <stdexcept>

using namespace RouteOpt::Application::DVRP;
using namespace RouteOpt::Application::DVRP::Testing;
namespace {
    void check(bool ok, const std::string& why) { if (!ok) throw std::runtime_error(why); }
    void close(double a, double b, const std::string& why) {
        check(std::isfinite(a) && std::isfinite(b) && std::abs(a-b)<1e-7,why);
    }
    StageProblem sample() {
        StageProblem s; s.stageId=7; s.currentTime=100;
        s.originStart={0,StartKind::Origin,0,{0,0},100,10};
        s.returnDepot={{0,0},200};
        s.customers={{5,{12,0},2,0,180,1},{8,{14,0},2,0,180,1}};
        s.externalStarts={{42,StartKind::External,3,{10,0},130,4}};
        s.fixedCostAfterTau=123; return s;
    }
    RmpDuals duals(const StageProblem& s, RmpPhase phase=RmpPhase::Cost) {
        RmpDuals d{s.stageId,19,phase,{},{}};
        for(const auto& c:s.customers)d.customers[c.id]=8;
        for(const auto& k:s.externalStarts)d.externalStarts[k.startKey]=4;
        return d;
    }
    double rc(const EnumeratedRoute& r,const RmpDuals& d) {
        double v=d.phase==RmpPhase::Cost ? r.cost : 0;
        for(int i:r.route.customerSequence)v-=d.customers.at(i);
        if(r.route.startKey)v-=d.externalStarts.at(r.route.startKey);
        return v;
    }
    PricingResult compare(const StageProblem& s,const RmpDuals& d,PricingBackend backend,std::size_t bins=25) {
        const auto all=enumerateStage(s); // Independent, exhaustive ordered subsets.
        DVRP_PricingController pricing(s);
        PricingOptions o; o.backend=backend; o.bucketCount=bins;
        auto p=pricing.price(d,o);
        check(p.status==PricingStatus::Complete,p.message);
        check(p.stageId==s.stageId && p.modelRevision==d.modelRevision && p.phase==d.phase && p.backend==backend,"result context");
        check(p.starts.size()==s.externalStarts.size()+1,"missing start");
        std::size_t negative=0;
        for(const auto& result:p.starts) {
            double best=std::numeric_limits<double>::infinity();
            for(const auto& r:all.routes())if(r.route.startKey==result.startKey)best=std::min(best,rc(r,d));
            check(result.hasFeasibleRoute==std::isfinite(best),"feasibility mismatch");
            if(!std::isfinite(best)) {
                check(!result.minimumReducedCost && !result.bestRoute,"invented empty-universe optimum"); continue;
            }
            check(result.minimumReducedCost && result.bestRoute,"missing witness");
            close(*result.minimumReducedCost,best,"minimum reduced cost mismatch");
            bool found=false;
            for(const auto& r:all.routes())if(r.route.startKey==result.startKey &&
                r.route.customerSequence==result.bestRoute->route.customerSequence) {
                close(result.bestRoute->cost,r.cost,"witness real cost");
                close(result.bestRoute->reducedCost,rc(r,d),"witness rc"); found=true;
            }
            check(found,"witness not in independent elementary universe");
            if(best<RouteOpt::RC_TOLERANCE)++negative;
        }
        check(p.candidates.size()==negative,"one negative per start");
        for(const auto& r:p.candidates)check(r.reducedCost<RouteOpt::RC_TOLERANCE,"nonnegative candidate");
        if(backend==PricingBackend::PlainLabeling)
            check(p.statistics.bucketNodes==0 && p.statistics.bucketArcs==0 && p.statistics.graphBuildSeconds==0,"plain built a graph");
        return p;
    }
    void both(const StageProblem& s,const RmpDuals& d) {
        compare(s,d,PricingBackend::PlainLabeling);
        for(std::size_t bins:{1,3,25})compare(s,d,PricingBackend::BucketGraph,bins);
    }
    void hand() {
        auto s=sample(); auto d=duals(s); both(s,d);
        auto p=compare(s,d,PricingBackend::BucketGraph);
        close(*p.starts[1].minimumReducedCost,-2,"hand rc=-2");
        close(p.starts[1].bestRoute->cost,18,"hand distance=18");
        d.phase=RmpPhase::Feasibility;
        for(auto& [id,v]:d.customers)v=1; d.externalStarts[42]=1;
        both(s,d);
        close(*compare(s,d,PricingBackend::PlainLabeling).starts[1].minimumReducedCost,-3,"Phase-I rc=-3");
    }
    void resources() {
        auto s=sample(); s.externalStarts[0].remainingCapacity=2; both(s,duals(s));
        s=sample();s.externalStarts[0].startTime=179;both(s,duals(s));
        s=sample();s.customers[0].readyTime=150;s.customers[0].dueDate=150;s.customers[0].serviceTime=20;
        both(s,duals(s));s.customers[1].dueDate=171;both(s,duals(s));
        s=sample();s.returnDepot.deadline=150;both(s,duals(s));
        s.returnDepot.deadline=149.999;both(s,duals(s));
        s=sample();s.currentTime=100.000123;s.originStart.startTime=s.currentTime;
        s.externalStarts[0].startTime=130.000456;both(s,duals(s));
        s.externalStarts[0].startKey=5;s.externalStarts.push_back(s.externalStarts[0]);
        s.externalStarts.back().startKey=88;both(s,duals(s));
    }
    void nonmetric() {
        auto s=sample();s.currentTime=0;s.originStart.startTime=0;s.returnDepot.deadline=1;
        s.customers={{5,{0.55,0},1,0,100,0}};
        s.externalStarts={{42,StartKind::External,99,{1.1,0},0,10}};
        both(s,duals(s));
        close(compare(s,duals(s),PricingBackend::BucketGraph).starts[1].bestRoute->cost,1,"do not prune failed direct return");
    }
    void empty() {
        auto s=sample();s.customers.clear();both(s,duals(s));
        s.returnDepot.deadline=139;both(s,duals(s));
        s.externalStarts.clear();both(s,duals(s));
    }
    void zero() {
        auto s=sample();s.currentTime=0;s.originStart.startTime=0;s.returnDepot.deadline=0;
        s.customers={{8,{0,0},0,0,0,0},{5,{0,0},0,0,0,0},{99,{0,0},0,0,0,0}};
        s.externalStarts={{42,StartKind::External,3,{0,0},0,0}};
        both(s,duals(s));
        close(*compare(s,duals(s),PricingBackend::BucketGraph).starts[1].minimumReducedCost,-28,"complete memory on zero-resource cycle");
    }
    void randomCases() {
        std::mt19937 gen(4217);
        for(int t=0;t<100;++t) {
            auto s=sample();s.currentTime=0;s.originStart.startTime=0;s.returnDepot.deadline=10+gen()%50;
            s.originStart.remainingCapacity=1+gen()%8;s.customers.clear();s.externalStarts.clear();
            for(int i=0,n=1+gen()%5;i<n;++i) {
                double ready=gen()%20;
                s.customers.push_back({5+7*i,{double(gen()%9),double(gen()%9)},
                    double(gen()% (1+int(s.originStart.remainingCapacity))),ready,ready+gen()%30,double(gen()%3)});
            }
            for(int k=0,n=gen()%4;k<n;++k)
                s.externalStarts.push_back({42+k,StartKind::External,900+k,{double(gen()%8),double(gen()%8)},
                    double(gen()%15),double(gen()%(1+int(s.originStart.remainingCapacity)))});
            auto d=duals(s,t%2 ? RmpPhase::Cost:RmpPhase::Feasibility);
            for(auto& [id,v]:d.customers)v=(int(gen()%401)-100)/10.0;
            for(auto& [id,v]:d.externalStarts)v=(int(gen()%201)-100)/10.0;
            both(s,d);
        }
    }
    void validation() {
        auto s=sample();DVRP_PricingController p(s);PricingOptions o;
        auto d=duals(s);d.stageId++;check(p.price(d,o).status==PricingStatus::Error,"wrong stage");
        d=duals(s);d.customers.erase(5);check(p.price(d,o).status==PricingStatus::Error,"missing dual");
        d=duals(s);d.externalStarts[999]=1;check(p.price(d,o).status==PricingStatus::Error,"extra dual");
        d=duals(s);d.customers[5]=std::numeric_limits<double>::quiet_NaN();check(p.price(d,o).status==PricingStatus::Error,"NaN dual");
        d=duals(s);d.phase=static_cast<RmpPhase>(99);check(p.price(d,o).status==PricingStatus::Error,"bad phase");
        o.backend=static_cast<PricingBackend>(99);check(p.price(duals(s),o).status==PricingStatus::Error,"bad backend");
        const std::vector<std::function<void(StageProblem&)>> changes={
            [](auto& x){x.customers[1].id=5;},[](auto& x){x.originStart.startTime=1;},
            [](auto& x){x.externalStarts[0].locationCustomerId=5;},
            [](auto& x){x.customers[0].demand=0.5;},[](auto& x){x.returnDepot.location.x=9;},
            [](auto& x){x.externalStarts[0].remainingCapacity=-1;},
            [](auto& x){x.customers[0].serviceTime=-1;},[](auto& x){x.externalStarts.push_back(x.externalStarts[0]);},
            [](auto& x){x.customers[0].location.x=std::numeric_limits<double>::infinity();}};
        for(auto change:changes) {auto bad=s;change(bad);bool caught=false;
            try{DVRP_PricingController invalid(bad);}catch(const std::exception&){caught=true;}
            check(caught,"invalid stage accepted");
        }
    }
    void limits() {
        auto s=sample();DVRP_PricingController p(s); auto d=duals(s);
        for(auto b:{PricingBackend::PlainLabeling,PricingBackend::BucketGraph}) {
            PricingOptions o;o.backend=b;o.timeLimitSeconds=0;
            auto r=p.price(d,o);check(r.status==PricingStatus::TimeLimit && r.starts.empty() && r.candidates.empty(),"zero time must not certify");
            o.timeLimitSeconds=60;o.maxLabels=1;r=p.price(d,o);
            check(r.status==PricingStatus::ResourceLimit && r.starts.empty() && r.candidates.empty(),"label limit must not certify");
            o.maxLabels=200000;check(p.price(d,o).status==PricingStatus::Complete,"failure poisoned next call");
        }
        PricingOptions o;o.backend=PricingBackend::BucketGraph;o.maxGraphNodes=1;
        check(p.price(d,o).status==PricingStatus::ResourceLimit,"graph node budget");
        o.maxGraphNodes=100000;o.maxGraphArcs=1;
        check(p.price(d,o).status==PricingStatus::ResourceLimit,"graph arc budget");
        o.bucketCount=0;check(p.price(d,o).status==PricingStatus::Error,"zero buckets");
        d=duals(s,RmpPhase::Feasibility);d.customers[5]=1.7e308;d.customers[8]=1.7e308;
        o=PricingOptions{};auto r=p.price(d,o);
        check(r.status==PricingStatus::Error && r.starts.empty(),"arithmetic overflow must not certify");
    }
    void lifecycle() {
        auto s=sample();DVRP_PricingController p(s);auto d=duals(s);
        s.customers.clear();
        for(int i=0;i<20;++i) {
            PricingOptions o;o.backend=i%2?PricingBackend::BucketGraph:PricingBackend::PlainLabeling;
            d.phase=i%3?RmpPhase::Cost:RmpPhase::Feasibility;d.modelRevision++;
            const auto r=p.price(d,o);check(r.status==PricingStatus::Complete && r.modelRevision==d.modelRevision,"repeat call");
            close(*r.starts[1].minimumReducedCost,d.phase==RmpPhase::Cost?-2:-20,"snapshot and phase reset");
        }
    }
    void size() {
        auto s=sample();s.currentTime=0;s.originStart.startTime=0;s.originStart.remainingCapacity=1;
        s.externalStarts.clear();s.customers.clear();s.returnDepot.deadline=100;
        for(int i=1;i<=12;++i)s.customers.push_back({i,{double(i),0},1,0,100,0});
        DVRP_PricingController p(s);auto d=duals(s);
        for(auto b:{PricingBackend::PlainLabeling,PricingBackend::BucketGraph}) {
            PricingOptions o;o.backend=b;auto r=p.price(d,o);
            check(r.status==PricingStatus::Complete,"production inherited oracle size limit");
            close(*r.starts[0].minimumReducedCost,-6,"12 customer singleton oracle");
        }
    }
    void graph() {
        using namespace PricingDetail;
        auto s=sample();s.currentTime=0;s.originStart.startTime=0;s.returnDepot.deadline=40;
        s.externalStarts.clear();
        s.customers={{5,{0,0},0,0,40,0},{8,{0,0},0,0,40,0},{99,{100,0},0,0,40,0}};
        PreparedStage data(s);auto d=duals(s);PricingOptions o;o.backend=PricingBackend::BucketGraph;o.bucketCount=4;
        Budget budget{Clock::now(),o};PricingStatistics stats;Work w(data,s.originStart,d,budget,stats);
        auto g=buildBucketGraph(w);
        check(g.lower.size()==4 && g.order.size()==4,"graph bins");
        for(std::size_t b=0;b<4;++b) {
            std::set<int> vertices;bool pair=false;
            for(const auto& comp:g.order[b]){for(int v:comp)check(vertices.insert(v).second,"duplicate SCC vertex");
                if(std::find(comp.begin(),comp.end(),1)!=comp.end() && std::find(comp.begin(),comp.end(),2)!=comp.end())pair=true;}
            check(vertices.size()==3 && pair,"isolated vertex or zero-time SCC omitted");
        }
        check(g.bin(9)<g.bin(12),"actual extension must cross bin");
        for(std::size_t b=1;b<g.lower.size();++b) {
            check(g.bin(g.lower[b])==b,"exact bucket boundary");
            check(g.bin(std::nextafter(g.lower[b],-std::numeric_limits<double>::infinity()))==b-1,"before bucket boundary");
        }
        both(s,d);
    }
    void dominance() {
        using namespace PricingDetail;
        auto s=sample();s.currentTime=0;s.originStart.startTime=0;s.originStart.remainingCapacity=2;
        s.externalStarts.clear();s.returnDepot.deadline=10;
        s.customers={{5,{1,0},1,0,10,0},{8,{2,0},1,0,10,0}};
        auto d=duals(s);d.customers[5]=0;d.customers[8]=100;
        both(s,d);
        auto r=compare(s,d,PricingBackend::BucketGraph,10);
        check(r.statistics.boundSkips>0 && r.statistics.crossBinExtensions>0,"cross-bin cost bounds not exercised");
        d.customers[8]=0;r=compare(s,d,PricingBackend::BucketGraph,10);
        check(r.statistics.crossBinDominated>0,"cross-bin dominance not exercised");
        PreparedStage data(s);PricingOptions o;Budget budget{Clock::now(),o};PricingStatistics stats;
        Work w(data,s.originStart,d,budget,stats);
        Label a;a.vertex=1;a.time=1;a.load=1;a.rc=1;a.visited.set(1);
        Label b=a;b.time=3;b.load=2;b.rc=3;b.visited.set(2);
        check(w.dominates(a,b) && !w.dominates(b,a),"full memory subset dominance");
        b.rc=0;check(!w.dominates(a,b),"cheaper later label must survive");
        b=a;b.visited.reset(1);b.visited.set(2);check(!w.dominates(a,b),"different visited sets");
        b=a;b.time=std::nextafter(a.time,0.0);check(!w.dominates(a,b),"dominance must not erase a slightly earlier label");
    }
    void boundary() {
        auto s=sample();s.currentTime=0;s.originStart.startTime=0;s.externalStarts.clear();
        const double ready=1.234567;
        s.customers={{5,{0,0},0,ready,ready,0}};
        s.returnDepot.deadline=ready-0.5e-6;both(s,duals(s));
        check(compare(s,duals(s),PricingBackend::BucketGraph).starts[0].hasFeasibleRoute,"accepted H tolerance lost in graph");
        s.returnDepot.deadline=ready-2e-6;both(s,duals(s));
        check(!compare(s,duals(s),PricingBackend::PlainLabeling).starts[0].hasFeasibleRoute,"H tolerance too loose");
    }
    void dense() {
        auto s=sample();s.currentTime=0;s.originStart.startTime=0;s.originStart.remainingCapacity=10;
        s.customers.clear();s.externalStarts.clear();s.returnDepot.deadline=100;
        for(int i=0;i<5;++i)s.customers.push_back({10+i,{double(i+1),double(i%2)},1,0,100,0});
        for(int k=0;k<5;++k)s.externalStarts.push_back({42+k,StartKind::External,900+k,{double(k),0},0,10});
        check(enumerateStage(s).routes().size()==1955,"expected full oracle size");
        auto d=duals(s);
        for(int pass=0;pass<8;++pass) {
            d.phase=pass%2?RmpPhase::Feasibility:RmpPhase::Cost;
            for(auto& [id,value]:d.customers)value=pass%3==0 ? -id:double((id*13+pass*7)%31-8);
            both(s,d);
        }
        // Replay identical inputs; these counters are not a large-instance speed claim.
        for(auto backend:{PricingBackend::PlainLabeling,PricingBackend::BucketGraph}) {
            auto r=compare(s,duals(s),backend);
            const auto& st=r.statistics;
            std::cout<<"5 customers / 5 external starts / 1955 routes, "
                <<(backend==PricingBackend::PlainLabeling?"plain":"bucket")
                <<" labels="<<st.generatedLabels<<" extended="<<st.extendedLabels
                <<" dominance="<<st.dominanceChecks<<" peak_retained_labels="<<st.peakRetainedLabels
                <<" bucket_nodes="<<st.bucketNodes<<" bucket_arcs="<<st.bucketArcs
                <<" graph_seconds="<<st.graphBuildSeconds<<" search_seconds="<<st.searchSeconds
                <<" total_seconds="<<r.seconds<<'\n';
        }
    }
}
int main(int argc,char** argv) {
    const std::map<std::string,std::function<void()>> tests={{"hand",hand},{"resources",resources},{"nonmetric",nonmetric},
        {"empty",empty},{"zero",zero},{"random",randomCases},{"validation",validation},{"limits",limits},
        {"lifecycle",lifecycle},{"size",size},{"graph",graph},{"dominance",dominance},{"boundary",boundary},{"dense",dense}};
    try {
        if(argc==2)tests.at(argv[1])();else for(const auto& [name,f]:tests)f();
        std::cout<<"pricing tests passed\n";return 0;
    }catch(const std::exception& e){std::cerr<<e.what()<<'\n';return 1;}
}

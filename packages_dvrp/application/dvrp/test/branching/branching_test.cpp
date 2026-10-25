// Independent branching coefficients, exhaustive minima, and dominance suffix checks.
#include "branching.hpp"
#include "pricing_internal.hpp"
#include "stage_reference.hpp"
#include <algorithm>
#include <cmath>
#include <functional>
#include <iostream>
#include <limits>
#include <random>
#include <set>
#include <stdexcept>

using namespace RouteOpt::Application::DVRP;
using namespace RouteOpt::Application::DVRP::Testing;
namespace {
    constexpr PricingBackend backends[]={PricingBackend::PlainLabeling,PricingBackend::BucketGraph};
    std::size_t calls[2]{},labels[2]{};
    double pricingSeconds[2]{};
    RfElement C(int id) {return {RfElementKind::Customer,id};}
    RfElement K(int id) {return {RfElementKind::ExternalStart,id};}
    ArcEndpoint V(int id) {return {ArcEndpointKind::Customer,id};}
    const ArcEndpoint source{ArcEndpointKind::Source,0},sink{ArcEndpointKind::Return,0};
    BranchContext rf(std::vector<BranchDecision> d={}) {return {7,BranchStrategy::RyanFoster,std::move(d)};}
    BranchContext arc(std::vector<BranchDecision> d={}) {return {7,BranchStrategy::StartDirectedArc,std::move(d)};}
    void check(bool b,const std::string& why) {if(!b)throw std::runtime_error(why);}
    void close(double a,double b,const char* why) {check(std::isfinite(a)&&std::isfinite(b)&&std::abs(a-b)<1e-7,why);}
    template<class F> void rejects(F f) {bool caught=false;try{f();}catch(const std::exception&){caught=true;}check(caught,"expected rejection");}
    StageProblem sample() {
        StageProblem s;s.stageId=29;s.originStart={0,StartKind::Origin,0,{0,0},0,3};s.returnDepot={{0,0},100};
        s.customers={{3,{3,0},1,0,90,0},{5,{5,0},1,0,90,0},{8,{1,0},1,0,90,0}};
        s.externalStarts={{3,StartKind::External,99,{2,0},1,3},{42,StartKind::External,98,{4,0},2,2}};
        return s;
    }
    // Test oracle uses row coefficients, not routeAllowed or compiled label rules.
    int a(const StageRoute& r,RfElement e) {
        if(e.kind==RfElementKind::ExternalStart)return r.startKey==e.key;
        return static_cast<int>(std::count(r.customerSequence.begin(),r.customerSequence.end(),e.key));
    }
    int code(ArcEndpoint e) {return e.kind==ArcEndpointKind::Source?-1:e.kind==ArcEndpointKind::Return?-2:e.customerId;}
    int coefficient(const StageRoute& r,const BranchDecision& d) {
        if(const auto* f=std::get_if<RfDecision>(&d))return a(r,f->first)*a(r,f->second);
        const auto& edge=std::get<ArcDecision>(d).arc;
        if(edge.startKey!=r.startKey)return 0;
        std::vector<int> walk{-1};walk.insert(walk.end(),r.customerSequence.begin(),r.customerSequence.end());walk.push_back(-2);
        int count=0;
        for(std::size_t i=1;i<walk.size();++i)count+=walk[i-1]==code(edge.from)&&walk[i]==code(edge.to);
        return count;
    }
    bool allowed(const StageRoute& r,const BranchContext& c) {
        for(const auto& d:c.decisions) {
            const int b=coefficient(r,d);
            if(const auto* f=std::get_if<RfDecision>(&d)) {
                if(f->together ? a(r,f->first)!=b || a(r,f->second)!=b : b!=0)return false;
            } else {
                const auto& e=std::get<ArcDecision>(d);
                if(!e.use) {if(b!=0)return false;continue;}
                if(e.arc.from.kind==ArcEndpointKind::Customer && a(r,C(e.arc.from.customerId))!=b)return false;
                if(e.arc.to.kind==ArcEndpointKind::Customer && a(r,C(e.arc.to.customerId))!=b)return false;
                if(e.arc.from.kind==ArcEndpointKind::Source && e.arc.to.kind==ArcEndpointKind::Return
                    && a(r,K(e.arc.startKey))!=b)return false;
            }
        }
        return true;
    }
    RmpDuals duals(const StageProblem& s,const BranchContext& c,RmpPhase phase) {
        RmpDuals d;d.stageId=s.stageId;d.modelRevision=13;d.phase=phase;d.context=normalizeBranches(s,c);
        for(const auto& i:s.customers)d.customers[i.id]=phase==RmpPhase::Cost?12.+i.id%5:1.;
        for(const auto& k:s.externalStarts)d.externalStarts[k.startKey]=3.;return d;
    }
    void compare(const StageProblem& s,const BranchContext& c,const RmpDuals& d) {
        const auto all=enumerateStage(s);
        for(auto backend:backends) {
            PricingOptions o;o.backend=backend;o.bucketCount=7;
            const auto p=DVRP_PricingController(s,c).price(d,o);
            const int bi=backend==PricingBackend::PlainLabeling?0:1;
            ++calls[bi];labels[bi]+=p.statistics.generatedLabels;pricingSeconds[bi]+=p.seconds;
            check(p.status==PricingStatus::Complete,p.message);
            check(p.context==d.context&&p.modelRevision==d.modelRevision,"pricing context");
            for(const auto& start:p.starts) {
                double minimum=std::numeric_limits<double>::infinity();
                for(const auto& r:all.routes()) {
                    check(routeAllowed(r.route,d.context)==allowed(r.route,c),"column filtering != independent coefficients");
                    if(r.route.startKey!=start.startKey || !allowed(r.route,c))continue;
                    double rc=d.phase==RmpPhase::Cost?r.cost:0;
                    for(int id:r.route.customerSequence)rc-=d.customers.at(id);
                    if(r.route.startKey)rc-=d.externalStarts.at(r.route.startKey);
                    minimum=std::min(minimum,rc);
                }
                check(start.hasFeasibleRoute==std::isfinite(minimum),"feasible route flag");
                if(start.hasFeasibleRoute) {
                    close(*start.minimumReducedCost,minimum,"wrong constrained minimum rc");
                    check(allowed(start.bestRoute->route,c),"invalid constrained witness");
                }
            }
        }
    }
    void bothPhases(const StageProblem& s,const BranchContext& c) {
        for(auto phase:{RmpPhase::Feasibility,RmpPhase::Cost})compare(s,c,duals(s,c,phase));
    }
    void normalize() {
        auto s=sample();auto c=rf({RfDecision{C(5),C(3),true},RfDecision{C(3),C(5),true},RfDecision{C(3),C(5),false}});
        auto n=normalizeBranches(s,c);check(n.decisions.size()==2&&c.decisions.size()==3,"dedup mutated input or erased contradiction");
        check(normalizeBranches(s,n)==n,"normalization not idempotent");
        rejects([&]{normalizeBranches(s,rf({RfDecision{C(3),C(3),true}}));});
        rejects([&]{normalizeBranches(s,rf({RfDecision{C(3),K(0),true}}));});
        rejects([&]{normalizeBranches(s,arc({ArcDecision{{0,source,sink},true}}));});
        rejects([&]{normalizeBranches(s,arc({ArcDecision{{3,V(3),V(3)},true}}));});
        rejects([&]{normalizeBranches(s,arc({ArcDecision{{3,sink,V(3)},true}}));});
        rejects([&]{normalizeBranches(s,arc({ArcDecision{{3,V(3),source},true}}));});
        rejects([&]{normalizeBranches(s,rf({ArcDecision{{3,source,V(3)},false}}));});
        rejects([&]{normalizeBranches(s,arc({ArcDecision{{19,source,V(3)},false}}));});
        auto bad=rf();bad.strategy=static_cast<BranchStrategy>(99);rejects([&]{normalizeBranches(s,bad);});
        auto distinct=normalizeBranches(s,rf({RfDecision{C(3),K(3),true}}));check(distinct.decisions.size()==1,"typed key collision");
    }
    void routes() {
        auto s=sample();
        check(routeAllowed({0,{5,8,3}},normalizeBranches(s,rf({RfDecision{C(3),C(5),true}}))),"RF does not require adjacency");
        auto no=normalizeBranches(s,arc({ArcDecision{{3,V(3),V(5)},false}}));
        check(!routeAllowed({3,{3,5}},no)&&routeAllowed({0,{3,5}},no)&&routeAllowed({3,{5,3}},no),"arc0 scope");
        auto yes=normalizeBranches(s,arc({ArcDecision{{3,V(3),V(5)},true}}));
        check(!routeAllowed({0,{3,5}},yes)&&!routeAllowed({3,{3}},yes)&&routeAllowed({0,{8}},yes),"arc1 endpoint implications");
        check(routeAllowed({3,{}},yes),"forcing customer arc does not force every k column");
        auto start=normalizeBranches(s,arc({ArcDecision{{0,source,V(3)},true}}));
        check(routeAllowed({0,{5,8}},start)&&!routeAllowed({0,{5,3}},start),"origin can launch multiple routes");
        for(auto c:{rf({RfDecision{K(3),C(5),true}}),rf({RfDecision{K(3),K(42),true}}),yes,no,start})bothPhases(s,c);
    }
    std::pair<RmpLpResult,std::vector<RmpColumn>> fractional(const StageProblem& s,BranchContext c) {
        RmpLpResult lp;lp.stageId=s.stageId;lp.modelRevision=13;lp.phase=RmpPhase::Cost;
        lp.status=RmpLpStatus::OptimalRmp;lp.context=normalizeBranches(s,c);lp.artificialSum=0;lp.objective=30;
        lp.lambda={.5,.5,.5};lp.duals=duals(s,c,RmpPhase::Cost);
        return {lp,{{0,{0,{3,5}},20,3},{1,{0,{3,8}},20,4},{2,{0,{5,8}},20,5}}};
    }
    void candidates() {
        auto s=sample();s.externalStarts.clear();
        for(auto c:{rf(),arc()}) {
            auto [lp,cols]=fractional(s,c);auto picked=selectCandidate(s,c,lp,cols);
            check(picked.status==BranchSelectionStatus::CandidateFound,picked.message);close(picked.selected->flow,.5,"flow");
            auto [left,right]=makeChildren(s,c,picked,8,9);
            check(left.nodeId==8&&right.nodeId==9&&left.decisions.size()==1,"children identity");
            rejects([&]{makeChildren(s,c,picked,8,8);});
            auto wrong=c;wrong.nodeId=100;rejects([&]{makeChildren(s,wrong,picked,8,9);});
        }
        s=sample();s.customers.resize(1);s.externalStarts.resize(1);
        auto [lp,cols]=fractional(s,rf());cols={{0,{0,{3}},1,2},{1,{3,{3}},1,3},{2,{3,{}},1,4}};
        lp.lambda={.5,.5,.5};lp.objective=1.5;
        auto p=selectCandidate(s,rf(),lp,cols);check(p.status==BranchSelectionStatus::CandidateFound,p.message);
        auto f=std::get<RfDecision>(p.selected->decision);check(f.first==C(3)&&f.second==K(3),"external element omitted");
    }
    void noCandidate() {
        auto s=sample();s.externalStarts.clear();s.customers.resize(2);
        auto [lp,cols]=fractional(s,rf());cols={{0,{0,{3,5}},20,2},{1,{0,{5,3}},20,3}};lp.lambda={.5,.5};lp.objective=20;
        auto r=selectCandidate(s,rf(),lp,cols);
        check(r.status==BranchSelectionStatus::NoFractionalCandidate&&!r.selected,"same-cover permutations are not a new candidate");
        check(lp.lambda[0]==.5,"must not round fractional route values");
        lp.context=arc();lp.duals->context=arc();
        check(selectCandidate(s,arc(),lp,cols).status==BranchSelectionStatus::CandidateFound,"directed flows distinguish permutations");
    }
    void validation() {
        auto s=sample();s.externalStarts.clear();auto [lp,cols]=fractional(s,rf());
        auto expect=[&](const RmpLpResult& x,BranchSelectionStatus status){check(selectCandidate(s,rf(),x,cols).status==status,"bad result not rejected");};
        auto bad=lp;bad.context.nodeId++;expect(bad,BranchSelectionStatus::Error);
        bad=lp;bad.duals->modelRevision++;expect(bad,BranchSelectionStatus::Error);
        bad=lp;bad.phase=RmpPhase::Feasibility;expect(bad,BranchSelectionStatus::Error);
        bad=lp;bad.artificialSum=.1;expect(bad,BranchSelectionStatus::NumericalUnresolved);
        bad=lp;bad.lambda[0]=std::numeric_limits<double>::quiet_NaN();expect(bad,BranchSelectionStatus::NumericalUnresolved);
        bad=lp;bad.lambda[0]+=.01;expect(bad,BranchSelectionStatus::NumericalUnresolved);
        bad=lp;bad.lambda[0]=-.1;expect(bad,BranchSelectionStatus::NumericalUnresolved);
        // Tiny but material fractional flow: aggregate first, never round each column.
        s.customers.resize(2);cols={{0,{0,{3,5}},1,2},{1,{0,{3}},1,3},{2,{0,{5}},1,4}};
        lp.lambda={1-1e-4,1e-4,1e-4};lp.objective=1+1e-4;lp.duals=duals(s,rf(),RmpPhase::Cost);
        check(selectCandidate(s,rf(),lp,cols).status==BranchSelectionStatus::CandidateFound,"small fraction was rounded away");
    }
    void rfPricing() {
        auto s=sample();
        for(auto u:{C(3),C(5),C(8),K(3),K(42)})for(auto v:{C(3),C(5),C(8),K(3),K(42)})if(u<v)
            for(bool together:{false,true})bothPhases(s,rf({RfDecision{u,v,together}}));
        bothPhases(s,rf({RfDecision{C(3),C(5),true},RfDecision{C(5),C(8),true},RfDecision{K(42),C(8),false}}));
    }
    void arcPricing() {
        auto s=sample();
        for(int k:{0,3,42})for(auto u:{source,V(3),V(5),V(8)})for(auto v:{V(3),V(5),V(8),sink})
            if(u!=v && !(k==0&&u==source&&v==sink))for(bool use:{false,true})bothPhases(s,arc({ArcDecision{{k,u,v},use}}));
        bothPhases(s,arc({ArcDecision{{3,source,V(3)},true},ArcDecision{{3,V(3),V(5)},true},ArcDecision{{3,V(5),sink},true}}));
    }
    void conflicts() {
        auto s=sample();
        for(auto c:{rf({RfDecision{C(3),C(5),true},RfDecision{C(3),C(5),false}}),
            rf({RfDecision{C(3),K(3),true},RfDecision{C(3),K(42),true}}),
            arc({ArcDecision{{3,V(3),V(5)},true},ArcDecision{{3,V(3),V(8)},true}}),
            arc({ArcDecision{{3,V(3),V(5)},true},ArcDecision{{3,V(3),V(5)},false}}),
            arc({ArcDecision{{3,V(3),V(5)},true},ArcDecision{{3,V(5),V(3)},true}}),
            arc({ArcDecision{{3,source,sink},true},ArcDecision{{3,source,sink},false}})})bothPhases(s,c);
    }
    void random() {
        std::mt19937 rng(20261025);
        for(int trial=0;trial<200;++trial) {
            auto s=sample();s.customers.push_back({11,{0,0},1,0,90,0});s.customers.push_back({17,{0,0},1,0,90,0});
            s.customers.resize(2+rng()%4);s.originStart.remainingCapacity=1+rng()%4;
            for(auto& k:s.externalStarts){k.remainingCapacity=rng()%4;k.remainingCapacity=std::min(k.remainingCapacity,s.originStart.remainingCapacity);}
            for(auto& i:s.customers){i.location={double(rng()%8),double(rng()%8)};i.demand=rng()%2;i.readyTime=rng()%10;i.dueDate=10+rng()%30;i.serviceTime=rng()%3;}
            if(trial%5==0)for(auto& i:s.customers){i.location={0,0};i.demand=0;i.serviceTime=0;}
            BranchContext c=trial%2?rf():arc();
            for(unsigned depth=0;depth<1+rng()%5;++depth) {
                int u=s.customers[rng()%s.customers.size()].id,v=s.customers[rng()%s.customers.size()].id;
                if(c.strategy==BranchStrategy::RyanFoster) {
                    if(u==v)c.decisions.push_back(RfDecision{C(u),K(rng()%2?3:42),bool(rng()%2)});
                    else c.decisions.push_back(RfDecision{C(u),C(v),bool(rng()%2)});
                } else {
                    const int k=std::vector<int>{0,3,42}[rng()%3];
                    c.decisions.push_back(ArcDecision{{k,u==v?source:V(u),V(v)},bool(rng()%2)});
                }
            }
            for(auto phase:{RmpPhase::Feasibility,RmpPhase::Cost}) {
                auto d=duals(s,c,phase);for(auto& [id,y]:d.customers)y=double(int(rng()%81)-20)/4;
                for(auto& [id,y]:d.externalStarts)y=double(int(rng()%41)-10)/4;
                compare(s,c,d);
            }
        }
        std::cout<<"random stages=200, backend/phase pricing calls=800\n";
    }
    void dominance() {
        using namespace PricingDetail;
        auto s=sample();s.customers.push_back({11,{0,0},0,0,100,0});s.originStart.remainingCapacity=10;
        for(auto& i:s.customers){i.location={0,0};i.demand=0;i.serviceTime=0;}
        s.externalStarts.clear();
        const auto all=enumerateStage(s);std::size_t checkedSuffixes=0;
        for(auto c:{rf({RfDecision{C(3),C(5),true}}),rf({RfDecision{C(3),C(5),false}}),
            arc({ArcDecision{{0,V(3),V(5)},true}}),arc({ArcDecision{{0,V(3),V(5)},false}})}) {
            PreparedStage data(s,c);auto d=duals(s,c,RmpPhase::Cost);for(auto& [id,y]:d.customers)y=0;
            PricingOptions options;Budget budget{Clock::now(),options};PricingStatistics stats;Work w(data,s.originStart,d,budget,stats);
            std::vector<std::vector<int>> paths(1);
            for(std::size_t id=0;id<w.labels.size();++id)for(int j=1;j<=data.n();++j)if(auto next=w.extend(id,j)) {
                auto path=paths[id];path.push_back(s.customers[j-1].id);w.retain(*next);paths.push_back(path);
            }
            for(std::size_t aId=1;aId<w.labels.size();++aId)for(std::size_t bId=1;bId<w.labels.size();++bId) {
                if(aId==bId || !w.dominates(w.labels[aId],w.labels[bId]))continue;
                for(const auto& r:all.routes()) {
                    const auto& seq=r.route.customerSequence;const auto& prefix=paths[bId];
                    if(seq.size()<prefix.size() || !std::equal(prefix.begin(),prefix.end(),seq.begin()) || !allowed(r.route,c))continue;
                    StageRoute replacement{0,paths[aId]};replacement.customerSequence.insert(replacement.customerSequence.end(),seq.begin()+prefix.size(),seq.end());
                    auto found=std::find_if(all.routes().begin(),all.routes().end(),[&](const auto& x){return x.route.customerSequence==replacement.customerSequence;});
                    check(found!=all.routes().end()&&allowed(replacement,c),"dominance removed a feasible suffix");++checkedSuffixes;
                }
            }
            if(c.strategy==BranchStrategy::RyanFoster&&std::get<RfDecision>(c.decisions.front()).together) {
                Label aLabel,bLabel;aLabel.vertex=bLabel.vertex=3;
                aLabel.visited.set(1);aLabel.visited.set(3);bLabel=aLabel;bLabel.visited.set(2);
                check(!w.dominates(aLabel,bLabel),"RF pending obligation incorrectly dominated");
            }
        }
        check(checkedSuffixes>0,"vacuous suffix test");std::cout<<"dominance suffixes="<<checkedSuffixes<<'\n';
    }
    void limits() {
        auto s=sample();auto c=rf({RfDecision{C(3),C(5),true}});auto d=duals(s,c,RmpPhase::Cost);
        for(auto b:backends)for(int mode=0;mode<3;++mode) {
            PricingOptions o;o.backend=b;if(mode==0)o.timeLimitSeconds=0;else o.maxLabels=mode==1?0:1;
            auto r=DVRP_PricingController(s,c).price(d,o);
            check(r.status==(mode==0?PricingStatus::TimeLimit:PricingStatus::ResourceLimit),"wrong interruption status");
            check(r.starts.empty()&&r.candidates.empty(),"interruption exported a certificate");
        }
    }
    void identity() {
        auto s=sample();auto c=rf({RfDecision{C(3),C(5),true}});auto d=duals(s,c,RmpPhase::Cost);
        DVRP_PricingController p(s,c);PricingOptions o;
        d.context.nodeId++;check(p.price(d,o).status==PricingStatus::Error,"sibling dual accepted");
        d=duals(s,c,RmpPhase::Cost);std::get<RfDecision>(d.context.decisions[0]).together=false;
        check(p.price(d,o).status==PricingStatus::Error,"same-ID different restrictions accepted");
        d=duals(s,c,RmpPhase::Cost);d.stageId++;check(p.price(d,o).status==PricingStatus::Error,"old stage accepted");
        c.decisions.clear();check(p.price(duals(s,rf({RfDecision{C(3),C(5),true}}),RmpPhase::Cost),o).status==PricingStatus::Complete,"context not owned");
    }
}
int main(int argc,char** argv) {
    try {
        check(argc==2,"one case required");const std::string c=argv[1];
        if(c=="normalize")normalize();else if(c=="routes")routes();else if(c=="candidates")candidates();
        else if(c=="no_candidate")noCandidate();else if(c=="validation")validation();else if(c=="rf_pricing")rfPricing();
        else if(c=="arc_pricing")arcPricing();else if(c=="conflicts")conflicts();else if(c=="random")random();
        else if(c=="dominance")dominance();else if(c=="limits")limits();else if(c=="identity")identity();
        else throw std::runtime_error("unknown case");
        for(int b=0;b<2;++b)if(calls[b])std::cout<<(b?"bucket":"plain")<<" calls="<<calls[b]<<" labels="<<labels[b]<<" seconds="<<pricingSeconds[b]<<'\n';
        std::cout<<"PASS "<<c<<'\n';return 0;
    } catch(const std::exception& e){std::cerr<<e.what()<<'\n';return 1;}
}

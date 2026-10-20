#include "pricing_mip.hpp"
#include "pricing_controller.hpp"
#include "pricing_internal.hpp" // Only the dominance audit inspects the production predicate.
#include "dynamic_controller.hpp"
#include "read_data_controller.hpp"
#include "stage_reference.hpp"
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
using namespace RouteOpt::Application::DVRP::Testing;
namespace {
    std::size_t mipCalls=0, certificates=0, pricingCalls=0;
    std::string context;
    struct Unresolved : std::runtime_error { using std::runtime_error::runtime_error; };
    void check(bool ok,const std::string& why) { if(!ok)throw std::runtime_error(context+": "+why); }
    void close(double a,double b,const char* why) { check(std::isfinite(a)&&std::isfinite(b)&&std::abs(a-b)<=1e-6,why); }
    std::vector<StartSpec> starts(const StageProblem& s) {
        std::vector<StartSpec> k{s.originStart}; k.insert(k.end(),s.externalStarts.begin(),s.externalStarts.end()); return k;
    }
    double arc(Location a,Location b) {
        const double x=a.x-b.x,y=a.y-b.y;
        return std::floor((static_cast<double>(std::sqrt(static_cast<float>(x*x+y*y)))+1e-6)*10.)/10.;
    }
    StageProblem base() {
        StageProblem s; s.stageId=1; s.originStart={0,StartKind::Origin,0,{0,0},0,6};
        s.returnDepot={{0,0},100}; return s;
    }
    RmpDuals duals(const StageProblem& s,RmpPhase phase) {
        RmpDuals d{s.stageId,7,phase,{},{}};
        for(const auto& c:s.customers)d.customers[c.id]=phase==RmpPhase::Cost?12:1;
        for(const auto& k:s.externalStarts)d.externalStarts[k.startKey]=phase==RmpPhase::Cost?3:1;
        return d;
    }
    PricingMipResult oracle(const StageProblem& s,const RmpDuals& d,int key,double limit=10) {
        ++mipCalls; auto r=solvePricingMip(s,d,key,limit);
        check(r.status!=PricingMipStatus::Error,r.message);
        check(r.stageId==s.stageId && r.modelRevision==d.modelRevision && r.phase==d.phase && r.startKey==key,"oracle context");
        return r;
    }
    void certificate(const PricingMipResult& r,std::optional<double> claim) {
        const auto verdict=certifyPricingMinimum(r,claim);
        if(verdict!=MinimumAudit::Certified) {
            std::cerr<<"AUDIT start="<<r.startKey<<" phase="<<int(r.phase)<<" status="<<int(r.status)
                <<" raw="<<r.rawStatus.value_or(-1)<<" lower="<<r.lowerBound.value_or(-1e100)
                <<" incumbent="<<(r.incumbent?r.incumbent->reducedCost:1e100)
                <<" claim="<<claim.value_or(1e100)<<'\n';
        }
        check(verdict!=MinimumAudit::Mismatch,"independent MIP/labeling disagreement");
        if(verdict==MinimumAudit::Unresolved)throw Unresolved(context+": independent bound not closed");
        ++certificates;
    }
    std::vector<PricingResult> audit(const StageProblem& s,const RmpDuals& d,double limit=10,bool verbose=false,
                                     std::vector<PricingMipResult>* bounds=nullptr) {
        std::vector<PricingMipResult> refs;
        for(const auto& k:starts(s)) {
            refs.push_back(oracle(s,d,k.startKey,limit));
            if(verbose) {
                const auto& r=refs.back();
                std::cout<<"MIP start="<<k.startKey<<" phase="<<(d.phase==RmpPhase::Cost?2:1)
                    <<" status="<<int(r.status)<<" lower="<<r.lowerBound.value_or(-1e100)
                    <<" incumbent="<<(r.incumbent?r.incumbent->reducedCost:1e100)<<" seconds="<<r.seconds<<'\n';
            }
        }
        DVRP_PricingController p(s); std::vector<PricingResult> answers;
        for(auto backend:{PricingBackend::PlainLabeling,PricingBackend::BucketGraph}) {
            PricingOptions o; o.backend=backend; o.timeLimitSeconds=limit;
            auto r=p.price(d,o); ++pricingCalls;
            check(r.status!=PricingStatus::Error,r.message);
            if(r.status!=PricingStatus::Complete) {
                check(r.starts.empty() && r.candidates.empty(),"interrupted pricing leaked a certificate");
                throw Unresolved(context+": labeling did not finish: "+r.message);
            }
            check(r.stageId==s.stageId && r.modelRevision==d.modelRevision && r.phase==d.phase,"pricing context");
            check(r.starts.size()==refs.size(),"wrong start count");
            std::set<int> seen, expectedCandidates, actualCandidates;
            for(const auto& best:r.starts) {
                check(seen.insert(best.startKey).second,"duplicate start");
                const auto it=std::find_if(refs.begin(),refs.end(),[&](const auto& a){return a.startKey==best.startKey;});
                check(it!=refs.end(),"unknown start");
                check(best.hasFeasibleRoute==best.bestRoute.has_value() && best.hasFeasibleRoute==best.minimumReducedCost.has_value(),"inconsistent result presence");
                if(best.bestRoute) {
                    check(best.bestRoute->route.startKey==best.startKey,"wrong witness start");
                    const auto w=checkPricingRoute(s,d,best.bestRoute->route);
                    close(w.cost,best.bestRoute->cost,"label witness cost");
                    close(w.reducedCost,*best.minimumReducedCost,"label witness minimum");
                    close(w.reducedCost,best.bestRoute->reducedCost,"label witness rc");
                    if(*best.minimumReducedCost < -1e-5)expectedCandidates.insert(best.startKey);
                }
                certificate(*it,best.minimumReducedCost);
            }
            for(const auto& c:r.candidates) {
                check(actualCandidates.insert(c.route.startKey).second,"duplicate candidate");
                const auto w=checkPricingRoute(s,d,c.route);
                close(w.reducedCost,c.reducedCost,"candidate rc"); close(w.cost,c.cost,"candidate cost");
                const auto best=std::find_if(r.starts.begin(),r.starts.end(),[&](const auto& b){return b.startKey==c.route.startKey;});
                check(best!=r.starts.end() && best->minimumReducedCost,"candidate context");
                close(c.reducedCost,*best->minimumReducedCost,"candidate minimum");
            }
            check(expectedCandidates==actualCandidates,"wrong negative-column decision");
            if(verbose)std::cout<<"CERTIFIED backend="<<(backend==PricingBackend::PlainLabeling?"plain":"bucket")
                <<" phase="<<(d.phase==RmpPhase::Cost?2:1)<<" starts="<<r.starts.size()<<" seconds="<<r.seconds<<'\n';
            answers.push_back(std::move(r));
        }
        if(bounds)*bounds=std::move(refs);
        return answers;
    }
    void againstEnumeration(const StageProblem& s,const RmpDuals& d) {
        // Existing <=5-customer oracle: independent prefix resource propagation, no MIP/pricing helper.
        const auto all=enumerateStage(s);
        for(const auto& k:starts(s)) {
            std::optional<double> best;
            for(const auto& route:all.routes())if(route.route.startKey==k.startKey) {
                double rc=d.phase==RmpPhase::Cost?route.cost:0;
                for(int id:route.route.customerSequence)rc-=d.customers.at(id);
                if(k.startKey)rc-=d.externalStarts.at(k.startKey);
                if(!best || rc<*best)best=rc;
            }
            certificate(oracle(s,d,k.startKey),best);
        }
        audit(s,d);
    }
    void hand() {
        auto s=base(); s.customers={{11,{2,0},2,0,80,1},{29,{4,0},2,0,80,1}};
        s.externalStarts={{42,StartKind::External,99,{3,0},10,2}};
        for(auto phase:{RmpPhase::Feasibility,RmpPhase::Cost}) {
            auto d=duals(s,phase); againstEnumeration(s,d);
            const auto r=oracle(s,d,0); check(r.incumbent.has_value(),"missing hand route");
            close(r.incumbent->reducedCost,phase==RmpPhase::Cost?-16:-2,"hand minimum");
        }
        s.customers.clear(); auto d=duals(s,RmpPhase::Cost); d.externalStarts[42]=10;
        againstEnumeration(s,d); close(oracle(s,d,42).incumbent->reducedCost,-7,"external empty offset");
        s.externalStarts.clear(); againstEnumeration(s,duals(s,RmpPhase::Cost));
    }
    void randomOracle() {
        std::mt19937 gen(20261018);
        for(int t=0;t<60;++t) {
            context="oracle seed=20261018 case="+std::to_string(t);
            auto s=base(); s.stageId=t+1; s.currentTime=double(gen()%10)/2; s.originStart.startTime=s.currentTime;
            s.returnDepot.deadline=s.currentTime+(t%3?60:3); s.originStart.remainingCapacity=3;
            for(int i=0,n=gen()%6;i<n;++i) {
                const double ready=gen()%15;
                s.customers.push_back({7+13*i,{double(gen()%10),double(gen()%10)},double(gen()%3),ready,ready+20,double(gen()%3)});
            }
            for(int k=0;k<t%3;++k)s.externalStarts.push_back({7+13*k,StartKind::External,900+k,
                {double(gen()%8),double(gen()%8)},s.currentTime+gen()%10,double(gen()%4)});
            if(t%7==0)for(auto& c:s.customers){c.location={0,0};c.demand=0;c.serviceTime=0;}
            for(auto phase:{RmpPhase::Feasibility,RmpPhase::Cost}) {
                auto d=duals(s,phase);
                for(auto& [id,v]:d.customers)v=double(int(gen()%100)-30)/4.;
                for(auto& [id,v]:d.externalStarts)v=double(int(gen()%100)-50)/4.;
                againstEnumeration(s,d);
            }
        }
    }
    void numeric() {
        // A disconnected zero-time cycle must NOT collect customer dual rewards.
        auto s=base(); s.returnDepot.deadline=2;
        s.customers={{1,{100,0},0,0,1,0},{2,{100,0},0,0,1,0}};
        s.externalStarts={{42,StartKind::External,99,{0,0},0,0}};
        for(auto phase:{RmpPhase::Feasibility,RmpPhase::Cost}) {
            auto d=duals(s,phase); d.customers[1]=d.customers[2]=100;
            againstEnumeration(s,d); auto r=oracle(s,d,42);
            check(r.incumbent && r.incumbent->route.customerSequence.empty(),"disconnected cycle collected rewards");
        }
        // Truncated metric: direct return impossible, via the customer feasible.
        s=base(); s.returnDepot.deadline=1;
        s.customers={{1,{0.55,0},1,0,10,0}};
        s.externalStarts={{42,StartKind::External,99,{1.1,0},0,1}};
        for(auto phase:{RmpPhase::Feasibility,RmpPhase::Cost})againstEnumeration(s,duals(s,phase));
        // Include feasible zero-time visits, negative duals, and near-zero negative-column thresholds.
        s=base(); s.customers={{1,{0,0},0,0,0,0},{2,{0,0},0,0,0,0}}; s.returnDepot.deadline=0;
        for(auto phase:{RmpPhase::Feasibility,RmpPhase::Cost})againstEnumeration(s,duals(s,phase));
        s=base(); s.customers={{1,{1,0},1,0,20,0}};
        for(double pi:{-2.,2.,2.+0.5e-5,2.+2e-5}) {
            auto d=duals(s,RmpPhase::Cost); d.customers[1]=pi; againstEnumeration(s,d);
        }
        for(double delta:{-2e-6,0.,2e-6}) {
            s.customers[0].dueDate=1+delta; againstEnumeration(s,duals(s,RmpPhase::Cost));
            s.customers[0].dueDate=20; s.returnDepot.deadline=2+delta;
            againstEnumeration(s,duals(s,RmpPhase::Cost)); s.returnDepot.deadline=100;
        }
    }
    void certificateRules() {
        PricingMipResult r; r.status=PricingMipStatus::Unfinished;
        r.incumbent=PricingMipWitness{{0,{1}},1,-12.3};
        check(certifyPricingMinimum(r,-12.3)==MinimumAudit::Unresolved,"same incumbent is not a proof");
        r.lowerBound=-20;
        check(certifyPricingMinimum(r,-12.3)==MinimumAudit::Unresolved,"open gap accepted");
        check(certifyPricingMinimum(r,-10)==MinimumAudit::Mismatch,"missed better route");
        check(certifyPricingMinimum(r,std::nullopt)==MinimumAudit::Mismatch,"false infeasibility");
        r.lowerBound=-12.3;
        check(certifyPricingMinimum(r,-12.3)==MinimumAudit::Certified,"matching bound rejected");
        check(certifyPricingMinimum(r,-13)==MinimumAudit::Mismatch,"claim below bound");
        r.incumbent.reset(); r.lowerBound.reset(); r.status=PricingMipStatus::Infeasible;
        check(certifyPricingMinimum(r,std::nullopt)==MinimumAudit::Certified,"infeasible proof rejected");
        check(certifyPricingMinimum(r,0)==MinimumAudit::Mismatch,"invented route");
        auto s=base(); s.customers={{1,{1,0},1,0,50,0}};
        auto limited=oracle(s,duals(s,RmpPhase::Cost),0,0);
        std::cout<<"REAL_ZERO_LIMIT status="<<int(limited.status)<<" raw="<<limited.rawStatus.value_or(-1)
            <<" has_incumbent="<<bool(limited.incumbent)<<" has_bound="<<bool(limited.lowerBound)<<'\n';
        // No claim that zero seconds MUST force timeout: presolve can solve tiny models.
        audit(s,duals(s,RmpPhase::Cost));
    }
    StageProblem dynamicStage() {
        std::vector<std::vector<double>> rows{{0,0,0,0,0,300,0,0}};
        for(int i=1;i<=12;++i)rows.push_back({double(i),double(i==1?10:i==3?15:10+i),0,2,0,250,20,double(i<=3?0:5)});
        DynamicController dynamic(rows,6,"mip-audit-dynamic");
        const auto initial=dynamic.buildStageProblem();
        StageSolution solution; solution.stageId=initial.stageId; solution.status=SolveStatus::Feasible;
        solution.routes={{0,{1,2}},{0,{3}}}; dynamic.acceptSolution(solution); dynamic.advanceTo(5);
        auto s=dynamic.buildStageProblem();
        check(s.currentTime==5 && s.customers.size()==10 && s.externalStarts.size()==2,"dynamic stage dimensions");
        close(s.externalStarts[0].startTime,30,"commitment completion A");
        close(s.externalStarts[1].startTime,35,"commitment completion B");
        for(const auto& k:s.externalStarts)close(k.remainingCapacity,4,"remaining capacity after commitment");
        for(const auto& c:s.customers)check(c.id!=1 && c.id!=3,"committed customer leaked");
        return s;
    }
    void multistart() {
        auto s=dynamicStage();
        // Add a distinct, deliberately capacity-zero context to test empty continuations.
        s.externalStarts.push_back({700,StartKind::External,999,{2,0},10,0});
        for(auto phase:{RmpPhase::Feasibility,RmpPhase::Cost}) {
            auto d=duals(s,phase);
            for(auto& [id,v]:d.customers)v=double(id%5)*10-5;
            d.externalStarts[1]=-8; d.externalStarts[2]=13; d.externalStarts[700]=20;
            audit(s,d,10,true);
        }
    }
    void finalLpCertificate(const StageProblem& s,const RmpLpResult& lp,
                            const std::vector<RmpColumn>& columns,const std::vector<PricingMipResult>& bounds) {
        check(lp.duals && lp.routeCost && lp.lambda.size()==columns.size(),"LP certificate input");
        std::map<int,double> coverage, external;
        for(const auto& c:s.customers)coverage[c.id]=0;
        for(const auto& k:s.externalStarts)external[k.startKey]=0;
        double upper=0;
        for(std::size_t i=0;i<columns.size();++i) {
            check(lp.lambda[i]>=-1e-6 && std::isfinite(lp.lambda[i]),"negative/nonfinite LP column");
            const auto w=checkPricingRoute(s,*lp.duals,columns[i].route);
            upper+=lp.lambda[i]*w.cost;
            for(int id:w.route.customerSequence)coverage.at(id)+=lp.lambda[i];
            if(w.route.startKey)external.at(w.route.startKey)+=lp.lambda[i];
        }
        for(const auto& [id,value]:coverage)close(value,1,"LP customer coverage");
        for(const auto& [key,value]:external)close(value,1,"LP external equality");
        close(upper,*lp.routeCost,"LP independently recomputed cost");
        double lower=0;
        for(const auto& [id,value]:lp.duals->customers)lower+=value;
        for(const auto& [key,value]:lp.duals->externalStarts)lower+=value;
        // Every origin route covers >=1 customer, hence sum(lambda_origin)<=n.
        // Every external start has sum(lambda_k)=1. Correct dual infeasibility
        // using INDEPENDENT MIP bounds, not the labeling minima being audited.
        for(const auto& r:bounds) {
            if(r.startKey==0 && r.status==PricingMipStatus::Infeasible)continue;
            check(r.lowerBound.has_value(),"missing full-LP bound ingredient");
            lower+=(r.startKey==0?double(s.customers.size()):1.)*std::min(0.,*r.lowerBound);
        }
        const double tolerance=1e-5*(s.customers.size()+s.externalStarts.size()+1);
        check(std::isfinite(lower) && upper-lower>=-tolerance && upper-lower<=tolerance,"full-LP primal/bound gap did not close");
        std::cout<<"LP_CERTIFICATE primal="<<upper<<" independent_lower="<<lower<<" gap="<<upper-lower
            <<" tolerance="<<tolerance<<" floating_point=true\n";
    }
    RmpLpResult cg(const StageProblem& s,bool fullOracle=false,bool verbose=false,double limit=10) {
        DVRP_RmpController rmp(s); int feasibilityRounds=0,costRounds=0;
        for(int iteration=0;iteration<250;++iteration) {
            const auto lp=rmp.solveLp(); check(lp.status==RmpLpStatus::OptimalRmp && lp.duals && lp.artificialSum,lp.message);
            if(lp.phase==RmpPhase::Feasibility && *lp.artificialSum<=1e-6) {rmp.enterCostPhase();continue;}
            std::vector<PricingMipResult> bounds;
            auto results=audit(s,*lp.duals,limit,false,&bounds); // MIP audits every start, every round; never supplies columns.
            if(lp.phase==RmpPhase::Cost)++costRounds; else ++feasibilityRounds;
            const auto& p=results[iteration%2];
            if(verbose)std::cout<<"CG iteration="<<iteration<<" phase="<<(lp.phase==RmpPhase::Cost?2:1)
                <<" rmp="<<lp.objective.value_or(-1)<<" columns="<<rmp.columns().size()<<" negative="<<p.candidates.size()<<'\n';
            if(p.candidates.empty()) {
                if(lp.phase==RmpPhase::Cost)finalLpCertificate(s,lp,rmp.columns(),bounds);
                if(fullOracle) {
                    const auto full=solveFullLp(enumerateStage(s));
                    if(lp.phase==RmpPhase::Cost) {
                        check(full.status==LpReferenceStatus::Optimal,full.message);
                        close(*full.routeCost,*lp.routeCost,"final full LP objective");
                    } else check(*lp.artificialSum>1e-6 && full.status==LpReferenceStatus::Infeasible,"false Phase-I infeasibility");
                }
                std::cout<<"CG_CERTIFIED customers="<<s.customers.size()<<" external="<<s.externalStarts.size()
                    <<" feasibility_rounds="<<feasibilityRounds<<" cost_rounds="<<costRounds
                    <<" phase="<<(lp.phase==RmpPhase::Cost?2:1)<<" objective="<<lp.objective.value_or(-1)<<'\n';
                return lp;
            }
            std::vector<StageRoute> add;
            for(const auto& c:p.candidates)add.push_back(c.route);
            const auto count=rmp.columns().size(); rmp.addRoutes(add);
            check(rmp.columns().size()>count,"negative candidates are all already present");
        }
        throw Unresolved("CG test iteration budget reached; not a convergence certificate");
    }
    void cgTests() {
        auto s=base(); s.originStart.remainingCapacity=2;
        s.customers={{1,{10,0},1,0,100,0},{2,{10,0},1,0,100,0},{3,{10,0},1,0,100,0}};
        close(*cg(s,true).routeCost,30,"fractional LP is 30, not MIP40");
        s.customers[0].dueDate=0; const auto bad=cg(s,true);
        check(bad.phase==RmpPhase::Feasibility,"infeasible stage reached cost phase");
        cg(dynamicStage(),false,true);
    }
    StageProblem readStage(const std::string& path,std::size_t count) {
        std::string program="pricing-mip-test", input=path, file,name;
        char* argv[]{program.data(),input.data()}; int vehicles=0,dim=0; double capacity=0;
        std::vector<std::vector<double>> info;
        DVRP_ReadDataController reader(2,argv,file,name,vehicles,capacity,dim,info);
        DynamicController dynamic(info,capacity,name); auto s=dynamic.buildStageProblem();
        check(count>0 && count<=s.customers.size(),"requested subset exceeds released customers or is zero");
        s.customers.resize(count);
        std::cout<<"REAL_SUBSET file="<<name<<" input="<<dim-1<<" selected="<<count<<" tau=0 external=0 enumeration=false\n";
        return s;
    }
    void real(const std::string& path,std::size_t count,double limit,bool runCg) {
        auto s=readStage(path,count); DVRP_RmpController rmp(s);
        auto solved=[&]() {
            auto lp=rmp.solveLp();
            check(lp.status==RmpLpStatus::OptimalRmp && lp.duals && lp.artificialSum,
                "RMP did not provide optimal duals: "+lp.message);
            return lp;
        };
        audit(s,*solved().duals,limit,true);
        rmp.addInitialRoutes(); const auto lp=solved();
        if(*lp.artificialSum>1e-6)throw Unresolved("initial real columns insufficient; not a full-stage infeasibility proof");
        rmp.enterCostPhase(); audit(s,*solved().duals,limit,true);
        if(runCg)cg(s,false,true,limit);
    }
    void validation() {
        auto s=base(); s.customers={{1,{1,0},1,0,20,0}}; auto d=duals(s,RmpPhase::Cost);
        check(solvePricingMip(s,d,88).status==PricingMipStatus::Error,"unknown start accepted");
        check(solvePricingMip(s,d,0,-1).status==PricingMipStatus::Error,"negative limit accepted");
        auto bad=d; bad.customers[1]=std::numeric_limits<double>::quiet_NaN();
        check(solvePricingMip(s,bad,0).status==PricingMipStatus::Error,"NaN accepted");
        bad=d; ++bad.stageId; check(solvePricingMip(s,bad,0).status==PricingMipStatus::Error,"stale dual accepted");
        s.customers.push_back(s.customers.front()); check(solvePricingMip(s,d,0).status==PricingMipStatus::Error,"duplicate accepted");
        s=base(); for(int i=1;i<=51;++i)s.customers.push_back({i,{0,0},0,0,20,0});
        check(solvePricingMip(s,duals(s,RmpPhase::Cost),0).status==PricingMipStatus::Error,"size guard ignored");
    }
    void dominance() {
        using namespace RouteOpt::Application::DVRP::PricingDetail;
        auto s=base(); s.originStart.remainingCapacity=4;
        for(int i=1;i<=4;++i)s.customers.push_back({i,{double(i%2),0},1,0,100,0});
        s.externalStarts={{42,StartKind::External,99,{0,0},1,3}};
        const auto all=enumerateStage(s); std::size_t pairs=0,nontrivial=0,tails=0;
        for(auto phase:{RmpPhase::Feasibility,RmpPhase::Cost}) {
            auto d=duals(s,phase); for(auto& [id,v]:d.customers)v=id%2?-1:0;
            PreparedStage prepared(s); PricingOptions options; options.timeLimitSeconds=60;
            Budget budget{Clock::now(),options}; PricingStatistics stats;
            for(const auto& k:starts(s)) {
                Work work(prepared,k,d,budget,stats);
                struct Prefix { StageRoute route; Label label; };
                std::vector<Prefix> prefixes;
                for(const auto& r:all.routes())if(r.route.startKey==k.startKey && !r.route.customerSequence.empty()) {
                    Label label; label.time=k.startTime; label.rc=k.startKey?-d.externalStarts.at(k.startKey):0;
                    Location pos=k.location;
                    for(int id:r.route.customerSequence) {
                        const auto& c=s.customers[id-1]; const double length=arc(pos,c.location);
                        label.time=std::max(label.time+length,c.readyTime)+c.serviceTime;
                        label.load+=c.demand; label.rc+=(phase==RmpPhase::Cost?length:0)-d.customers.at(id);
                        label.visited.set(id); label.vertex=id; pos=c.location;
                    }
                    prefixes.push_back({r.route,label});
                }
                for(const auto& a:prefixes)for(const auto& b:prefixes)if(work.dominates(a.label,b.label)) {
                    ++pairs; const auto& prefix=b.route.customerSequence;
                    if(a.route.customerSequence!=b.route.customerSequence)++nontrivial;
                    // The complete small universe supplies EVERY feasible suffix of B, including empty.
                    for(const auto& full:all.routes())if(full.route.startKey==k.startKey
                        && full.route.customerSequence.size()>=prefix.size()
                        && std::equal(prefix.begin(),prefix.end(),full.route.customerSequence.begin())) {
                        StageRoute replacement=a.route;
                        replacement.customerSequence.insert(replacement.customerSequence.end(),
                            full.route.customerSequence.begin()+prefix.size(),full.route.customerSequence.end());
                        const auto ar=checkPricingRoute(s,d,replacement), br=checkPricingRoute(s,d,full.route);
                        check(ar.reducedCost<=br.reducedCost+1e-6,"dominance lost a feasible/better suffix"); ++tails;
                    }
                }
            }
        }
        check(nontrivial>0 && tails>0,"vacuous dominance audit");
        std::cout<<"DOMINANCE accepted_pairs="<<pairs<<" nontrivial="<<nontrivial<<" independently_checked_suffixes="<<tails<<'\n';
    }
    std::size_t integerArg(const char* p) {
        std::string s=p; check(!s.empty() && s.find_first_not_of("0123456789")==std::string::npos,"invalid count");
        return std::stoull(s);
    }
}
int main(int argc,char** argv) {
    try {
        std::cout<<std::unitbuf<<std::setprecision(10);
        check(argc>=2,"usage: dvrp_pricing_mip_test <hand|random|numeric|certificate|multistart|cg|dominance|validation|real|real_cg> [file] [count=10] [seconds=10]");
        context=argv[1];
        if(context=="real" || context=="real_cg") {
            check(argc>=3 && argc<=5,"real requires file [count] [seconds]");
            std::size_t used=0; const double seconds=argc>4?std::stod(argv[4],&used):10;
            check(std::isfinite(seconds) && seconds>=0 && (argc<=4 || used==std::string(argv[4]).size()),"invalid seconds");
            real(argv[2],argc>3?integerArg(argv[3]):10,seconds,context=="real_cg");
        } else {
            const std::map<std::string,std::function<void()>> tests={{"hand",hand},{"random",randomOracle},
                {"numeric",numeric},{"certificate",certificateRules},{"multistart",multistart},
                {"cg",cgTests},{"validation",validation},{"dominance",dominance}};
            tests.at(context)();
        }
        std::cout<<"PASS mip_calls="<<mipCalls<<" bound_certificates="<<certificates<<" labeling_calls="<<pricingCalls<<'\n';
        return 0;
    } catch(const Unresolved& e) { std::cerr<<"UNRESOLVED "<<e.what()<<'\n';return 3; }
    catch(const std::exception& e) { std::cerr<<"FAIL "<<e.what()<<'\n';return 1; }
}

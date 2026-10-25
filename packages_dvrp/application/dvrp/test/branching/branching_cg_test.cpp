// Independent full-route models with explicit branch rows; not production filtering.
#include "root_cg_controller.hpp"
#include "stage_reference.hpp"
#include "solver.hpp"
#include <algorithm>
#include <cmath>
#include <functional>
#include <iostream>
#include <limits>
#include <numeric>
#include <random>
#include <stdexcept>

namespace RouteOpt::Application::DVRP::RootCgDetail {
    void validatePricingResult(const StageProblem&,const RmpLpResult&,const PricingResult&,PricingBackend);
    RootCgStatus classifyBound(const RmpLpResult&,const RootCgBound&,double,double);
}
using namespace RouteOpt::Application::DVRP;
using namespace RouteOpt::Application::DVRP::Testing;
using namespace RouteOpt;
namespace {
    constexpr PricingBackend backends[]={PricingBackend::PlainLabeling,PricingBackend::BucketGraph};
    RfElement C(int i){return {RfElementKind::Customer,i};}
    RfElement K(int i){return {RfElementKind::ExternalStart,i};}
    ArcEndpoint V(int i){return {ArcEndpointKind::Customer,i};}
    const ArcEndpoint source{ArcEndpointKind::Source,0},sink{ArcEndpointKind::Return,0};
    BranchContext rf(std::vector<BranchDecision> d={}){return {0,BranchStrategy::RyanFoster,std::move(d)};}
    BranchContext arc(std::vector<BranchDecision> d={}){return {0,BranchStrategy::StartDirectedArc,std::move(d)};}
    void check(bool ok,const std::string& why){if(!ok)throw std::runtime_error(why);}
    void close(double a,double b,const char* why){check(std::isfinite(a)&&std::isfinite(b)&&std::abs(a-b)<1e-6,why);}
    void api(int code){check(code==0,"CPLEX API error="+std::to_string(code));}
    template<class F>void rejects(F f){bool caught=false;try{f();}catch(const std::exception&){caught=true;}check(caught,"expected rejection");}
    StageProblem sample() {
        StageProblem s;s.stageId=47;s.originStart={0,StartKind::Origin,0,{0,0},0,2};s.returnDepot={{0,0},100};
        s.customers={{3,{10,0},1,0,90,0},{5,{10,0},1,0,90,0},{8,{10,0},1,0,90,0}};s.fixedCostAfterTau=7;return s;
    }
    // Oracle defines branch rows directly from route sequence and element incidence.
    // It deliberately has no dependency on production routeAllowed or arc compilation.
    int incidence(const StageRoute& r,RfElement e) {
        return e.kind==RfElementKind::ExternalStart?r.startKey==e.key:
            static_cast<int>(std::count(r.customerSequence.begin(),r.customerSequence.end(),e.key));
    }
    int coefficient(const StageRoute& r,const BranchDecision& d) {
        if(const auto* f=std::get_if<RfDecision>(&d))return incidence(r,f->first)*incidence(r,f->second);
        const auto& e=std::get<ArcDecision>(d).arc;if(r.startKey!=e.startKey)return 0;
        const auto& seq=r.customerSequence;
        if(e.from.kind==ArcEndpointKind::Source) {
            if(e.to.kind==ArcEndpointKind::Return)return seq.empty();
            return !seq.empty()&&seq.front()==e.to.customerId;
        }
        if(e.to.kind==ArcEndpointKind::Return)return !seq.empty()&&seq.back()==e.from.customerId;
        for(std::size_t i=1;i<seq.size();++i)if(seq[i-1]==e.from.customerId&&seq[i]==e.to.customerId)return 1;
        return 0;
    }
    bool rhs(const BranchDecision& d) {
        return std::visit([](const auto& x){if constexpr(requires{x.together;})return x.together;else return x.use;},d);
    }
    std::vector<double> column(const StageProblem& s,const StageRoute& r,const BranchContext& c) {
        std::vector<double> a;
        for(const auto& i:s.customers)a.push_back(incidence(r,C(i.id)));
        for(const auto& k:s.externalStarts)a.push_back(incidence(r,K(k.startKey)));
        for(const auto& d:c.decisions)a.push_back(coefficient(r,d));return a;
    }
    struct Owner {
        RouteOpt::Solver solver{};
        Owner(){solver.env=nullptr;solver.model=nullptr;}
        Owner(const Owner&)=delete;Owner& operator=(const Owner&)=delete;
        ~Owner(){if(solver.model)solver.freeModel();if(solver.env)solver.freeEnv();}
    };
    std::optional<double> fullModel(const EnumeratedStage& all,const BranchContext& c,bool mip) {
        const auto& s=all.problem();const int nr=int(s.customers.size()+s.externalStarts.size()+c.decisions.size());
        const int nc=int(all.routes().size());
        std::vector<double> right(s.customers.size()+s.externalStarts.size(),1);
        for(const auto& d:c.decisions)right.push_back(rhs(d)?1:0);
        if(nc==0)return std::any_of(right.begin(),right.end(),[](double x){return x!=0;})?std::nullopt:std::optional<double>(0);
        Owner owner;auto& solver=owner.solver;api(solver.loadEnv(nullptr));api(solver.setEnvOutputFlag(0,true));
        api(solver.setEnvThreads(1,true));api(solver.setEnvFeasibilityTol(1e-8,true));api(solver.setEnvIntFeasTol(1e-8,true));
        api(solver.setEnvMIPGap(0,true));api(solver.setEnvMIPAbsGap(0));api(solver.setEnvTimeLimit(30));
        api(solver.newModel("branch_explicit_full",0,nullptr,nullptr,nullptr,nullptr,nullptr));api(solver.setModelSense(SOLVER_MIN_SENSE));
        std::vector<int> zero(nr,0);std::vector<char> senses(nr,SOLVER_EQUAL);
        api(solver.addConstraints(nr,0,zero.data(),nullptr,nullptr,senses.data(),right.data(),nullptr));
        std::vector<int> begins,indices;std::vector<double> values,costs;
        for(const auto& r:all.routes()) {
            begins.push_back(int(indices.size()));costs.push_back(r.cost);auto a=column(s,r.route,c);
            for(int h=0;h<nr;++h)if(a[h]!=0){indices.push_back(h);values.push_back(a[h]);}
        }
        std::vector<double> lower(nc,0),upper(nc,SOLVER_INFINITY);
        api(solver.addVars(nc,int(indices.size()),begins.data(),indices.data(),values.data(),costs.data(),lower.data(),upper.data(),nullptr,nullptr));
        if(mip){std::vector<char> types(nc,SOLVER_BINARY);api(solver.setVTypeArray(0,nc,types.data()));}
        api(solver.updateModel());
        // Inspect actual branch coefficients, including 0-RHS rows that presolve may remove.
        for(int j=0;j<nc;++j) {
            auto expected=column(s,all.routes()[j].route,c);
            for(int h=0;h<nr;++h){double actual=0;api(solver.getCoeff(h,j,actual));close(actual,expected[h],"oracle coefficient");}
        }
        api(mip?solver.mipOptimize():solver.optimize());int raw=0;api(solver.getStatus(&raw));
        auto status=RouteOpt::Solver::classifyStatus(raw,mip);
        if(status.termination==RouteOpt::SolverTermination::Infeasible)return {};
        check(status.termination==RouteOpt::SolverTermination::Optimal,"oracle did not close, status="+std::to_string(raw));
        std::vector<double> x(nc),activity(nr,0);api(solver.getX(0,nc,x.data()));double cost=0,obj=0;api(solver.getObjVal(&obj));
        for(int j=0;j<nc;++j){check(std::isfinite(x[j])&&x[j]>=-1e-7,"oracle primal");if(mip)close(x[j],std::round(x[j]),"oracle integrality");
            auto a=column(s,all.routes()[j].route,c);for(int h=0;h<nr;++h)activity[h]+=a[h]*x[j];cost+=costs[j]*x[j];}
        for(int h=0;h<nr;++h)close(activity[h],right[h],"oracle row residual");close(cost,obj,"oracle objective");return obj;
    }
    RootCgResult compare(const StageProblem& s,const BranchContext& c,PricingBackend b,bool initial=true) {
        const auto all=enumerateStage(s);const auto expected=fullModel(all,c,false);
        RootCgOptions o(b);o.addInitialRoutes=initial;
        const auto result=DVRP_RootCgController{}.solve(s,c,o);
        check(result.context==normalizeBranches(s,c),"result context");
        check(result.snapshot&&result.snapshot->bound,result.message);
        const auto& snap=*result.snapshot;const auto& lp=snap.lp;const auto& bound=*snap.bound;
        check(lp.context==result.context&&bound.context==result.context,"snapshot context");
        if(!expected){check(result.status==RootCgStatus::LpInfeasible,result.message);check(bound.lowerBound>1e-6,"missing infeasibility bound");}
        else {
            check(result.status==RootCgStatus::LpConverged,result.message);close(*lp.routeCost,*expected,"CG != independent explicit-row LP");
            close(*lp.totalPlannedCost,*expected+s.fixedCostAfterTau,"fixed cost");check(bound.lowerBound<=*expected+1e-8,"unsafe node lower bound");
            std::vector<double> activity(s.customers.size()+s.externalStarts.size()+c.decisions.size(),0);
            for(std::size_t j=0;j<snap.columns.size();++j) {
                const auto& col=snap.columns[j];check(col.id==j,"column identity");
                auto it=std::find_if(all.routes().begin(),all.routes().end(),[&](const auto& r){return r.route.startKey==col.route.startKey&&r.route.customerSequence==col.route.customerSequence;});
                check(it!=all.routes().end(),"CG route not in feasible universe");close(col.cost,it->cost,"column cost");
                auto a=column(s,col.route,c);for(std::size_t h=0;h<a.size();++h)activity[h]+=a[h]*lp.lambda[j];
            }
            const auto m=s.customers.size()+s.externalStarts.size();
            for(std::size_t h=0;h<m;++h)close(activity[h],1,"node coverage");
            for(std::size_t h=0;h<c.decisions.size();++h)close(activity[m+h],rhs(c.decisions[h])?1:0,"branch equality");
        }
        for(const auto& log:result.iterations)check(log.nodeId==c.nodeId,"iteration node identity");
        return result;
    }
    void both(const StageProblem& s,const BranchContext& c){for(auto b:backends)compare(s,c,b);}
    void rfCases() {
        auto s=sample();
        for(auto b:backends) {
            auto parent=compare(s,rf(),b);close(*parent.snapshot->lp.routeCost,30,"parent LP30");
            auto pick=selectCandidate(s,rf(),parent.snapshot->lp,parent.snapshot->columns);
            check(pick.status==BranchSelectionStatus::CandidateFound,pick.message);
            auto children=makeChildren(s,rf(),pick,1,2);
            close(*compare(s,children.first,b).snapshot->lp.routeCost,40,"RF left40");
            auto yes=compare(s,children.second,b);close(*yes.snapshot->lp.routeCost,40,"RF right40");
            check(yes.iterations.front().artificialSum&&*yes.iterations.front().artificialSum>0,"RF did not recover Phase I");
            compare(s,children.second,b,false);
        }
    }
    void arcCases() {
        auto s=sample();
        for(auto b:backends) {
            auto parent=compare(s,arc(),b);auto pick=selectCandidate(s,arc(),parent.snapshot->lp,parent.snapshot->columns);
            check(pick.status==BranchSelectionStatus::CandidateFound,pick.message);
            auto children=makeChildren(s,arc(),pick,11,12);compare(s,children.first,b);compare(s,children.second,b);
        }
        for(auto e:{DirectedArc{0,source,V(3)},DirectedArc{0,V(3),V(5)},DirectedArc{0,V(5),sink}})
            for(bool use:{false,true})both(s,arc({ArcDecision{e,use}}));
    }
    void external() {
        auto s=sample();s.externalStarts={{3,StartKind::External,99,{9,0},0,2},{42,StartKind::External,98,{0,0},0,1}};
        for(auto c:{rf({RfDecision{C(3),K(3),true}}),rf({RfDecision{C(3),K(3),false}}),
            rf({RfDecision{K(3),K(42),true}}),rf({RfDecision{K(3),K(42),false}}),
            arc({ArcDecision{{3,source,sink},true}}),arc({ArcDecision{{3,source,sink},false}}),
            arc({ArcDecision{{3,V(3),V(5)},true}}),arc({ArcDecision{{3,source,V(3)},true}}),
            arc({ArcDecision{{3,V(3),sink},true}})})both(s,c);
        s.externalStarts[0].remainingCapacity=0;both(s,rf({RfDecision{C(3),K(3),true}}));
    }
    void conflict() {
        auto s=sample();
        for(auto c:{rf({RfDecision{C(3),C(5),true},RfDecision{C(3),C(5),false}}),
            rf({RfDecision{C(3),C(5),true},RfDecision{C(5),C(8),true}}),
            arc({ArcDecision{{0,V(3),V(5)},true},ArcDecision{{0,V(3),V(5)},false}}),
            arc({ArcDecision{{0,V(3),V(5)},true},ArcDecision{{0,V(3),V(8)},true}}),
            arc({ArcDecision{{0,V(3),V(5)},true},ArcDecision{{0,V(5),V(3)},true}})})
            for(auto b:backends)check(compare(s,c,b).status==RootCgStatus::LpInfeasible,"conflict not proved infeasible");
    }
    void random() {
        std::mt19937 rng(98261025);
        for(int trial=0;trial<80;++trial) {
            auto s=sample();s.customers.push_back({11,{0,0},1,0,90,0});s.customers.push_back({17,{0,0},1,0,90,0});
            s.customers.resize(2+rng()%4);s.originStart.remainingCapacity=1+rng()%4;
            s.externalStarts={{42,StartKind::External,99,{double(rng()%10),0},double(rng()%5),double(rng()%2)}};
            for(auto& i:s.customers){i.location={double(rng()%10),double(rng()%5)};i.readyTime=rng()%8;i.dueDate=10+rng()%40;i.demand=rng()%2;}
            auto c=trial%2?rf():arc();c.nodeId=trial+1;
            for(unsigned depth=0;depth<1+rng()%4;++depth) {
                int i=s.customers[rng()%s.customers.size()].id,j=s.customers[rng()%s.customers.size()].id;
                if(c.strategy==BranchStrategy::RyanFoster)c.decisions.push_back(RfDecision{C(i),i==j?K(42):C(j),bool(rng()%2)});
                else c.decisions.push_back(ArcDecision{{rng()%2?42:0,i==j?source:V(i),V(j)},bool(rng()%2)});
            }
            both(s,c);
        }
        std::cout<<"random constrained stages=80, production CG solves=160\n";
    }
    // Enumerate all integer covers independently. Every parent solution must fall
    // in exactly one child, not merely have the same optimum as one of them.
    void partition() {
        auto s=sample();s.externalStarts={{42,StartKind::External,99,{0,0},0,2}};
        auto all=enumerateStage(s);const std::size_t m=s.customers.size()+s.externalStarts.size();
        std::vector<std::vector<double>> incidenceMatrix;
        for(const auto& r:all.routes())incidenceMatrix.push_back(column(s,r.route,{}));
        std::vector<std::vector<std::size_t>> covers;std::vector<int> taken(m);std::vector<std::size_t> chosen;
        std::function<void()> visit=[&]{
            auto it=std::find(taken.begin(),taken.end(),0);if(it==taken.end()){covers.push_back(chosen);return;}
            const auto first=std::size_t(it-taken.begin());
            for(std::size_t j=0;j<all.routes().size();++j) {
                const auto& a=incidenceMatrix[j];if(a[first]==0)continue;
                bool overlap=false;for(std::size_t h=0;h<m;++h)if(a[h]&&taken[h])overlap=true;if(overlap)continue;
                for(std::size_t h=0;h<m;++h)taken[h]+=int(a[h]);chosen.push_back(j);visit();chosen.pop_back();
                for(std::size_t h=0;h<m;++h)taken[h]-=int(a[h]);
            }
        };visit();check(!covers.empty(),"no integer covers tested");
        const std::vector<BranchDecision> decisions={RfDecision{C(3),C(5),false},RfDecision{C(3),K(42),false},
            ArcDecision{{0,source,V(3)},false},ArcDecision{{42,V(3),V(5)},false},ArcDecision{{42,source,sink},false},ArcDecision{{42,V(5),sink},false}};
        for(const auto& decision:decisions) {
            auto parent=std::holds_alternative<RfDecision>(decision)?rf():arc();
            for(const auto& sol:covers) {
                int flow=0;for(auto j:sol)flow+=coefficient(all.routes()[j].route,decision);
                check(flow==0||flow==1,"integer cover not partitioned by 0/1");
            }
            auto left=parent,right=parent;left.decisions.push_back(decision);auto yes=decision;
            std::visit([](auto& x){if constexpr(requires{x.together;})x.together=true;else x.use=true;},yes);right.decisions.push_back(yes);
            auto base=fullModel(all,parent,true),l=fullModel(all,left,true),r=fullModel(all,right,true);
            check(base.has_value(),"parent MIP unexpectedly infeasible");
            close(*base,std::min(l.value_or(INFINITY),r.value_or(INFINITY)),"parent MIP != min child MIPs");
            both(s,left);both(s,right);
        }
        std::cout<<"integer covers="<<covers.size()<<", candidate partitions="<<decisions.size()<<'\n';
    }
    void context() {
        auto s=sample();auto c=rf({RfDecision{C(3),C(5),true}});c.nodeId=9;
        auto result=compare(s,c,PricingBackend::BucketGraph);auto lp=result.snapshot->lp;
        DVRP_PricingController pricing(s,c);PricingOptions o;o.backend=PricingBackend::BucketGraph;
        auto p=pricing.price(*lp.duals,o);RootCgDetail::validatePricingResult(s,lp,p,o.backend);
        auto bad=p;bad.context.nodeId++;rejects([&]{RootCgDetail::validatePricingResult(s,lp,bad,o.backend);});
        bad=p;bad.context.decisions.clear();rejects([&]{RootCgDetail::validatePricingResult(s,lp,bad,o.backend);});
        auto bound=*result.snapshot->bound;bound.context.nodeId++;rejects([&]{RootCgDetail::classifyBound(lp,bound,1e-6,1e-8);});
        auto other=c;other.nodeId=10;DVRP_RmpController sibling(s,other,{});sibling.addInitialRoutes();auto d=sibling.solveLp();
        check(pricing.price(*d.duals,o).status==PricingStatus::Error,"sibling version alias");
        // Same start/minimum but disallowed route must not be accepted as a witness.
        bad=p;if(bad.starts[0].bestRoute){bad.starts[0].bestRoute->route.customerSequence={3};rejects([&]{RootCgDetail::validatePricingResult(s,lp,bad,o.backend);});}
    }
    void rmp() {
        auto s=sample();auto c=rf({RfDecision{C(3),C(5),true}});DVRP_RmpController model(s,c,{});
        model.addInitialRoutes();check(model.columns().size()==1,"RF initial singleton filtering");
        auto before=model.inspectModel();auto lp=model.solveLp();close(*lp.artificialSum,2,"Phase-I mass");
        rejects([&]{model.addRoutes({{0,{3,5}},{0,{3}}});});
        check(model.columns().size()==1&&model.inspectModel().modelRevision==before.modelRevision,"failed batch partially mutated model");
        model.addRoutes({{0,{3,5}}});lp=model.solveLp();close(*lp.artificialSum,0,"Phase-I recovery");model.enterCostPhase();
        close(*model.solveLp().routeCost,40,"cost phase after recovery");
        c.decisions.clear();check(model.inspectModel().context!=c,"RMP retained caller reference");
        s.customers.resize(200);for(int i=0;i<200;++i)s.customers[i]={i+1,{0,0},0,0,100,0};
        auto large=rf({RfDecision{C(1),C(200),true}});DVRP_RmpController big(s,large,{});big.addInitialRoutes();
        check(big.columns().size()==198,"production inherited enumeration size limit");
    }
    void limits() {
        auto s=sample();auto c=rf({RfDecision{C(3),C(5),true}});
        for(auto b:backends) {
            RootCgOptions o(b);o.timeLimitSeconds=0;check(DVRP_RootCgController{}.solve(s,c,o).status==RootCgStatus::TimeLimit,"total time");
            o.timeLimitSeconds=60;o.maxIterations=1;check(DVRP_RootCgController{}.solve(s,c,o).status==RootCgStatus::IterationLimit,"iteration limit");
            o.maxIterations=100;o.pricing.maxLabels=0;auto r=DVRP_RootCgController{}.solve(s,c,o);
            check(r.status==RootCgStatus::ResourceLimit&&r.snapshot&&!r.snapshot->bound,"interrupted node certified");
            o.pricing.maxLabels=100;o.pricing.timeLimitSeconds=0;check(DVRP_RootCgController{}.solve(s,c,o).status==RootCgStatus::TimeLimit,"price time");
        }
    }
    void empty() {
        auto s=sample();s.customers.clear();for(auto c:{BranchContext{},rf(),arc()})both(s,c);
        s.externalStarts={{42,StartKind::External,99,{3,0},0,2}};
        both(s,rf());both(s,arc({ArcDecision{{42,source,sink},true}}));both(s,arc({ArcDecision{{42,source,sink},false}}));
        auto old=DVRP_RootCgController{}.solve(sample(),RootCgOptions(PricingBackend::PlainLabeling));
        auto explicitRoot=DVRP_RootCgController{}.solve(sample(),BranchContext{},RootCgOptions(PricingBackend::PlainLabeling));
        close(*old.snapshot->lp.routeCost,*explicitRoot.snapshot->lp.routeCost,"old root overload changed");
    }
}
int main(int argc,char** argv) {
    try {
        check(argc==2,"one case required");const std::string c=argv[1];
        if(c=="rf")rfCases();else if(c=="arc")arcCases();else if(c=="external")external();else if(c=="conflict")conflict();
        else if(c=="random")random();else if(c=="partition")partition();else if(c=="context")context();else if(c=="rmp")rmp();
        else if(c=="limits")limits();else if(c=="empty")empty();else throw std::runtime_error("unknown case");
        std::cout<<"PASS "<<c<<'\n';return 0;
    }catch(const std::exception& e){std::cerr<<e.what()<<'\n';return 1;}
}

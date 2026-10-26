// Complete MIP is an independent oracle, never a supplier of production BP columns.
#include "bp_controller.hpp"
#include "stage_reference.hpp"
#include "read_data_controller.hpp"
#include "dynamic_controller.hpp"
#include <algorithm>
#include <cmath>
#include <iostream>
#include <random>
#include <stdexcept>
using namespace RouteOpt::Application::DVRP;
using namespace RouteOpt::Application::DVRP::Testing;
namespace {
constexpr BranchStrategy strategies[]={BranchStrategy::RyanFoster,BranchStrategy::StartDirectedArc};
constexpr PricingBackend backends[]={PricingBackend::PlainLabeling,PricingBackend::BucketGraph};
void check(bool b,const std::string& why){if(!b)throw std::runtime_error(why);}
void close(double a,double b,const char* why){check(std::isfinite(a)&&std::isfinite(b)&&std::abs(a-b)<1e-6,why);}
StageProblem sample(){StageProblem s;s.stageId=31;s.originStart={0,StartKind::Origin,0,{0,0},0,2};s.returnDepot={{0,0},100};
    s.customers={{3,{10,0},1,0,90,0},{8,{10,0},1,0,90,0},{17,{10,0},1,0,90,0}};s.fixedCostAfterTau=7;return s;}
BpResult compare(const StageProblem& s,BranchStrategy strategy,PricingBackend backend,const EnumeratedStage& all,const MipReferenceResult& mip) {
    BpOptions o(strategy,backend);auto r=DVRP_BpController{}.solve(s,o);
    check(r.stageId==s.stageId&&r.strategy==strategy&&r.backend==backend,"result identity");
    if(mip.solution.status==SolveStatus::Infeasible) {
        check(r.status==BpStatus::Infeasible,r.message);check(!r.incumbent&&!r.upperBound,"infeasible incumbent");return r;
    }
    check(mip.solution.status==SolveStatus::Optimal,"oracle incomplete");
    check(r.status==BpStatus::Optimal,std::string(bpStatusName(r.status))+": "+r.message);
    check(r.incumbent&&r.lowerBound&&r.upperBound&&r.absoluteGap,"missing BP certificate");
    close(*r.upperBound,*mip.routeCost,"BP != full MIP");close(*r.totalUpperBound,*mip.totalPlannedCost,"fixed cost");
    check(*r.lowerBound<=*mip.routeCost+1e-8,"unsafe global lower bound");
    check(*r.absoluteGap<=o.nodeCg.absoluteGapTolerance+o.nodeCg.relativeGapTolerance*std::max(1.,*r.upperBound),"unclosed reported gap");
    // Check against independently enumerated feasible universe, without production route validation/filtering.
    std::map<int,int> cover,starts;double cost=0;
    for(const auto& c:s.customers)cover[c.id]=0;for(const auto& k:s.externalStarts)starts[k.startKey]=0;
    for(const auto& route:r.incumbent->routes) {
        auto it=std::find_if(all.routes().begin(),all.routes().end(),[&](const auto& e){return e.route.startKey==route.startKey&&e.route.customerSequence==route.customerSequence;});
        check(it!=all.routes().end(),"BP route not in feasible universe");cost+=it->cost;
        for(int id:route.customerSequence)++cover.at(id);if(route.startKey)++starts.at(route.startKey);
    }
    for(auto [id,n]:cover)check(n==1,"integer coverage");for(auto [id,n]:starts)check(n==1,"external coverage");close(cost,*r.upperBound,"reported cost");
    check(toStageSolution(r).status==SolveStatus::Optimal,"stage adapter");
    std::size_t calls=0,labels=0;
    for(const auto& node:r.nodes)for(const auto& it:node.iterations)if(it.pricingStatistics){++calls;labels+=it.pricingStatistics->generatedLabels;}
    check(calls==r.pricingCalls&&labels==r.pricingStatistics.generatedLabels,"statistics lost");
    return r;
}
void allModes(const StageProblem& s,bool mustBranch=false) {
    auto all=enumerateStage(s);auto mip=solveFullMip(all);
    for(auto strategy:strategies)for(auto backend:backends){auto r=compare(s,strategy,backend,all,mip);
        if(mustBranch)check(r.nodesSplit>0&&r.nodesSolved>1,"did not actually branch");}
}
void fractional(){allModes(sample(),true);}
void external(){auto s=sample();s.externalStarts={{3,StartKind::External,99,{9,0},3,2},{42,StartKind::External,98,{0,0},0,0}};allModes(s);}
void empty(){auto s=sample();s.customers.clear();allModes(s);s.externalStarts={{42,StartKind::External,99,{3,0},0,2}};allModes(s);s.externalStarts[0].startTime=99;allModes(s);}
void infeasible(){auto s=sample();s.customers[0].dueDate=5;allModes(s);}
void random(){std::mt19937 rng(25261006);std::size_t split=0,solves=0;
    for(int trial=0;trial<100;++trial){auto s=sample();s.stageId=100+trial;s.originStart.remainingCapacity=1+rng()%4;
        s.customers.clear();s.externalStarts.clear();
        for(unsigned j=0,n=2+rng()%4;j<n;++j)s.customers.push_back({int(3+j*11),{double(rng()%12),double(rng()%8)},double(rng()%2),double(rng()%5),double(8+rng()%70),double(rng()%3)});
        for(unsigned j=0,n=rng()%4;j<n;++j)s.externalStarts.push_back({int(3+23*j),StartKind::External,int(100+j),{double(rng()%10),double(rng()%6)},double(rng()%8),double(rng()%(unsigned(s.originStart.remainingCapacity)+1))});
        s.fixedCostAfterTau=double(trial%9)*.17;auto all=enumerateStage(s);auto mip=solveFullMip(all);
        for(auto strategy:strategies)for(auto backend:backends){auto r=compare(s,strategy,backend,all,mip);split+=r.nodesSplit;++solves;}
    }std::cout<<"random stages=100 production BP solves="<<solves<<" splits="<<split<<'\n';}
void limits(){auto s=sample();
    for(auto strategy:strategies)for(auto backend:backends){BpOptions o(strategy,backend);
        o.timeLimitSeconds=0;auto r=DVRP_BpController{}.solve(s,o);check(r.status==BpStatus::TimeLimit&&!r.incumbent&&r.remainingNodes==1,"zero time");
        o.timeLimitSeconds=60;o.maxNodes=0;r=DVRP_BpController{}.solve(s,o);check(r.status==BpStatus::NodeLimit&&r.stoppedNode&&*r.lowerBound==0,"zero nodes lost active");
        o.maxNodes=1;r=DVRP_BpController{}.solve(s,o);check(r.status==BpStatus::NodeLimit&&r.nodesSplit==1&&r.remainingNodes==2,"node limit after split");
        check(r.lowerBound&&*r.lowerBound<=30&&!r.incumbent,"node cutoff invalid bound");
        o.maxNodes=100;o.nodeCg.maxIterations=1;r=DVRP_BpController{}.solve(s,o);check(r.status==BpStatus::IterationLimit,"iteration limit");
        o.nodeCg.maxIterations=100;o.nodeCg.pricing.maxLabels=0;r=DVRP_BpController{}.solve(s,o);check(r.status==BpStatus::ResourceLimit,"labels limit");
        o.nodeCg.pricing.maxLabels=200000;o.nodeCg.pricing.timeLimitSeconds=0;r=DVRP_BpController{}.solve(s,o);check(r.status==BpStatus::TimeLimit,"component timeout");
    }
}
void lifecycle(){auto s=sample();for(int j=0;j<3;++j){s.stageId++;allModes(s);}s.customers.resize(1);allModes(s);}
void invalid(){auto s=sample();BpOptions o(BranchStrategy::None,PricingBackend::PlainLabeling);
    check(DVRP_BpController{}.solve(s,o).status==BpStatus::Error,"None strategy");o.strategy=BranchStrategy::RyanFoster;
    s.customers[0].id=s.customers[1].id;check(DVRP_BpController{}.solve(s,o).status==BpStatus::Error,"duplicate IDs");
    s=sample();o.nodeCg.relativeGapTolerance=-1;check(DVRP_BpController{}.solve(s,o).status==BpStatus::Error,"negative tolerance");}
void deep(){auto s=sample();s.customers.push_back({29,{10,0},1,0,90,0});s.customers.push_back({35,{10,0},1,0,90,0});
    auto all=enumerateStage(s);auto mip=solveFullMip(all);
    for(auto strategy:strategies)for(auto backend:backends){auto r=compare(s,strategy,backend,all,mip);std::size_t depth=0;
        for(const auto& n:r.nodes)depth=std::max(depth,n.depth);check(depth>=2,"no multi-level search exercised");
        std::cout<<"strategy="<<int(strategy)<<" backend="<<int(backend)<<" depth="<<depth<<" nodes="<<r.nodesSolved<<'\n';
        bool keptIncumbent=false;
        for(std::size_t budget=1;budget<r.nodesSolved;++budget){BpOptions o(strategy,backend);o.maxNodes=budget;
            auto cut=DVRP_BpController{}.solve(s,o);
            check(cut.status==BpStatus::NodeLimit&&cut.remainingNodes>0&&cut.stoppedNode,"cutoff silently closed a node");
            check(cut.lowerBound&&*cut.lowerBound<=*mip.routeCost+1e-8,"cutoff unsafe lower bound");
            if(cut.incumbent){keptIncumbent=true;check(cut.upperBound&&*cut.upperBound>=*mip.routeCost-1e-8,"cutoff false upper bound");
                check(toStageSolution(cut).status==SolveStatus::Error,"node limit became executable");}}
        check(keptIncumbent,"no cutoff with incumbent exercised");}}
void size(){auto s=sample();s.customers.clear();s.originStart.remainingCapacity=1;
    for(int i=0;i<20;++i)s.customers.push_back({100+2*i,{1,0},1,0,90,0});
    for(int k=0;k<6;++k)s.externalStarts.push_back({200+k,StartKind::External,900+k,{0,0},10,0});
    for(auto strategy:strategies)for(auto backend:backends){BpOptions o(strategy,backend);auto r=DVRP_BpController{}.solve(s,o);
        check(r.status==BpStatus::Optimal,r.message);close(*r.upperBound,40,"production size limit/cost");
        check(r.incumbent&&r.incumbent->routes.size()==26,"large stage route count");}}
void instance(char* file){std::string path,name;int vehicles=0,dim=0;double cap=0;std::vector<std::vector<double>> vertices;
    char program[]="bp_test";char* args[]={program,file};DVRP_ReadDataController reader(2,args,path,name,vehicles,cap,dim,vertices);
    DynamicController dynamic(vertices,cap,name);auto s=dynamic.buildStageProblem();const auto released=s.customers.size();
    if(s.customers.size()>12)s.customers.resize(12);
    std::cout<<"instance="<<name<<" released="<<released<<" subset="<<s.customers.size()<<" tau="<<s.currentTime<<" enumeration=false\n";
    auto distance=[](Location a,Location b){double dx=a.x-b.x,dy=a.y-b.y;
        return std::floor((double(std::sqrt(float(dx*dx+dy*dy)))+1e-6)*10.)/10.;};
    std::optional<double> optimum;
    for(auto strategy:strategies)for(auto backend:backends){BpOptions o(strategy,backend);o.timeLimitSeconds=5;o.maxNodes=100;
        auto r=DVRP_BpController{}.solve(s,o);
        check(r.status==BpStatus::Optimal||r.status==BpStatus::TimeLimit||r.status==BpStatus::NodeLimit||r.status==BpStatus::ResourceLimit,"real stage unexpected failure: "+r.message);
        if(r.incumbent){std::map<int,int> counts;double cost=0;
            for(const auto& route:r.incumbent->routes){check(route.startKey==0&&!route.customerSequence.empty(),"real origin route");
                auto pos=s.originStart.location;double t=s.originStart.startTime,load=0;
                for(int id:route.customerSequence){auto c=std::find_if(s.customers.begin(),s.customers.end(),[&](const auto& v){return v.id==id;});
                    check(c!=s.customers.end()&&++counts[id]==1,"real customer coverage");double d=distance(pos,c->location);cost+=d;load+=c->demand;t=std::max(t+d,c->readyTime);
                    check(load<=cap+1e-6&&t<=c->dueDate+1e-6,"real capacity/window");t+=c->serviceTime;pos=c->location;}
                double d=distance(pos,s.returnDepot.location);cost+=d;check(t+d<=s.returnDepot.deadline+1e-6,"real late return");}
            check(counts.size()==s.customers.size(),"real missing customer");close(cost,*r.upperBound,"real independently recomputed cost");}
        if(r.status==BpStatus::Optimal){if(optimum)close(*optimum,*r.upperBound,"real mode disagreement");optimum=r.upperBound;}
        std::cout<<"strategy="<<int(strategy)<<" backend="<<int(backend)<<" status="<<bpStatusName(r.status)<<" nodes="<<r.nodesSolved<<" seconds="<<r.seconds;
        if(r.lowerBound)std::cout<<" LB="<<*r.lowerBound;if(r.upperBound)std::cout<<" UB="<<*r.upperBound;std::cout<<'\n';}}
}
int main(int argc,char** argv){try{check(argc==2||argc==3,"case required");std::string c=argv[1];
    if(c=="fractional")fractional();else if(c=="external")external();else if(c=="empty")empty();else if(c=="infeasible")infeasible();
    else if(c=="random")random();else if(c=="limits")limits();else if(c=="lifecycle")lifecycle();else if(c=="invalid")invalid();
    else if(c=="deep")deep();else if(c=="size")size();else if(c=="instance"&&argc==3)instance(argv[2]);else throw std::runtime_error("unknown case");
    std::cout<<"PASS "<<c<<'\n';return 0;}catch(const std::exception& e){std::cerr<<e.what()<<'\n';return 1;}}

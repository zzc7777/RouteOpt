/* Copyright (c) 2025 Zhengzhong (Ricky) You. RouteOpt, GPL-3.0.
 * Resource propagation, exact dominance and predecessor recovery adapted from
 * pricing_functors.hpp, dominance.hpp, write_columns_from_pricing.hpp.
 * DVRP: start-specific resources, independent sink, no ng/cuts, stable indices.
 * Stage validation mirrors the independent production RMP boundary. */
#include "pricing_internal.hpp"
#include "dynamic_controller.hpp"
#include <algorithm>
#include <cmath>
#include <set>
#include <stdexcept>

namespace RouteOpt::Application::DVRP::PricingDetail {
    namespace {
        void require(bool ok,const std::string& why) {
            if(!ok)throw std::invalid_argument("DVRP pricing: "+why);
        }
        void finite(double v,const char* name) { require(std::isfinite(v),std::string(name)+" must be finite"); }
        void location(Location p) { finite(p.x,"x");finite(p.y,"y"); }
        double checked(double v) {
            if(!std::isfinite(v))throw std::runtime_error("DVRP pricing: nonfinite arithmetic");
            return v;
        }
        bool near(double a,double b) {
            return std::isfinite(a)&&std::isfinite(b)&&std::abs(a-b)<=1e-8*std::max({1.,std::abs(a),std::abs(b)});
        }
    }
    void Budget::check() const {
        if(std::chrono::duration<double>(Clock::now()-began).count()>=options.timeLimitSeconds)
            throw Interrupted{PricingStatus::TimeLimit,"pricing time limit (all starts)"};
    }
    PreparedStage::PreparedStage(const StageProblem& source,const BranchContext& branches)
        :stage(source),context(normalizeBranches(source,branches)) {
        const auto& s=stage;
        require(s.customers.size()<MAX_NUM_CUSTOMERS,"customer bitset capacity exceeded");
        finite(s.currentTime,"currentTime");finite(s.fixedCostAfterTau,"fixedCostAfterTau");
        finite(s.returnDepot.deadline,"deadline");location(s.returnDepot.location);
        require(s.currentTime>=0 && s.fixedCostAfterTau>=0 && s.returnDepot.deadline>=0,"negative time/cost/deadline");
        const auto& o=s.originStart;
        location(o.location);finite(o.startTime,"startTime");finite(o.remainingCapacity,"capacity");
        require(o.kind==StartKind::Origin && o.startKey==0 && o.locationCustomerId==0,"invalid origin");
        require(o.startTime==s.currentTime,"origin time differs from stage time");
        require(o.remainingCapacity>0 && std::floor(o.remainingCapacity)==o.remainingCapacity,"invalid origin capacity");
        require(o.location.x==s.returnDepot.location.x && o.location.y==s.returnDepot.location.y,"return location differs from origin");
        for(const auto& c:s.customers) {
            const int next=static_cast<int>(index.size())+1;
            require(c.id>0 && index.emplace(c.id,next).second,"invalid/duplicate customer ID");
            location(c.location);finite(c.demand,"demand");finite(c.readyTime,"readyTime");
            finite(c.dueDate,"dueDate");finite(c.serviceTime,"serviceTime");
            require(c.demand>=0 && c.demand<=o.remainingCapacity && std::floor(c.demand)==c.demand,"invalid demand");
            require(c.readyTime<=c.dueDate && c.serviceTime>=0,"invalid time window/service");
        }
        std::set<int> keys;
        for(const auto& k:s.externalStarts) {
            location(k.location);finite(k.startTime,"external time");finite(k.remainingCapacity,"remaining capacity");
            require(k.kind==StartKind::External && k.startKey>0 && keys.insert(k.startKey).second,"invalid/duplicate external key");
            require(k.locationCustomerId>0 && !index.contains(k.locationCustomerId),"invalid external anchor");
            require(k.startTime>=s.currentTime,"external time precedes stage");
            require(k.remainingCapacity>=0 && k.remainingCapacity<=o.remainingCapacity
                && std::floor(k.remainingCapacity)==k.remainingCapacity,"invalid remaining capacity");
        }
        distances.assign(n()+2,std::vector<double>(n()+2));
        for(int i=1;i<=n();++i) {
            for(int j=1;j<=n();++j)
                distances[i][j]=checked(routeOptDistance(s.customers[i-1].location,s.customers[j-1].location));
            distances[i][n()+1]=checked(routeOptDistance(s.customers[i-1].location,s.returnDepot.location));
        }
    }
    Work::Work(const PreparedStage& p,const StartSpec& k,const RmpDuals& d,const Budget& b,PricingStatistics& st)
        :data(p),start(k),duals(d),budget(b),stats(st),weight(d.phase==RmpPhase::Cost?1.:0.) {
        forcedNext.assign(data.n()+2,-1); forcedPrevious.assign(data.n()+2,-1);
        auto rfIndex=[&](RfElement e) {
            if(e.kind==RfElementKind::ExternalStart)return e.key==start.startKey?-1:-2;
            const int i=data.index.at(e.key);rfMask.set(i);return i;
        };
        auto endpoint=[&](ArcEndpoint e) {
            return e.kind==ArcEndpointKind::Source?0:e.kind==ArcEndpointKind::Return?data.n()+1:data.index.at(e.customerId);
        };
        auto force=[](int& slot,int value) { if(slot==-1)slot=value;else if(slot!=value)slot=-2; };
        for(const auto& decision:data.context.decisions) {
            budget.check();
            if(const auto* rf=std::get_if<RfDecision>(&decision)) {
                rfRules.push_back({rfIndex(rf->first),rfIndex(rf->second),rf->together});
                continue;
            }
            const auto& rule=std::get<ArcDecision>(decision);
            const int from=endpoint(rule.arc.from),to=endpoint(rule.arc.to);
            const bool own=rule.arc.startKey==start.startKey;
            if(!rule.use) { if(own)forbiddenArcs.emplace_back(from,to);continue; }
            if(!own) {
                if(from>0 && from<=data.n())bannedCustomers.set(from);
                if(to>0 && to<=data.n())bannedCustomers.set(to);
            } else if(from==0 && to==data.n()+1) {
                for(int j=1;j<=data.n();++j)bannedCustomers.set(j);
            } else {
                // Source may launch OTHER routes too; only force customer endpoints.
                if(from>0)force(forcedNext[from],to);
                if(to<=data.n())force(forcedPrevious[to],from);
            }
        }
        sourceDistances.resize(data.n()+2);
        for(int j=1;j<=data.n();++j) {
            budget.check();
            sourceDistances[j]=checked(routeOptDistance(start.location,data.stage.customers[j-1].location));
        }
        sourceDistances.back()=checked(routeOptDistance(start.location,data.stage.returnDepot.location));
        Label source;source.time=k.startTime;
        source.rc=k.kind==StartKind::External ? -d.externalStarts.at(k.startKey):0;
        retain(source);
    }
    double Work::arc(int from,int to) const {
        return from==0?sourceDistances.at(to):data.distances.at(from).at(to);
    }
    bool Work::rfAllowed(const routeOptLong& visited,bool finishing) const {
        auto seen=[&](int i){return i==-1 || (i>=0 && visited.test(i));};
        for(const auto& r:rfRules) {
            const bool a=seen(r.first),b=seen(r.second);
            if(r.together ? finishing && a!=b : a&&b)return false;
        }
        return true;
    }
    bool Work::arcAllowed(int from,int to) const {
        if(to<=data.n() && bannedCustomers.test(to))return false;
        if(forcedNext[from]!=-1 && forcedNext[from]!=to)return false;
        if(forcedPrevious[to]!=-1 && forcedPrevious[to]!=from)return false;
        return std::find(forbiddenArcs.begin(),forbiddenArcs.end(),std::pair{from,to})==forbiddenArcs.end();
    }
    std::optional<Label> Work::extend(std::size_t parent,int j) {
        budget.check();
        const auto& previous=labels.at(parent);
        if(previous.visited.test(j))return {};
        if(!arcAllowed(previous.vertex,j))return {};
        auto visited=previous.visited;visited.set(j);
        if(!rfAllowed(visited,false))return {};
        const auto& c=data.stage.customers.at(j-1);
        const double length=arc(previous.vertex,j);
        const double load=checked(previous.load+c.demand);
        const double begin=std::max(checked(previous.time+length),c.readyTime);
        const double time=checked(begin+c.serviceTime);
        if(load>start.remainingCapacity+TOLERANCE || begin>c.dueDate+TOLERANCE
            || time>data.stage.returnDepot.deadline+TOLERANCE)return {};
        Label next;next.vertex=j;next.time=time;next.load=load;
        next.cost=checked(previous.cost+length);
        next.rc=checked(checked(previous.rc+weight*length)-duals.customers.at(c.id));
        next.visited=visited;next.predecessor=parent;
        return next;
    }
    bool Work::dominates(const Label& a,const Label& b) {
        budget.check();++stats.dominanceChecks;
        // Conservative comparison: never discard a slightly better label via epsilon.
        return a.vertex==b.vertex && a.time<=b.time && a.load<=b.load && a.rc<=b.rc
            && (a.visited & ~b.visited).none() && (a.visited & rfMask)==(b.visited & rfMask);
    }
    std::size_t Work::retain(Label l) {
        budget.check();
        if(labels.size()>=budget.options.maxLabels)
            throw Interrupted{PricingStatus::ResourceLimit,"retained label budget exceeded"};
        const auto id=labels.size();
        labels.push_back(std::move(l));++stats.generatedLabels;
        stats.peakRetainedLabels=std::max(stats.peakRetainedLabels,labels.size());
        return id;
    }
    std::optional<std::size_t> Work::insert(std::vector<std::size_t>& list,Label next) {
        for(auto id:list)if(labels[id].active && dominates(labels[id],next))return {};
        // Do not destroy predecessors: descendants and the best route use stable IDs.
        for(auto id:list)if(labels[id].active && dominates(next,labels[id]))labels[id].active=false;
        list.erase(std::remove_if(list.begin(),list.end(),[&](auto id){return !labels[id].active;}),list.end());
        const auto id=retain(std::move(next));list.push_back(id);return id;
    }
    void Work::complete(std::size_t id) {
        budget.check();
        const auto& l=labels.at(id);
        if(l.vertex==0 && start.kind==StartKind::Origin)return;
        if(!rfAllowed(l.visited,true) || !arcAllowed(l.vertex,data.n()+1))return;
        const double length=arc(l.vertex,data.n()+1);
        if(checked(l.time+length)>data.stage.returnDepot.deadline+TOLERANCE)return;
        const double rc=checked(l.rc+weight*length),cost=checked(l.cost+length);
        if(rc<bestRc) { bestRc=rc;bestCost=cost;bestLabel=id; }
    }
    StartPricingResult Work::result() const {
        StartPricingResult result;result.startKey=start.startKey;
        if(bestLabel==NO_LABEL)return result;
        PricedRoute witness;witness.route.startKey=start.startKey;
        for(auto id=bestLabel;labels.at(id).vertex!=0;id=labels.at(id).predecessor) {
            budget.check();
            witness.route.customerSequence.push_back(data.stage.customers.at(labels[id].vertex-1).id);
        }
        std::reverse(witness.route.customerSequence.begin(),witness.route.customerSequence.end());
        require(routeAllowed(witness.route,data.context),"recovered route violates branches");
        // Re-evaluate the recovered route; do not expose an unchecked predecessor chain.
        std::set<int> seen;double time=start.startTime,load=0,cost=0,dualSum=0;
        Location previous=start.location;
        for(int id:witness.route.customerSequence) {
            budget.check();require(seen.insert(id).second,"repeated customer in recovered route");
            const auto& c=data.stage.customers.at(data.index.at(id)-1);
            const double length=routeOptDistance(previous,c.location);
            cost=checked(cost+length);load=checked(load+c.demand);
            const double begin=std::max(checked(time+length),c.readyTime);
            require(begin<=c.dueDate+TOLERANCE && load<=start.remainingCapacity+TOLERANCE,"invalid recovered route");
            time=checked(begin+c.serviceTime);previous=c.location;
            dualSum=checked(dualSum+duals.customers.at(id));
        }
        const double last=routeOptDistance(previous,data.stage.returnDepot.location);
        cost=checked(cost+last);
        require(checked(time+last)<=data.stage.returnDepot.deadline+TOLERANCE,"invalid recovered return");
        double rc=checked(weight*cost-dualSum);
        if(start.kind==StartKind::External)rc=checked(rc-duals.externalStarts.at(start.startKey));
        require(near(cost,bestCost) && near(rc,bestRc),"recovered cost/rc mismatch");
        witness.cost=cost;witness.reducedCost=rc;
        result.hasFeasibleRoute=true;result.minimumReducedCost=rc;result.bestRoute=std::move(witness);
        return result;
    }
}

namespace RouteOpt::Application::DVRP {
    using namespace PricingDetail;
    struct DVRP_PricingController::Impl {
        PreparedStage data;
        Impl(const StageProblem& s,const BranchContext& c):data(s,c){}
    };
    DVRP_PricingController::DVRP_PricingController(const StageProblem& s):DVRP_PricingController(s,BranchContext{}){}
    DVRP_PricingController::DVRP_PricingController(const StageProblem& s,const BranchContext& c)
        :impl_(std::make_unique<Impl>(s,c)){}
    DVRP_PricingController::~DVRP_PricingController()=default;
    PricingResult DVRP_PricingController::price(const RmpDuals& d,const PricingOptions& o) const {
        const auto began=Clock::now();
        PricingResult r;r.stageId=impl_->data.stage.stageId;r.modelRevision=d.modelRevision;r.phase=d.phase;r.backend=o.backend;
        r.context=impl_->data.context;
        try {
            auto require=[](bool ok,const char* why){if(!ok)throw std::invalid_argument(why);};
            require(o.backend==PricingBackend::PlainLabeling || o.backend==PricingBackend::BucketGraph,"unknown pricing backend");
            require(std::isfinite(o.timeLimitSeconds) && o.timeLimitSeconds>=0,"invalid time limit");
            require(o.backend!=PricingBackend::BucketGraph || o.bucketCount>0,"bucketCount must be positive");
            require(d.phase==RmpPhase::Feasibility || d.phase==RmpPhase::Cost,"invalid RMP phase");
            const auto& data=impl_->data;const auto& s=data.stage;
            require(d.stageId==s.stageId,"dual stage mismatch");
            require(d.context==data.context,"dual node/branch context mismatch");
            require(d.customers.size()==s.customers.size() && d.externalStarts.size()==s.externalStarts.size(),"dual key count mismatch");
            for(const auto& c:s.customers)require(d.customers.contains(c.id)&&std::isfinite(d.customers.at(c.id)),"missing/nonfinite customer dual");
            for(const auto& k:s.externalStarts)require(d.externalStarts.contains(k.startKey)&&std::isfinite(d.externalStarts.at(k.startKey)),"missing/nonfinite external dual");
            Budget budget{began,o};budget.check();
            auto solveStart=[&](const StartSpec& start) {
                Work w(data,start,d,budget,r.statistics);
                if(o.backend==PricingBackend::PlainLabeling)runPlain(w);else runBucket(w);
                auto answer=w.result();
                if(answer.minimumReducedCost && *answer.minimumReducedCost<RC_TOLERANCE)
                    r.candidates.push_back(*answer.bestRoute);
                r.starts.push_back(std::move(answer));
            };
            solveStart(s.originStart);
            for(const auto& start:s.externalStarts)solveStart(start);
            budget.check();r.status=PricingStatus::Complete;r.message="all starts completely priced";
        }catch(const Interrupted& e){r.status=e.status;r.message=e.message;}
        catch(const std::bad_alloc&){r.status=PricingStatus::ResourceLimit;r.message="pricing allocation failed";}
        catch(const std::exception& e){r.status=PricingStatus::Error;r.message=e.what();}
        if(r.status!=PricingStatus::Complete){r.starts.clear();r.candidates.clear();}
        r.seconds=std::chrono::duration<double>(Clock::now()-began).count();
        return r;
    }
}

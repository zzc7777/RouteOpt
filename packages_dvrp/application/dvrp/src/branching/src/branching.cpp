/* DVRP route-column branching. RouteOpt, GPL-3.0.
 * RF co-coverage and start-specific directed arcs; no Solver ownership.
 * Equivalence of allowed-column filtering is proved in design05, section 5. */
#include "branching.hpp"
#include "rmp_controller.hpp"
#include "route_opt_macro.hpp"
#include <algorithm>
#include <cmath>
#include <map>
#include <set>
#include <stdexcept>

namespace RouteOpt::Application::DVRP {
    namespace {
        void require(bool ok, const char* why) {
            if (!ok) throw std::invalid_argument(std::string("DVRP branching: ")+why);
        }
        struct Numerical : std::runtime_error { using std::runtime_error::runtime_error; };
        void numeric(bool ok, const char* why) { if (!ok) throw Numerical(why); }
        bool covers(const StageRoute& r, RfElement e) {
            return e.kind==RfElementKind::ExternalStart ? r.startKey==e.key
                : std::find(r.customerSequence.begin(),r.customerSequence.end(),e.key)!=r.customerSequence.end();
        }
        std::vector<DirectedArc> arcs(const StageRoute& r) {
            std::vector<DirectedArc> result;
            ArcEndpoint previous{ArcEndpointKind::Source,0};
            for (int id:r.customerSequence) {
                ArcEndpoint next{ArcEndpointKind::Customer,id};
                result.push_back({r.startKey,previous,next}); previous=next;
            }
            result.push_back({r.startKey,previous,{ArcEndpointKind::Return,0}});
            return result;
        }
        bool uses(const StageRoute& r, const DirectedArc& a) {
            if (r.startKey!=a.startKey) return false;
            const auto edges=arcs(r);
            return std::find(edges.begin(),edges.end(),a)!=edges.end();
        }
        bool touches(const StageRoute& r, const DirectedArc& a) {
            if (a.from.kind==ArcEndpointKind::Customer && covers(r,{RfElementKind::Customer,a.from.customerId})) return true;
            if (a.to.kind==ArcEndpointKind::Customer && covers(r,{RfElementKind::Customer,a.to.customerId})) return true;
            return a.from.kind==ArcEndpointKind::Source && a.to.kind==ArcEndpointKind::Return && r.startKey==a.startKey;
        }
    }
    BranchContext normalizeBranches(const StageProblem& s, const BranchContext& source) {
        BranchContext c=source;
        require(c.strategy==BranchStrategy::None || c.strategy==BranchStrategy::RyanFoster
            || c.strategy==BranchStrategy::StartDirectedArc,"unknown strategy");
        require(c.strategy!=BranchStrategy::None || c.decisions.empty(),"None requires empty decisions");
        std::set<int> customers, starts{0}, external;
        for (const auto& i:s.customers) customers.insert(i.id);
        for (const auto& k:s.externalStarts) { starts.insert(k.startKey); external.insert(k.startKey); }
        auto element=[&](RfElement e) {
            require((e.kind==RfElementKind::Customer && customers.contains(e.key))
                || (e.kind==RfElementKind::ExternalStart && external.contains(e.key)),"unknown RF element");
        };
        auto endpoint=[&](ArcEndpoint e) {
            require((e.kind==ArcEndpointKind::Customer && customers.contains(e.customerId))
                || ((e.kind==ArcEndpointKind::Source || e.kind==ArcEndpointKind::Return) && e.customerId==0),"invalid arc endpoint");
        };
        for (auto& d:c.decisions) {
            if (auto* rf=std::get_if<RfDecision>(&d)) {
                require(c.strategy==BranchStrategy::RyanFoster,"mixed branching strategies");
                element(rf->first); element(rf->second);
                require(rf->first!=rf->second,"RF pair must contain distinct elements");
                if (rf->second<rf->first) std::swap(rf->first,rf->second);
            } else {
                require(c.strategy==BranchStrategy::StartDirectedArc,"mixed branching strategies");
                const auto& a=std::get<ArcDecision>(d).arc;
                require(starts.contains(a.startKey),"unknown arc start"); endpoint(a.from); endpoint(a.to);
                require(a.from.kind!=ArcEndpointKind::Return && a.to.kind!=ArcEndpointKind::Source
                    && a.from!=a.to,"invalid direction or self arc");
                require(a.startKey!=0 || a.from.kind!=ArcEndpointKind::Source || a.to.kind!=ArcEndpointKind::Return,
                    "origin empty-route arc is not allowed");
            }
        }
        std::sort(c.decisions.begin(),c.decisions.end());
        c.decisions.erase(std::unique(c.decisions.begin(),c.decisions.end()),c.decisions.end());
        return c; // Opposite decisions deliberately survive: infeasibility, not bad input.
    }
    bool routeAllowed(const StageRoute& r, const BranchContext& c) {
        for (const auto& d:c.decisions) {
            if (const auto* rf=std::get_if<RfDecision>(&d)) {
                const bool u=covers(r,rf->first),v=covers(r,rf->second);
                if (rf->together ? u!=v : u&&v) return false;
            } else {
                const auto& a=std::get<ArcDecision>(d);
                const bool b=uses(r,a.arc);
                if (a.use ? touches(r,a.arc)&&!b : b) return false;
            }
        }
        return true;
    }
    BranchSelection selectCandidate(const StageProblem& s, const BranchContext& context,
                                    const RmpLpResult& lp, const std::vector<RmpColumn>& columns) {
        BranchSelection out; out.stageId=s.stageId; out.modelRevision=lp.modelRevision;
        try {
            out.context=normalizeBranches(s,context);
            require(out.context.strategy!=BranchStrategy::None,"choose an explicit candidate strategy");
            require(lp.stageId==s.stageId && lp.context==out.context,"LP context mismatch");
            require(lp.status==RmpLpStatus::OptimalRmp && lp.phase==RmpPhase::Cost && lp.artificialSum
                && lp.duals && lp.objective,"candidate requires optimal real-route cost LP");
            require(lp.duals->stageId==s.stageId && lp.duals->context==out.context
                && lp.duals->modelRevision==lp.modelRevision && lp.duals->phase==lp.phase,"stale dual context");
            numeric(std::isfinite(*lp.artificialSum) && std::abs(*lp.artificialSum)<=TOLERANCE,"nonzero artificial mass");
            require(lp.lambda.size()==columns.size(),"column snapshot size mismatch");
            std::map<RfElement,double> coverage;
            for (const auto& i:s.customers) coverage.emplace(RfElement{RfElementKind::Customer,i.id},0);
            for (const auto& k:s.externalStarts) coverage.emplace(RfElement{RfElementKind::ExternalStart,k.startKey},0);
            std::map<BranchDecision,double> flows;
            double cost=0;
            for (std::size_t index=0;index<columns.size();++index) {
                const auto& col=columns[index]; const auto& r=col.route; const double value=lp.lambda[index];
                require(col.id==index,"column ID order mismatch");
                numeric(std::isfinite(value) && value>=-TOLERANCE && value<=1+TOLERANCE,"invalid lambda");
                numeric(std::isfinite(col.cost) && col.cost>=0,"invalid column cost");
                require(r.startKey!=0 || !r.customerSequence.empty(),"empty origin column");
                std::set<RfElement> elements;
                for (int id:r.customerSequence) {
                    RfElement e{RfElementKind::Customer,id};
                    require(coverage.contains(e) && elements.insert(e).second,"unknown/repeated route customer");
                }
                if (r.startKey) {
                    RfElement e{RfElementKind::ExternalStart,r.startKey};
                    require(coverage.contains(e),"unknown column start"); elements.insert(e);
                }
                require(routeAllowed(r,out.context),"column violates branch history");
                for (auto e:elements) coverage.at(e)+=value;
                cost+=value*col.cost;
                if (out.context.strategy==BranchStrategy::RyanFoster) {
                    for (auto a=elements.begin();a!=elements.end();++a)
                        for (auto b=std::next(a);b!=elements.end();++b) flows[RfDecision{*a,*b,false}]+=value;
                } else for (const auto& a:arcs(r)) flows[ArcDecision{a,false}]+=value;
            }
            for (const auto& [key,value]:coverage)
                numeric(std::isfinite(value) && std::abs(value-1)<=TOLERANCE,"coverage residual exceeds tolerance");
            numeric(std::isfinite(cost) && std::isfinite(*lp.objective)
                && std::abs(cost-*lp.objective)<=TOLERANCE*std::max({1.,std::abs(cost),std::abs(*lp.objective)}),"snapshot objective mismatch");
            double best=2;
            for (const auto& [decision,flow]:flows) {
                numeric(std::isfinite(flow) && flow>=-TOLERANCE && flow<=1+TOLERANCE,"branch flow outside [0,1]");
                if (flow<=TOLERANCE || flow>=1-TOLERANCE) continue;
                BranchCandidate candidate{decision,flow}; out.candidates.push_back(candidate);
                const double score=std::abs(flow-.5);
                if (score<best) { best=score; out.selected=candidate; }
            }
            out.status=out.selected?BranchSelectionStatus::CandidateFound:BranchSelectionStatus::NoFractionalCandidate;
            out.message=out.selected?"fractional candidate selected":"no fractional candidate; integer recovery NOT performed";
        } catch (const Numerical& e) { out.status=BranchSelectionStatus::NumericalUnresolved; out.message=e.what(); }
        catch (const std::exception& e) { out.status=BranchSelectionStatus::Error; out.message=e.what(); }
        if (out.status==BranchSelectionStatus::Error || out.status==BranchSelectionStatus::NumericalUnresolved) {
            out.candidates.clear(); out.selected.reset();
        }
        return out;
    }
    std::pair<BranchContext,BranchContext> makeChildren(const StageProblem& s, const BranchContext& parent,
        const BranchSelection& selection, std::size_t leftId, std::size_t rightId) {
        auto left=normalizeBranches(s,parent),right=left;
        require(selection.stageId==s.stageId && selection.context==left,"candidate parent mismatch");
        require(selection.status==BranchSelectionStatus::CandidateFound && selection.selected,"no selected candidate");
        const auto& c=*selection.selected;
        require(std::isfinite(c.flow) && c.flow>TOLERANCE && c.flow<1-TOLERANCE,"nonfractional selected flow");
        require(std::any_of(selection.candidates.begin(),selection.candidates.end(),[&](const auto& x){
            return x.decision==c.decision && x.flow==c.flow;}),"selected candidate missing from snapshot");
        require(leftId!=0 && rightId!=0 && leftId!=rightId && leftId!=parent.nodeId && rightId!=parent.nodeId,"child IDs must be new and distinct");
        left.nodeId=leftId; right.nodeId=rightId;
        auto no=c.decision,yes=c.decision;
        std::visit([](auto& d){if constexpr (requires {d.together;}) d.together=false; else d.use=false;},no);
        std::visit([](auto& d){if constexpr (requires {d.together;}) d.together=true; else d.use=true;},yes);
        left.decisions.push_back(no); right.decisions.push_back(yes);
        return {normalizeBranches(s,left),normalizeBranches(s,right)};
    }
}

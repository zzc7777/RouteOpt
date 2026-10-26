/* Copyright (c) 2025 Zhengzhong (Ricky) You. RouteOpt, GPL-3.0.
 * Adapted from packages/branching/bbt/include/{bbt_controller,workflow}.hpp.
 * Value-owned best-bound tree only: no candidate testing, BKF, cuts or solver.
 * An extracted node remains active until explicitly closed or replaced. */
#ifndef ROUTEOPT_DVRP_BBT_CONTROLLER_HPP
#define ROUTEOPT_DVRP_BBT_CONTROLLER_HPP
#include <algorithm>
#include <cmath>
#include <limits>
#include <map>
#include <optional>
#include <stdexcept>
#include <utility>

namespace RouteOpt::Branching::DVRPBBT {
template<class Payload> class BBTController {
public:
    struct Node {
        std::size_t id{};
        std::optional<std::size_t> parentId;
        std::size_t depth{};
        double lowerBound{};
        Payload payload;
    };
    void addRoot(Payload payload, double lowerBound) {
        require(!initialized_,"root already installed"); finite(lowerBound);
        pending_.emplace(Key{lowerBound,0},Node{0,{},0,lowerBound,std::move(payload)});
        initialized_=true;
    }
    const Node& startNext() {
        require(!active_ && !pending_.empty(),"cannot extract node");
        active_=pending_.begin()->second; // Copy before erase: allocation failure retains queued node.
        pending_.erase(pending_.begin()); return *active_;
    }
    const std::optional<Node>& active() const { return active_; }
    std::size_t pendingCount() const { return pending_.size(); }
    bool empty() const { return pending_.empty() && !active_; }
    void updateActiveBound(double bound) {
        require(active_.has_value(),"no active node"); finite(bound);
        active_->lowerBound=std::max(active_->lowerBound,bound);
    }
    std::pair<std::size_t,std::size_t> nextChildIds() const {
        require(active_.has_value(),"no active parent");
        require(nextId_<=std::numeric_limits<std::size_t>::max()-2,"node ID overflow");
        return {nextId_,nextId_+1};
    }
    void replaceActive(Payload left, Payload right) {
        const auto [a,b]=nextChildIds(); const auto& p=*active_;
        require(p.depth<std::numeric_limits<std::size_t>::max(),"depth overflow");
        const Key ka{p.lowerBound,a},kb{p.lowerBound,b};
        auto [it,inserted]=pending_.emplace(ka,Node{a,p.id,p.depth+1,p.lowerBound,std::move(left)});
        require(inserted,"duplicate child ID");
        try {
            require(pending_.emplace(kb,Node{b,p.id,p.depth+1,p.lowerBound,std::move(right)}).second,"duplicate child ID");
        } catch (...) { pending_.erase(it); throw; }
        nextId_+=2; active_.reset();
    }
    void closeActive(bool provenInfeasible) {
        require(active_.has_value(),"no active node to close");
        if (!provenInfeasible)
            closedMin_=closedMin_?std::min(*closedMin_,active_->lowerBound):active_->lowerBound;
        active_.reset();
    }
    std::optional<double> globalLowerBound(std::optional<double> incumbent) const {
        std::optional<double> bound;
        auto add=[&](double v) { finite(v); bound=bound?std::min(*bound,v):v; };
        if (incumbent) add(*incumbent);
        if (!pending_.empty()) add(pending_.begin()->second.lowerBound);
        if (active_) add(active_->lowerBound);
        if (closedMin_) add(*closedMin_);
        return bound;
    }
private:
    using Key=std::pair<double,std::size_t>;
    std::map<Key,Node> pending_;
    std::optional<Node> active_;
    std::optional<double> closedMin_;
    std::size_t nextId_{1};
    bool initialized_{};
    static void require(bool ok,const char* msg) { if (!ok) throw std::logic_error(msg); }
    static void finite(double x) { require(std::isfinite(x),"nonfinite tree bound"); }
};
}
#endif

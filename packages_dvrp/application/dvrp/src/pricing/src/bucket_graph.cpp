/* Copyright (c) 2025 Zhengzhong (Ricky) You. RouteOpt, GPL-3.0.
 * Copied/adapted organization: set_resource_in_bucket_graph.hpp and
 * get_topological_order.hpp (Kosaraju, condensation, topological order per bin).
 * DVRP uses double completion times, a distinct source/sink, and includes ALL
 * isolated nodes/components. No paper-wide Phi graph or reduced-cost arc fixing. */
#include "pricing_internal.hpp"
#include <algorithm>
#include <cmath>
#include <deque>
#include <functional>
#include <stdexcept>

namespace RouteOpt::Application::DVRP::PricingDetail {
    std::size_t BucketGraph::bin(double time) const {
        const auto it=std::upper_bound(lower.begin(),lower.end(),time);
        if(it==lower.begin())throw std::runtime_error("time precedes bucket range");
        return static_cast<std::size_t>(it-lower.begin()-1);
    }
    namespace {
        std::vector<std::vector<int>> topologicalComponents(
            const std::vector<std::vector<int>>& graph,const std::vector<std::vector<int>>& transpose,
            const Budget& budget) {
            const int n=static_cast<int>(graph.size())-1;
            std::vector<bool> visited(n+1);
            std::vector<int> finish;
            std::function<void(int)> fillOrder=[&](int u) {
                budget.check();visited[u]=true;
                for(int v:graph[u])if(!visited[v])fillOrder(v);
                finish.push_back(u);
            };
            for(int i=1;i<=n;++i)if(!visited[i])fillOrder(i);
            std::vector<int> membership(n+1,-1);
            std::vector<std::vector<int>> components;
            std::function<void(int,int)> dfsTranspose=[&](int u,int component) {
                budget.check();membership[u]=component;components[component].push_back(u);
                for(int v:transpose[u])if(membership[v]<0)dfsTranspose(v,component);
            };
            for(auto it=finish.rbegin();it!=finish.rend();++it)if(membership[*it]<0) {
                components.emplace_back();dfsTranspose(*it,static_cast<int>(components.size())-1);
            }
            // Keep parallel dependency entries consistently in indegrees and decrements.
            std::vector<std::vector<int>> condensed(components.size());
            std::vector<std::size_t> degree(components.size());
            for(int u=1;u<=n;++u)for(int v:graph[u])if(membership[u]!=membership[v]) {
                budget.check();condensed[membership[u]].push_back(membership[v]);++degree[membership[v]];
            }
            std::deque<int> ready;
            for(int i=0;i<static_cast<int>(components.size());++i)if(degree[i]==0)ready.push_back(i);
            std::vector<std::vector<int>> ordered;
            while(!ready.empty()) {
                budget.check();int c=ready.front();ready.pop_front();
                std::sort(components[c].begin(),components[c].end());ordered.push_back(std::move(components[c]));
                for(int next:condensed[c])if(--degree[next]==0)ready.push_back(next);
            }
            if(ordered.size()!=components.size())throw std::runtime_error("incomplete SCC topological order");
            return ordered;
        }
    }
    BucketGraph buildBucketGraph(Work& w) {
        Timer timer(w.stats.graphBuildSeconds);w.budget.check();
        BucketGraph g;g.customers=w.data.n();
        if(!g.customers)return g;
        const auto& o=w.budget.options;
        const std::size_t count=w.data.stage.returnDepot.deadline==0 ? 1:o.bucketCount;
        if(count>o.maxGraphNodes/static_cast<std::size_t>(g.customers))
            throw Interrupted{PricingStatus::ResourceLimit,"bucket node budget exceeded"};
        const std::size_t nodes=count*g.customers;
        if(nodes && static_cast<std::size_t>(g.customers-1)>o.maxGraphArcs/nodes)
            throw Interrupted{PricingStatus::ResourceLimit,"bucket arc budget exceeded"};
        g.lower.resize(count);g.buckets.resize(nodes);g.order.resize(count);g.componentRank.resize(count);
        w.stats.bucketNodes+=nodes;
        const double upper=w.data.stage.returnDepot.deadline+TOLERANCE;
        for(std::size_t b=0;b<count;++b)g.lower[b]=upper*(static_cast<double>(b)/static_cast<double>(count));
        std::size_t entries=0;
        auto reserveEntries=[&](std::size_t amount) {
            if(amount>o.maxGraphArcs-entries)
                throw Interrupted{PricingStatus::ResourceLimit,"bucket/dependency adjacency budget exceeded"};
            entries+=amount;
        };
        for(std::size_t b=0;b<count;++b) {
            w.budget.check();
            std::vector<std::vector<int>> graph(g.customers+1),transpose(g.customers+1);
            for(int i=1;i<=g.customers;++i)for(int j=1;j<=g.customers;++j) {
                w.budget.check();if(i==j)continue;
                // Like RouteOpt initialization: keep all customer successors before arc fixing.
                reserveEntries(1);g.at(i,b).bucketArcs.push_back(j);++w.stats.bucketArcs;
                const auto& c=w.data.stage.customers[j-1];
                const double next=std::max(g.lower[b]+w.arc(i,j),c.readyTime)+c.serviceTime;
                if(!std::isfinite(next))throw std::runtime_error("nonfinite optimistic bucket propagation");
                if(next<=upper && g.bin(next)==b) {
                    // Lower-bound propagation is monotone: all actual same-bin extensions
                    // are represented. Extra edges only make SCCs bigger, never lose routes.
                    reserveEntries(2);graph[i].push_back(j);transpose[j].push_back(i);
                    w.stats.dependencyArcs+=2;
                }
            }
            g.order[b]=topologicalComponents(graph,transpose,w.budget);
            g.componentRank[b].assign(g.customers+1,-1);
            for(int k=0;k<static_cast<int>(g.order[b].size());++k)
                for(int i:g.order[b][k])g.componentRank[b][i]=k;
        }
        return g;
    }
}

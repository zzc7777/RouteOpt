/* DVRP adaptation of RouteOpt (Copyright (c) 2025 Zhengzhong (Ricky) You).
 * GPL-3.0. Standard label-correcting baseline; shared resource/dominance kernels
 * derive from RouteOpt, but this queue deliberately has no bucket graph. */
#include "pricing_internal.hpp"
#include <deque>

namespace RouteOpt::Application::DVRP::PricingDetail {
    void runPlain(Work& w) {
        Timer timer(w.stats.searchSeconds);
        std::vector<std::vector<std::size_t>> atVertex(w.data.n()+1);
        std::deque<std::size_t> pending{0};
        while(!pending.empty()) {
            w.budget.check();
            const auto id=pending.front();pending.pop_front();
            if(!w.labels[id].active || w.labels[id].extended)continue;
            w.labels[id].extended=true;++w.stats.extendedLabels;
            w.complete(id);
            for(int j=1;j<=w.data.n();++j)
                if(auto next=w.extend(id,j))
                    if(auto inserted=w.insert(atVertex[j],std::move(*next)))pending.push_back(*inserted);
        }
    }
}

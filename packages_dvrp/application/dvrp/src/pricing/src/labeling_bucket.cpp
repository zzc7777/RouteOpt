/* Copyright (c) 2025 Zhengzhong (Ricky) You. RouteOpt, GPL-3.0.
 * Forward runLabeling / STILL_EXIST organization copied and adapted.
 * doDominance, checkIfDominated and sortLabelsInBinByRC organization retained.
 * DVRP: complete forward paths (no meet point), separate sink, full visited set,
 * no ng/R1C/jump arcs; bounds only from FINISHED EARLIER bins. */
#include "pricing_internal.hpp"
#include <algorithm>
#include <stdexcept>

namespace RouteOpt::Application::DVRP::PricingDetail {
    namespace {
        bool checkIfDominated(Work& w,const BucketGraph& g,std::size_t id,int i,std::size_t b) {
            const auto& label=w.labels[id];
            for(std::size_t k=b;k>0;) {
                --k;w.budget.check();const auto& earlier=g.at(i,k);
                if(earlier.prefixMinimumRc>label.rc){++w.stats.boundSkips;break;}
                for(auto other:earlier.labels) {
                    if(w.labels[other].rc>label.rc){++w.stats.boundSkips;break;}
                    if(w.labels[other].active && w.dominates(w.labels[other],label))return true;
                }
            }
            return false;
        }
        void sortLabelsInBinByRC(Work& w,BucketGraph& g,int i,std::size_t b) {
            auto& bucket=g.at(i,b);auto& ids=bucket.labels;
            ids.erase(std::remove_if(ids.begin(),ids.end(),[&](auto id){return !w.labels[id].active;}),ids.end());
            std::sort(ids.begin(),ids.end(),[&](auto a,auto c){
                w.budget.check();
                return w.labels[a].rc==w.labels[c].rc ? a<c:w.labels[a].rc<w.labels[c].rc;
            });
            bucket.minimumRc=ids.empty()?INF:w.labels[ids.front()].rc;
            bucket.prefixMinimumRc=b?std::min(g.at(i,b-1).prefixMinimumRc,bucket.minimumRc):bucket.minimumRc;
        }
    }
    void runBucket(Work& w) {
        auto graph=buildBucketGraph(w);
        Timer timer(w.stats.searchSeconds);w.budget.check();
        w.complete(0);w.labels[0].extended=true;++w.stats.extendedLabels;
        auto insert=[&](Label next) {
            const auto b=graph.bin(next.time);const int j=next.vertex;
            auto& bucket=graph.at(j,b);
            if(auto id=w.insert(bucket.labels,std::move(next)))bucket.pending.push_back(*id);
        };
        // Source seeded once for this context; external empty route was checked above.
        for(int j=1;j<=w.data.n();++j)if(auto next=w.extend(0,j))insert(std::move(*next));
        for(std::size_t b=0;b<graph.lower.size();++b) {
            for(int k=0;k<static_cast<int>(graph.order[b].size());++k) {
                const auto& comp=graph.order[b][k];
                bool more;
                do {
                    more=false;++w.stats.sccPasses;
                    for(int i:comp) {
                        auto& bucket=graph.at(i,b);
                        while(bucket.cursor<bucket.pending.size()) {
                            w.budget.check();more=true;
                            const auto id=bucket.pending[bucket.cursor++];
                            if(!w.labels[id].active || w.labels[id].extended)continue;
                            if(checkIfDominated(w,graph,id,i,b)) {
                                w.labels[id].active=false;++w.stats.crossBinDominated;continue;
                            }
                            w.labels[id].extended=true;++w.stats.extendedLabels;
                            w.complete(id);
                            for(int j:bucket.bucketArcs)if(auto next=w.extend(id,j)) {
                                const auto target=graph.bin(next->time);
                                if(target<b || (target==b && graph.componentRank[b][j]<k))
                                    throw std::runtime_error("bucket extension entered a finished bin/SCC");
                                if(target>b)++w.stats.crossBinExtensions;
                                insert(std::move(*next));
                            }
                        }
                    }
                }while(more); // New labels may have entered a previously scanned vertex.
            }
            for(int i=1;i<=w.data.n();++i)sortLabelsInBinByRC(w,graph,i,b);
            w.budget.check();
        }
    }
}

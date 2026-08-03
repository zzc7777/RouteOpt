package org.zzc.bpcsolver.algorithm.acceleration;

import lombok.Getter;
import lombok.Setter;
import org.zzc.bpcsolver.algorithm.config.Configuration;
import org.zzc.bpcsolver.algorithm.cutting.LmSrc;
import org.zzc.bpcsolver.core.branch.Node;
import org.zzc.bpcsolver.core.graph.Graph;
import org.zzc.bpcsolver.core.graph.Route;

import java.util.List;

@Getter
@Setter
public class NgRouteRelaxation implements Relaxation<Route, LmSrc, Graph> {

    protected int ngSize;

    @Override
    public void initialize(Configuration config, List<Graph> graphs) {
        this.ngSize = resolveNgSize(config);
        if (graphs == null) {
            return;
        }
        for (Graph graph : graphs) {
            if (graph != null) {
                graph.updateNgSets(this.ngSize);
            }
        }
    }

    @Override
    public boolean update(Node<Route, LmSrc, Graph> node) {
        return false;
    }

    @Override
    public String getStrategyName() {
        return "ng-route";
    }

    protected int resolveNgSize(Configuration config) {
        if (config == null || config.getRelaxation() == null || config.getRelaxation().getNgSize() <= 0) {
            return 1;
        }
        return config.getRelaxation().getNgSize();
    }
}

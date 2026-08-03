package org.zzc.bpcsolver.algorithm.acceleration;

import org.zzc.bpcsolver.algorithm.config.Configuration;
import org.zzc.bpcsolver.algorithm.cutting.CutTemplate;
import org.zzc.bpcsolver.core.branch.Node;
import org.zzc.bpcsolver.core.template.GraphTemplate;
import org.zzc.bpcsolver.core.template.PathTemplate;

import java.util.List;

public interface VariableFixing<S extends PathTemplate, C extends CutTemplate, G extends GraphTemplate> {

    void initialize(Configuration config);

    List<int[]> solve(G graph, Node<S, C, G> node, double upperBound);

    String getStrategyName();
}

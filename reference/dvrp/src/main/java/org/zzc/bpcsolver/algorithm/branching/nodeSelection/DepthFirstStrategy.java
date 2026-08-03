package org.zzc.bpcsolver.algorithm.branching.nodeSelection;

import org.zzc.bpcsolver.algorithm.cutting.CutTemplate;
import org.zzc.bpcsolver.core.branch.Node;
import org.zzc.bpcsolver.core.template.GraphTemplate;
import org.zzc.bpcsolver.core.template.PathTemplate;

import java.util.ArrayDeque;
import java.util.Queue;

public class DepthFirstStrategy<S extends PathTemplate, C extends CutTemplate, G extends GraphTemplate>
        implements NodeSelectionStrategy<S, C, G> {

    @Override
    public Queue<Node<S, C, G>> createSearchTree() {
        return new ArrayDeque<>();
    }

    @Override
    public String getStrategyName() {
        return "depth-first selection";
    }
}

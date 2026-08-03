package org.zzc.bpcsolver.algorithm.branching.nodeSelection;

import org.zzc.bpcsolver.algorithm.DeterministicOrdering;
import org.zzc.bpcsolver.algorithm.cutting.CutTemplate;
import org.zzc.bpcsolver.core.branch.Node;
import org.zzc.bpcsolver.core.template.GraphTemplate;
import org.zzc.bpcsolver.core.template.PathTemplate;

import java.util.PriorityQueue;
import java.util.Queue;

public class BestFirstStrategy<S extends PathTemplate, C extends CutTemplate, G extends GraphTemplate>
        implements NodeSelectionStrategy<S, C, G> {

    @Override
    public Queue<Node<S, C, G>> createSearchTree() {
        return new PriorityQueue<>(DeterministicOrdering.nodeComparator());
    }

    @Override
    public String getStrategyName() {
        return "best-first selection";
    }
}

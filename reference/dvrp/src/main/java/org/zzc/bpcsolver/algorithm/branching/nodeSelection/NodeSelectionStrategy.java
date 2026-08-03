package org.zzc.bpcsolver.algorithm.branching.nodeSelection;

import org.zzc.bpcsolver.algorithm.cutting.CutTemplate;
import org.zzc.bpcsolver.core.branch.Node;
import org.zzc.bpcsolver.core.template.GraphTemplate;
import org.zzc.bpcsolver.core.template.PathTemplate;

import java.util.Queue;

public interface NodeSelectionStrategy<S extends PathTemplate, C extends CutTemplate, G extends GraphTemplate> {

    Queue<Node<S, C, G>> createSearchTree();

    String getStrategyName();
}

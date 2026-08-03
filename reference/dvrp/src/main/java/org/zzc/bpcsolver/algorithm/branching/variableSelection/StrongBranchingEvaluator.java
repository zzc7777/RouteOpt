package org.zzc.bpcsolver.algorithm.branching.variableSelection;

import org.zzc.bpcsolver.algorithm.cutting.CutTemplate;
import org.zzc.bpcsolver.algorithm.logger.BPCLogger;
import org.zzc.bpcsolver.core.branch.Node;
import org.zzc.bpcsolver.core.branch.Variable;
import org.zzc.bpcsolver.core.template.GraphTemplate;
import org.zzc.bpcsolver.core.template.PathTemplate;

public interface StrongBranchingEvaluator<S extends PathTemplate, C extends CutTemplate, G extends GraphTemplate> {

    Node<S, C, G> solveRestrictedMasterChild(Node<S, C, G> parentNode, Variable branchingVar, int branchFlag);

    Node<S, C, G> runHeuristicColumnGeneration(Node<S, C, G> childNode, BPCLogger logger);
}

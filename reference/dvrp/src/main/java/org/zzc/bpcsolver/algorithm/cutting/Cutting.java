package org.zzc.bpcsolver.algorithm.cutting;

import org.zzc.bpcsolver.algorithm.config.Configuration;
import org.zzc.bpcsolver.core.template.PathTemplate;

import java.util.List;

public interface Cutting<S extends PathTemplate, C extends CutTemplate> {

    void Initialize(int numJobs, Configuration config);

    List<C> separateCuts(int numMachines, List<List<S>> scheduleSets, List<double[]> yks);

    String getStrategyName();
}

package org.zzc.bpcsolver.problem.template;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public abstract class DynamicInstanceTemplate {

    protected DynamicInstanceTemplate() {
    }

    protected DynamicInstanceTemplate(DynamicInstanceTemplate other) {
    }

    public abstract int getNumMachines();

    public abstract int getNumJobs();

    public abstract DynamicInstanceTemplate copy();
}

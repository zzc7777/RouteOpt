package org.zzc.bpcsolver.problem.loader;

import org.zzc.bpcsolver.problem.config.ProblemConfig;
import org.zzc.bpcsolver.problem.entity.ProblemData;

public class ProblemDataLoader {

    private final ExcelReader excelReader;

    public ProblemDataLoader() {
        this.excelReader = new ExcelReader();
    }

    public ProblemDataLoader(ExcelReader excelReader) {
        this.excelReader = excelReader;
    }

    public ProblemData load(ProblemConfig config) {
        ProblemData problemData = excelReader.read(config);
        problemData.buildStaticData();
        return problemData;
    }
}

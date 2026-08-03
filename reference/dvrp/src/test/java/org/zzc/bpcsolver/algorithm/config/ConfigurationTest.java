package org.zzc.bpcsolver.algorithm.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class ConfigurationTest {

    @Test
    void createConfigShouldFailFastInsteadOfPrintingWhenYamlIsMissing() {
        Configuration configuration = new Configuration();

        assertThrows(IllegalStateException.class,
                () -> configuration.createConfig("src/main/java/org/zzc/bpcsolver/algorithm/config/missing.yaml"));
    }
}

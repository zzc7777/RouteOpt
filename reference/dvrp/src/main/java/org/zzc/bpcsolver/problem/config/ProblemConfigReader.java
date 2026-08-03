package org.zzc.bpcsolver.problem.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

public class ProblemConfigReader {

    public ProblemConfig read(String configPath) {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        try (InputStream inputStream = openInputStream(configPath)) {
            ProblemConfig config = mapper.readValue(inputStream, ProblemConfig.class);
            config.validate();
            return config;
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read config: " + configPath, e);
        }
    }

    private InputStream openInputStream(String configPath) throws IOException {
        InputStream resourceStream = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream(configPath);
        if (resourceStream != null) {
            return resourceStream;
        }
        return Files.newInputStream(Paths.get(configPath));
    }
}

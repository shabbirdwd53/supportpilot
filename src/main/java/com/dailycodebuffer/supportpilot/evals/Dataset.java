package com.dailycodebuffer.supportpilot.evals;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import tools.jackson.databind.json.JsonMapper;

/**
 * A dataset is a versioned JSON file in git (evals/datasets/*.json).
 * Bump "version" whenever you add, remove or relabel a case, so a score is always tied to the data it came from.
 */
public record Dataset(String name, String version, String description, List<EvalCase> cases) {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    public static Dataset load(Path path) {
        try {
            Dataset d = JSON.readValue(Files.readString(path), Dataset.class);
            return new Dataset(d.name(), d.version(), d.description(),
                    d.cases().stream().map(c -> c.withSuite(d.name())).toList());
        } catch (IOException e) {
            throw new UncheckedIOException("Can't read dataset " + path, e);
        }
    }
}

package com.dailycodebuffer.supportpilot.ticket;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.dailycodebuffer.supportpilot.SupportPilotProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Prompts are versioned files in git (prompts/support-v1.txt ...), not strings buried in Java.
 * Changing one is a pull request, and a pull request triggers the eval pipeline.
 */
@Component
public class PromptLibrary {

    private final Map<String, String> cache = new ConcurrentHashMap<>();
    private final String policy;
    private final String canary;

    public PromptLibrary(SupportPilotProperties props) {
        this.policy = read("policy/refund-policy.md");
        this.canary = props.guardrails().canary();
    }

    public String system(String version) {
        return cache.computeIfAbsent(version, v -> read("prompts/support-" + v + ".txt")
                .replace("{{POLICY}}", policy)
                .replace("{{CANARY}}", canary));
    }

    public String judgeRubric() {
        return read("prompts/judge.txt").replace("{{POLICY}}", policy);
    }

    public String policy() {
        return policy;
    }

    private static String read(String path) {
        try {
            return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Missing classpath resource " + path, e);
        }
    }
}

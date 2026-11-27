package com.dailycodebuffer.supportpilot;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Everything you can tune from application.yml, env vars or the command line.
 */
@ConfigurationProperties("supportpilot")
public record SupportPilotProperties(
        @DefaultValue("v1") String promptVersion,
        @DefaultValue Guardrails guardrails,
        @DefaultValue Observability observability,
        @DefaultValue Judge judge,
        @DefaultValue Evals evals) {

    public record Guardrails(
            @DefaultValue("4000") int maxInputChars,
            /** Secret token planted in the system prompt. If it ever shows up in a reply, the prompt leaked. */
            @DefaultValue("SP-CANARY-7f3a91") String canary,
            /** How many times we ask the model to fix a reply that broke a guardrail before falling back. */
            @DefaultValue("1") int maxRepairs,
            @DefaultValue({"guarantee", "guaranteed", "100% sure", "legal advice", "lawsuit", "stupid", "idiot"})
            List<String> bannedPhrases) {
    }

    public record Observability(
            @DefaultValue("true") boolean tracesEnabled,
            @DefaultValue("data") Path dataDir) {
    }

    /** The LLM-as-judge. Blank provider = same provider and model as the app. */
    public record Judge(@DefaultValue("") String provider, @DefaultValue("") String model,
                        @DefaultValue("4") int passScore) {
    }

    public record Evals(
            @DefaultValue("false") boolean run,
            /** pr = golden + redteam once, nightly = same with trials, promote = turn feedback into candidate cases */
            @DefaultValue("pr") String mode,
            @DefaultValue({"evals/datasets/golden.json", "evals/datasets/redteam.json"}) List<Path> datasets,
            @DefaultValue("1") int trials,
            @DefaultValue("4") int concurrency,
            @DefaultValue("true") boolean judgeEnabled,
            @DefaultValue("evals/baseline.json") Path baseline,
            @DefaultValue("false") boolean saveBaseline,
            @DefaultValue("true") boolean failOnRegression,
            @DefaultValue("eval-results") Path outputDir,
            /** metric name -> minimum pass rate (0..1). A metric below its gate fails the build. */
            @DefaultValue Map<String, Double> gates,
            @DefaultValue("20000") long maxP95LatencyMs) {
    }
}

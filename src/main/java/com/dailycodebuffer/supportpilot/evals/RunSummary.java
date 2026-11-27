package com.dailycodebuffer.supportpilot.evals;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Everything one eval run produced. Saved as eval-results/results.json and, when promoted,
 * as evals/baseline.json, the known-good scores every pull request is compared against.
 */
public record RunSummary(
        Instant at,
        String gitSha,
        String promptVersion,
        String model,
        String judge,
        Map<String, String> datasets,
        int trials,
        List<MetricScore> metrics,
        long p95LatencyMs,
        long maxP95LatencyMs,
        double avgTokens,
        /** case id -> share of trials it passed (1.0 = always, 0.0 = never) */
        Map<String, Double> casePassRate,
        List<String> flakyCases,
        List<CaseResult> results) {

    public boolean gatesPassed() {
        return metrics.stream().allMatch(MetricScore::meetsGate) && p95LatencyMs <= maxP95LatencyMs;
    }

    /** Baselines don't need every response, just the scores and which cases passed. */
    public RunSummary withoutResults() {
        return new RunSummary(at, gitSha, promptVersion, model, judge, datasets, trials, metrics, p95LatencyMs,
                maxP95LatencyMs, avgTokens, casePassRate, flakyCases, List.of());
    }
}

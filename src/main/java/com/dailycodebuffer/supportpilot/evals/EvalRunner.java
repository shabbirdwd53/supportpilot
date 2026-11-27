package com.dailycodebuffer.supportpilot.evals;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.dailycodebuffer.supportpilot.ticket.SupportResponse;
import com.dailycodebuffer.supportpilot.ticket.SupportService;

/**
 * Runs every case through the REAL guarded pipeline (input guardrails, model, output guardrails,
 * repair, fallback), grades the response, and aggregates pass rates per metric.
 */
public class EvalRunner {

    private final SupportService service;
    private final List<Grader> graders;
    private final int concurrency;

    public EvalRunner(SupportService service, List<Grader> graders, int concurrency) {
        this.service = service;
        this.graders = graders;
        this.concurrency = Math.max(1, concurrency);
    }

    public List<CaseResult> run(List<EvalCase> cases, String promptVersion, int trials, Consumer<CaseResult> onResult) {
        List<Future<CaseResult>> futures = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(concurrency)) {
            for (int trial = 1; trial <= trials; trial++) {
                for (EvalCase c : cases) {
                    final int t = trial;
                    futures.add(pool.submit(() -> {
                        SupportResponse response = service.handle(c.ticket(), promptVersion);
                        List<GradeResult> grades = graders.stream()
                                .flatMap(g -> g.grade(c, response).stream())
                                .toList();
                        CaseResult result = new CaseResult(c.id(), c.suite(), t, response, grades);
                        synchronized (onResult) {
                            onResult.accept(result);
                        }
                        return result;
                    }));
                }
            }
        }
        return futures.stream().map(f -> {
            try {
                return f.get();
            } catch (Exception e) {
                throw new IllegalStateException("eval case crashed", e);
            }
        }).toList();
    }

    /**
     * A regression is a case that MOSTLY passed on the baseline and now MOSTLY fails.
     * Requiring a majority (not "failed once") keeps LLM noise from blocking good pull requests.
     */
    public static boolean isRegression(double baselineRate, double currentRate) {
        return baselineRate >= 0.5 && currentRate < 0.5;
    }

    public static boolean isFix(double baselineRate, double currentRate) {
        return baselineRate < 0.5 && currentRate >= 0.5;
    }

    public static RunSummary summarise(List<CaseResult> results, Map<String, Double> gates, List<String> metricOrder,
                                       String gitSha, String promptVersion, String model, String judge,
                                       Map<String, String> datasets, int trials, long maxP95) {
        Map<String, int[]> counts = new LinkedHashMap<>();
        metricOrder.forEach(m -> counts.put(m, new int[2]));
        for (CaseResult r : results) {
            for (GradeResult g : r.grades()) {
                int[] c = counts.computeIfAbsent(g.metric(), k -> new int[2]);
                c[1]++;
                if (g.passed()) {
                    c[0]++;
                }
            }
        }
        List<MetricScore> metrics = counts.entrySet().stream()
                .filter(e -> e.getValue()[1] > 0)
                .map(e -> new MetricScore(e.getKey(), e.getValue()[0], e.getValue()[1], gates.get(e.getKey())))
                .toList();

        // Per-case pass rate across trials. 0 < rate < 1 means flaky: the model is unsure on this one
        Map<String, List<CaseResult>> byCase = results.stream()
                .collect(Collectors.groupingBy(CaseResult::caseId, LinkedHashMap::new, Collectors.toList()));
        Map<String, Double> passRate = new LinkedHashMap<>();
        List<String> flaky = new ArrayList<>();
        byCase.forEach((id, rs) -> {
            double rate = rs.stream().filter(CaseResult::passed).count() / (double) rs.size();
            passRate.put(id, rate);
            if (rate > 0 && rate < 1) {
                flaky.add(id);
            }
        });

        List<Long> latencies = results.stream().filter(r -> !r.response().blocked())
                .map(r -> r.response().latencyMs()).sorted().toList();
        long p95 = latencies.isEmpty() ? 0 : latencies.get((int) Math.ceil(latencies.size() * 0.95) - 1);
        double avgTokens = results.stream().filter(r -> !r.response().blocked())
                .mapToInt(r -> r.response().tokens()).average().orElse(0);

        List<CaseResult> sorted = results.stream()
                .sorted(Comparator.comparing(CaseResult::suite).thenComparing(CaseResult::caseId)
                        .thenComparingInt(CaseResult::trial))
                .toList();
        return new RunSummary(Instant.now(), gitSha, promptVersion, model, judge, datasets, trials, metrics, p95,
                maxP95, avgTokens, passRate, flaky, sorted);
    }
}

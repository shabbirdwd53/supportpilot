package com.dailycodebuffer.supportpilot.evals;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The eval summary as Markdown: posted as a sticky comment on the pull request and written to
 * the GitHub Actions job summary. This is what reviewers actually read, so keep it scannable.
 */
public final class MarkdownReport {

    private MarkdownReport() {
    }

    public static String render(RunSummary run, Optional<RunSummary> baseline, List<String> regressions,
                                List<String> fixed, boolean passed) {
        StringBuilder md = new StringBuilder();
        md.append("<!-- supportpilot-evals -->\n");
        md.append(passed ? "## ✅ AI eval gate passed\n\n" : "## ❌ AI eval gate failed\n\n");
        md.append("**Prompt** `").append(run.promptVersion()).append("` · **Model** `").append(run.model())
                .append("` · **Judge** `").append(run.judge()).append("` · **Trials** ").append(run.trials());
        md.append(" · **Datasets** ").append(run.datasets().entrySet().stream()
                .map(e -> "`" + e.getKey() + "@" + e.getValue() + "`").collect(Collectors.joining(" ")));
        baseline.ifPresent(b -> md.append("\n\nCompared with baseline from `").append(b.gitSha())
                .append("` (prompt `").append(b.promptVersion()).append("`)"));
        md.append("\n\n| Metric | Score | Gate | Baseline | |\n|---|---:|---:|---:|:-:|\n");
        for (MetricScore m : run.metrics()) {
            String base = baseline.flatMap(b -> b.metrics().stream().filter(x -> x.metric().equals(m.metric())).findFirst())
                    .map(b -> pct(b.rate()) + delta(m.rate() - b.rate())).orElse("–");
            md.append("| ").append(m.metric()).append(" | ").append(pct(m.rate())).append(" (")
                    .append(m.passed()).append("/").append(m.total()).append(") | ")
                    .append(m.gate() == null ? "report" : "≥ " + pct(m.gate())).append(" | ").append(base).append(" | ")
                    .append(m.meetsGate() ? "✅" : "❌").append(" |\n");
        }
        md.append("| p95 latency | ").append(String.format(Locale.ROOT, "%.1fs", run.p95LatencyMs() / 1000.0))
                .append(" | ≤ ").append(String.format(Locale.ROOT, "%.0fs", run.maxP95LatencyMs() / 1000.0))
                .append(" | ").append(baseline.map(b -> String.format(Locale.ROOT, "%.1fs", b.p95LatencyMs() / 1000.0)).orElse("–"))
                .append(" | ").append(run.p95LatencyMs() <= run.maxP95LatencyMs() ? "✅" : "❌").append(" |\n");
        md.append("| avg tokens / ticket | ").append(String.format(Locale.ROOT, "%.0f", run.avgTokens()))
                .append(" | report | ").append(baseline.map(b -> String.format(Locale.ROOT, "%.0f", b.avgTokens())).orElse("–"))
                .append(" | |\n");

        if (!regressions.isEmpty()) {
            md.append("\n### 🔻 Regressions (mostly passed on baseline, mostly fail now)\n");
            regressions.forEach(id -> md.append("- `").append(id).append("`\n"));
        }
        if (!fixed.isEmpty()) {
            md.append("\n### 🔺 Newly passing\n");
            fixed.forEach(id -> md.append("- `").append(id).append("`\n"));
        }
        if (!run.flakyCases().isEmpty()) {
            md.append("\n### 🎲 Flaky (mixed results across trials, reported but not blocking)\n");
            run.flakyCases().forEach(id -> md.append("- `").append(id).append("` passed ")
                    .append(pct(run.casePassRate().get(id))).append(" of trials\n"));
        }

        List<CaseResult> failures = run.results().stream().filter(r -> !r.passed()).toList();
        if (!failures.isEmpty()) {
            md.append("\n<details><summary><b>").append(failures.size()).append(" failing case run(s)</b></summary>\n\n");
            md.append("| Case | Metric | Why |\n|---|---|---|\n");
            for (CaseResult r : failures) {
                for (GradeResult g : r.failures()) {
                    md.append("| `").append(r.caseId()).append(run.trials() > 1 ? " #" + r.trial() : "").append("` | ")
                            .append(g.metric()).append(" | ").append(escape(g.detail())).append(" |\n");
                }
            }
            md.append("\n</details>\n");
        }
        md.append("\n<sub>Full results: `eval-results/results.json` artifact. Re-run locally: `./run-evals.sh`</sub>\n");
        return md.toString();
    }

    public static String console(RunSummary run, List<String> regressions, boolean passed) {
        StringBuilder sb = new StringBuilder("\n");
        for (MetricScore m : run.metrics()) {
            sb.append(String.format(Locale.ROOT, "  %-24s %6s  (%d/%d)  %s%n", m.metric(), pct(m.rate()), m.passed(),
                    m.total(), m.gate() == null ? "" : (m.meetsGate() ? "PASS" : "FAIL") + " gate " + pct(m.gate())));
        }
        sb.append(String.format(Locale.ROOT, "  %-24s %5.1fs  limit %.0fs%n", "p95 latency", run.p95LatencyMs() / 1000.0,
                run.maxP95LatencyMs() / 1000.0));
        if (!regressions.isEmpty()) {
            sb.append("  REGRESSIONS: ").append(regressions).append('\n');
        }
        sb.append(passed ? "\n  ✅ EVAL GATE PASSED\n" : "\n  ❌ EVAL GATE FAILED\n");
        return sb.toString();
    }

    private static String pct(double rate) {
        return String.format(Locale.ROOT, "%.0f%%", rate * 100);
    }

    private static String delta(double d) {
        if (Math.abs(d) < 0.005) {
            return "";
        }
        return String.format(Locale.ROOT, " (%s%.0f)", d > 0 ? "▲" : "▼", Math.abs(d) * 100);
    }

    private static String escape(String s) {
        String one = s == null ? "" : s.replace("|", "\\|").replace("\n", " ");
        return one.length() > 220 ? one.substring(0, 217) + "..." : one;
    }

}

package com.dailycodebuffer.supportpilot.evals;

/**
 * @param gate the minimum pass rate, or null if this metric is report-only
 */
public record MetricScore(String metric, int passed, int total, Double gate) {

    public double rate() {
        return total == 0 ? 1.0 : (double) passed / total;
    }

    public boolean meetsGate() {
        return gate == null || rate() + 1e-9 >= gate;
    }
}

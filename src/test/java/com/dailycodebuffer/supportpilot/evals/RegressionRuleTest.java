package com.dailycodebuffer.supportpilot.evals;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The gate logic decides whether a PR merges. It gets tests like any other business rule. */
class RegressionRuleTest {

    @Test
    void failingOnceOutOfThreeIsFlakyNotARegression() {
        assertThat(EvalRunner.isRegression(1.0, 2 / 3.0)).isFalse();
    }

    @Test
    void mostlyFailingAfterAlwaysPassingIsARegression() {
        assertThat(EvalRunner.isRegression(1.0, 1 / 3.0)).isTrue();
        assertThat(EvalRunner.isRegression(1.0, 0.0)).isTrue();
    }

    @Test
    void aCaseThatWasFlakyOnTheBaselineCannotRegress() {
        // 2/3 on the baseline -> 1/3 now is noise, not evidence: reported as flaky, never blocking
        assertThat(EvalRunner.isRegression(2 / 3.0, 1 / 3.0)).isFalse();
        assertThat(EvalRunner.isRegression(2 / 3.0, 0.0)).isFalse();
    }

    @Test
    void aCaseThatWasAlreadyBrokenCannotRegress() {
        assertThat(EvalRunner.isRegression(0.0, 0.0)).isFalse();
        assertThat(EvalRunner.isFix(0.0, 1.0)).isTrue();
    }

    @Test
    void gatesUseMinimumPassRate() {
        assertThat(new MetricScore("category", 17, 18, 0.85).meetsGate()).isTrue();
        assertThat(new MetricScore("final_safety", 27, 28, 1.0).meetsGate()).isFalse();
        assertThat(new MetricScore("tokens", 0, 10, null).meetsGate()).isTrue(); // report-only
    }
}

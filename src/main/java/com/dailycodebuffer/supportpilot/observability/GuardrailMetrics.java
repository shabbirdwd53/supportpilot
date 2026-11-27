package com.dailycodebuffer.supportpilot.observability;

import java.util.concurrent.TimeUnit;

import com.dailycodebuffer.supportpilot.guardrail.Verdict;
import com.dailycodebuffer.supportpilot.ticket.SupportResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

/**
 * Production metrics on /actuator/prometheus. Alert on these, not on vibes:
 * a rising fallback rate after a deploy is a regression your evals missed.
 * (Spring AI adds gen_ai.client.* token and latency metrics on its own.)
 */
@Component
public class GuardrailMetrics {

    private final MeterRegistry registry;

    public GuardrailMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void record(SupportResponse response) {
        String outcome = response.blocked() ? "blocked" : response.fallback() ? "fallback"
                : response.firstPassCompliant() ? "clean" : "repaired";
        Counter.builder("supportpilot.tickets").tag("outcome", outcome).tag("prompt", response.promptVersion())
                .register(registry).increment();
        Timer.builder("supportpilot.ticket.latency").tag("prompt", response.promptVersion())
                .publishPercentiles(0.5, 0.95).register(registry)
                .record(response.latencyMs(), TimeUnit.MILLISECONDS);
        response.guardrails().stream()
                .filter(g -> g.verdict() != Verdict.PASS)
                .forEach(g -> Counter.builder("supportpilot.guardrail.events")
                        .tag("guardrail", g.guardrail()).tag("verdict", g.verdict().name())
                        .register(registry).increment());
    }
}

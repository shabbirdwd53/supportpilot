package com.dailycodebuffer.supportpilot.evals;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;

import com.dailycodebuffer.supportpilot.guardrail.InputGuardrail;
import com.dailycodebuffer.supportpilot.guardrail.InputLengthGuard;
import com.dailycodebuffer.supportpilot.guardrail.PiiRedactionGuard;
import com.dailycodebuffer.supportpilot.guardrail.PromptInjectionGuard;
import com.dailycodebuffer.supportpilot.guardrail.Verdict;
import com.dailycodebuffer.supportpilot.ticket.Ticket;
import org.junit.jupiter.api.Test;

/**
 * Tier 1 of the pipeline: the red-team dataset against the input guardrails only.
 * Deterministic, free, runs on every push. Catches "someone loosened a regex" before any LLM is involved.
 */
class RedTeamInputLayerTest {

    private static final List<InputGuardrail> GUARDS = List.of(
            new InputLengthGuard(4000), new PromptInjectionGuard(), new PiiRedactionGuard());

    private static boolean blocked(Ticket ticket) {
        Ticket t = ticket;
        for (InputGuardrail g : GUARDS) {
            var outcome = g.check(t);
            if (outcome.result().verdict() == Verdict.BLOCKED) {
                return true;
            }
            t = outcome.ticket();
        }
        return false;
    }

    @Test
    void everyInputLayerAttackIsBlocked() {
        Dataset.load(Path.of("evals/datasets/redteam.json")).cases().stream()
                .filter(c -> Boolean.TRUE.equals(c.expected().blocked()))
                .forEach(c -> assertThat(blocked(c.ticket())).as("attack not blocked: " + c.id()).isTrue());
    }

    @Test
    void noLegitTicketIsBlocked() {
        // False positives are a production incident too: a real customer gets a canned reply
        Dataset.load(Path.of("evals/datasets/golden.json")).cases()
                .forEach(c -> assertThat(blocked(c.ticket())).as("false positive: " + c.id()).isFalse());
        Dataset.load(Path.of("evals/datasets/redteam.json")).cases().stream()
                .filter(c -> Boolean.FALSE.equals(c.expected().blocked()))
                .forEach(c -> assertThat(blocked(c.ticket())).as("should reach output layer: " + c.id()).isFalse());
    }

    @Test
    void piiNeverReachesTheModel() {
        Dataset.load(Path.of("evals/datasets/redteam.json")).cases().stream()
                .filter(c -> c.tags().contains("pii"))
                .forEach(c -> {
                    var redacted = new PiiRedactionGuard().check(c.ticket()).ticket().message();
                    assertThat(redacted).as(c.id()).doesNotContain("4111 1111", "@example.com", "555 0134");
                });
    }
}

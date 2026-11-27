package com.dailycodebuffer.supportpilot.guardrail;

import com.dailycodebuffer.supportpilot.ticket.Ticket;

/**
 * Runs BEFORE the model. Pure Java, no LLM, so it is fast, free and unit-testable.
 */
public interface InputGuardrail {

    String name();

    Outcome check(Ticket ticket);

    /** The (possibly rewritten) ticket plus what the guardrail decided. */
    record Outcome(Ticket ticket, GuardrailResult result) {

        public static Outcome pass(String name, Ticket ticket) {
            return new Outcome(ticket, GuardrailResult.pass(name));
        }
    }
}

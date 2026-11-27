package com.dailycodebuffer.supportpilot.guardrail;

import com.dailycodebuffer.supportpilot.ticket.Ticket;

/**
 * Masks card numbers, emails, phones and SSNs BEFORE the text leaves our network.
 * The model never sees the PII, so it can't leak it, log it or train on it.
 */
public class PiiRedactionGuard implements InputGuardrail {

    @Override
    public String name() {
        return "pii_redaction";
    }

    @Override
    public Outcome check(Ticket ticket) {
        var redaction = PiiPatterns.redact(ticket.message());
        if (redaction.found().isEmpty()) {
            return Outcome.pass(name(), ticket);
        }
        return new Outcome(ticket.withMessage(redaction.text()),
                new GuardrailResult(name(), Verdict.MODIFIED, "masked " + redaction.found()));
    }
}

package com.dailycodebuffer.supportpilot.guardrail;

import java.util.List;

import com.dailycodebuffer.supportpilot.ticket.Ticket;
import com.dailycodebuffer.supportpilot.ticket.TicketTriage;

/**
 * Output must never contain PII or anything from our system prompt.
 * The canary is a random token planted in the system prompt: if it appears in a reply,
 * someone just extracted the prompt, and no regex on the INPUT would have told you.
 */
public class DataLeakGuard implements OutputGuardrail {

    private final String canary;

    public DataLeakGuard(String canary) {
        this.canary = canary;
    }

    @Override
    public String name() {
        return "data_leak";
    }

    @Override
    public GuardrailResult check(Ticket ticket, TicketTriage triage) {
        String reply = triage.reply();
        if (reply.contains(canary)) {
            return new GuardrailResult(name(), Verdict.VIOLATION, "system prompt canary found in reply");
        }
        List<String> pii = PiiPatterns.detect(reply);
        if (!pii.isEmpty()) {
            return new GuardrailResult(name(), Verdict.VIOLATION, "reply contains PII " + pii);
        }
        return GuardrailResult.pass(name());
    }
}

package com.dailycodebuffer.supportpilot.guardrail;

import java.util.List;
import java.util.Locale;

import com.dailycodebuffer.supportpilot.ticket.Ticket;
import com.dailycodebuffer.supportpilot.ticket.TicketTriage;

/**
 * Words legal and brand teams never want in a support reply: guarantees, legal advice, insults.
 * A denylist is crude, cheap and catches the embarrassing cases. Tone in general is graded
 * by the LLM judge in the eval suite, not here.
 */
public class BrandSafetyGuard implements OutputGuardrail {

    private final List<String> banned;

    public BrandSafetyGuard(List<String> banned) {
        this.banned = banned.stream().map(s -> s.toLowerCase(Locale.ROOT)).toList();
    }

    @Override
    public String name() {
        return "brand_safety";
    }

    @Override
    public GuardrailResult check(Ticket ticket, TicketTriage triage) {
        String reply = triage.reply().toLowerCase(Locale.ROOT);
        for (String phrase : banned) {
            if (reply.contains(phrase)) {
                return new GuardrailResult(name(), Verdict.VIOLATION, "reply contains \"" + phrase + "\"");
            }
        }
        return GuardrailResult.pass(name());
    }
}

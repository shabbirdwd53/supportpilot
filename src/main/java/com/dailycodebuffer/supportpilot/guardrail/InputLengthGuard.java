package com.dailycodebuffer.supportpilot.guardrail;

import com.dailycodebuffer.supportpilot.ticket.Ticket;

/** Cheapest guardrail there is: huge inputs cost money and are a common way to smuggle instructions. */
public class InputLengthGuard implements InputGuardrail {

    private final int maxChars;

    public InputLengthGuard(int maxChars) {
        this.maxChars = maxChars;
    }

    @Override
    public String name() {
        return "input_length";
    }

    @Override
    public Outcome check(Ticket ticket) {
        int length = ticket.message().length();
        if (length > maxChars) {
            return new Outcome(ticket, new GuardrailResult(name(), Verdict.BLOCKED,
                    "message is " + length + " chars, limit is " + maxChars));
        }
        return Outcome.pass(name(), ticket);
    }
}

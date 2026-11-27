package com.dailycodebuffer.supportpilot.guardrail;

import com.dailycodebuffer.supportpilot.ticket.Category;
import com.dailycodebuffer.supportpilot.ticket.Priority;
import com.dailycodebuffer.supportpilot.ticket.Ticket;
import com.dailycodebuffer.supportpilot.ticket.TicketTriage;

/**
 * Security tickets always go to a human at URGENT priority. The model decides the category;
 * code makes sure that decision has the right consequences.
 */
public class EscalationPolicyGuard implements OutputGuardrail {

    @Override
    public String name() {
        return "escalation_policy";
    }

    @Override
    public GuardrailResult check(Ticket ticket, TicketTriage triage) {
        if (triage.category() == Category.SECURITY && (!triage.escalate() || triage.priority() != Priority.URGENT)) {
            return new GuardrailResult(name(), Verdict.VIOLATION, "SECURITY ticket must be escalated at URGENT");
        }
        return GuardrailResult.pass(name());
    }
}

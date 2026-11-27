package com.dailycodebuffer.supportpilot.guardrail;

import com.dailycodebuffer.supportpilot.ticket.Ticket;
import com.dailycodebuffer.supportpilot.ticket.TicketTriage;

/**
 * Runs AFTER the model, on its structured output. Checks business rules against facts we own
 * (the order from our database), never against what the customer claimed.
 */
public interface OutputGuardrail {

    String name();

    GuardrailResult check(Ticket ticket, TicketTriage triage);
}

package com.dailycodebuffer.supportpilot.ticket;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * What the model must return. Structured output is the contract that makes guardrails
 * and evals possible: you can't assert on a paragraph, you can assert on a field.
 */
public record TicketTriage(
        @NotNull Category category,
        @NotNull Priority priority,
        @JsonPropertyDescription("true when a human agent must take over (security, legal threats, enterprise outages)")
        boolean escalate,
        @NotNull @Valid RefundDecision refund,
        @JsonPropertyDescription("customer-facing reply, under 120 words")
        @NotBlank @Size(max = 1200) String reply) {

    public TicketTriage withReply(String newReply) {
        return new TicketTriage(category, priority, escalate, refund, newReply);
    }
}

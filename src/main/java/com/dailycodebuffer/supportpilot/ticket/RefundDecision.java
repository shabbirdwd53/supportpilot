package com.dailycodebuffer.supportpilot.ticket;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public record RefundDecision(
        @JsonPropertyDescription("true only if the refund policy allows a refund for this ticket") boolean eligible,
        @JsonPropertyDescription("refund amount in the order currency, 0 when not eligible") BigDecimal amount,
        @JsonPropertyDescription("one short sentence citing the policy rule used") String reason) {

    public static RefundDecision none(String reason) {
        return new RefundDecision(false, BigDecimal.ZERO, reason);
    }
}

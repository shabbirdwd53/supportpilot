package com.dailycodebuffer.supportpilot.ticket;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Order context comes from OUR database, not from the customer.
 * That's what lets guardrails check refunds against facts instead of trusting the model.
 *
 * @param daysSinceDelivery null when the order hasn't been delivered yet
 */
public record Order(@NotBlank String orderId, @PositiveOrZero BigDecimal total, Integer daysSinceDelivery) {
}

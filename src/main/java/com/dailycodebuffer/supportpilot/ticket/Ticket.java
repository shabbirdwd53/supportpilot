package com.dailycodebuffer.supportpilot.ticket;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** An incoming support ticket. The message is untrusted user input; everything else is ours. */
public record Ticket(@NotNull CustomerTier customerTier, @NotBlank String message, @Valid Order order) {

    public Ticket withMessage(String newMessage) {
        return new Ticket(customerTier, newMessage, order);
    }
}

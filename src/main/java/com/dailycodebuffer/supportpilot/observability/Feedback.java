package com.dailycodebuffer.supportpilot.observability;

import java.time.Instant;

import jakarta.validation.constraints.NotNull;

/** A support agent's verdict on an AI reply. Thumbs-down feedback becomes tomorrow's eval case. */
public record Feedback(String traceId, @NotNull Rating rating, String comment, Instant at) {

    public enum Rating { UP, DOWN }
}

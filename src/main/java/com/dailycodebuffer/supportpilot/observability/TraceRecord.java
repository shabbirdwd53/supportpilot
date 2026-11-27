package com.dailycodebuffer.supportpilot.observability;

import java.time.Instant;

import com.dailycodebuffer.supportpilot.ticket.SupportResponse;
import com.dailycodebuffer.supportpilot.ticket.Ticket;

/** One line in data/traces.jsonl. The ticket is the redacted one, so traces are safe to keep and share. */
public record TraceRecord(Instant at, Ticket ticket, SupportResponse response) {
}

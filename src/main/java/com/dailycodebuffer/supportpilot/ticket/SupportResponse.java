package com.dailycodebuffer.supportpilot.ticket;

import java.util.List;

import com.dailycodebuffer.supportpilot.guardrail.GuardrailResult;

/**
 * What the API returns, what the trace store records and what the evals grade.
 *
 * @param blocked            an input guardrail refused the ticket; the model was never called
 * @param fallback           the safe canned reply was used (blocked, or the model couldn't produce a compliant answer)
 * @param firstPassCompliant the model's FIRST answer passed every output guardrail, with no repair needed
 * @param attempts           model calls made (1 = clean, 2+ = repaired)
 */
public record SupportResponse(
        String traceId,
        TicketTriage triage,
        boolean blocked,
        boolean fallback,
        boolean firstPassCompliant,
        int attempts,
        List<GuardrailResult> guardrails,
        String promptVersion,
        String model,
        long latencyMs,
        int tokens,
        String error) {
}

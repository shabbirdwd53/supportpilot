package com.dailycodebuffer.supportpilot.evals;

import java.math.BigDecimal;
import java.util.List;

import com.dailycodebuffer.supportpilot.ticket.Category;
import com.dailycodebuffer.supportpilot.ticket.Priority;
import com.dailycodebuffer.supportpilot.ticket.Ticket;

/**
 * One row of the eval dataset: an input and what "correct" means for it.
 * Every field in {@link Expected} is optional; a grader only runs when its field is set.
 */
public record EvalCase(String id, String suite, List<String> tags, Ticket ticket, Expected expected,
                       String referenceReply, String source) {

    public record Expected(Category category, Priority priority, Boolean escalate, Boolean refundEligible,
                           BigDecimal refundAmount, Boolean blocked) {
    }

    public EvalCase withSuite(String newSuite) {
        return new EvalCase(id, newSuite, tags, ticket, expected, referenceReply, source);
    }
}

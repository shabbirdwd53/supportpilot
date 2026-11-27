package com.dailycodebuffer.supportpilot.evals;

import java.util.Optional;

import com.dailycodebuffer.supportpilot.ticket.SupportResponse;

/** Turns one (case, response) pair into one metric. Empty = this grader doesn't apply to this case. */
public interface Grader {

    String metric();

    Optional<GradeResult> grade(EvalCase evalCase, SupportResponse response);
}

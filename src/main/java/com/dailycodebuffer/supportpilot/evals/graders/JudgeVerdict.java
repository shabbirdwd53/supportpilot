package com.dailycodebuffer.supportpilot.evals.graders;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/** Reasoning comes before the score on purpose: the judge thinks, then decides. */
public record JudgeVerdict(
        @JsonPropertyDescription("2-3 sentences explaining the score against the rubric") String reasoning,
        @JsonPropertyDescription("integer from 1 to 5") Integer score) {
}

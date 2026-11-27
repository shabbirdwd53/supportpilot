package com.dailycodebuffer.supportpilot.guardrail;

public enum Verdict {
    /** Nothing to do. */
    PASS,
    /** Input was changed (e.g. PII masked) but the request continues. */
    MODIFIED,
    /** Input refused: the model is never called. */
    BLOCKED,
    /** Model output broke a rule. We repair it or fall back. */
    VIOLATION
}

package com.dailycodebuffer.supportpilot.guardrail;

/** One guardrail's decision, recorded on every response, trace and eval result. */
public record GuardrailResult(String guardrail, Verdict verdict, String detail) {

    public static GuardrailResult pass(String guardrail) {
        return new GuardrailResult(guardrail, Verdict.PASS, "");
    }

    public boolean failed() {
        return verdict == Verdict.BLOCKED || verdict == Verdict.VIOLATION;
    }
}

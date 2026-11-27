package com.dailycodebuffer.supportpilot.evals;

public record GradeResult(String metric, boolean passed, String detail) {

    public static GradeResult pass(String metric, String detail) {
        return new GradeResult(metric, true, detail);
    }

    public static GradeResult fail(String metric, String detail) {
        return new GradeResult(metric, false, detail);
    }
}

package com.dailycodebuffer.supportpilot.evals;

import java.util.List;

import com.dailycodebuffer.supportpilot.ticket.SupportResponse;

public record CaseResult(String caseId, String suite, int trial, SupportResponse response, List<GradeResult> grades) {

    public boolean passed() {
        return grades.stream().allMatch(GradeResult::passed);
    }

    public List<GradeResult> failures() {
        return grades.stream().filter(g -> !g.passed()).toList();
    }
}

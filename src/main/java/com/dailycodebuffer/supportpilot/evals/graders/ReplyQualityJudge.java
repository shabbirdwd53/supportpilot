package com.dailycodebuffer.supportpilot.evals.graders;

import java.util.Optional;

import com.dailycodebuffer.supportpilot.evals.EvalCase;
import com.dailycodebuffer.supportpilot.evals.GradeResult;
import com.dailycodebuffer.supportpilot.evals.Grader;
import com.dailycodebuffer.supportpilot.ticket.SupportResponse;
import com.dailycodebuffer.supportpilot.ticket.SupportService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

/**
 * LLM-as-judge for what code can't check: is the reply helpful, empathetic, accurate to the
 * policy and to the decision? Scored 1-5 against a written rubric and a reference reply.
 * Only runs on the golden suite; red-team cases are graded by code.
 */
public class ReplyQualityJudge implements Grader {

    private final ChatClient judge;
    private final String rubric;
    private final int passScore;

    public ReplyQualityJudge(ChatClient.Builder builder, String rubric, int passScore) {
        this.judge = builder.build();
        this.rubric = rubric;
        this.passScore = passScore;
    }

    @Override
    public String metric() {
        return "reply_quality";
    }

    @Override
    public Optional<GradeResult> grade(EvalCase evalCase, SupportResponse response) {
        if (!"golden".equals(evalCase.suite()) || response.blocked() || evalCase.referenceReply() == null) {
            return Optional.empty();
        }
        if (response.fallback()) {
            return Optional.of(GradeResult.fail(metric(), "fallback reply, the customer got no real answer"));
        }
        var t = response.triage();
        String input = """
                ## Ticket
                %s

                ## What a great reply covers (reference, written by a senior agent)
                %s

                ## Candidate to grade
                Decision: category=%s priority=%s escalate=%s refund_eligible=%s refund_amount=%s
                Reply:
                %s
                """.formatted(SupportService.renderForJudge(evalCase.ticket()), evalCase.referenceReply(),
                t.category(), t.priority(), t.escalate(), t.refund().eligible(), t.refund().amount(), t.reply());
        try {
            JudgeVerdict verdict = judge.prompt()
                    .messages(new SystemMessage(rubric), new UserMessage(input))
                    .call()
                    .entity(JudgeVerdict.class);
            if (verdict == null || verdict.score() == null) {
                return Optional.of(GradeResult.fail(metric(), "judge returned no score"));
            }
            return Optional.of(new GradeResult(metric(), verdict.score() >= passScore,
                    verdict.score() + "/5 " + verdict.reasoning()));
        } catch (RuntimeException e) {
            return Optional.of(GradeResult.fail(metric(), "judge error: " + e.getMessage()));
        }
    }
}

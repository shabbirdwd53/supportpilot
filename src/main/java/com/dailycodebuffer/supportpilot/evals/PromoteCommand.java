package com.dailycodebuffer.supportpilot.evals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.dailycodebuffer.supportpilot.SupportPilotProperties;
import com.dailycodebuffer.supportpilot.observability.Feedback;
import com.dailycodebuffer.supportpilot.observability.FeedbackController;
import com.dailycodebuffer.supportpilot.observability.TraceRecord;
import com.dailycodebuffer.supportpilot.observability.TraceStore;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * The feedback loop: ./run-evals.sh promote
 * Production traces that got a thumbs-down, fell back, or were blocked become CANDIDATE eval cases.
 * A human labels the "expected" block and moves them into golden.json or redteam.json in a PR.
 * Your dataset grows from real failures, not from what you imagined on day one.
 */
@Component
@ConditionalOnExpression("${supportpilot.evals.run:false} and '${supportpilot.evals.mode:pr}' == 'promote'")
public class PromoteCommand implements CommandLineRunner {

    private static final JsonMapper JSON = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();

    private final TraceStore traces;
    private final FeedbackController feedback;
    private final ConfigurableApplicationContext context;

    public PromoteCommand(TraceStore traces, FeedbackController feedback, SupportPilotProperties props,
                          ConfigurableApplicationContext context) {
        this.traces = traces;
        this.feedback = feedback;
        this.context = context;
    }

    @Override
    public void run(String... args) throws Exception {
        Map<String, Feedback> downVotes = feedback.all().stream()
                .filter(f -> f.rating() == Feedback.Rating.DOWN)
                .collect(Collectors.toMap(Feedback::traceId, f -> f, (a, b) -> b));
        Set<String> seen = new java.util.HashSet<>();
        List<EvalCase> candidates = new ArrayList<>();
        for (TraceRecord t : traces.all()) {
            var r = t.response();
            String reason = downVotes.containsKey(r.traceId()) ? "thumbs-down: " + downVotes.get(r.traceId()).comment()
                    : r.blocked() ? "blocked by input guardrail (attack or false positive?)"
                    : r.fallback() ? "fell back to a human: model never produced a compliant answer"
                    : !r.firstPassCompliant() ? "needed a guardrail repair" : null;
            if (reason == null || !seen.add(t.ticket().message())) {
                continue;
            }
            candidates.add(new EvalCase("cand-" + r.traceId().substring(0, 8), r.blocked() ? "redteam" : "golden",
                    List.of("from-production", "needs-label"), t.ticket(),
                    new EvalCase.Expected(null, null, null, null, null, null),
                    "TODO: write what a great reply covers", "trace " + r.traceId() + " - " + reason));
        }
        Path out = Path.of("evals/candidates/candidates-" + LocalDate.now() + ".json");
        Files.createDirectories(out.getParent());
        Files.writeString(out, JSON.writeValueAsString(candidates));
        System.out.printf("%n  %d candidate case(s) from %d trace(s) -> %s%n"
                + "  Label the 'expected' block, then move them into evals/datasets/ in a pull request.%n%n",
                candidates.size(), traces.all().size(), out);
        System.exit(SpringApplication.exit(context, () -> 0));
    }
}

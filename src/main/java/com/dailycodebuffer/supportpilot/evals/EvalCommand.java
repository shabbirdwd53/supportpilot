package com.dailycodebuffer.supportpilot.evals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.dailycodebuffer.supportpilot.SupportPilotProperties;
import com.dailycodebuffer.supportpilot.evals.graders.CodeGraders;
import com.dailycodebuffer.supportpilot.evals.graders.JudgeModels;
import com.dailycodebuffer.supportpilot.evals.graders.ReplyQualityJudge;
import com.dailycodebuffer.supportpilot.guardrail.OutputGuardrail;
import com.dailycodebuffer.supportpilot.ticket.PromptLibrary;
import com.dailycodebuffer.supportpilot.ticket.SupportService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * The CI entry point: ./run-evals.sh
 * Runs the datasets through the guarded pipeline, compares with the committed baseline,
 * writes eval-results/{results.json,summary.md} and exits 1 when a gate fails or a case regresses.
 * That exit code is what blocks the merge.
 */
@Component
@ConditionalOnExpression("${supportpilot.evals.run:false} and '${supportpilot.evals.mode:pr}' != 'promote'")
public class EvalCommand implements CommandLineRunner {

    static final List<String> METRIC_ORDER = List.of("attack_blocked", "final_safety", "schema_valid", "category",
            "priority", "escalation", "refund_decision", "first_pass_compliance", "reply_quality");

    private static final JsonMapper JSON = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();

    private final SupportService service;
    private final ChatClient.Builder chatClientBuilder;
    private final List<OutputGuardrail> outputGuardrails;
    private final PromptLibrary prompts;
    private final SupportPilotProperties props;
    private final Environment env;
    private final ConfigurableApplicationContext context;

    public EvalCommand(SupportService service, ChatClient.Builder chatClientBuilder, List<OutputGuardrail> outputGuardrails,
                       PromptLibrary prompts, SupportPilotProperties props, Environment env,
                       ConfigurableApplicationContext context) {
        this.service = service;
        this.chatClientBuilder = chatClientBuilder;
        this.outputGuardrails = outputGuardrails;
        this.prompts = prompts;
        this.props = props;
        this.env = env;
        this.context = context;
    }

    @Override
    public void run(String... args) throws Exception {
        var cfg = props.evals();
        Map<String, String> datasetVersions = new LinkedHashMap<>();
        List<EvalCase> cases = new ArrayList<>();
        for (Path path : cfg.datasets()) {
            Dataset d = Dataset.load(path);
            datasetVersions.put(d.name(), d.version());
            cases.addAll(d.cases());
        }

        String model = SupportServiceLabels.model(env);
        List<Grader> graders = new ArrayList<>(CodeGraders.all(outputGuardrails));
        String judgeLabel = "off";
        if (cfg.judgeEnabled()) {
            var judge = props.judge();
            graders.add(new ReplyQualityJudge(JudgeModels.builderFor(judge, chatClientBuilder,
                    env.getProperty("spring.ai.model.chat", "ollama")), prompts.judgeRubric(), judge.passScore()));
            judgeLabel = JudgeModels.label(judge, model);
        }

        String prompt = props.promptVersion();
        System.out.printf("%nSupportPilot evals: prompt %s on %s, judge %s, %d cases x %d trial(s), %s%n%n",
                prompt, model, judgeLabel, cases.size(), cfg.trials(), datasetVersions);

        var runner = new EvalRunner(service, graders, cfg.concurrency());
        List<CaseResult> results = runner.run(cases, prompt, cfg.trials(), r -> System.out.printf("  %s %-34s %5.1fs %s%n",
                r.passed() ? "✓" : "✗", r.caseId() + (cfg.trials() > 1 ? " #" + r.trial() : ""),
                r.response().latencyMs() / 1000.0,
                r.failures().stream().map(g -> g.metric() + ": " + g.detail()).findFirst().orElse("")));

        RunSummary run = EvalRunner.summarise(results, cfg.gates(), METRIC_ORDER, gitSha(), prompt, model, judgeLabel,
                datasetVersions, cfg.trials(), cfg.maxP95LatencyMs());

        Optional<RunSummary> baseline = loadBaseline(cfg.baseline());
        List<String> regressions = new ArrayList<>();
        List<String> fixed = new ArrayList<>();
        baseline.ifPresent(b -> run.casePassRate().forEach((id, rate) -> {
            Double before = b.casePassRate().get(id);
            if (before == null) {
                return; // new case: nothing to compare with yet
            }
            if (EvalRunner.isRegression(before, rate)) {
                regressions.add(id);
            } else if (EvalRunner.isFix(before, rate)) {
                fixed.add(id);
            }
        }));
        boolean passed = run.gatesPassed() && (!cfg.failOnRegression() || regressions.isEmpty());

        Files.createDirectories(cfg.outputDir());
        Files.writeString(cfg.outputDir().resolve("results.json"), JSON.writeValueAsString(run));
        String markdown = MarkdownReport.render(run, baseline, regressions, fixed, passed);
        Files.writeString(cfg.outputDir().resolve("summary.md"), markdown);
        Files.writeString(cfg.outputDir().resolve("gate.txt"), passed ? "passed" : "failed");
        System.out.print(MarkdownReport.console(run, regressions, passed));
        System.out.println("  report: " + cfg.outputDir().resolve("summary.md"));

        if (cfg.saveBaseline()) {
            Files.createDirectories(cfg.baseline().toAbsolutePath().getParent());
            Files.writeString(cfg.baseline(), JSON.writeValueAsString(run.withoutResults()));
            System.out.println("  baseline saved: " + cfg.baseline());
        }

        int exitCode = passed ? 0 : 1;
        System.exit(SpringApplication.exit(context, () -> exitCode));
    }

    private static Optional<RunSummary> loadBaseline(Path path) throws IOException {
        if (!Files.exists(path)) {
            return Optional.empty();
        }
        RunSummary baseline = JSON.readValue(Files.readString(path), RunSummary.class);
        if (baseline.casePassRate() == null || baseline.metrics() == null) {
            System.out.println("  ! " + path + " is in an old format, ignoring it. Re-create it with SAVE_BASELINE=true");
            return Optional.empty();
        }
        return Optional.of(baseline);
    }

    private String gitSha() {
        String sha = System.getenv("GITHUB_SHA");
        if (sha == null || sha.isBlank()) {
            sha = env.getProperty("GIT_SHA", "local");
        }
        return sha.length() > 7 ? sha.substring(0, 7) : sha;
    }
}

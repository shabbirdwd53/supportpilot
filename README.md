# SupportPilot: guardrails + evals + a CI/CD quality gate for an enterprise AI feature

SupportPilot is an AI support-ticket copilot for a fictional electronics store, ShopNest.
A ticket comes in, and the AI returns a **structured triage** (category, priority, escalate, refund decision) and a **draft reply**.

The point of the repo isn't the copilot. It's everything around it that makes it safe to ship:

```
                  ┌──────────────────────── SupportPilot request path ────────────────────────┐
 ticket ──► input guardrails ──► LLM (structured JSON) ──► output guardrails ──► reply + trace
            length · injection        Spring AI ChatClient      refund policy · escalation
            PII redaction             any provider              data leak (PII + canary) · brand
                 │ blocked                                           │ violation
                 ▼                                                   ▼
            safe fallback                         repair once with feedback ─► still bad? safe fallback + human

                  ┌──────────────────────── Delivery pipeline (GitHub Actions) ───────────────┐
 every push ─► ci.yml         unit tests, guardrail tests, red-team vs input guards, fake-model pipeline tests (no key, ~1 min)
 AI PR      ─► ai-evals.yml   golden + red-team datasets x3 trials on a real model, LLM judge,
                              thresholds + regression vs baseline ─► sticky PR comment ─► required check
 nightly    ─► nightly-evals  x5 trials on main, catches model drift and flaky cases, opens an issue
 production ─► traces.jsonl + /actuator/prometheus + agent feedback ─► ./run-evals.sh promote ─► new eval cases
```

Stack: **Java 21, Spring Boot 4.1, Spring AI 2.0**. Provider-agnostic: Ollama (local, free), OpenAI or Anthropic via `AI_PROVIDER`.

## Run it

```bash
# 1. Tests that need no model and no API key (what ci.yml runs)
./mvnw verify

# 2. The app (local model)
ollama pull qwen3.5:9b
./mvnw spring-boot:run
curl -s localhost:8080/api/tickets/triage -H 'Content-Type: application/json' -d @requests/refund.json | jq
curl -s localhost:8080/api/tickets/triage -H 'Content-Type: application/json' -d @requests/injection.json | jq
curl -s localhost:8080/api/tickets/triage -H 'Content-Type: application/json' -d @requests/fake-approval.json | jq

# 3. The eval suite (what ai-evals.yml runs)
export AI_PROVIDER=anthropic ANTHROPIC_API_KEY=sk-ant-...
export JUDGE_PROVIDER=anthropic JUDGE_MODEL=claude-sonnet-5-5
./run-evals.sh                          # prompt from application.yml, 3 trials, compares with evals/baseline.json
PROMPT_VERSION=v2 ./run-evals.sh        # evaluate another prompt
TRIALS=1 JUDGE=false ./run-evals.sh     # quick and cheap: code graders only
SAVE_BASELINE=true ./run-evals.sh       # promote this run to the baseline (commit evals/baseline.json)

# 4. Feedback loop
curl -s localhost:8080/api/tickets/<traceId>/feedback -H 'Content-Type: application/json' -d '{"rating":"DOWN","comment":"should have escalated"}'
./run-evals.sh promote                  # bad traces -> evals/candidates/candidates-<date>.json
```

Outputs: `eval-results/summary.md` (the PR comment), `eval-results/results.json` (every response and grade), `eval-results/gate.txt`.

## What's where

| Path | What it is |
|---|---|
| `ticket/SupportService.java` | The guarded pipeline: input guards, model call, output guards, repair loop, fallback |
| `ticket/TicketTriage.java` | The structured output contract (records + Bean Validation) |
| `guardrail/` | 3 input guardrails, 4 output guardrails, all plain Java with unit tests |
| `src/main/resources/prompts/support-v*.txt` | Versioned prompts. v1 = main, v2 = the "friendlier" PR, v3 = the fix |
| `src/main/resources/policy/refund-policy.md` | The business policy, shared by the prompt and the judge |
| `evals/datasets/golden.json` | 16 everyday tickets with expected triage + reference replies |
| `evals/datasets/redteam.json` | 12 attacks: 5 must be blocked at the door, 7 must pass through without anything unsafe shipping |
| `evals/baseline.json` | Known-good scores from main. PRs are compared with it |
| `evals/graders/` | Code graders + LLM-as-judge (`ReplyQualityJudge`) |
| `evals/EvalCommand.java` | The CI entry point: runs, compares, writes the report, exits 1 on failure |
| `observability/` | JSONL traces (PII-redacted), Micrometer metrics, feedback endpoint |
| `.github/workflows/` | `ci.yml`, `ai-evals.yml`, `nightly-evals.yml` |

## The gates (application.yml)

| Metric | Gate | Why |
|---|---|---|
| `attack_blocked` | 100% | Known attacks must be stopped before the model |
| `final_safety` | 100% | Nothing unsafe may reach a customer, ever |
| `schema_valid` | 95% | The model's first answer parses and validates |
| `category` / `priority` / `escalation` / `refund_decision` | 85 / 75 / 90 / 90% | Decision accuracy, with room for LLM noise |
| `first_pass_compliance` | 85% | How often the model was right *without* a guardrail stepping in |
| `reply_quality` | 80% | LLM judge score ≥ 4/5 |
| p95 latency | ≤ 20 s | |
| regressions | none | A case that mostly passed on the baseline and now mostly fails blocks the merge |

## Set up the pipeline on GitHub

1. Push the repo, add the `ANTHROPIC_API_KEY` repository secret.
2. Settings → Branches → protect `main` → require the status checks **`build-and-test`** (ci) and **`evals`** (ai-evals).
3. Replace `YOUR_GITHUB_USERNAME` in `.github/CODEOWNERS` so prompt, policy and dataset changes need an AI owner's review.
4. On main, run `SAVE_BASELINE=true ./run-evals.sh` and commit `evals/baseline.json`. Re-baseline whenever a PR deliberately changes behaviour.

## Real results (Claude Haiku 5.5 app, Claude Sonnet 5.5 judge, 3 trials, 2026-11-15)

| Prompt | Gate | Notes |
|---|---|---|
| v1 (main) | ✅ | Baseline. 4 flaky cases at 67% |
| v2 "be customer-obsessed" | ❌ | Every metric above its threshold, category even **up** 7 points, but 2 cases regressed: `refund-day-31-boundary` started escalating, `angry-customer-refund` jumped to HIGH |
| v3 "warm tone, strict decisions" | ✅ | No regressions, escalation 100% |

## Swapping in heavier tools

The guardrails are interfaces, so you can plug in industry tools without touching the pipeline:
Microsoft Presidio or a cloud DLP API for PII, an LLM classifier (Llama Guard, a moderation API) next to the regex injection guard,
NVIDIA NeMo Guardrails or Guardrails AI as a sidecar service, and Langfuse / Arize Phoenix / OpenTelemetry instead of `traces.jsonl`.

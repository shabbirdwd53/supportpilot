#!/usr/bin/env bash
# SupportPilot eval suite. Same command on your laptop and in CI.
#
#   ./run-evals.sh                         # golden + redteam, prompt from application.yml, compare with baseline
#   PROMPT_VERSION=v2 ./run-evals.sh       # evaluate a different prompt
#   TRIALS=1 ./run-evals.sh                # quick single pass (default is 3 trials per case)
#   SAVE_BASELINE=true ./run-evals.sh      # store this run as evals/baseline.json (commit it)
#   JUDGE=false ./run-evals.sh             # code graders only: fast and free
#   ./run-evals.sh promote                 # turn bad production traces into candidate eval cases
#
#   AI_PROVIDER=anthropic ANTHROPIC_API_KEY=... JUDGE_PROVIDER=anthropic ./run-evals.sh
set -euo pipefail
cd "$(dirname "$0")"
MODE="${1:-pr}"
./mvnw -q -DskipTests package
exec java --enable-native-access=ALL-UNNAMED -jar target/supportpilot-1.0.0.jar \
  --spring.main.web-application-type=none \
  --spring.main.banner-mode=off \
  --logging.level.root=WARN \
  --logging.level.io.netty=OFF \
  --supportpilot.observability.traces-enabled="$([ "$MODE" = promote ] && echo true || echo false)" \
  --supportpilot.evals.run=true \
  --supportpilot.evals.mode="${MODE}" \
  --supportpilot.evals.trials="${TRIALS:-3}" \
  --supportpilot.evals.concurrency="${CONCURRENCY:-4}" \
  --supportpilot.evals.judge-enabled="${JUDGE:-true}" \
  --supportpilot.evals.save-baseline="${SAVE_BASELINE:-false}"

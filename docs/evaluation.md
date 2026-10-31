# GitContext Evaluation Suite

GitContext includes automated benchmark suites for both retrieval performance and end-to-end grounded answer quality.

---

## Retrieval Evaluation

GitContext includes an automated retrieval evaluation benchmark runner comparing `VECTOR`, `KEYWORD`, `HYBRID` (RRF), and `HYBRID_RERANK` search strategies on JSONL benchmark datasets.

### Running Retrieval Evaluation (Windows)

From the `backend/` directory, launch the evaluation profile:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=eval"
```

### Overriding Parameters

You can override dataset, repository, cutoff ranks, variants, or output directory via `-Dspring-boot.run.arguments`:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=eval" "-Dspring-boot.run.arguments=--gitcontext.eval.dataset=../eval/datasets/smoke.jsonl --gitcontext.eval.repo=tarunipriya/gitcontext --gitcontext.eval.variants=VECTOR,KEYWORD,HYBRID --gitcontext.eval.ks=1,5,10"
```

### Sweeping Keyword Weights in Evaluation

For `HYBRID` and `HYBRID_RERANK` variants, you can append an optional `:kw=<number>` suffix to sweep the keyword reciprocal rank fusion weight (e.g. `HYBRID:kw=0.5`). When omitted, the default weight from `gitcontext.hybrid.keyword-weight` (1.0) is used.

To run an evaluation sweep across multiple keyword weights on Windows (PowerShell):

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=eval" "-Dspring-boot.run.arguments=--gitcontext.eval.variants=VECTOR,KEYWORD,HYBRID,HYBRID:kw=0.75,HYBRID:kw=0.5,HYBRID:kw=0.25,HYBRID_RERANK,HYBRID_RERANK:kw=0.5"
```

### Evaluation Results

Evaluation reports are written to [`eval/results/`](../eval/results/) in both formatted Markdown (`.md`) and structured JSON (`.json`) containing:
- Multi-cutoff metrics tables (`Hit@k`, `Recall@k`, `Precision@k`, `MRR`, `nDCG@k`)
- Latency percentile distributions (`p50`, `p95`, `p99`, `mean`)
- Missing ground-truth file path analysis
- Hit misses analysis with top retrieved passages

---

## Answer Quality Evaluation

GitContext includes an automated answer-quality evaluation benchmark runner that measures factual coverage via an LLM judge, citation recall, groundedness, refusal accuracy, and end-to-end latencies across benchmark datasets.

> **Prerequisites:** Requires a configured `LLM_API_KEY` in the `.env` file at repository root. The evaluation uses ~2 Gemini API calls per question (one for answer generation and one for LLM judge evaluation). A configurable delay between consecutive calls (`gitcontext.answer-eval.request-delay-ms`, default 13000 ms) respects free-tier Gemini rate limits (~5 requests/minute). This can be lowered (e.g. to 1000–4000 ms) for accounts with higher API quotas.

### Running Answer Quality Evaluation (Windows)

From the `backend/` directory, launch the answer evaluation profile:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=answer-eval"
```

### Gemini Free-Tier Limits & Multi-Day Evaluation

The Google Gemini free tier enforces two distinct rate limits:
1. **Per-minute limit:** 5 requests/minute (handled by the inter-request delay `request-delay-ms=13000`).
2. **Daily quota:** **20 requests/day PER MODEL**.

A complete evaluation run on [`eval/datasets/gitcontext.jsonl`](../eval/datasets/gitcontext.jsonl) (35 cases: 32 answerable + 3 unanswerable) requires **~67 total LLM calls** (32 answerable × 2 [answer generation + LLM judge] + 3 unanswerable [generation only]).

Because 67 calls exceed the single-model daily quota:
- **Separate Judge Model:** Distribute calls across two distinct models by assigning a separate model for evaluation (e.g. `gemini-3.5-flash-lite` for the judge while using `gemini-3.5-flash` for answer generation). Each model receives its own 20 requests/day allowance, allowing ~20 cases to be processed per day.
- **Fail Fast:** After 3 consecutive generation failures (`max-consecutive-failures=3`, typically triggered by HTTP 429 quota exhaustion), the runner stops immediately, logs an ERROR instructing you to resume after quota reset, saves progress to `eval/results/answers-<dataset>.checkpoint.jsonl`, and exits cleanly without writing an incomplete final report.
- **Checkpoint & Resume:** On the next day, re-running the command automatically resumes from the checkpoint (`resume=true`), skipping already completed cases and retrying any failed cases.
- **Final Report:** Once all cases finish successfully, the runner builds the final `answers-<dataset>-<timestamp>.md` and `.json` reports from the checkpoint and removes the checkpoint file.

### Running Across Days with Resume & Separate Judge Model (Windows)

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=answer-eval" "-Dspring-boot.run.arguments=--gitcontext.answer-eval.judge-model=gemini-3.5-flash-lite"
```

### Quick Smoke Run

For a quick smoke run limiting evaluation to 3 cases:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=answer-eval" "-Dspring-boot.run.arguments=--gitcontext.answer-eval.max-cases=3"
```

### Overriding Parameters

You can customize the benchmark dataset, target repository, judge options, and output directory via arguments:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=answer-eval" "-Dspring-boot.run.arguments=--gitcontext.answer-eval.dataset=../eval/datasets/spring-petclinic.jsonl --gitcontext.answer-eval.repo=spring-projects/spring-petclinic --gitcontext.answer-eval.max-cases=5 --gitcontext.answer-eval.reingest=false"
```

Answer evaluation reports are persisted to [`eval/results/answers-<dataset>-<timestamp>.md`](../eval/results/) and `.json`.

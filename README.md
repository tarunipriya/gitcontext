# GitContext

[![CI](https://github.com/tarunipriya/gitcontext/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/tarunipriya/gitcontext/actions/workflows/ci.yml)

A RAG developer copilot that answers questions about a GitHub repository using its source code, docs, commit history, PR discussions, and incident reports, with cited answers.

Built with **Java 25** and **Spring Boot**.

> 🚧 Under active development — Phase 6 (evaluation) complete.

## Results

Measured with the evaluation suite in `eval/` on two repositories: this repository and the held-out [`spring-projects/spring-petclinic`](https://github.com/spring-projects/spring-petclinic). Datasets were written before running and not tuned afterwards.

**Headline (default configuration: hybrid BM25 + vector, equal-weight RRF, no reranker):** 73–75% Recall@5 across both repositories at under 70 ms p50 retrieval latency; 92% answer fact coverage with 90.6% of answers citing an expected source.

### Retrieval (k = 5)

| Variant | gitcontext Recall@5 | gitcontext p50 | petclinic Recall@5 | petclinic p50 |
|---|---|---|---|---|
| Vector (BGE-small) | 0.750 | 25 ms | 0.613 | 25 ms |
| Keyword (Lucene BM25) | 0.531 | 11 ms | 0.649 | 13 ms |
| **Hybrid RRF (default)** | **0.750** | **69 ms** | **0.732** | **46 ms** |
| Hybrid RRF, keyword weight 0.5 | 0.828 | 64 ms | 0.685 | 38 ms |
| Hybrid + cross-encoder rerank | 0.813 | 3305 ms | 0.613 | 3010 ms |

gitcontext: 32 answerable questions at commit `1746672`; petclinic: 28 answerable questions at commit `500158f`. Questions cover code, docs, configuration and Git history (pull requests and commits).

**What the numbers show**
- **Hybrid is the robust choice.** Vector search is stronger on this repository, BM25 is stronger on petclinic (descriptive class names match question words), and hybrid matches or beats the best single retriever on both.
- **A tuned fusion weight did not generalize.** Lowering the keyword weight to 0.5 raised gitcontext Recall@5 from 0.750 to 0.828 but lowered petclinic from 0.732 to 0.685, so the default stays at 1.0.
- **The reranker is off by default.** `ms-marco-MiniLM-L-6-v2` is trained on web search, not code: it hurt petclinic (0.613 vs 0.732) and costs ~3 s per query on CPU.

### Answer quality (gitcontext, 35 questions)

Answer model `gemini-3.5-flash`, graded by a separate judge model `gemini-3.5-flash-lite` at temperature 0, commit `863d682`.

| Metric | Value |
|---|---|
| Mean fact coverage | 0.923 |
| Fully correct answers | 0.893 (25 of 28 graded) |
| Answers citing an expected source | 0.906 |
| Correct refusals on unanswerable questions | 1.000 (3 of 3) |
| False refusals on answerable questions | 0.125 (4 of 32) |
| Groundedness (judge) | 1.000 |
| End-to-end latency | p50 4.3 s, p95 11.7 s |

Incomplete answers occur on exactly the questions where hybrid retrieval missed the source, and most false refusals are Git-history questions, so answer quality is bounded by retrieval quality. Groundedness of 1.000 means the judge found no obvious unsupported claims; a small judge from the same model family can be lenient, so it is not proof of zero hallucination.

### Reproduce

Datasets: [`eval/datasets/`](eval/datasets/). Full reports with per-question results: [`eval/results/`](eval/results/) — `gitcontext-20261021-160519.md`, `spring-petclinic-20261021-163602.md`, `answers-gitcontext-20261023-153022.md`. Commands are in the [Evaluation](#evaluation) section below.

## Running Qdrant

GitContext uses Qdrant for vector similarity search. You can start the local Qdrant container with Docker Compose:

```bash
# Start Qdrant in the background
docker compose up -d

# Open the Qdrant web dashboard
# http://localhost:6333/dashboard

# Stop Qdrant when finished
docker compose down
```

## Incremental Re-indexing

GitContext reuses embeddings of unchanged chunks during repository updates to drastically reduce re-indexing time (e.g. from ~150 s down to a few seconds):

- **Content Hash Matching**: For every chunk, GitContext computes a deterministic SHA-256 hash `embedHash = sha256(modelId + "\n" + chunk.text)` stored in the Qdrant point payload.
- **Scroll & Cache Reuse**: On re-indexing, `VectorStore` scrolls existing repository points via Qdrant's `/points/scroll` API, transferring only necessary payload fields and cached vectors. Any chunk with a matching `embedHash` and valid vector dimension reuses its existing vector without invoking the embedding model.
- **Identical Index & Search Results**: All points (reused + newly embedded) are upserted with the current `runId`, and stale entries from older indexing runs or deleted files are pruned via `deleteStale(repo, runId)`. Vector store contents and query retrieval results are identical to a full re-index.
- **Configuration**: Incremental re-indexing is enabled by default via `gitcontext.indexing.reuse-embeddings=true`. Set to `false` in `application.yml` or via `--gitcontext.indexing.reuse-embeddings=false` to force full re-embedding. If scrolling Qdrant fails, GitContext logs a warning and automatically falls back to full embedding.

## Reranking

GitContext supports an optional second-stage cross-encoder reranker (`ms-marco-MiniLM-L-6-v2` running locally in Java via ONNX Runtime) to rescore candidate passages retrieved by hybrid search (RRF across dense vector and sparse BM25).

- **Opt-in**: Reranking is disabled by default (`gitcontext.rerank.enabled=false`) and can be enabled per search request via `?rerank=true`.
- **Benchmark Findings**: In local benchmarks across 5 code questions on CPU, reranking 30 candidates took 4,000–7,000 ms. Because `ms-marco-MiniLM-L-6-v2` is a general web-trained text model, it hurt ranking on 2 of 5 code questions, helped on 1, and tied on 2 compared to pure hybrid RRF.
- **Phase 6 result**: On the held-out spring-petclinic evaluation the reranker lowered Recall@5 from 0.732 to 0.613 at ~3 s per query, so it remains opt-in. A code-aware cross-encoder is future work.

## Answer Generation

GitContext provides grounded question answering with verified citations via the `POST /api/repos/{owner}/{name}/ask` endpoint.

### How `/ask` Works
1. **Hybrid Retrieval**: Runs Reciprocal Rank Fusion (RRF) across dense vector similarity (Qdrant) and sparse BM25 keyword matching (Apache Lucene), with optional test exclusion and cross-encoder reranking.
2. **Numbered Sources**: Retrieved chunks undergo overlap suppression, structural deduplication, and token budget enforcement, then are formatted into numbered reference passages (`[1]`, `[2]`, ...).
3. **Gemini Generation**: Dispatches prompts with strict persona guidelines and numbered sources to the Google Gemini REST API.
4. **Citation Verification**: Parses and validates all bracketed citation markers (e.g. `[1]`, `[1][2]`) against the provided source list, attaching file paths, line ranges, symbols, and GitHub URLs, while warning on ungrounded citations.

### Grounding Rules
- **Source-Only Answers**: The model answers strictly from provided sources; it does not assume or extrapolate external facts.
- **In-Text Bracketed Citations**: Every factual statement must cite its numbered source (e.g. `[1]`).
- **Partial Answering**: If sources partly answer a question, the model answers the supported portion with citations and explicitly states what is missing.
- **Strict Refusal**: If no relevant information is present in the indexed sources, the model returns: `"I couldn't find this in the indexed repository."`
- **Data Isolation**: Source content is treated strictly as data to prevent prompt injection.

### Model Benchmarks & Default Choice
In end-to-end repository benchmarking on `tarunipriya/gitcontext`:
- `gemini-3.8-flash` took **40–60 s** per query and frequently timed out under load.
- `gemini-3.5-flash` answered consistently in **~5–6 s** with equivalent answer quality and citation accuracy.

Because of this performance difference, `gemini-3.5-flash` is configured as the default model (`gitcontext.llm.model`), with `gemini-3.8-flash` configured as the fallback (`gitcontext.llm.fallback-model`). Read and connection timeouts are configurable via `gitcontext.llm.timeout-seconds` (default: 60s) with exponential retry backoff and automatic failover.

### Example Request (PowerShell)
```powershell
$body = @{
    question = "How does GitContext handle GitHub API rate limits?"
    mode = "HYBRID"
    excludeTests = $true
    maxSources = 8
} | ConvertTo-Json

$response = Invoke-RestMethod -Uri "http://localhost:8080/api/repos/tarunipriya/gitcontext/ask" `
    -Method Post `
    -ContentType "application/json" `
    -Body $body

$response.answer
$response.citations | Format-Table number, path, startLine, endLine, url
```

## Web UI

GitContext includes a React + TypeScript web frontend in `frontend/` featuring the Ask interface with grounded markdown answers, clickable verified citations, a retrieved sources panel, and latency telemetry.

### Running on Windows

1. **Start Qdrant**:
   ```powershell
   docker compose up -d
   ```
2. **Start the backend** (runs on port 8081):
   ```powershell
   cd backend
   .\mvnw.cmd spring-boot:run
   ```
3. **Start the frontend** (Vite dev server with `/api` proxy to port 8081):
   ```powershell
   cd frontend
   npm install
   npm run dev
   ```
4. **Open in browser**:
   Navigate to [http://localhost:5173](http://localhost:5173).

## Evaluation

GitContext includes an automated retrieval evaluation benchmark runner comparing `VECTOR`, `KEYWORD`, `HYBRID` (RRF), and `HYBRID_RERANK` search strategies on JSONL benchmark datasets.

### Running Retrieval Evaluation (Windows)

From the `backend/` directory, launch the evaluation profile:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=eval"
```

### Overriding Parameters

You can override dataset, repository, cutoff ranks, variants, or output directory via `-Dspring-boot.run.arguments`:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=eval" "-Dspring-boot.run.arguments=--gitcontext.eval.dataset=eval/datasets/smoke.jsonl --gitcontext.eval.repo=tarunipriya/gitcontext --gitcontext.eval.variants=VECTOR,KEYWORD,HYBRID --gitcontext.eval.ks=1,5,10"
```

### Sweeping Keyword Weights in Evaluation

For `HYBRID` and `HYBRID_RERANK` variants, you can append an optional `:kw=<number>` suffix to sweep the keyword reciprocal rank fusion weight (e.g. `HYBRID:kw=0.5`). When omitted, the default weight from `gitcontext.hybrid.keyword-weight` (1.0) is used.

To run an evaluation sweep across multiple keyword weights on Windows (PowerShell):

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=eval" "-Dspring-boot.run.arguments=--gitcontext.eval.variants=VECTOR,KEYWORD,HYBRID,HYBRID:kw=0.75,HYBRID:kw=0.5,HYBRID:kw=0.25,HYBRID_RERANK,HYBRID_RERANK:kw=0.5"
```

### Evaluation Results

Evaluation reports are written to `eval/results/` in both formatted Markdown (`.md`) and structured JSON (`.json`) containing:
- Multi-cutoff metrics tables (`Hit@k`, `Recall@k`, `Precision@k`, `MRR`, `nDCG@k`)
- Latency percentile distributions (`p50`, `p95`, `p99`, `mean`)
- Missing ground-truth file path analysis
- Hit misses analysis with top retrieved passages

### Answer Quality Evaluation

GitContext includes an automated answer-quality evaluation benchmark runner that measures factual coverage via an LLM judge, citation recall, groundedness, refusal accuracy, and end-to-end latencies across benchmark datasets.

> **Prerequisites:** Requires a configured `LLM_API_KEY` in the `.env` file at repository root. The evaluation uses ~2 Gemini API calls per question (one for answer generation and one for LLM judge evaluation). A configurable delay between consecutive calls (`gitcontext.answer-eval.request-delay-ms`, default 13000 ms) respects free-tier Gemini rate limits (~5 requests/minute). This can be lowered (e.g. to 1000–4000 ms) for accounts with higher API quotas.

#### Running Answer Quality Evaluation (Windows)

From the `backend/` directory, launch the answer evaluation profile:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=answer-eval"
```

#### Gemini Free-Tier Limits & Multi-Day Evaluation

The Google Gemini free tier enforces two distinct rate limits:
1. **Per-minute limit:** 5 requests/minute (handled by the inter-request delay `request-delay-ms=13000`).
2. **Daily quota:** **20 requests/day PER MODEL**.

A complete evaluation run on `eval/datasets/gitcontext.jsonl` (35 cases: 32 answerable + 3 unanswerable) requires **~67 total LLM calls** (32 answerable × 2 [answer generation + LLM judge] + 3 unanswerable [generation only]).

Because 67 calls exceed the single-model daily quota:
- **Separate Judge Model:** Distribute calls across two distinct models by assigning a separate model for evaluation (e.g. `gemini-3.5-flash-lite` for the judge while using `gemini-3.5-flash` for answer generation). Each model receives its own 20 requests/day allowance, allowing ~20 cases to be processed per day.
- **Fail Fast:** After 3 consecutive generation failures (`max-consecutive-failures=3`, typically triggered by HTTP 429 quota exhaustion), the runner stops immediately, logs an ERROR instructing you to resume after quota reset, saves progress to `eval/results/answers-<dataset>.checkpoint.jsonl`, and exits cleanly without writing an incomplete final report.
- **Checkpoint & Resume:** On the next day, re-running the command automatically resumes from the checkpoint (`resume=true`), skipping already completed cases and retrying any failed cases.
- **Final Report:** Once all cases finish successfully, the runner builds the final `answers-<dataset>-<timestamp>.md` and `.json` reports from the checkpoint and removes the checkpoint file.

#### Running Across Days with Resume & Separate Judge Model (Windows)

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=answer-eval" "-Dspring-boot.run.arguments=--gitcontext.answer-eval.judge-model=gemini-3.5-flash-lite"
```

#### Quick Smoke Run

For a quick smoke run limiting evaluation to 3 cases:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=answer-eval" "-Dspring-boot.run.arguments=--gitcontext.answer-eval.max-cases=3"
```

#### Overriding Parameters

You can customize the benchmark dataset, target repository, judge options, and output directory via arguments:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=answer-eval" "-Dspring-boot.run.arguments=--gitcontext.answer-eval.dataset=eval/datasets/spring-petclinic.jsonl --gitcontext.answer-eval.repo=spring-projects/spring-petclinic --gitcontext.answer-eval.max-cases=5 --gitcontext.answer-eval.reingest=false"
```

Answer evaluation reports are persisted to `eval/results/answers-<dataset>-<timestamp>.md` and `.json`.




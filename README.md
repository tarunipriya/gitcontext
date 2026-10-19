# GitContext

A RAG developer copilot that answers questions about a GitHub repository using its source code, docs, commit history, PR discussions, and incident reports, with cited answers.

Built with **Java 25** and **Spring Boot**.

> 🚧 Under active development.

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

## Reranking

GitContext supports an optional second-stage cross-encoder reranker (`ms-marco-MiniLM-L-6-v2` running locally in Java via ONNX Runtime) to rescore candidate passages retrieved by hybrid search (RRF across dense vector and sparse BM25).

- **Opt-in**: Reranking is disabled by default (`gitcontext.rerank.enabled=false`) and can be enabled per search request via `?rerank=true`.
- **Benchmark Findings**: In local benchmarks across 5 code questions on CPU, reranking 30 candidates took 4,000–7,000 ms. Because `ms-marco-MiniLM-L-6-v2` is a general web-trained text model, it hurt ranking on 2 of 5 code questions, helped on 1, and tied on 2 compared to pure hybrid RRF.
- **Future Outlook**: This design decision will be formally revisited during the Phase 6 evaluation suite, with consideration for a code-aware cross-encoder model.

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



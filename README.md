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


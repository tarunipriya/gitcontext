# GitContext — project guide for AI agents

## What this project is
GitContext ingests a GitHub repository (code, README/docs, commits, PRs, incident-labelled issues), chunks code by function/class/module using tree-sitter, embeds chunks into Qdrant, retrieves with hybrid search (vector + BM25 via Apache Lucene) plus a reranker, and generates answers with citations (file path, lines, commit, PR). It re-indexes only changed files when the repo updates. It is evaluated with Recall@K, retrieval precision, answer correctness, citation accuracy and latency.

## Pipeline
Indexing: ingestion -> chunking -> embedding -> index store (Qdrant + Lucene BM25)
Query: question -> hybrid retrieval -> rank fusion -> rerank -> LLM answer with citations

## Repo layout
- backend/ — Spring Boot application (Maven), base package io.github.tarunipriya.gitcontext
  - config/     — @ConfigurationProperties classes and Spring configuration
  - model/      — records shared across modules (Document, Chunk, etc.)
  - ingestion/  — clone repos and read commits (JGit), fetch PRs/issues (GitHub REST API)
  - chunking/   — AST-aware code chunking (tree-sitter), doc chunking by heading
  - indexing/   — embeddings, Qdrant store, Lucene BM25 index, stale-file sync
  - retrieval/  — hybrid search, rank fusion, reranker
  - generation/ — LLM client, prompt templates
  - api/        — REST controllers and request/response DTOs
- cli/        — command-line client (picocli, later phase)
- frontend/   — web UI (React, later phase)
- evaluation/ — test datasets, evaluation runner, results
- docs/       — architecture diagrams, screenshots

## Core design rule
All ingested content is normalised into one `Document` record (content + metadata: sourceType, path, url, author, timestamp, commitSha, prNumber, etc.). Downstream code only ever handles Documents and Chunks, never raw API responses.

## Tech stack
Java 25, Spring Boot, Maven (wrapper), JGit, Spring RestClient for GitHub API, tree-sitter Java bindings, LangChain4j (embeddings, LLM, Qdrant, reranking), Apache Lucene (BM25), JUnit 5, Mockito, AssertJ. Frontend: React (later).

## Coding conventions
- Java records for immutable data; constructor injection only (no field @Autowired).
- Small, single-responsibility classes; Javadoc on public classes and methods.
- Use java.nio.file.Path for all paths; the developer works on Windows, so code must be cross-platform.
- Config only via @ConfigurationProperties bound from application.yml; never hard-code secrets, tokens or URLs.
- Every class with logic gets JUnit 5 tests; mock all network calls (GitHub API, LLM) in tests.
- Use SLF4J logging, never System.out in application code.
- Conventional commit messages: feat:, fix:, test:, docs:, chore:, refactor:.

## How we work
- The project is built in phases and steps. Implement ONLY the step requested; do not jump ahead.
- If you add a dependency, add it to backend/pom.xml with a short comment saying why.
- After each step, summarise what was built, key design decisions and why, and how to test it on Windows.

## Commands (run from backend/)
- Build and test: .\mvnw.cmd test
- Run the app: .\mvnw.cmd spring-boot:run
- Health check: http://localhost:8080/actuator/health

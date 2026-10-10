# GitContext — project guide for Claude

## What this project is
GitContext ingests a GitHub repository (code, README/docs, commits, PRs, incident-labelled issues), chunks code by function/class/module using tree-sitter, embeds chunks into Qdrant, retrieves with hybrid search (vector + BM25) plus a cross-encoder reranker, and generates answers with citations (file path, lines, commit, PR). It re-indexes only changed files when the repo updates. It is evaluated with Recall@K, retrieval precision, answer correctness, citation accuracy and latency.

## Pipeline
Indexing: ingestion -> chunking -> embedding -> index store (Qdrant + BM25)
Query: question -> hybrid retrieval -> rerank -> LLM answer with citations

## Repo layout
- backend/app/ingestion/  — clone repos (GitPython), read commits, fetch PRs/issues (GitHub REST API via httpx)
- backend/app/chunking/   — AST-aware code chunking (tree-sitter), doc chunking by heading
- backend/app/indexing/   — embeddings, Qdrant store, BM25 index, stale-file sync
- backend/app/retrieval/  — hybrid search, rank fusion, reranker
- backend/app/generation/ — LLM client, prompt templates
- backend/app/models/     — Pydantic schemas shared across modules
- backend/app/config.py   — settings via pydantic-settings, loaded from .env
- backend/app/main.py     — FastAPI app
- backend/tests/          — pytest tests
- cli/                    — command-line client
- frontend/               — web UI (React, built in a later phase)
- evaluation/             — test datasets, eval scripts, results
- docs/                   — architecture diagrams, screenshots

## Core design rule
All ingested content is normalised into one `Document` model (content + metadata: source_type, path, url, author, timestamp, commit_sha, pr_number, etc.). Downstream modules only ever handle Documents and Chunks, never raw API responses.

## Tech stack
Python 3.11+, FastAPI, Pydantic v2, pydantic-settings, GitPython, httpx, tree-sitter, Qdrant, rank-bm25, sentence-transformers (embeddings + cross-encoder reranker), pytest. Frontend: React (later).

## Coding conventions
- Type hints everywhere; Pydantic models for any data crossing module boundaries.
- Small, single-purpose modules; docstrings on public functions and classes.
- Use pathlib for all paths; the developer works on Windows, so code must be cross-platform.
- Config only via backend/app/config.py; never hard-code secrets, tokens or URLs.
- Every module gets pytest tests; mock all network calls (GitHub API, LLM) in tests.
- Use the `logging` module (or rich for CLI output), never bare print() in library code.
- Conventional commit messages: feat:, fix:, test:, docs:, chore:, refactor:.

## How we work
- The project is built in phases and steps. Implement ONLY the step requested; do not jump ahead.
- If you add a dependency, add it to backend/requirements.txt.
- In every PR description, explain what was built, key design decisions and why, and how to test it locally on Windows.

## Commands
- Install: pip install -r backend/requirements.txt
- Test: pytest backend/tests -v

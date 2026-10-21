# GitContext Evaluation Framework

This directory houses the evaluation datasets, benchmark definitions, and ground-truth test cases used to evaluate GitContext retrieval quality, ranking effectiveness, answer correctness, and citation accuracy.

---

## Data Leakage Prevention

The `eval/` folder is **strictly excluded** from repository ingestion by default (configured via `gitcontext.ingestion.exclude-path-prefixes: ["eval/"]`).

### Why is this necessary?
If benchmark questions, expected answers, and ground-truth file mappings were ingested into GitContext:
- Ground-truth files would be embedded into Qdrant vector storage and indexed into Apache Lucene BM25 keyword indices.
- Retrieval queries matching benchmark questions would artificially retrieve the evaluation definitions themselves rather than the actual repository source code.
- Metrics such as Recall@K, Precision@K, HitRate@K, nDCG@K, and answer correctness would be artificially inflated (test-set contamination).

Root path prefix matching ensures that files in `eval/` (e.g. `eval/datasets/smoke.jsonl`) are excluded from ingestion while allowing legitimate code files named `eval` in nested source directories (such as `backend/src/main/java/.../evaluation/`) to be ingested normally.

---

## Dataset Schema (JSON Lines Format)

Datasets are stored in JSON Lines (`.jsonl`) files inside `eval/datasets/`. Each line represents a single self-contained evaluation case formatted as a valid JSON object.

- Lines starting with `//` are treated as comments and ignored.
- Blank lines are ignored.
- UTF-8 text encoding is enforced.
- Unknown JSON properties trigger strict validation errors.

### Schema Specification

| Field | Type | Required | Description |
|---|---|---|---|
| `id` | `String` | Yes | Unique identifier for the case within the dataset (e.g. `"ingest-001"`). Non-blank. |
| `question` | `String` | Yes | The natural language question a developer would ask. Non-blank. |
| `expectedPaths` | `List<String>` | Yes | Repository-relative canonical file paths or Git history source keys required to answer the question. Normalized automatically. Must be non-empty if `answerable=true`; must be empty if `answerable=false`. |
| `expectedFacts` | `List<String>` | Yes | Concise, atomic facts that must be present in the generated answer for correctness scoring. |
| `category` | `String` | No | Architectural or thematic domain (e.g. `"architecture"`, `"ingestion"`, `"chunking"`, `"retrieval"`). Defaults to `"general"` if omitted or blank. |
| `answerable` | `Boolean` | No | `true` if the question can be answered from the repository; `false` for negative/unanswerable test cases. Defaults to `true` if omitted. |

### Source Keys for Git History

In addition to source file paths, `expectedPaths` supports Git history entities via canonical source keys:

- **Pull Requests**: `"pr:<number>"` (e.g. `"pr:16"`, case-insensitive `"PR:16"`).
- **Issues**: `"issue:<number>"` (e.g. `"issue:3"`).
- **Commits**: `"commit:<sha>"` (e.g. `"commit:abc1234"`). Either full (40-hex) or short (minimum 7-hex) commit SHAs are allowed and are automatically normalized to `"commit:"` plus the first 7 lowercase hex characters.

### Example: Code File Question

```json
{"id": "chunk-ast-001", "question": "Which tree-sitter grammars are supported for AST chunking?", "expectedPaths": ["backend/src/main/java/io/github/tarunipriya/gitcontext/chunking/treesitter/CodeChunker.java", "backend/pom.xml"], "expectedFacts": ["Java, Python, JavaScript, TypeScript, and TSX grammars are supported", "Grammar artifacts are bundled via io.github.bonede tree-sitter dependencies"], "category": "chunking", "answerable": true}
```

### Example: Git History Question (Pull Request)

```json
{"id": "history-pr-001", "question": "Which pull request introduced support for AST-aware code chunking?", "expectedPaths": ["pr:16"], "expectedFacts": ["PR 16 added tree-sitter chunking support", "Introduced CodeChunker and grammars"], "category": "history", "answerable": true}
```

### Example: Commit Question (Full or Short SHA)

```json
{"id": "history-commit-001", "question": "Which commit added the initial Lucene BM25 index?", "expectedPaths": ["commit:4f7b2c9d0123456789abcdef0123456789abcdef"], "expectedFacts": ["Commit 4f7b2c9 created the KeywordIndex using Apache Lucene"], "category": "history", "answerable": true}
```

### Example: Unanswerable Case (`answerable=false`)

```json
{"id": "front-react-001", "question": "Where is the React frontend application code located?", "expectedPaths": [], "expectedFacts": ["The React frontend has not been implemented in this phase", "Only the Spring Boot backend is present"], "category": "general", "answerable": false}
```

---

## What `answerable=false` Means

An unanswerable test case evaluates the system's ability to **refuse gracefully** or indicate that the requested information is absent from the ingested repository.

- Retrieval expectations: `expectedPaths` must be empty (`[]`).
- Generation expectations: The LLM should decline to answer, declare that the context does not contain the answer, or cite that the component does not exist in the codebase, rather than hallucinating plausible components or APIs.

---

## Rules for Writing High-Quality Evaluation Cases

1. **Ask like a developer would:** Formulate realistic questions based on intent (e.g., *"How do I configure the cross-encoder reranker?"* or *"Where are git commits parsed during ingestion?"*), rather than synthetic mechanical keyword queries.
2. **Avoid identifier leakage in questions:** Do not copy exact file names or unique class names directly into the question when avoidable (e.g. avoid *"What does CodeAnalyzer.java do?"*; instead ask *"How does keyword search tokenize code identifiers?"*). This tests semantic retrieval rather than exact literal matching.
3. **List every file needed to answer:** Include all files necessary to construct a complete, correct answer in `expectedPaths`. If understanding an answer requires both an interface and its implementation, include both.
4. **Keep expected facts short and checkable:** Each item in `expectedFacts` should be an atomic, verifiable factual proposition (e.g., *"Defaults to 512 KB"* or *"Uses Reciprocal Rank Fusion with default k=60"*). This enables automated fact verification and LLM-as-a-judge correctness evaluation.

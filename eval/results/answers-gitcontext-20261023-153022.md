# Answer Quality Evaluation: gitcontext

- **Repository:** tarunipriya/gitcontext
- **Commit SHA:** 863d68267247acfcdf7e3110dc6bf9b19c620fe9
- **Answer model:** gemini-3.5-flash (35)
- **Judge model:** gemini-3.5-flash-lite
- **Started At:** 2026-10-23 15:30:22 UTC
- **Answerable Cases:** 32
- **Unanswerable Cases:** 3
- **Judged Cases:** 28
- **Judge Failures:** 0
- **Generation Failures:** 0

## Metrics

| Metric | Value |
|---|---|
| Mean Fact Coverage | 0.923 |
| Fully Correct Rate | 0.893 |
| Grounded Rate | 1.000 |
| Cited Any Expected Rate | 0.906 |
| Mean Cited-Expected Recall | 0.828 |
| Refusal Accuracy | 1.000 |
| False Refusal Rate | 0.125 |

## Latency

| Phase | p50 (ms) | p95 (ms) | mean (ms) |
|---|---|---|---|
| Retrieval | 88 | 244 | 106.7 |
| Generation | 4214 | 11630 | 5196.3 |
| Total | 4295 | 11705 | 5305.3 |

> [!NOTE]
> **Judge caveat:** The LLM judge uses the same model family as answer generation to evaluate its own answers (temperature 0). The evaluation may be lenient towards stylistic variations or subtle inaccuracies.

## Per-Case Results

| id | model | refused | fact coverage | grounded | cited expected |
|---|---|---|---|---|---|
| gc-001 | gemini-3.5-flash | no | 0.333 | yes | 0.500 |
| gc-002 | gemini-3.5-flash | no | 1.000 | yes | 1.000 |
| gc-003 | gemini-3.5-flash | no | 1.000 | yes | 1.000 |
| gc-004 | gemini-3.5-flash | no | 1.000 | yes | 1.000 |
| gc-005 | gemini-3.5-flash | no | 1.000 | yes | 1.000 |
| gc-006 | gemini-3.5-flash | no | 1.000 | yes | 0.000 |
| gc-007 | gemini-3.5-flash | no | 1.000 | yes | 1.000 |
| gc-008 | gemini-3.5-flash | no | 1.000 | yes | 1.000 |
| gc-009 | gemini-3.5-flash | no | 1.000 | yes | 1.000 |
| gc-010 | gemini-3.5-flash | no | 1.000 | yes | 1.000 |
| gc-011 | gemini-3.5-flash | no | 1.000 | yes | 1.000 |
| gc-012 | gemini-3.5-flash | no | 1.000 | yes | 0.500 |
| gc-013 | gemini-3.5-flash | no | 1.000 | yes | 1.000 |
| gc-014 | gemini-3.5-flash | no | 1.000 | yes | 1.000 |
| gc-015 | gemini-3.5-flash | no | 1.000 | yes | 1.000 |
| gc-016 | gemini-3.5-flash | yes | n/a | n/a | 1.000 |
| gc-017 | gemini-3.5-flash | no | 0.000 | yes | 0.500 |
| gc-018 | gemini-3.5-flash | no | 1.000 | yes | 1.000 |
| gc-019 | gemini-3.5-flash | no | 1.000 | yes | 1.000 |
| gc-020 | gemini-3.5-flash | no | 1.000 | yes | 1.000 |
| gc-021 | gemini-3.5-flash | yes | n/a | n/a | 0.500 |
| gc-022 | gemini-3.5-flash | no | 0.500 | yes | 1.000 |
| gc-023 | gemini-3.5-flash | no | 1.000 | yes | 1.000 |
| gc-024 | gemini-3.5-flash | no | 1.000 | yes | 1.000 |
| gc-025 | gemini-3.5-flash | no | 1.000 | yes | 1.000 |
| gc-026 | gemini-3.5-flash | no | 1.000 | yes | 1.000 |
| gc-027 | gemini-3.5-flash | no | 1.000 | yes | 1.000 |
| gc-028 | gemini-3.5-flash | yes | n/a | n/a | 0.000 |
| gc-029 | gemini-3.5-flash | no | 1.000 | yes | 1.000 |
| gc-030 | gemini-3.5-flash | no | 1.000 | yes | 1.000 |
| gc-031 | gemini-3.5-flash | no | 1.000 | yes | 0.500 |
| gc-032 | gemini-3.5-flash | yes | n/a | n/a | 0.000 |
| gc-033 | gemini-3.5-flash | yes | n/a | n/a | 0.000 |
| gc-034 | gemini-3.5-flash | yes | n/a | n/a | 0.000 |
| gc-035 | gemini-3.5-flash | yes | n/a | n/a | 0.000 |

## Problems

- **gc-001**: Incomplete fact coverage (33.3%)
- **gc-016**: False refusal (answerable question was refused)
- **gc-017**: Incomplete fact coverage (0.0%)
- **gc-021**: False refusal (answerable question was refused)
- **gc-022**: Incomplete fact coverage (50.0%)
- **gc-028**: False refusal (answerable question was refused)
- **gc-032**: False refusal (answerable question was refused)

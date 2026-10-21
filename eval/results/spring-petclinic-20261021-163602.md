# Retrieval Evaluation: spring-petclinic

- **Repository:** spring-projects/spring-petclinic
- **Commit SHA:** 500158f732419217507c7656904b8e6aa1bcc0d6
- **Started At:** 2026-10-21 16:36:02 UTC
- **Answerable Cases:** 28
- **Skipped Unanswerable Cases:** 3

## Metrics @ k=1

| Variant | Hit@1 | Recall@1 | Precision@1 | MRR | nDCG@1 |
|---|---|---|---|---|---|
| VECTOR | 0.321 | 0.226 | 0.321 | 0.424 | 0.321 |
| KEYWORD | 0.321 | 0.244 | 0.321 | 0.479 | 0.321 |
| HYBRID | 0.429 | 0.315 | 0.429 | 0.540 | 0.429 |
| HYBRID:kw=0.75 | 0.429 | 0.315 | 0.429 | 0.542 | 0.429 |
| HYBRID:kw=0.5 | 0.393 | 0.280 | 0.393 | 0.523 | 0.393 |
| HYBRID:kw=0.25 | 0.357 | 0.268 | 0.357 | 0.506 | 0.357 |
| HYBRID_RERANK | 0.357 | 0.321 | 0.357 | 0.501 | 0.357 |
| HYBRID_RERANK:kw=0.5 | 0.357 | 0.321 | 0.357 | 0.508 | 0.357 |

## Metrics @ k=3

| Variant | Hit@3 | Recall@3 | Precision@3 | MRR | nDCG@3 |
|---|---|---|---|---|---|
| VECTOR | 0.536 | 0.476 | 0.214 | 0.424 | 0.394 |
| KEYWORD | 0.607 | 0.560 | 0.250 | 0.479 | 0.461 |
| HYBRID | 0.571 | 0.530 | 0.238 | 0.540 | 0.471 |
| HYBRID:kw=0.75 | 0.607 | 0.548 | 0.238 | 0.542 | 0.478 |
| HYBRID:kw=0.5 | 0.607 | 0.577 | 0.262 | 0.523 | 0.484 |
| HYBRID:kw=0.25 | 0.607 | 0.577 | 0.262 | 0.506 | 0.479 |
| HYBRID_RERANK | 0.607 | 0.530 | 0.226 | 0.501 | 0.464 |
| HYBRID_RERANK:kw=0.5 | 0.643 | 0.565 | 0.238 | 0.508 | 0.482 |

## Metrics @ k=5

| Variant | Hit@5 | Recall@5 | Precision@5 | MRR | nDCG@5 |
|---|---|---|---|---|---|
| VECTOR | 0.643 | 0.613 | 0.164 | 0.424 | 0.455 |
| KEYWORD | 0.679 | 0.649 | 0.171 | 0.479 | 0.501 |
| HYBRID | 0.750 | 0.732 | 0.193 | 0.540 | 0.558 |
| HYBRID:kw=0.75 | 0.714 | 0.696 | 0.186 | 0.542 | 0.545 |
| HYBRID:kw=0.5 | 0.714 | 0.685 | 0.179 | 0.523 | 0.528 |
| HYBRID:kw=0.25 | 0.750 | 0.720 | 0.186 | 0.506 | 0.537 |
| HYBRID_RERANK | 0.679 | 0.613 | 0.164 | 0.501 | 0.504 |
| HYBRID_RERANK:kw=0.5 | 0.714 | 0.661 | 0.179 | 0.508 | 0.528 |

## Metrics @ k=10

| Variant | Hit@10 | Recall@10 | Precision@10 | MRR | nDCG@10 |
|---|---|---|---|---|---|
| VECTOR | 0.643 | 0.613 | 0.082 | 0.424 | 0.455 |
| KEYWORD | 0.750 | 0.720 | 0.093 | 0.479 | 0.523 |
| HYBRID | 0.821 | 0.786 | 0.104 | 0.540 | 0.578 |
| HYBRID:kw=0.75 | 0.821 | 0.786 | 0.104 | 0.542 | 0.578 |
| HYBRID:kw=0.5 | 0.821 | 0.786 | 0.104 | 0.523 | 0.566 |
| HYBRID:kw=0.25 | 0.857 | 0.821 | 0.107 | 0.506 | 0.575 |
| HYBRID_RERANK | 0.857 | 0.786 | 0.107 | 0.501 | 0.569 |
| HYBRID_RERANK:kw=0.5 | 0.821 | 0.768 | 0.107 | 0.508 | 0.570 |

## Latency

| Variant | p50 | p95 | p99 | mean (ms) | failed |
|---|---|---|---|---|---|
| VECTOR | 25 | 33 | 37 | 26.5 | 0 |
| KEYWORD | 13 | 53 | 53 | 20.2 | 0 |
| HYBRID | 46 | 85 | 89 | 54.5 | 0 |
| HYBRID:kw=0.75 | 37 | 81 | 82 | 45.3 | 0 |
| HYBRID:kw=0.5 | 38 | 78 | 80 | 45.3 | 0 |
| HYBRID:kw=0.25 | 38 | 85 | 96 | 48.3 | 0 |
| HYBRID_RERANK | 3010 | 4382 | 5263 | 3165.0 | 0 |
| HYBRID_RERANK:kw=0.5 | 2816 | 4595 | 4747 | 2999.2 | 0 |

## Missing Expected Paths

- None

## Misses at k=5

### VECTOR

- **pc-003**: Top-3 retrieved: `pr:2645`, `pr:2672`, `pr:2503`
- **pc-004**: Top-3 retrieved: `pr:2534`, `pr:2514`, `pr:2495`
- **pc-006**: Top-3 retrieved: `pr:2609`, `commit:3a61275`, `pr:2556`
- **pc-007**: Top-3 retrieved: `pr:2573`, `pr:2573`, `pr:2609`
- **pc-008**: Top-3 retrieved: `pr:2690`, `src/main/java/org/springframework/samples/petclinic/owner/Visit.java`, `commit:bcce267`
- **pc-012**: Top-3 retrieved: `src/main/java/org/springframework/samples/petclinic/owner/PetController.java`, `src/main/java/org/springframework/samples/petclinic/owner/PetController.java`, `src/test/java/org/springframework/samples/petclinic/owner/PetControllerTests.java`
- **pc-015**: Top-3 retrieved: `commit:cc46671`, `commit:f53e0d0`, `commit:f53e0d0`
- **pc-019**: Top-3 retrieved: `.github/workflows/deploy-and-test-cluster.yml`, `commit:317562a`, `commit:a3026bd`
- **pc-020**: Top-3 retrieved: `commit:1095a15`, `commit:4ecc4c3`, `commit:3c1ef72`
- **pc-024**: Top-3 retrieved: `commit:95f7e82`, `commit:3aa79e3`, `pr:2519`

### KEYWORD

- **pc-003**: Top-3 retrieved: `commit:cf35266`, `src/test/java/org/springframework/samples/petclinic/system/CrashControllerIntegrationTests.java`, `pr:2672`
- **pc-004**: Top-3 retrieved: `src/test/java/org/springframework/samples/petclinic/PetClinicIntegrationTests.java`, `src/main/java/org/springframework/samples/petclinic/vet/Vets.java`, `pr:2498`
- **pc-007**: Top-3 retrieved: `src/test/java/org/springframework/samples/petclinic/service/ClinicServiceTests.java`, `src/test/java/org/springframework/samples/petclinic/owner/PetControllerTests.java`, `src/test/java/org/springframework/samples/petclinic/owner/OwnerTests.java`
- **pc-015**: Top-3 retrieved: `src/test/java/org/springframework/samples/petclinic/PostgresIntegrationTests.java`, `commit:cc46671`, `src/test/java/org/springframework/samples/petclinic/service/ClinicServiceTests.java`
- **pc-017**: Top-3 retrieved: `src/test/java/org/springframework/samples/petclinic/MysqlTestApplication.java`, `src/main/resources/db/mysql/petclinic_db_setup_mysql.txt`, `README.md`
- **pc-019**: Top-3 retrieved: `src/main/java/org/springframework/samples/petclinic/PetClinicApplication.java`, `src/test/java/org/springframework/samples/petclinic/MysqlTestApplication.java`, `src/test/java/org/springframework/samples/petclinic/PostgresIntegrationTests.java`
- **pc-023**: Top-3 retrieved: `src/main/java/org/springframework/samples/petclinic/system/WebConfiguration.java`, `pr:2579`, `pr:2561`
- **pc-025**: Top-3 retrieved: `src/main/java/org/springframework/samples/petclinic/owner/PetController.java`, `src/main/java/org/springframework/samples/petclinic/system/WebConfiguration.java`, `src/test/java/org/springframework/samples/petclinic/owner/PetValidatorTests.java`
- **pc-026**: Top-3 retrieved: `src/test/java/org/springframework/samples/petclinic/model/ValidatorTests.java`, `src/main/java/org/springframework/samples/petclinic/owner/OwnerController.java`, `src/test/java/org/springframework/samples/petclinic/owner/OwnerControllerTests.java`

### HYBRID

- **pc-003**: Top-3 retrieved: `pr:2672`, `pr:2503`, `pr:2688`
- **pc-004**: Top-3 retrieved: `pr:2495`, `pr:2497`, `pr:2534`
- **pc-012**: Top-3 retrieved: `src/test/java/org/springframework/samples/petclinic/owner/PetControllerTests.java`, `src/main/java/org/springframework/samples/petclinic/owner/PetController.java`, `src/test/java/org/springframework/samples/petclinic/owner/PetValidatorTests.java`
- **pc-015**: Top-3 retrieved: `commit:cc46671`, `pr:2519`, `commit:a2c2ef9`
- **pc-019**: Top-3 retrieved: `commit:a3026bd`, `commit:a3026bd`, `commit:a3026bd`
- **pc-020**: Top-3 retrieved: `commit:3c1ef72`, `commit:a7439c7`, `commit:1095a15`
- **pc-025**: Top-3 retrieved: `src/main/java/org/springframework/samples/petclinic/owner/PetController.java`, `pr:2573`, `src/test/java/org/springframework/samples/petclinic/service/ClinicServiceTests.java`

### HYBRID:kw=0.75

- **pc-003**: Top-3 retrieved: `pr:2672`, `pr:2503`, `pr:2688`
- **pc-004**: Top-3 retrieved: `pr:2495`, `pr:2497`, `pr:2534`
- **pc-006**: Top-3 retrieved: `pr:2611`, `pr:2609`, `pr:2561`
- **pc-012**: Top-3 retrieved: `src/test/java/org/springframework/samples/petclinic/owner/PetControllerTests.java`, `src/main/java/org/springframework/samples/petclinic/owner/PetController.java`, `src/main/java/org/springframework/samples/petclinic/owner/PetController.java`
- **pc-015**: Top-3 retrieved: `commit:cc46671`, `pr:2519`, `commit:a2c2ef9`
- **pc-019**: Top-3 retrieved: `commit:a3026bd`, `commit:a3026bd`, `commit:a3026bd`
- **pc-020**: Top-3 retrieved: `commit:3c1ef72`, `commit:a7439c7`, `commit:1095a15`
- **pc-025**: Top-3 retrieved: `src/main/java/org/springframework/samples/petclinic/owner/PetController.java`, `pr:2573`, `src/test/java/org/springframework/samples/petclinic/service/ClinicServiceTests.java`

### HYBRID:kw=0.5

- **pc-003**: Top-3 retrieved: `pr:2672`, `pr:2503`, `pr:2688`
- **pc-004**: Top-3 retrieved: `pr:2534`, `pr:2495`, `pr:2497`
- **pc-006**: Top-3 retrieved: `pr:2611`, `pr:2609`, `pr:2556`
- **pc-012**: Top-3 retrieved: `src/test/java/org/springframework/samples/petclinic/owner/PetControllerTests.java`, `src/main/java/org/springframework/samples/petclinic/owner/PetController.java`, `src/main/java/org/springframework/samples/petclinic/owner/PetController.java`
- **pc-015**: Top-3 retrieved: `commit:cc46671`, `pr:2519`, `commit:a2c2ef9`
- **pc-019**: Top-3 retrieved: `commit:a3026bd`, `commit:a3026bd`, `commit:a3026bd`
- **pc-020**: Top-3 retrieved: `commit:3c1ef72`, `commit:1095a15`, `commit:a7439c7`
- **pc-025**: Top-3 retrieved: `pr:2573`, `src/main/java/org/springframework/samples/petclinic/owner/PetController.java`, `src/main/java/org/springframework/samples/petclinic/owner/PetController.java`

### HYBRID:kw=0.25

- **pc-003**: Top-3 retrieved: `pr:2672`, `pr:2503`, `pr:2688`
- **pc-004**: Top-3 retrieved: `pr:2534`, `pr:2495`, `pr:2497`
- **pc-008**: Top-3 retrieved: `src/main/java/org/springframework/samples/petclinic/owner/Visit.java`, `pr:2690`, `pr:2490`
- **pc-012**: Top-3 retrieved: `src/test/java/org/springframework/samples/petclinic/owner/PetControllerTests.java`, `src/main/java/org/springframework/samples/petclinic/owner/PetController.java`, `src/main/java/org/springframework/samples/petclinic/owner/PetController.java`
- **pc-015**: Top-3 retrieved: `commit:cc46671`, `pr:2519`, `commit:a2c2ef9`
- **pc-019**: Top-3 retrieved: `commit:a3026bd`, `commit:a3026bd`, `commit:a3026bd`
- **pc-020**: Top-3 retrieved: `commit:1095a15`, `commit:3c1ef72`, `commit:4ecc4c3`

### HYBRID_RERANK

- **pc-007**: Top-3 retrieved: `pr:2573`, `pr:2611`, `pr:2536`
- **pc-008**: Top-3 retrieved: `pr:2690`, `pr:2490`, `src/main/java/org/springframework/samples/petclinic/owner/Visit.java`
- **pc-009**: Top-3 retrieved: `pr:2631`, `commit:405cdc6`, `commit:c5af32d`
- **pc-010**: Top-3 retrieved: `commit:c7ee170`, `commit:5b0f037`, `src/main/java/org/springframework/samples/petclinic/owner/VisitController.java`
- **pc-012**: Top-3 retrieved: `src/main/java/org/springframework/samples/petclinic/owner/Pet.java`, `src/main/java/org/springframework/samples/petclinic/owner/PetController.java`, `src/main/java/org/springframework/samples/petclinic/owner/PetValidator.java`
- **pc-015**: Top-3 retrieved: `commit:a2c2ef9`, `commit:f53e0d0`, `commit:cc46671`
- **pc-016**: Top-3 retrieved: `commit:e0db9b1`, `pr:2573`, `pr:2595`
- **pc-017**: Top-3 retrieved: `commit:3b90fac`, `commit:3c1ef72`, `commit:edf4db2`
- **pc-019**: Top-3 retrieved: `commit:a3026bd`, `.github/workflows/deploy-and-test-cluster.yml`, `commit:317562a`

### HYBRID_RERANK:kw=0.5

- **pc-007**: Top-3 retrieved: `pr:2573`, `pr:2537`, `pr:2561`
- **pc-008**: Top-3 retrieved: `pr:2690`, `pr:2490`, `src/main/java/org/springframework/samples/petclinic/owner/Visit.java`
- **pc-010**: Top-3 retrieved: `commit:c7ee170`, `commit:5b0f037`, `src/main/java/org/springframework/samples/petclinic/owner/VisitController.java`
- **pc-012**: Top-3 retrieved: `src/main/java/org/springframework/samples/petclinic/owner/Pet.java`, `src/main/java/org/springframework/samples/petclinic/owner/PetValidator.java`, `src/main/java/org/springframework/samples/petclinic/owner/PetController.java`
- **pc-015**: Top-3 retrieved: `commit:a2c2ef9`, `commit:f53e0d0`, `commit:cc46671`
- **pc-016**: Top-3 retrieved: `commit:e0db9b1`, `pr:2573`, `pr:2595`
- **pc-017**: Top-3 retrieved: `commit:3b90fac`, `commit:3c1ef72`, `commit:edf4db2`
- **pc-019**: Top-3 retrieved: `commit:a3026bd`, `.github/workflows/deploy-and-test-cluster.yml`, `commit:317562a`


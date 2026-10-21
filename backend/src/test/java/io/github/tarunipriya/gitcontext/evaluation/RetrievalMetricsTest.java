package io.github.tarunipriya.gitcontext.evaluation;

import java.util.List;
import java.util.Set;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class RetrievalMetricsTest {

	private static final Offset<Double> EPSILON = Offset.offset(1e-9);

	@Nested
	@DisplayName("Path Normalization")
	class PathNormalizationTests {

		@Test
		@DisplayName("Converts Windows backslashes to forward slashes")
		void convertsBackslashes() {
			assertThat(RetrievalMetrics.normalizePath("src\\main\\java\\App.java"))
					.isEqualTo("src/main/java/App.java");
		}

		@Test
		@DisplayName("Strips single and repeated leading dot-slash prefixes")
		void stripsLeadingDotSlash() {
			assertThat(RetrievalMetrics.normalizePath("./src/main/App.java"))
					.isEqualTo("src/main/App.java");
			assertThat(RetrievalMetrics.normalizePath(".\\src\\main\\App.java"))
					.isEqualTo("src/main/App.java");
			assertThat(RetrievalMetrics.normalizePath("././src/main/App.java"))
					.isEqualTo("src/main/App.java");
		}

		@Test
		@DisplayName("Strips single and repeated leading forward slashes")
		void stripsLeadingSlash() {
			assertThat(RetrievalMetrics.normalizePath("/src/main/App.java"))
					.isEqualTo("src/main/App.java");
			assertThat(RetrievalMetrics.normalizePath("//src/main/App.java"))
					.isEqualTo("src/main/App.java");
			assertThat(RetrievalMetrics.normalizePath("/./src/main/App.java"))
					.isEqualTo("src/main/App.java");
		}

		@Test
		@DisplayName("Leaves relative parent segments intact")
		void leavesParentSegments() {
			assertThat(RetrievalMetrics.normalizePath("../src/App.java"))
					.isEqualTo("../src/App.java");
		}

		@Test
		@DisplayName("Handles root or dot-only paths without throwing")
		void handlesShortPaths() {
			assertThat(RetrievalMetrics.normalizePath("./")).isEmpty();
			assertThat(RetrievalMetrics.normalizePath("/")).isEmpty();
			assertThat(RetrievalMetrics.normalizePath("")).isEmpty();
		}

		@Test
		@DisplayName("Leaves Git history source keys unchanged")
		void leavesSourceKeysUnchanged() {
			assertThat(RetrievalMetrics.normalizePath("pr:16")).isEqualTo("pr:16");
			assertThat(RetrievalMetrics.normalizePath("issue:3")).isEqualTo("issue:3");
			assertThat(RetrievalMetrics.normalizePath("commit:abc1234")).isEqualTo("commit:abc1234");
		}

		@Test
		@DisplayName("Rejects null path with NullPointerException")
		void rejectsNullPath() {
			assertThatNullPointerException()
					.isThrownBy(() -> RetrievalMetrics.normalizePath(null))
					.withMessageContaining("path must not be null");
		}
	}

	@Nested
	@DisplayName("Hand-Computed Metric Scenarios")
	class HandComputedScenarios {

		@Test
		@DisplayName("Relevant item at rank 1 (hitRate=1, recall=1, precision=1/k, RR=1, nDCG=1)")
		void relevantItemAtRank1() {
			List<String> retrieved = List.of("src/A.java", "src/B.java", "src/C.java");
			Set<String> relevant = Set.of("src/A.java");

			// At k = 1
			assertThat(RetrievalMetrics.hitRateAtK(retrieved, relevant, 1)).isCloseTo(1.0, EPSILON);
			assertThat(RetrievalMetrics.recallAtK(retrieved, relevant, 1)).isCloseTo(1.0, EPSILON);
			assertThat(RetrievalMetrics.precisionAtK(retrieved, relevant, 1)).isCloseTo(1.0, EPSILON);
			assertThat(RetrievalMetrics.reciprocalRank(retrieved, relevant)).isCloseTo(1.0, EPSILON);
			assertThat(RetrievalMetrics.ndcgAtK(retrieved, relevant, 1)).isCloseTo(1.0, EPSILON);

			// At k = 3: precision = 1 / 3 = 0.3333333333
			assertThat(RetrievalMetrics.hitRateAtK(retrieved, relevant, 3)).isCloseTo(1.0, EPSILON);
			assertThat(RetrievalMetrics.recallAtK(retrieved, relevant, 3)).isCloseTo(1.0, EPSILON);
			assertThat(RetrievalMetrics.precisionAtK(retrieved, relevant, 3)).isCloseTo(1.0 / 3.0, EPSILON);
			assertThat(RetrievalMetrics.reciprocalRank(retrieved, relevant)).isCloseTo(1.0, EPSILON);
			assertThat(RetrievalMetrics.ndcgAtK(retrieved, relevant, 3)).isCloseTo(1.0, EPSILON);
		}

		@Test
		@DisplayName("Relevant item at rank 3 (below k=2, included at k=3)")
		void relevantItemAtRank3() {
			List<String> retrieved = List.of("src/X.java", "src/Y.java", "src/Target.java", "src/Z.java");
			Set<String> relevant = Set.of("src/Target.java");

			// At k = 2 (not yet in top-k)
			assertThat(RetrievalMetrics.hitRateAtK(retrieved, relevant, 2)).isCloseTo(0.0, EPSILON);
			assertThat(RetrievalMetrics.recallAtK(retrieved, relevant, 2)).isCloseTo(0.0, EPSILON);
			assertThat(RetrievalMetrics.precisionAtK(retrieved, relevant, 2)).isCloseTo(0.0, EPSILON);
			assertThat(RetrievalMetrics.reciprocalRank(retrieved, relevant)).isCloseTo(1.0 / 3.0, EPSILON);
			assertThat(RetrievalMetrics.ndcgAtK(retrieved, relevant, 2)).isCloseTo(0.0, EPSILON);

			// At k = 3 (item is at 1-based rank 3)
			// DCG@3 = 1 / log2(3 + 1) = 1 / log2(4) = 1 / 2 = 0.5
			// IDCG@3 = 1 / log2(1 + 1) = 1 / log2(2) = 1.0
			// nDCG@3 = 0.5 / 1.0 = 0.5
			assertThat(RetrievalMetrics.hitRateAtK(retrieved, relevant, 3)).isCloseTo(1.0, EPSILON);
			assertThat(RetrievalMetrics.recallAtK(retrieved, relevant, 3)).isCloseTo(1.0, EPSILON);
			assertThat(RetrievalMetrics.precisionAtK(retrieved, relevant, 3)).isCloseTo(1.0 / 3.0, EPSILON);
			assertThat(RetrievalMetrics.reciprocalRank(retrieved, relevant)).isCloseTo(1.0 / 3.0, EPSILON);
			assertThat(RetrievalMetrics.ndcgAtK(retrieved, relevant, 3)).isCloseTo(0.5, EPSILON);
		}

		@Test
		@DisplayName("Relevant item not present in retrieved list (all metrics 0.0)")
		void relevantItemNotPresent() {
			List<String> retrieved = List.of("src/X.java", "src/Y.java");
			Set<String> relevant = Set.of("src/Missing.java");

			assertThat(RetrievalMetrics.hitRateAtK(retrieved, relevant, 2)).isCloseTo(0.0, EPSILON);
			assertThat(RetrievalMetrics.recallAtK(retrieved, relevant, 2)).isCloseTo(0.0, EPSILON);
			assertThat(RetrievalMetrics.precisionAtK(retrieved, relevant, 2)).isCloseTo(0.0, EPSILON);
			assertThat(RetrievalMetrics.reciprocalRank(retrieved, relevant)).isCloseTo(0.0, EPSILON);
			assertThat(RetrievalMetrics.ndcgAtK(retrieved, relevant, 2)).isCloseTo(0.0, EPSILON);
		}

		@Test
		@DisplayName("Multiple relevant items with mixed positions")
		void multipleRelevantItems() {
			// retrieved: [A (rel), X (non-rel), B (rel), Y (non-rel), C (rel)]
			List<String> retrieved = List.of("src/A.java", "src/X.java", "src/B.java", "src/Y.java", "src/C.java");
			Set<String> relevant = Set.of("src/A.java", "src/B.java", "src/C.java");

			// Top 3 contains A and B (2 out of 3 relevant documents)
			// hitRate@3 = 1.0
			// recall@3 = 2 / 3 = 0.6666666667
			// precision@3 = 2 / 3 = 0.6666666667
			// reciprocalRank = 1 / 1 = 1.0
			//
			// DCG@3:
			//   Rank 1 ("src/A.java"): 1.0 / log2(2) = 1.0
			//   Rank 2 ("src/X.java"): 0.0
			//   Rank 3 ("src/B.java"): 1.0 / log2(4) = 0.5
			//   DCG@3 = 1.0 + 0.5 = 1.5
			//
			// IDCG@3 (idealCount = min(k=3, |relevant|=3) = 3):
			//   Rank 1: 1.0 / log2(2) = 1.0
			//   Rank 2: 1.0 / log2(3) = 1.0 / 1.584962500721156 ≈ 0.6309297535714574
			//   Rank 3: 1.0 / log2(4) = 0.5
			//   IDCG@3 = 1.0 + 0.6309297535714574 + 0.5 = 2.1309297535714575
			//
			// nDCG@3 = 1.5 / 2.1309297535714575 ≈ 0.7039180907153957
			double expectedNdcg = 1.5 / (1.5 + (1.0 / (Math.log(3) / Math.log(2))));

			assertThat(RetrievalMetrics.hitRateAtK(retrieved, relevant, 3)).isCloseTo(1.0, EPSILON);
			assertThat(RetrievalMetrics.recallAtK(retrieved, relevant, 3)).isCloseTo(2.0 / 3.0, EPSILON);
			assertThat(RetrievalMetrics.precisionAtK(retrieved, relevant, 3)).isCloseTo(2.0 / 3.0, EPSILON);
			assertThat(RetrievalMetrics.reciprocalRank(retrieved, relevant)).isCloseTo(1.0, EPSILON);
			assertThat(RetrievalMetrics.ndcgAtK(retrieved, relevant, 3)).isCloseTo(expectedNdcg, EPSILON);
		}

		@Test
		@DisplayName("Deduplicates multiple chunks from same file preserving first occurrence")
		void deduplicatesRetrieved() {
			// Chunks: A at rank 1, A duplicate at rank 2, B at rank 3, A duplicate at rank 4
			// Deduplicated order: [A, B]
			List<String> retrieved = List.of("src/A.java", "src/A.java", "src/B.java", "src/A.java");
			Set<String> relevant = Set.of("src/B.java");

			// After dedup, B is at rank 2
			// hitRate@2 = 1.0
			// recall@2 = 1.0 / 1.0 = 1.0
			// precision@2 = 1.0 / 2 = 0.5
			// reciprocalRank = 1.0 / 2 = 0.5
			// DCG@2 = 0 + 1.0 / log2(3) = 1.0 / log2(3)
			// IDCG@2 = 1.0 / log2(2) = 1.0
			// nDCG@2 = 1.0 / log2(3) ≈ 0.6309297535714574
			double expectedNdcg = 1.0 / (Math.log(3) / Math.log(2));

			assertThat(RetrievalMetrics.hitRateAtK(retrieved, relevant, 2)).isCloseTo(1.0, EPSILON);
			assertThat(RetrievalMetrics.recallAtK(retrieved, relevant, 2)).isCloseTo(1.0, EPSILON);
			assertThat(RetrievalMetrics.precisionAtK(retrieved, relevant, 2)).isCloseTo(0.5, EPSILON);
			assertThat(RetrievalMetrics.reciprocalRank(retrieved, relevant)).isCloseTo(0.5, EPSILON);
			assertThat(RetrievalMetrics.ndcgAtK(retrieved, relevant, 2)).isCloseTo(expectedNdcg, EPSILON);
		}

		@Test
		@DisplayName("Normalizes mixed path separators across retrieved and relevant sets")
		void mixedPathSeparators() {
			List<String> retrieved = List.of(
					".\\src\\model\\User.java",
					"/src/util/Helper.java",
					"src\\model\\User.java" // duplicate of first after normalization
			);
			Set<String> relevant = Set.of(
					"src/model/User.java",
					"./src/util/Helper.java"
			);

			// Both retrieved files match both relevant files!
			assertThat(RetrievalMetrics.hitRateAtK(retrieved, relevant, 2)).isCloseTo(1.0, EPSILON);
			assertThat(RetrievalMetrics.recallAtK(retrieved, relevant, 2)).isCloseTo(1.0, EPSILON);
			assertThat(RetrievalMetrics.precisionAtK(retrieved, relevant, 2)).isCloseTo(1.0, EPSILON);
			assertThat(RetrievalMetrics.reciprocalRank(retrieved, relevant)).isCloseTo(1.0, EPSILON);
			assertThat(RetrievalMetrics.ndcgAtK(retrieved, relevant, 2)).isCloseTo(1.0, EPSILON);
		}

		@Test
		@DisplayName("Cutoff k larger than retrieved list size is allowed")
		void cutoffKLargerThanList() {
			List<String> retrieved = List.of("src/A.java");
			Set<String> relevant = Set.of("src/A.java", "src/B.java");
			int k = 10;

			// hitRate@10 = 1.0
			// recall@10 = 1 / 2 = 0.5
			// precision@10 = 1 / 10 = 0.1 (divided by k, not by list size!)
			// reciprocalRank = 1.0
			//
			// DCG@10 = 1.0 / log2(2) = 1.0
			// IDCG@10 (idealCount = min(k=10, |relevant|=2) = 2):
			//   IDCG@10 = 1.0 / log2(2) + 1.0 / log2(3) = 1.0 + 0.6309297535714574 = 1.6309297535714574
			// nDCG@10 = 1.0 / 1.6309297535714574 ≈ 0.6131471927654585
			double idcg = 1.0 + (1.0 / (Math.log(3) / Math.log(2)));
			double expectedNdcg = 1.0 / idcg;

			assertThat(RetrievalMetrics.hitRateAtK(retrieved, relevant, k)).isCloseTo(1.0, EPSILON);
			assertThat(RetrievalMetrics.recallAtK(retrieved, relevant, k)).isCloseTo(0.5, EPSILON);
			assertThat(RetrievalMetrics.precisionAtK(retrieved, relevant, k)).isCloseTo(0.1, EPSILON);
			assertThat(RetrievalMetrics.reciprocalRank(retrieved, relevant)).isCloseTo(1.0, EPSILON);
			assertThat(RetrievalMetrics.ndcgAtK(retrieved, relevant, k)).isCloseTo(expectedNdcg, EPSILON);
		}
	}

	@Nested
	@DisplayName("nDCG Specific Properties")
	class NdcgTests {

		@Test
		@DisplayName("nDCG perfect ranking equals 1.0")
		void perfectRankingEqualsOne() {
			List<String> retrieved = List.of("src/A.java", "src/B.java", "src/C.java", "src/D.java");
			Set<String> relevant = Set.of("src/A.java", "src/B.java");

			assertThat(RetrievalMetrics.ndcgAtK(retrieved, relevant, 2)).isCloseTo(1.0, EPSILON);
			assertThat(RetrievalMetrics.ndcgAtK(retrieved, relevant, 4)).isCloseTo(1.0, EPSILON);
		}

		@Test
		@DisplayName("nDCG known non-perfect case with expected value computed in comment")
		void knownNonPerfectRanking() {
			// Setup:
			// Relevant set: {"docA", "docB"} (2 relevant documents)
			// Retrieved list: ["docX", "docA", "docY", "docB"] (rank 1 non-rel, rank 2 rel, rank 3 non-rel, rank 4 rel)
			// k = 3
			//
			// Top 3 items: ["docX", "docA", "docY"]
			//
			// DCG@3:
			//   Rank 1 ("docX"): rel = 0 -> contribution = 0.0
			//   Rank 2 ("docA"): rel = 1 -> contribution = 1.0 / log2(2 + 1) = 1.0 / log2(3) = ln(2) / ln(3) ≈ 0.6309297535714574
			//   Rank 3 ("docY"): rel = 0 -> contribution = 0.0
			//   DCG@3 = 0.6309297535714574
			//
			// IDCG@3 (ideal ranking places min(k=3, |relevant|=2) = 2 relevant items at ranks 1 and 2):
			//   Rank 1: rel = 1 -> contribution = 1.0 / log2(1 + 1) = 1.0 / log2(2) = 1.0
			//   Rank 2: rel = 1 -> contribution = 1.0 / log2(2 + 1) = 1.0 / log2(3) ≈ 0.6309297535714574
			//   IDCG@3 = 1.0 + (1.0 / log2(3)) ≈ 1.6309297535714574
			//
			// Expected nDCG@3 = DCG@3 / IDCG@3
			//                  = (1 / log2(3)) / (1 + 1 / log2(3))
			//                  = 1 / (log2(3) + 1)
			//                  ≈ 1 / 2.584962500721156
			//                  ≈ 0.3868528072345416
			List<String> retrieved = List.of("docX", "docA", "docY", "docB");
			Set<String> relevant = Set.of("docA", "docB");
			int k = 3;

			double log2_3 = Math.log(3) / Math.log(2);
			double expectedNdcg = 1.0 / (log2_3 + 1.0);

			double actualNdcg = RetrievalMetrics.ndcgAtK(retrieved, relevant, k);
			assertThat(actualNdcg).isCloseTo(expectedNdcg, EPSILON);
			assertThat(actualNdcg).isCloseTo(0.3868528072345416, EPSILON);
		}

		@Test
		@DisplayName("Empty retrieved list produces 0.0 nDCG")
		void emptyRetrievedYieldsZero() {
			List<String> retrieved = List.of();
			Set<String> relevant = Set.of("docA");

			assertThat(RetrievalMetrics.ndcgAtK(retrieved, relevant, 5)).isCloseTo(0.0, EPSILON);
		}
	}

	@Nested
	@DisplayName("Validation Error Cases")
	class ValidationTests {

		private final List<String> retrieved = List.of("src/A.java");
		private final Set<String> relevant = Set.of("src/A.java");

		@Test
		@DisplayName("k <= 0 throws IllegalArgumentException with clear message")
		void rejectsNonPositiveK() {
			assertThatIllegalArgumentException()
					.isThrownBy(() -> RetrievalMetrics.hitRateAtK(retrieved, relevant, 0))
					.withMessageContaining("k must be greater than 0, got: 0");

			assertThatIllegalArgumentException()
					.isThrownBy(() -> RetrievalMetrics.recallAtK(retrieved, relevant, -1))
					.withMessageContaining("k must be greater than 0, got: -1");

			assertThatIllegalArgumentException()
					.isThrownBy(() -> RetrievalMetrics.precisionAtK(retrieved, relevant, 0))
					.withMessageContaining("k must be greater than 0, got: 0");

			assertThatIllegalArgumentException()
					.isThrownBy(() -> RetrievalMetrics.ndcgAtK(retrieved, relevant, -5))
					.withMessageContaining("k must be greater than 0, got: -5");
		}

		@Test
		@DisplayName("Empty relevant set throws IllegalArgumentException with clear message")
		void rejectsEmptyRelevant() {
			Set<String> emptyRelevant = Set.of();

			assertThatIllegalArgumentException()
					.isThrownBy(() -> RetrievalMetrics.hitRateAtK(retrieved, emptyRelevant, 5))
					.withMessageContaining("relevant set must not be empty");

			assertThatIllegalArgumentException()
					.isThrownBy(() -> RetrievalMetrics.recallAtK(retrieved, emptyRelevant, 5))
					.withMessageContaining("relevant set must not be empty");

			assertThatIllegalArgumentException()
					.isThrownBy(() -> RetrievalMetrics.precisionAtK(retrieved, emptyRelevant, 5))
					.withMessageContaining("relevant set must not be empty");

			assertThatIllegalArgumentException()
					.isThrownBy(() -> RetrievalMetrics.reciprocalRank(retrieved, emptyRelevant))
					.withMessageContaining("relevant set must not be empty");

			assertThatIllegalArgumentException()
					.isThrownBy(() -> RetrievalMetrics.ndcgAtK(retrieved, emptyRelevant, 5))
					.withMessageContaining("relevant set must not be empty");
		}

		@Test
		@DisplayName("Null arguments throw NullPointerException via Objects.requireNonNull")
		void rejectsNullArguments() {
			assertThatNullPointerException()
					.isThrownBy(() -> RetrievalMetrics.hitRateAtK(null, relevant, 1))
					.withMessageContaining("retrieved list must not be null");

			assertThatNullPointerException()
					.isThrownBy(() -> RetrievalMetrics.hitRateAtK(retrieved, null, 1))
					.withMessageContaining("relevant set must not be null");

			assertThatNullPointerException()
					.isThrownBy(() -> RetrievalMetrics.reciprocalRank(null, relevant))
					.withMessageContaining("retrieved list must not be null");

			assertThatNullPointerException()
					.isThrownBy(() -> RetrievalMetrics.reciprocalRank(retrieved, null))
					.withMessageContaining("relevant set must not be null");
		}
	}
}

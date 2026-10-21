package io.github.tarunipriya.gitcontext.evaluation;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class MetricSummaryTest {

	private static final Offset<Double> EPSILON = Offset.offset(1e-9);

	@Nested
	@DisplayName("QueryMetrics Record")
	class QueryMetricsTests {

		@Test
		@DisplayName("QueryMetrics computes individual query metrics correctly via factory")
		void computeFactory() {
			List<String> retrieved = List.of("src/A.java", "src/B.java", "src/C.java");
			Set<String> relevant = Set.of("src/B.java");
			int k = 2;

			QueryMetrics qm = QueryMetrics.compute(retrieved, relevant, k);

			// Item B is at rank 2
			assertThat(qm.hitRateAtK()).isCloseTo(1.0, EPSILON);
			assertThat(qm.recallAtK()).isCloseTo(1.0, EPSILON);
			assertThat(qm.precisionAtK()).isCloseTo(0.5, EPSILON);
			assertThat(qm.reciprocalRank()).isCloseTo(0.5, EPSILON);

			double expectedNdcg = 1.0 / (Math.log(3) / Math.log(2));
			assertThat(qm.ndcgAtK()).isCloseTo(expectedNdcg, EPSILON);
		}
	}

	@Nested
	@DisplayName("MetricSummary Aggregation")
	class AggregationTests {

		@Test
		@DisplayName("Averages per-query metric values correctly for known values")
		void averagesKnownValues() {
			// Query 1: perfect retrieval
			QueryMetrics q1 = new QueryMetrics(1.0, 1.0, 0.5, 1.0, 1.0);
			// Query 2: completely missed
			QueryMetrics q2 = new QueryMetrics(0.0, 0.0, 0.0, 0.0, 0.0);
			// Query 3: partial hit at rank 2
			QueryMetrics q3 = new QueryMetrics(1.0, 0.5, 0.25, 0.5, 0.6);

			int k = 4;
			MetricSummary summary = MetricSummary.of(k, List.of(q1, q2, q3));

			assertThat(summary.queryCount()).isEqualTo(3);
			assertThat(summary.k()).isEqualTo(4);

			// Mean hit rate: (1.0 + 0.0 + 1.0) / 3 = 2 / 3
			assertThat(summary.meanHitRateAtK()).isCloseTo(2.0 / 3.0, EPSILON);

			// Mean recall: (1.0 + 0.0 + 0.5) / 3 = 1.5 / 3 = 0.5
			assertThat(summary.meanRecallAtK()).isCloseTo(0.5, EPSILON);

			// Mean precision: (0.5 + 0.0 + 0.25) / 3 = 0.75 / 3 = 0.25
			assertThat(summary.meanPrecisionAtK()).isCloseTo(0.25, EPSILON);

			// MRR: (1.0 + 0.0 + 0.5) / 3 = 1.5 / 3 = 0.5
			assertThat(summary.mrr()).isCloseTo(0.5, EPSILON);

			// Mean nDCG: (1.0 + 0.0 + 0.6) / 3 = 1.6 / 3 = 0.5333333333
			assertThat(summary.meanNdcgAtK()).isCloseTo(1.6 / 3.0, EPSILON);
		}

		@Test
		@DisplayName("MetricAggregator aggregates raw queries into MetricSummary")
		void metricAggregatorUtility() {
			MetricAggregator.QueryEvaluation eval1 = new MetricAggregator.QueryEvaluation(
					List.of("src/A.java", "src/B.java"),
					Set.of("src/A.java")
			);
			MetricAggregator.QueryEvaluation eval2 = new MetricAggregator.QueryEvaluation(
					List.of("src/X.java", "src/Y.java"),
					Set.of("src/Z.java")
			);

			// eval1 at k=2: hitRate=1.0, recall=1.0, precision=0.5, rr=1.0, ndcg=1.0
			// eval2 at k=2: hitRate=0.0, recall=0.0, precision=0.0, rr=0.0, ndcg=0.0
			MetricSummary summary = MetricAggregator.aggregateQueries(2, List.of(eval1, eval2));

			assertThat(summary.queryCount()).isEqualTo(2);
			assertThat(summary.k()).isEqualTo(2);
			assertThat(summary.meanHitRateAtK()).isCloseTo(0.5, EPSILON);
			assertThat(summary.meanRecallAtK()).isCloseTo(0.5, EPSILON);
			assertThat(summary.meanPrecisionAtK()).isCloseTo(0.25, EPSILON);
			assertThat(summary.mrr()).isCloseTo(0.5, EPSILON);
			assertThat(summary.meanNdcgAtK()).isCloseTo(0.5, EPSILON);
		}
	}

	@Nested
	@DisplayName("Validation Error Cases")
	class ValidationTests {

		@Test
		@DisplayName("k <= 0 throws IllegalArgumentException with clear message")
		void rejectsNonPositiveK() {
			List<QueryMetrics> metrics = List.of(new QueryMetrics(1.0, 1.0, 1.0, 1.0, 1.0));

			assertThatIllegalArgumentException()
					.isThrownBy(() -> MetricSummary.of(0, metrics))
					.withMessageContaining("k must be greater than 0, got: 0");

			assertThatIllegalArgumentException()
					.isThrownBy(() -> MetricAggregator.aggregateQueries(-1, List.of(
							new MetricAggregator.QueryEvaluation(List.of("A"), Set.of("A"))
					)))
					.withMessageContaining("k must be greater than 0, got: -1");
		}

		@Test
		@DisplayName("Empty collection throws IllegalArgumentException with clear message")
		void rejectsEmptyMetrics() {
			assertThatIllegalArgumentException()
					.isThrownBy(() -> MetricSummary.of(5, List.of()))
					.withMessageContaining("perQueryMetrics must not be empty");

			assertThatIllegalArgumentException()
					.isThrownBy(() -> MetricAggregator.aggregateQueries(5, List.of()))
					.withMessageContaining("queries collection must not be empty");
		}

		@Test
		@DisplayName("Null collections throw NullPointerException")
		void rejectsNullCollections() {
			assertThatNullPointerException()
					.isThrownBy(() -> MetricSummary.of(5, null))
					.withMessageContaining("perQueryMetrics must not be null");

			assertThatNullPointerException()
					.isThrownBy(() -> MetricAggregator.aggregateQueries(5, null))
					.withMessageContaining("queries collection must not be null");
		}

		@Test
		@DisplayName("Collection containing null element throws NullPointerException")
		void rejectsNullElement() {
			List<QueryMetrics> listWithNull = new ArrayList<>();
			listWithNull.add(new QueryMetrics(1.0, 1.0, 1.0, 1.0, 1.0));
			listWithNull.add(null);

			assertThatNullPointerException()
					.isThrownBy(() -> MetricSummary.of(5, listWithNull))
					.withMessageContaining("QueryMetrics entry must not be null");
		}
	}
}

package io.github.tarunipriya.gitcontext.evaluation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.LongStream;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class LatencyStatsTest {

	private static final Offset<Double> EPSILON = Offset.offset(1e-9);

	@Nested
	@DisplayName("Statistical Computations")
	class StatisticalTests {

		@Test
		@DisplayName("Known list of 1..100 produces exact nearest-rank percentiles (p50=50, p95=95, p99=99)")
		void knownListOneToHundred() {
			List<Long> values = new ArrayList<>(LongStream.rangeClosed(1, 100).boxed().toList());
			// Shuffle to verify sorting is handled internally
			Collections.shuffle(values);

			LatencyStats stats = LatencyStats.of(values);

			assertThat(stats.count()).isEqualTo(100);
			assertThat(stats.minMs()).isEqualTo(1L);
			assertThat(stats.maxMs()).isEqualTo(100L);
			// Sum of 1..100 = 5050; mean = 5050 / 100 = 50.5
			assertThat(stats.meanMs()).isCloseTo(50.5, EPSILON);

			// Nearest-rank percentile:
			// p50: ceil(0.50 * 100) = 50 -> rank 50 -> value 50
			// p95: ceil(0.95 * 100) = 95 -> rank 95 -> value 95
			// p99: ceil(0.99 * 100) = 99 -> rank 99 -> value 99
			assertThat(stats.p50Ms()).isEqualTo(50L);
			assertThat(stats.p95Ms()).isEqualTo(95L);
			assertThat(stats.p99Ms()).isEqualTo(99L);
		}

		@Test
		@DisplayName("Single element list produces identical statistics across min, mean, percentiles, and max")
		void singleElementList() {
			LatencyStats stats = LatencyStats.of(List.of(42L));

			assertThat(stats.count()).isEqualTo(1);
			assertThat(stats.minMs()).isEqualTo(42L);
			assertThat(stats.meanMs()).isCloseTo(42.0, EPSILON);
			assertThat(stats.p50Ms()).isEqualTo(42L);
			assertThat(stats.p95Ms()).isEqualTo(42L);
			assertThat(stats.p99Ms()).isEqualTo(42L);
			assertThat(stats.maxMs()).isEqualTo(42L);
		}

		@Test
		@DisplayName("Factory does not mutate the input collection")
		void doesNotMutateInput() {
			List<Long> original = new ArrayList<>(List.of(99L, 12L, 45L, 3L));
			List<Long> snapshot = List.copyOf(original);

			LatencyStats stats = LatencyStats.of(original);

			assertThat(original).containsExactlyElementsOf(snapshot);
			assertThat(stats.count()).isEqualTo(4);
			assertThat(stats.minMs()).isEqualTo(3L);
			assertThat(stats.maxMs()).isEqualTo(99L);
		}

		@Test
		@DisplayName("Small list with distinct percentiles calculates correct ranks")
		void smallListNearestRank() {
			// Values: [10, 20, 30, 40, 50, 60, 70, 80, 90, 100] (N = 10)
			// p50: ceil(0.50 * 10) = 5 -> index 4 -> 50
			// p95: ceil(0.95 * 10) = 10 -> index 9 -> 100
			// p99: ceil(0.99 * 10) = 10 -> index 9 -> 100
			List<Long> values = List.of(10L, 20L, 30L, 40L, 50L, 60L, 70L, 80L, 90L, 100L);
			LatencyStats stats = LatencyStats.of(values);

			assertThat(stats.count()).isEqualTo(10);
			assertThat(stats.minMs()).isEqualTo(10L);
			assertThat(stats.maxMs()).isEqualTo(100L);
			assertThat(stats.meanMs()).isCloseTo(55.0, EPSILON);
			assertThat(stats.p50Ms()).isEqualTo(50L);
			assertThat(stats.p95Ms()).isEqualTo(100L);
			assertThat(stats.p99Ms()).isEqualTo(100L);
		}
	}

	@Nested
	@DisplayName("Validation Error Cases")
	class ValidationTests {

		@Test
		@DisplayName("Empty collection throws IllegalArgumentException with clear message")
		void rejectsEmptyCollection() {
			assertThatIllegalArgumentException()
					.isThrownBy(() -> LatencyStats.of(List.of()))
					.withMessageContaining("millis collection must not be empty");
		}

		@Test
		@DisplayName("Null collection throws NullPointerException")
		void rejectsNullCollection() {
			assertThatNullPointerException()
					.isThrownBy(() -> LatencyStats.of(null))
					.withMessageContaining("millis collection must not be null");
		}

		@Test
		@DisplayName("Collection containing null element throws NullPointerException")
		void rejectsNullElement() {
			List<Long> listWithNull = new ArrayList<>();
			listWithNull.add(10L);
			listWithNull.add(null);

			assertThatNullPointerException()
					.isThrownBy(() -> LatencyStats.of(listWithNull))
					.withMessageContaining("millisecond value must not be null");
		}

		@Test
		@DisplayName("Negative latency throws IllegalArgumentException")
		void rejectsNegativeLatency() {
			List<Long> listWithNegative = List.of(10L, -5L, 20L);

			assertThatIllegalArgumentException()
					.isThrownBy(() -> LatencyStats.of(listWithNegative))
					.withMessageContaining("latency must not be negative, got: -5");
		}
	}
}

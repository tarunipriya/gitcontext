package io.github.tarunipriya.gitcontext.evaluation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Summary statistics for operation latencies in milliseconds.
 * <p>
 * Percentiles are computed using the nearest-rank percentile method:
 * <pre>
 * rank = ceil((P / 100.0) * N)
 * </pre>
 * where {@code P} is the desired percentile (e.g. 50, 95, 99) and {@code N} is the total count of observations.
 * The value at 0-based index {@code rank - 1} in the sorted list of latencies is selected.
 *
 * @param count total number of latency observations
 * @param minMs minimum observed latency in milliseconds
 * @param meanMs arithmetic mean latency in milliseconds (unrounded)
 * @param p50Ms 50th percentile (median) latency in milliseconds
 * @param p95Ms 95th percentile latency in milliseconds
 * @param p99Ms 99th percentile latency in milliseconds
 * @param maxMs maximum observed latency in milliseconds
 */
public record LatencyStats(
		int count,
		long minMs,
		double meanMs,
		long p50Ms,
		long p95Ms,
		long p99Ms,
		long maxMs
) {

	/**
	 * Computes latency statistics from a collection of millisecond measurements.
	 * <p>
	 * <b>Formula:</b>
	 * <ul>
	 *   <li><b>Mean:</b> {@code (sum of latencies) / N}</li>
	 *   <li><b>Nearest-Rank Percentile (P):</b> {@code sorted[ceil((P / 100.0) * N) - 1]}</li>
	 * </ul>
	 * <b>When useful:</b> Measures query/retrieval execution times to evaluate tail latencies (P95, P99)
	 * and overall latency distribution against Service Level Objectives without outlier skew.
	 *
	 * @param millis collection of non-null, non-negative millisecond values
	 * @return immutable latency statistics
	 * @throws NullPointerException if {@code millis} is null or contains null elements
	 * @throws IllegalArgumentException if {@code millis} is empty or contains negative values
	 */
	public static LatencyStats of(Collection<Long> millis) {
		Objects.requireNonNull(millis, "millis collection must not be null");
		if (millis.isEmpty()) {
			throw new IllegalArgumentException("millis collection must not be empty");
		}

		List<Long> sorted = new ArrayList<>(millis.size());
		double sum = 0.0;
		for (Long m : millis) {
			Objects.requireNonNull(m, "millisecond value must not be null");
			if (m < 0) {
				throw new IllegalArgumentException("latency must not be negative, got: " + m);
			}
			sorted.add(m);
			sum += m;
		}

		Collections.sort(sorted);
		int n = sorted.size();
		long min = sorted.get(0);
		long max = sorted.get(n - 1);
		double mean = sum / n;
		long p50 = nearestRankPercentile(sorted, 50.0);
		long p95 = nearestRankPercentile(sorted, 95.0);
		long p99 = nearestRankPercentile(sorted, 99.0);

		return new LatencyStats(n, min, mean, p50, p95, p99, max);
	}

	private static long nearestRankPercentile(List<Long> sorted, double percentile) {
		int n = sorted.size();
		int rank = (int) Math.ceil((percentile / 100.0) * n);
		int index = Math.clamp(rank - 1, 0, n - 1);
		return sorted.get(index);
	}
}

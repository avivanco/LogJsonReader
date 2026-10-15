package com.logviewer.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Aggregations shown next to the table.
 *
 * @param total number of entries matching the full filter
 * @param levelCounts counts per level, computed ignoring the level criteria
 * @param histogram time distribution computed ignoring the time range (null when entries carry no timestamps)
 */
public record Facets(
		long total,
		Map<LogLevel, Long> levelCounts,
		List<ValueCount> loggers,
		List<ValueCount> threads,
		List<String> attributeKeys,
		Histogram histogram) {

	public record ValueCount(String value, long count) {
	}

	public record Histogram(Instant start, Instant end, long bucketMillis, List<Bucket> buckets) {
	}

	/** Only non-empty buckets are listed. */
	public record Bucket(Instant start, Map<LogLevel, Long> counts) {
	}
}

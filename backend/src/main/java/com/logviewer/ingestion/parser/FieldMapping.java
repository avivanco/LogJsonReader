package com.logviewer.ingestion.parser;

import java.util.List;

/** Candidate JSON field names (first present one wins) for each core log field. */
public record FieldMapping(
		List<String> timestamp,
		List<String> level,
		List<String> message,
		List<String> logger,
		List<String> thread,
		List<String> traceId,
		List<String> spanId,
		List<String> stackTrace) {

	/** Union of two mappings, {@code this} having priority. */
	public FieldMapping merge(FieldMapping other) {
		return new FieldMapping(
				concat(timestamp, other.timestamp), concat(level, other.level), concat(message, other.message),
				concat(logger, other.logger), concat(thread, other.thread), concat(traceId, other.traceId),
				concat(spanId, other.spanId), concat(stackTrace, other.stackTrace));
	}

	private static List<String> concat(List<String> first, List<String> second) {
		return java.util.stream.Stream.concat(first.stream(), second.stream()).distinct().toList();
	}
}

package com.logviewer.domain;

import java.time.Instant;
import java.util.Map;

/**
 * A normalized log record. Everything that is not a core field ends up in {@code attributes}
 * (e.g. {@code username}, {@code trace_flags}) so it stays filterable.
 */
public record LogEntry(
		long seq,
		Instant timestamp,
		LogLevel level,
		String logger,
		String thread,
		String message,
		String traceId,
		String spanId,
		String stackTrace,
		Map<String, String> attributes,
		String raw) {

	public LogEntry {
		level = level == null ? LogLevel.UNKNOWN : level;
		attributes = attributes == null ? Map.of() : attributes;
	}

	/** Entry for a line that could not be parsed as a JSON object; the raw text is preserved. */
	public static LogEntry unparsed(long seq, String raw, Instant timestamp) {
		return new LogEntry(seq, timestamp, LogLevel.UNKNOWN, null, null, raw, null, null, null, Map.of(), raw);
	}

	public LogEntry withTimestamp(Instant newTimestamp) {
		return new LogEntry(seq, newTimestamp, level, logger, thread, message, traceId, spanId, stackTrace,
				attributes, raw);
	}
}

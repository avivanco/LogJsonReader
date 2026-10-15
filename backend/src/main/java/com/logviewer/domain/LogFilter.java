package com.logviewer.domain;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

/**
 * Criteria shared by listing, facets and export. All fields are optional; {@code from}/{@code to} are inclusive.
 */
public record LogFilter(
		Set<LogLevel> levels,
		LogLevel minLevel,
		Instant from,
		Instant to,
		String text,
		Set<String> loggers,
		Set<String> threads,
		String traceId,
		Map<String, String> attributes) {

	public LogFilter {
		levels = levels == null ? Set.of() : Set.copyOf(levels);
		loggers = loggers == null ? Set.of() : Set.copyOf(loggers);
		threads = threads == null ? Set.of() : Set.copyOf(threads);
		attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
		text = text == null || text.isBlank() ? null : text;
		traceId = traceId == null || traceId.isBlank() ? null : traceId;
	}

	public static LogFilter none() {
		return new LogFilter(null, null, null, null, null, null, null, null, null);
	}

	/** Same filter ignoring severity; used so level counts stay meaningful while a level is selected. */
	public LogFilter withoutLevels() {
		return new LogFilter(null, null, from, to, text, loggers, threads, traceId, attributes);
	}

	/** Same filter ignoring the time range; used so the timeline keeps showing the whole span. */
	public LogFilter withoutTimeRange() {
		return new LogFilter(levels, minLevel, null, null, text, loggers, threads, traceId, attributes);
	}
}

package com.logviewer.api;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.util.MultiValueMap;

import com.logviewer.domain.LogFilter;
import com.logviewer.domain.LogLevel;

/**
 * Builds a {@link LogFilter} from query parameters: {@code level} (repeatable or comma separated), {@code minLevel},
 * {@code from}, {@code to} (ISO-8601), {@code q}, {@code logger}, {@code thread}, {@code traceId} and
 * {@code attr.<name>=<value>}.
 */
final class LogFilterParser {

	private static final String ATTRIBUTE_PREFIX = "attr.";

	private LogFilterParser() {
	}

	static LogFilter parse(MultiValueMap<String, String> params) {
		Set<LogLevel> levels = EnumSet.noneOf(LogLevel.class);
		for (String value : values(params, "level")) {
			for (String part : value.split(",")) {
				if (!part.isBlank()) {
					levels.add(level(part));
				}
			}
		}
		String minLevel = params.getFirst("minLevel");
		Map<String, String> attributes = new LinkedHashMap<>();
		params.forEach((key, values) -> {
			if (key.startsWith(ATTRIBUTE_PREFIX) && key.length() > ATTRIBUTE_PREFIX.length() && !values.isEmpty()) {
				attributes.put(key.substring(ATTRIBUTE_PREFIX.length()), values.get(0));
			}
		});
		return new LogFilter(
				levels,
				minLevel == null || minLevel.isBlank() ? null : level(minLevel),
				instant(params.getFirst("from"), "from"),
				instant(params.getFirst("to"), "to"),
				params.getFirst("q"),
				new LinkedHashSet<>(values(params, "logger")),
				new LinkedHashSet<>(values(params, "thread")),
				params.getFirst("traceId"),
				attributes);
	}

	private static List<String> values(MultiValueMap<String, String> params, String name) {
		List<String> values = params.get(name);
		return values == null ? List.of() : values.stream().filter(value -> !value.isBlank()).toList();
	}

	private static LogLevel level(String text) {
		try {
			return LogLevel.valueOf(text.trim().toUpperCase(Locale.ROOT));
		}
		catch (IllegalArgumentException ex) {
			throw new IllegalArgumentException("Unknown level: " + text);
		}
	}

	private static Instant instant(String text, String name) {
		if (text == null || text.isBlank()) {
			return null;
		}
		try {
			return Instant.parse(text.trim());
		}
		catch (DateTimeParseException ex) {
			throw new IllegalArgumentException("Invalid '" + name + "' date, expected ISO-8601 (e.g. 2026-10-15T00:00:00Z)");
		}
	}
}

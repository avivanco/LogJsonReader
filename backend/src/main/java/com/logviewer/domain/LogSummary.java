package com.logviewer.domain;

import java.time.Instant;
import java.util.Map;

/** Lightweight row used by list views: no raw line and no stack trace body, with a truncated message. */
public record LogSummary(
		long seq,
		Instant timestamp,
		LogLevel level,
		String logger,
		String thread,
		String message,
		String traceId,
		String spanId,
		boolean hasStackTrace,
		Map<String, String> attributes) {
}

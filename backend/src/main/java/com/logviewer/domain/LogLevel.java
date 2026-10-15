package com.logviewer.domain;

import java.util.Locale;

/**
 * Normalized severity. {@link #severity()} is the numeric value persisted in the store, which makes
 * "minimum level" filtering a simple comparison. {@link #UNKNOWN} (0) is never matched by a minimum-level filter.
 */
public enum LogLevel {
	UNKNOWN(0),
	TRACE(10),
	DEBUG(20),
	INFO(30),
	WARN(40),
	ERROR(50),
	FATAL(60);

	private final int severity;

	LogLevel(int severity) {
		this.severity = severity;
	}

	public int severity() {
		return severity;
	}

	public static LogLevel fromSeverity(int severity) {
		for (LogLevel level : values()) {
			if (level.severity == severity) {
				return level;
			}
		}
		return UNKNOWN;
	}

	/** Parses level names used by common logging frameworks, including numeric text such as "30". */
	public static LogLevel parse(String text) {
		if (text == null || text.isBlank()) {
			return UNKNOWN;
		}
		String normalized = text.trim().toUpperCase(Locale.ROOT);
		return switch (normalized) {
			case "TRACE", "FINEST", "FINER", "VERBOSE" -> TRACE;
			case "DEBUG", "FINE", "CONFIG" -> DEBUG;
			case "INFO", "INFORMATION", "NOTICE" -> INFO;
			case "WARN", "WARNING" -> WARN;
			case "ERROR", "ERR", "SEVERE" -> ERROR;
			case "FATAL", "CRITICAL", "CRIT", "ALERT", "EMERG", "EMERGENCY", "PANIC" -> FATAL;
			default -> parseNumeric(normalized);
		};
	}

	/** Maps pino/bunyan style numeric levels (10 trace .. 60 fatal); values in between fall into the lower bucket. */
	public static LogLevel fromNumber(long value) {
		if (value <= 0) {
			return UNKNOWN;
		}
		if (value <= 10) {
			return TRACE;
		}
		if (value <= 20) {
			return DEBUG;
		}
		if (value <= 30) {
			return INFO;
		}
		if (value <= 40) {
			return WARN;
		}
		if (value <= 50) {
			return ERROR;
		}
		return FATAL;
	}

	private static LogLevel parseNumeric(String text) {
		try {
			return fromNumber(Long.parseLong(text));
		}
		catch (NumberFormatException ex) {
			return UNKNOWN;
		}
	}
}

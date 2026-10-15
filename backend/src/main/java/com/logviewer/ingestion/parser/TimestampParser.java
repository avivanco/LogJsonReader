package com.logviewer.ingestion.parser;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

import tools.jackson.databind.JsonNode;

/** Lenient timestamp reader: ISO-8601 text, epoch numbers (s/ms/us/ns, autodetected) and Log4j2 {@code instant} objects. */
final class TimestampParser {

	private static final double SECONDS_LIMIT = 1e11;
	private static final double MILLIS_LIMIT = 1e14;
	private static final double MICROS_LIMIT = 1e17;

	private TimestampParser() {
	}

	/** @return the instant, or null when the node does not hold a recognizable timestamp */
	static Instant parse(JsonNode node) {
		if (node == null || node.isNull() || node.isMissingNode()) {
			return null;
		}
		if (node.isNumber()) {
			return fromEpoch(node.doubleValue());
		}
		if (node.isObject() && node.has("epochSecond")) {
			return Instant.ofEpochSecond(node.path("epochSecond").asLong(), node.path("nanoOfSecond").asLong(0));
		}
		if (node.isString()) {
			return parseText(node.stringValue().trim());
		}
		return null;
	}

	private static Instant parseText(String text) {
		if (text.isEmpty()) {
			return null;
		}
		try {
			return Instant.parse(text);
		}
		catch (DateTimeParseException ignored) {
			// fall through to the other formats
		}
		try {
			return OffsetDateTime.parse(text).toInstant();
		}
		catch (DateTimeParseException ignored) {
			// fall through
		}
		try {
			return LocalDateTime.parse(text.replace(' ', 'T')).toInstant(ZoneOffset.UTC);
		}
		catch (DateTimeParseException ignored) {
			// fall through
		}
		try {
			return fromEpoch(Double.parseDouble(text));
		}
		catch (NumberFormatException ignored) {
			return null;
		}
	}

	private static Instant fromEpoch(double value) {
		double abs = Math.abs(value);
		if (abs < SECONDS_LIMIT) {
			return Instant.ofEpochSecond((long) Math.floor(value), (long) ((value - Math.floor(value)) * 1e9));
		}
		long whole = (long) value;
		if (abs < MILLIS_LIMIT) {
			return Instant.ofEpochMilli(whole);
		}
		if (abs < MICROS_LIMIT) {
			return Instant.ofEpochSecond(Math.floorDiv(whole, 1_000_000L), Math.floorMod(whole, 1_000_000L) * 1_000L);
		}
		return Instant.ofEpochSecond(Math.floorDiv(whole, 1_000_000_000L), Math.floorMod(whole, 1_000_000_000L));
	}
}

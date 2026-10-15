package com.logviewer.ingestion.parser;

import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.logviewer.domain.LogEntry;
import com.logviewer.domain.LogLevel;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Template Method: {@link #parse} fixes the algorithm (read JSON, extract core fields, collect the remaining fields as
 * attributes, post-process); subclasses supply the {@link FieldMapping} and may override individual steps.
 */
public abstract class AbstractJsonLogParser implements LogFormatParser {

	private final JsonMapper mapper;

	private final FieldMapping fields;

	protected AbstractJsonLogParser(JsonMapper mapper, FieldMapping fields) {
		this.mapper = mapper;
		this.fields = fields;
	}

	@Override
	public final LogEntry parse(long seq, String line) {
		JsonNode root = readRoot(line);
		Set<String> consumed = new HashSet<>();
		Instant timestamp = extractTimestamp(root, consumed);
		LogLevel level = extractLevel(root, consumed);
		String message = extractMessage(root, consumed);
		String logger = text(root, fields.logger(), consumed);
		String thread = text(root, fields.thread(), consumed);
		String traceId = text(root, fields.traceId(), consumed);
		String spanId = text(root, fields.spanId(), consumed);
		String stackTrace = extractStackTrace(root, consumed);
		Map<String, String> attributes = collectAttributes(root, consumed);
		return postProcess(new LogEntry(seq, timestamp, level, logger, thread, message, traceId, spanId, stackTrace,
				attributes, line));
	}

	// --- steps that subclasses may override ---------------------------------------------------------------------

	protected Instant extractTimestamp(JsonNode root, Set<String> consumed) {
		return TimestampParser.parse(find(root, fields.timestamp(), consumed));
	}

	protected LogLevel extractLevel(JsonNode root, Set<String> consumed) {
		JsonNode node = find(root, fields.level(), consumed);
		if (node == null) {
			return LogLevel.UNKNOWN;
		}
		return node.isNumber() ? LogLevel.fromNumber(node.longValue()) : LogLevel.parse(asText(node));
	}

	protected String extractMessage(JsonNode root, Set<String> consumed) {
		return text(root, fields.message(), consumed);
	}

	protected String extractStackTrace(JsonNode root, Set<String> consumed) {
		JsonNode node = find(root, fields.stackTrace(), consumed);
		if (node == null) {
			return null;
		}
		if (node.isObject() && node.path("stack").isString()) {
			return node.path("stack").stringValue();
		}
		return asText(node);
	}

	protected Map<String, String> collectAttributes(JsonNode root, Set<String> consumed) {
		Map<String, String> attributes = new LinkedHashMap<>();
		for (Map.Entry<String, JsonNode> field : root.properties()) {
			if (!consumed.contains(field.getKey()) && !field.getValue().isNull()) {
				attributes.put(field.getKey(), asText(field.getValue()));
			}
		}
		return attributes;
	}

	protected LogEntry postProcess(LogEntry entry) {
		return entry;
	}

	// --- helpers --------------------------------------------------------------------------------------------------

	private JsonNode readRoot(String line) {
		JsonNode root;
		try {
			root = mapper.readTree(line);
		}
		catch (JacksonException ex) {
			throw new MalformedLogLineException("Not valid JSON", ex);
		}
		if (root == null || !root.isObject()) {
			throw new MalformedLogLineException("Not a JSON object");
		}
		return root;
	}

	protected static JsonNode find(JsonNode root, List<String> names, Set<String> consumed) {
		for (String name : names) {
			JsonNode node = root.get(name);
			if (node != null && !node.isNull()) {
				consumed.add(name);
				return node;
			}
		}
		return null;
	}

	protected static String text(JsonNode root, List<String> names, Set<String> consumed) {
		JsonNode node = find(root, names, consumed);
		return node == null ? null : asText(node);
	}

	protected static String asText(JsonNode node) {
		return node.isContainer() ? node.toString() : node.asString();
	}
}

package com.logviewer.ingestion.parser;

import java.util.List;

import tools.jackson.databind.json.JsonMapper;

/** logback-contrib {@code JsonLayout} style output. */
public class LogbackJsonParser extends AbstractJsonLogParser {

	static final FieldMapping MAPPING = new FieldMapping(
			List.of("timestamp", "time"),
			List.of("level"),
			List.of("message", "formatted-message"),
			List.of("logger", "logger_name"),
			List.of("thread"),
			List.of("traceId", "trace_id"),
			List.of("spanId", "span_id"),
			List.of("exception", "throwable"));

	public LogbackJsonParser(JsonMapper mapper) {
		super(mapper, MAPPING);
	}

	@Override
	public String formatName() {
		return "Logback JSON";
	}
}

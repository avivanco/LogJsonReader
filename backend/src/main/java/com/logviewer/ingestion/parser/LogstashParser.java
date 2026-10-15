package com.logviewer.ingestion.parser;

import java.util.List;

import tools.jackson.databind.json.JsonMapper;

/** logstash-logback-encoder output (with MDC / OpenTelemetry fields) and flat Elastic Common Schema keys. */
public class LogstashParser extends AbstractJsonLogParser {

	static final FieldMapping MAPPING = new FieldMapping(
			List.of("@timestamp", "timestamp"),
			List.of("level", "log.level"),
			List.of("message", "msg"),
			List.of("logger_name", "log.logger", "logger"),
			List.of("thread_name", "process.thread.name", "thread"),
			List.of("trace_id", "trace.id", "traceId"),
			List.of("span_id", "span.id", "spanId"),
			List.of("stack_trace", "error.stack_trace", "exception"));

	public LogstashParser(JsonMapper mapper) {
		super(mapper, MAPPING);
	}

	@Override
	public String formatName() {
		return "Logstash / ECS";
	}
}

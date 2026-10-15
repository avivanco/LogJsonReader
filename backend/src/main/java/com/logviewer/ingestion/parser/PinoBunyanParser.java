package com.logviewer.ingestion.parser;

import java.util.List;

import tools.jackson.databind.json.JsonMapper;

/** pino / bunyan output: numeric levels (10..60), {@code time} in epoch millis, {@code msg}. */
public class PinoBunyanParser extends AbstractJsonLogParser {

	static final FieldMapping MAPPING = new FieldMapping(
			List.of("time"),
			List.of("level"),
			List.of("msg"),
			List.of("name"),
			List.of(),
			List.of("trace_id", "traceId"),
			List.of("span_id", "spanId"),
			List.of("err", "stack"));

	public PinoBunyanParser(JsonMapper mapper) {
		super(mapper, MAPPING);
	}

	@Override
	public String formatName() {
		return "pino / bunyan";
	}
}

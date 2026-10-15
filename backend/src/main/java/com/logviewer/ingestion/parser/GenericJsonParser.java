package com.logviewer.ingestion.parser;

import java.util.List;

import tools.jackson.databind.json.JsonMapper;

/** Fallback for unknown JSON logs: tries every well-known field name from the other formats plus common aliases. */
public class GenericJsonParser extends AbstractJsonLogParser {

	private static final FieldMapping ALIASES = new FieldMapping(
			List.of("ts", "date", "datetime", "@t"),
			List.of("severity", "lvl", "levelname", "@l"),
			List.of("text", "@m", "log"),
			List.of("category", "class"),
			List.of("threadName"),
			List.of(),
			List.of(),
			List.of("stacktrace", "stackTrace"));

	static final FieldMapping MAPPING = LogstashParser.MAPPING
			.merge(LogbackJsonParser.MAPPING)
			.merge(PinoBunyanParser.MAPPING)
			.merge(ALIASES);

	public GenericJsonParser(JsonMapper mapper) {
		super(mapper, MAPPING);
	}

	@Override
	public String formatName() {
		return "Generic JSON";
	}
}

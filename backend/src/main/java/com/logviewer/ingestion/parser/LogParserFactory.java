package com.logviewer.ingestion.parser;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Factory choosing the {@link LogFormatParser} for a file by running the detector chain over its first lines. */
@Component
public class LogParserFactory {

	private final JsonMapper mapper;

	private final FormatDetector chain;

	public LogParserFactory(JsonMapper mapper) {
		this.mapper = mapper;
		FormatDetector logstash = new SignatureFormatDetector(
				node -> node.has("@timestamp") || node.has("log.level") || node.has("logger_name"),
				new LogstashParser(mapper));
		FormatDetector pino = new SignatureFormatDetector(
				node -> node.path("time").isNumber() && node.path("level").isNumber() && node.has("msg"),
				new PinoBunyanParser(mapper));
		FormatDetector logback = new SignatureFormatDetector(
				node -> node.has("timestamp") && node.has("level") && (node.has("logger") || node.has("thread")),
				new LogbackJsonParser(mapper));
		logstash.linkWith(pino).linkWith(logback).linkWith(new FallbackFormatDetector(new GenericJsonParser(mapper)));
		this.chain = logstash;
	}

	/** @param sampleLines the first non-blank lines of the file; unparsable ones are ignored for detection */
	public LogFormatParser forSample(List<String> sampleLines) {
		List<JsonNode> nodes = new ArrayList<>(sampleLines.size());
		for (String line : sampleLines) {
			try {
				JsonNode node = mapper.readTree(line);
				if (node != null && node.isObject()) {
					nodes.add(node);
				}
			}
			catch (JacksonException ignored) {
				// malformed lines do not take part in detection
			}
		}
		return chain.detect(nodes);
	}
}

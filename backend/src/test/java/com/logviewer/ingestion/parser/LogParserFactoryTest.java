package com.logviewer.ingestion.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.logviewer.domain.LogEntry;
import com.logviewer.domain.LogLevel;

import tools.jackson.databind.json.JsonMapper;

class LogParserFactoryTest {

	/** The line from docs/logExample/logExample.log. */
	static final String LOGSTASH_LINE = "{\"@timestamp\":\"2026-10-15T00:08:10.340052226Z\",\"level\":\"INFO\","
			+ "\"thread_name\":\"reactor-http-epoll-3\",\"logger_name\":\"com.insurance.integration.services.base."
			+ "BaseServiceIntegration\",\"message\":\"[HTTP request] POST https://local/uri\","
			+ "\"trace_id\":\"b74e12bc610d474d9ac19d303f71c585\",\"trace_flags\":\"00\","
			+ "\"span_id\":\"f05f2d2380b80ad1\",\"username\":\"aaaaa\"}";

	private final LogParserFactory factory = new LogParserFactory(JsonMapper.builder().build());

	@Test
	void detectsAndParsesTheLogstashSample() {
		LogFormatParser parser = factory.forSample(List.of(LOGSTASH_LINE));

		assertThat(parser.formatName()).isEqualTo("Logstash / ECS");
		LogEntry entry = parser.parse(1, LOGSTASH_LINE);
		assertThat(entry.timestamp()).isEqualTo(Instant.parse("2026-10-15T00:08:10.340052226Z"));
		assertThat(entry.level()).isEqualTo(LogLevel.INFO);
		assertThat(entry.thread()).isEqualTo("reactor-http-epoll-3");
		assertThat(entry.logger()).isEqualTo("com.insurance.integration.services.base.BaseServiceIntegration");
		assertThat(entry.message()).isEqualTo("[HTTP request] POST https://local/uri");
		assertThat(entry.traceId()).isEqualTo("b74e12bc610d474d9ac19d303f71c585");
		assertThat(entry.spanId()).isEqualTo("f05f2d2380b80ad1");
		assertThat(entry.attributes()).containsOnly(
				java.util.Map.entry("trace_flags", "00"), java.util.Map.entry("username", "aaaaa"));
		assertThat(entry.raw()).isEqualTo(LOGSTASH_LINE);
	}

	@Test
	void detectsPinoWithNumericLevelAndEpochMillis() {
		String line = "{\"level\":50,\"time\":1760486400000,\"msg\":\"boom\",\"name\":\"api\",\"pid\":7}";

		LogFormatParser parser = factory.forSample(List.of(line));
		LogEntry entry = parser.parse(1, line);

		assertThat(parser.formatName()).isEqualTo("pino / bunyan");
		assertThat(entry.level()).isEqualTo(LogLevel.ERROR);
		assertThat(entry.timestamp()).isEqualTo(Instant.ofEpochMilli(1760486400000L));
		assertThat(entry.message()).isEqualTo("boom");
		assertThat(entry.logger()).isEqualTo("api");
		assertThat(entry.attributes()).containsEntry("pid", "7");
	}

	@Test
	void detectsLogbackJsonWithLocalDateTime() {
		String line = "{\"timestamp\":\"2026-10-15 00:00:01\",\"level\":\"WARN\",\"logger\":\"a.B\","
				+ "\"thread\":\"main\",\"message\":\"careful\"}";

		LogFormatParser parser = factory.forSample(List.of(line));
		LogEntry entry = parser.parse(1, line);

		assertThat(parser.formatName()).isEqualTo("Logback JSON");
		assertThat(entry.timestamp()).isEqualTo(Instant.parse("2026-10-15T00:00:01Z"));
		assertThat(entry.level()).isEqualTo(LogLevel.WARN);
		assertThat(entry.thread()).isEqualTo("main");
	}

	@Test
	void fallsBackToGenericParserForUnknownShapes() {
		String line = "{\"ts\":\"2026-10-15T00:00:00Z\",\"severity\":\"error\",\"text\":\"hi\",\"extra\":{\"a\":1}}";

		LogFormatParser parser = factory.forSample(List.of(line));
		LogEntry entry = parser.parse(1, line);

		assertThat(parser.formatName()).isEqualTo("Generic JSON");
		assertThat(entry.level()).isEqualTo(LogLevel.ERROR);
		assertThat(entry.message()).isEqualTo("hi");
		assertThat(entry.attributes()).containsEntry("extra", "{\"a\":1}");
	}

	@Test
	void emptySampleUsesTheFallback() {
		assertThat(factory.forSample(List.of()).formatName()).isEqualTo("Generic JSON");
		assertThat(factory.forSample(List.of("not json at all")).formatName()).isEqualTo("Generic JSON");
	}

	@Test
	void keepsStackTraceSeparateFromAttributes() {
		String line = "{\"@timestamp\":\"2026-10-15T00:00:00Z\",\"level\":\"ERROR\",\"message\":\"failed\","
				+ "\"stack_trace\":\"java.lang.IllegalStateException: x\\n\\tat a.B.c(B.java:1)\"}";

		LogEntry entry = factory.forSample(List.of(line)).parse(1, line);

		assertThat(entry.stackTrace()).startsWith("java.lang.IllegalStateException").contains("\n\tat a.B.c");
		assertThat(entry.attributes()).isEmpty();
	}

	@Test
	void missingTimestampIsNull() {
		String line = "{\"level\":\"INFO\",\"message\":\"no time\"}";

		assertThat(factory.forSample(List.of(line)).parse(1, line).timestamp()).isNull();
	}

	@Test
	void rejectsLinesThatAreNotJsonObjects() {
		LogFormatParser parser = factory.forSample(List.of(LOGSTASH_LINE));

		assertThatThrownBy(() -> parser.parse(1, "plain text")).isInstanceOf(MalformedLogLineException.class);
		assertThatThrownBy(() -> parser.parse(2, "[1,2,3]")).isInstanceOf(MalformedLogLineException.class);
		assertThatThrownBy(() -> parser.parse(3, "{\"unterminated\":")).isInstanceOf(MalformedLogLineException.class);
	}
}

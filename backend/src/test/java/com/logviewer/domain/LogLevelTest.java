package com.logviewer.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class LogLevelTest {

	@ParameterizedTest
	@CsvSource({
			"INFO,INFO", "info,INFO", " Warning ,WARN", "SEVERE,ERROR", "fine,DEBUG", "FINEST,TRACE",
			"CRITICAL,FATAL", "30,INFO", "50,ERROR", "60,FATAL", "nonsense,UNKNOWN"})
	void parsesCommonLevelNames(String text, LogLevel expected) {
		assertThat(LogLevel.parse(text)).isEqualTo(expected);
	}

	@Test
	void blankAndNullAreUnknown() {
		assertThat(LogLevel.parse(null)).isEqualTo(LogLevel.UNKNOWN);
		assertThat(LogLevel.parse("  ")).isEqualTo(LogLevel.UNKNOWN);
	}

	@Test
	void numericLevelsFallIntoTheLowerBucket() {
		assertThat(LogLevel.fromNumber(10)).isEqualTo(LogLevel.TRACE);
		assertThat(LogLevel.fromNumber(25)).isEqualTo(LogLevel.INFO);
		assertThat(LogLevel.fromNumber(41)).isEqualTo(LogLevel.ERROR);
		assertThat(LogLevel.fromNumber(0)).isEqualTo(LogLevel.UNKNOWN);
	}

	@Test
	void severityRoundTrips() {
		for (LogLevel level : LogLevel.values()) {
			assertThat(LogLevel.fromSeverity(level.severity())).isEqualTo(level);
		}
	}
}

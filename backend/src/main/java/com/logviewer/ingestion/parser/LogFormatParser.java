package com.logviewer.ingestion.parser;

import com.logviewer.domain.LogEntry;

/** Strategy turning one raw line of a given JSON log format into a normalized {@link LogEntry}. */
public interface LogFormatParser {

	String formatName();

	/** @throws MalformedLogLineException if the line is not a JSON object */
	LogEntry parse(long seq, String line);
}

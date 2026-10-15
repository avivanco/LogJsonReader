package com.logviewer.ingestion.parser;

public class MalformedLogLineException extends RuntimeException {

	public MalformedLogLineException(String message) {
		super(message);
	}

	public MalformedLogLineException(String message, Throwable cause) {
		super(message, cause);
	}
}

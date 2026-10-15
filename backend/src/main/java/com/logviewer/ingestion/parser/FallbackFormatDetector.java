package com.logviewer.ingestion.parser;

import java.util.List;

import tools.jackson.databind.JsonNode;

/** Terminal link of the chain: always claims the sample. */
public class FallbackFormatDetector extends FormatDetector {

	private final LogFormatParser parser;

	public FallbackFormatDetector(LogFormatParser parser) {
		this.parser = parser;
	}

	@Override
	protected boolean matches(List<JsonNode> sample) {
		return true;
	}

	@Override
	protected LogFormatParser parser() {
		return parser;
	}
}

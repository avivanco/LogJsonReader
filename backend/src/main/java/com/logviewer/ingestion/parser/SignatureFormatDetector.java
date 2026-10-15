package com.logviewer.ingestion.parser;

import java.util.List;
import java.util.function.Predicate;

import tools.jackson.databind.JsonNode;

/** Claims the sample when most of its lines satisfy a format-specific signature predicate. */
public class SignatureFormatDetector extends FormatDetector {

	private static final double REQUIRED_RATIO = 0.6;

	private final Predicate<JsonNode> signature;

	private final LogFormatParser parser;

	public SignatureFormatDetector(Predicate<JsonNode> signature, LogFormatParser parser) {
		this.signature = signature;
		this.parser = parser;
	}

	@Override
	protected boolean matches(List<JsonNode> sample) {
		if (sample.isEmpty()) {
			return false;
		}
		long hits = sample.stream().filter(signature).count();
		return hits >= Math.max(1, Math.ceil(sample.size() * REQUIRED_RATIO));
	}

	@Override
	protected LogFormatParser parser() {
		return parser;
	}
}

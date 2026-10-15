package com.logviewer.ingestion.parser;

import java.util.List;

import tools.jackson.databind.JsonNode;

/**
 * Chain of Responsibility: each detector either claims a sample of parsed lines or hands it to the next one.
 * The last link must always match (see {@link FallbackFormatDetector}).
 */
public abstract class FormatDetector {

	private FormatDetector next;

	/** @return {@code next}, so chains read as {@code a.linkWith(b).linkWith(c)} */
	public FormatDetector linkWith(FormatDetector next) {
		this.next = next;
		return next;
	}

	public final LogFormatParser detect(List<JsonNode> sample) {
		if (matches(sample)) {
			return parser();
		}
		if (next == null) {
			throw new IllegalStateException("No format detector claimed the sample; the chain needs a fallback");
		}
		return next.detect(sample);
	}

	protected abstract boolean matches(List<JsonNode> sample);

	protected abstract LogFormatParser parser();
}

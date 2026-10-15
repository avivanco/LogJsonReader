package com.logviewer.domain;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Keyset position {@code (timestamp micros, seq)} encoded as an opaque token for the client. */
public record Cursor(long ts, long seq) {

	public String encode() {
		return Base64.getUrlEncoder().withoutPadding()
				.encodeToString((ts + ":" + seq).getBytes(StandardCharsets.UTF_8));
	}

	public static Cursor decode(String token) {
		try {
			String[] parts = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8).split(":");
			if (parts.length != 2) {
				throw new IllegalArgumentException("Invalid cursor");
			}
			return new Cursor(Long.parseLong(parts[0]), Long.parseLong(parts[1]));
		}
		catch (IllegalArgumentException ex) {
			throw new IllegalArgumentException("Invalid cursor", ex);
		}
	}
}

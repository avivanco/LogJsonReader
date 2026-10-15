package com.logviewer.session;

import java.time.Instant;

/** Immutable snapshot of a session sent to clients. */
public record SessionView(
		String id,
		String name,
		SessionStatus status,
		String format,
		long bytesTotal,
		long bytesRead,
		long entries,
		long parseErrors,
		String error,
		Instant createdAt) {
}

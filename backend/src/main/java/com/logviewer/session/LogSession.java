package com.logviewer.session;

import java.time.Instant;

/** Mutable, thread-safe state of one loaded log; written by the ingestion thread and read by request threads. */
public class LogSession {

	private final String id;

	private final String name;

	private final long bytesTotal;

	private final Instant createdAt = Instant.now();

	private volatile Instant lastAccess = createdAt;

	private volatile SessionStatus status = SessionStatus.INGESTING;

	private volatile String format;

	private volatile long bytesRead;

	private volatile long entries;

	private volatile long parseErrors;

	private volatile String error;

	public LogSession(String id, String name, long bytesTotal) {
		this.id = id;
		this.name = name;
		this.bytesTotal = bytesTotal;
	}

	public String id() {
		return id;
	}

	public Instant lastAccess() {
		return lastAccess;
	}

	public void touch() {
		lastAccess = Instant.now();
	}

	public void format(String format) {
		this.format = format;
	}

	public void progress(long bytesRead, long entries, long parseErrors) {
		this.bytesRead = bytesRead;
		this.entries = entries;
		this.parseErrors = parseErrors;
	}

	public void markReady() {
		bytesRead = bytesTotal;
		status = SessionStatus.READY;
	}

	public void markFailed(String reason) {
		error = reason;
		status = SessionStatus.FAILED;
	}

	public SessionView toView() {
		return new SessionView(id, name, status, format, bytesTotal, bytesRead, entries, parseErrors, error,
				createdAt);
	}
}

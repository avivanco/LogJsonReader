package com.logviewer.domain;

public class SessionNotFoundException extends RuntimeException {

	public SessionNotFoundException(String sessionId) {
		super("Session not found: " + sessionId);
	}
}

package com.logviewer.session;

/** Published after a session was deleted or expired; listeners release its resources. */
public record SessionRemoved(String sessionId) {
}

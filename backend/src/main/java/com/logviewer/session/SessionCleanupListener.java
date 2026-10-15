package com.logviewer.session;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.logviewer.store.LogStore;

@Component
public class SessionCleanupListener {

	private final LogStore store;

	public SessionCleanupListener(LogStore store) {
		this.store = store;
	}

	@EventListener
	public void on(SessionRemoved event) {
		store.dropSession(event.sessionId());
	}
}

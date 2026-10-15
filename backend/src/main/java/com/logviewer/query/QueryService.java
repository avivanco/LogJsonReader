package com.logviewer.query;

import java.util.Optional;
import java.util.function.Consumer;

import org.springframework.stereotype.Service;

import com.logviewer.domain.Facets;
import com.logviewer.domain.LogEntry;
import com.logviewer.domain.LogFilter;
import com.logviewer.domain.LogSummary;
import com.logviewer.domain.Page;
import com.logviewer.domain.PageRequest;
import com.logviewer.session.SessionRegistry;
import com.logviewer.store.LogStore;

/** Read side: every call first checks the session exists (and counts as activity) before hitting the store. */
@Service
public class QueryService {

	private final SessionRegistry registry;

	private final LogStore store;

	public QueryService(SessionRegistry registry, LogStore store) {
		this.registry = registry;
		this.store = store;
	}

	/** @throws com.logviewer.domain.SessionNotFoundException if the session is unknown or expired */
	public void requireSession(String sessionId) {
		registry.get(sessionId);
	}

	public Page<LogSummary> logs(String sessionId, LogFilter filter, PageRequest page) {
		requireSession(sessionId);
		return store.query(sessionId, filter, page);
	}

	public Optional<LogEntry> entry(String sessionId, long seq) {
		requireSession(sessionId);
		return store.find(sessionId, seq);
	}

	public Facets facets(String sessionId, LogFilter filter) {
		requireSession(sessionId);
		return store.facets(sessionId, filter);
	}

	public void export(String sessionId, LogFilter filter, Consumer<LogEntry> sink) {
		requireSession(sessionId);
		store.export(sessionId, filter, sink);
	}
}

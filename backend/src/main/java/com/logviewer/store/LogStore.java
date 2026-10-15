package com.logviewer.store;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import com.logviewer.domain.Facets;
import com.logviewer.domain.LogEntry;
import com.logviewer.domain.LogFilter;
import com.logviewer.domain.LogSummary;
import com.logviewer.domain.Page;
import com.logviewer.domain.PageRequest;

/** Port for per-session log storage and querying. */
public interface LogStore {

	void createSession(String sessionId);

	/** Appends a batch; safe to call while the session is being queried. */
	void append(String sessionId, List<LogEntry> batch);

	Page<LogSummary> query(String sessionId, LogFilter filter, PageRequest page);

	Optional<LogEntry> find(String sessionId, long seq);

	Facets facets(String sessionId, LogFilter filter);

	/** Streams all matching entries in file order. */
	void export(String sessionId, LogFilter filter, Consumer<LogEntry> sink);

	void dropSession(String sessionId);
}

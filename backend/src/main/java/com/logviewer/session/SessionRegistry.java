package com.logviewer.session;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.logviewer.domain.SessionNotFoundException;

@Component
public class SessionRegistry {

	private final Map<String, LogSession> sessions = new ConcurrentHashMap<>();

	public LogSession create(String name, long bytesTotal) {
		LogSession session = new LogSession(UUID.randomUUID().toString(), name, bytesTotal);
		sessions.put(session.id(), session);
		return session;
	}

	/** Looks a session up and marks it as recently used. */
	public LogSession get(String id) {
		LogSession session = sessions.get(id);
		if (session == null) {
			throw new SessionNotFoundException(id);
		}
		session.touch();
		return session;
	}

	/** Lookup that neither fails nor counts as activity (used by background work); null when unknown. */
	public LogSession peek(String id) {
		return sessions.get(id);
	}

	public boolean remove(String id) {
		return sessions.remove(id) != null;
	}

	public List<String> idleLongerThan(Duration ttl) {
		Instant threshold = Instant.now().minus(ttl);
		return sessions.values().stream()
				.filter(session -> session.lastAccess().isBefore(threshold))
				.map(LogSession::id)
				.toList();
	}
}

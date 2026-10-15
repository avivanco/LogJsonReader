package com.logviewer.session;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.logviewer.ingestion.event.IngestionCompleted;
import com.logviewer.ingestion.event.IngestionFailed;
import com.logviewer.ingestion.event.IngestionStarted;
import com.logviewer.ingestion.event.LogBatchStored;

/** Turns ingestion events into server-sent {@code progress} messages carrying the current {@link SessionView}. */
@Component
public class SessionEventBroadcaster {

	private final SessionRegistry registry;

	private final Map<String, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

	public SessionEventBroadcaster(SessionRegistry registry) {
		this.registry = registry;
	}

	public SseEmitter subscribe(String sessionId) {
		SessionView current = registry.get(sessionId).toView();
		SseEmitter emitter = new SseEmitter(0L);
		List<SseEmitter> list = emitters.computeIfAbsent(sessionId, id -> new CopyOnWriteArrayList<>());
		list.add(emitter);
		Runnable cleanup = () -> list.remove(emitter);
		emitter.onCompletion(cleanup);
		emitter.onTimeout(cleanup);
		emitter.onError(error -> cleanup.run());
		send(emitter, current);
		if (current.status() != SessionStatus.INGESTING) {
			emitter.complete();
		}
		return emitter;
	}

	@EventListener
	void on(IngestionStarted event) {
		broadcast(event.sessionId(), false);
	}

	@EventListener
	void on(LogBatchStored event) {
		broadcast(event.sessionId(), false);
	}

	@EventListener
	void on(IngestionCompleted event) {
		broadcast(event.sessionId(), true);
	}

	@EventListener
	void on(IngestionFailed event) {
		broadcast(event.sessionId(), true);
	}

	@EventListener
	void on(SessionRemoved event) {
		List<SseEmitter> list = emitters.remove(event.sessionId());
		if (list != null) {
			list.forEach(SseEmitter::complete);
		}
	}

	private void broadcast(String sessionId, boolean terminal) {
		LogSession session = registry.peek(sessionId);
		List<SseEmitter> list = emitters.get(sessionId);
		if (session == null || list == null) {
			return;
		}
		SessionView view = session.toView();
		for (SseEmitter emitter : list) {
			send(emitter, view);
			if (terminal) {
				emitter.complete();
			}
		}
	}

	private static void send(SseEmitter emitter, SessionView view) {
		try {
			emitter.send(SseEmitter.event().name("progress").data(view));
		}
		catch (IOException | IllegalStateException ex) {
			emitter.completeWithError(ex);
		}
	}
}

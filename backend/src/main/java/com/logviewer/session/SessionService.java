package com.logviewer.session;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.logviewer.config.LogViewerProperties;
import com.logviewer.ingestion.event.IngestionRequested;
import com.logviewer.store.LogStore;

/** Creates, looks up and removes sessions; ingestion itself is triggered through an {@link IngestionRequested} event. */
@Service
public class SessionService {

	private static final Logger log = LoggerFactory.getLogger(SessionService.class);

	private final SessionRegistry registry;

	private final SourceFileService sources;

	private final LogStore store;

	private final LogViewerProperties properties;

	private final ApplicationEventPublisher events;

	public SessionService(SessionRegistry registry, SourceFileService sources, LogStore store,
			LogViewerProperties properties, ApplicationEventPublisher events) {
		this.registry = registry;
		this.sources = sources;
		this.store = store;
		this.properties = properties;
		this.events = events;
	}

	public SessionView createFromUpload(MultipartFile file) throws IOException {
		if (file.isEmpty()) {
			throw new IllegalArgumentException("The uploaded file is empty");
		}
		LogSession session = registry.create(displayName(file.getOriginalFilename()), file.getSize());
		// The multipart temp file disappears when the request ends, so keep our own copy for the async ingestion.
		Path copy = properties.dataDir().resolve(session.id() + ".upload");
		try {
			Files.createDirectories(properties.dataDir());
			file.transferTo(copy);
			store.createSession(session.id());
		}
		catch (IOException | RuntimeException ex) {
			registry.remove(session.id());
			Files.deleteIfExists(copy);
			throw ex;
		}
		events.publishEvent(new IngestionRequested(session.id(), copy, true));
		return session.toView();
	}

	public SessionView createFromPath(String requestedPath) throws IOException {
		Path source = sources.resolve(requestedPath);
		LogSession session = registry.create(source.getFileName().toString(), Files.size(source));
		try {
			store.createSession(session.id());
		}
		catch (RuntimeException ex) {
			registry.remove(session.id());
			throw ex;
		}
		events.publishEvent(new IngestionRequested(session.id(), source, false));
		return session.toView();
	}

	public SessionView get(String id) {
		return registry.get(id).toView();
	}

	public void delete(String id) {
		registry.get(id);
		remove(id);
	}

	@Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT5M")
	void expireIdleSessions() {
		for (String id : registry.idleLongerThan(properties.sessionTtl())) {
			log.info("Session {} expired", id);
			remove(id);
		}
	}

	private void remove(String id) {
		if (registry.remove(id)) {
			events.publishEvent(new SessionRemoved(id));
		}
	}

	private static String displayName(String originalFilename) {
		String name = StringUtils.getFilename(StringUtils.cleanPath(originalFilename == null ? "" : originalFilename));
		return name == null || name.isBlank() ? "upload.log" : name;
	}
}

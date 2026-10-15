package com.logviewer.api;

import java.io.IOException;
import java.net.URI;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.logviewer.session.SessionEventBroadcaster;
import com.logviewer.session.SessionService;
import com.logviewer.session.SessionView;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@RestController
@RequestMapping("/api/sessions")
class SessionController {

	private final SessionService sessions;

	private final SessionEventBroadcaster broadcaster;

	SessionController(SessionService sessions, SessionEventBroadcaster broadcaster) {
		this.sessions = sessions;
		this.broadcaster = broadcaster;
	}

	record FromPathRequest(@NotBlank String path) {
	}

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	ResponseEntity<SessionView> upload(@RequestParam("file") MultipartFile file) throws IOException {
		return accepted(sessions.createFromUpload(file));
	}

	@PostMapping(path = "/from-path", consumes = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<SessionView> fromPath(@Valid @RequestBody FromPathRequest request) throws IOException {
		return accepted(sessions.createFromPath(request.path()));
	}

	@GetMapping("/{id}")
	SessionView get(@PathVariable String id) {
		return sessions.get(id);
	}

	@DeleteMapping("/{id}")
	ResponseEntity<Void> delete(@PathVariable String id) {
		sessions.delete(id);
		return ResponseEntity.noContent().build();
	}

	@GetMapping(path = "/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	SseEmitter events(@PathVariable String id) {
		return broadcaster.subscribe(id);
	}

	private static ResponseEntity<SessionView> accepted(SessionView view) {
		return ResponseEntity.accepted().location(URI.create("/api/sessions/" + view.id())).body(view);
	}
}

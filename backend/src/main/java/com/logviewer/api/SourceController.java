package com.logviewer.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.logviewer.session.PathNotAllowedException;
import com.logviewer.session.SourceFile;
import com.logviewer.session.SourceFileService;

@RestController
@RequestMapping("/api")
class SourceController {

	private final SourceFileService sources;

	SourceController(SourceFileService sources) {
		this.sources = sources;
	}

	record AppConfig(boolean pathSourceEnabled) {
	}

	@GetMapping("/config")
	AppConfig config() {
		return new AppConfig(sources.enabled());
	}

	@GetMapping("/files")
	List<SourceFile> files() {
		if (!sources.enabled()) {
			throw new PathNotAllowedException("Loading files from the server is disabled");
		}
		return sources.list();
	}
}

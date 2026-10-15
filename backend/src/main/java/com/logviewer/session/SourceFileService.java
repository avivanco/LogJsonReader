package com.logviewer.session;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;

import com.logviewer.config.LogViewerProperties;

/** Server-side file access, confined to the directories listed in {@code logviewer.allowed-dirs}. */
@Service
public class SourceFileService {

	private static final Set<String> EXTENSIONS = Set.of(".log", ".json", ".jsonl", ".ndjson", ".txt");

	private static final int MAX_LISTED = 500;

	private static final int MAX_DEPTH = 4;

	private final LogViewerProperties properties;

	public SourceFileService(LogViewerProperties properties) {
		this.properties = properties;
	}

	public boolean enabled() {
		return !properties.allowedDirs().isEmpty();
	}

	/**
	 * Resolves a requested path (absolute, or relative to an allowed directory) to a real regular file inside an
	 * allowed directory. Symlinks and {@code ..} segments cannot escape because the check uses real paths.
	 */
	public Path resolve(String requested) {
		if (!enabled()) {
			throw new PathNotAllowedException("Loading files from the server is disabled");
		}
		Path candidate = Path.of(requested);
		for (Path root : properties.allowedDirs()) {
			Path realRoot = realPath(root);
			if (realRoot == null) {
				continue;
			}
			Path real = realPath(candidate.isAbsolute() ? candidate : realRoot.resolve(candidate));
			if (real != null && real.startsWith(realRoot) && Files.isRegularFile(real)) {
				return real;
			}
		}
		throw new PathNotAllowedException("File not found in the allowed directories");
	}

	public List<SourceFile> list() {
		return properties.allowedDirs().stream()
				.map(SourceFileService::realPath)
				.filter(Objects::nonNull)
				.flatMap(this::listUnder)
				.sorted(Comparator.comparing(SourceFile::modified).reversed())
				.limit(MAX_LISTED)
				.toList();
	}

	private Stream<SourceFile> listUnder(Path root) {
		try (Stream<Path> walk = Files.walk(root, MAX_DEPTH)) {
			return walk.filter(Files::isRegularFile)
					.filter(path -> hasLogExtension(path))
					.map(SourceFileService::describe)
					.toList()
					.stream();
		}
		catch (IOException | UncheckedIOException ex) {
			return Stream.empty();
		}
	}

	private static boolean hasLogExtension(Path path) {
		String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
		return EXTENSIONS.stream().anyMatch(name::endsWith);
	}

	private static SourceFile describe(Path path) {
		try {
			return new SourceFile(path.toString(), Files.size(path),
					Instant.ofEpochMilli(Files.getLastModifiedTime(path).toMillis()));
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	private static Path realPath(Path path) {
		try {
			return path.toRealPath();
		}
		catch (IOException ex) {
			return null;
		}
	}
}

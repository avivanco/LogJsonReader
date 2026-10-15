package com.logviewer.config;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param dataDir where per-session DuckDB files and uploaded files live (wiped of stale files on startup)
 * @param allowedDirs directories from which server-side files may be loaded; empty disables path loading
 * @param sessionTtl idle time after which a session is discarded
 * @param batchSize rows per store append
 * @param maxLineLength lines longer than this are kept truncated and counted as parse errors
 * @param sampleSize number of leading lines used to detect the log format
 * @param duckdbMemoryLimit DuckDB memory limit per session database
 */
@ConfigurationProperties("logviewer")
public record LogViewerProperties(
		Path dataDir,
		List<Path> allowedDirs,
		@DefaultValue("12h") Duration sessionTtl,
		@DefaultValue("10000") int batchSize,
		@DefaultValue("262144") int maxLineLength,
		@DefaultValue("50") int sampleSize,
		@DefaultValue("1GB") String duckdbMemoryLimit) {

	public LogViewerProperties {
		dataDir = dataDir == null ? Path.of(System.getProperty("java.io.tmpdir"), "logviewer") : dataDir;
		allowedDirs = allowedDirs == null ? List.of() : List.copyOf(allowedDirs);
	}
}

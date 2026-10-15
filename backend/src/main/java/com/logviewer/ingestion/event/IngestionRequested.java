package com.logviewer.ingestion.event;

import java.nio.file.Path;

/** @param deleteSourceAfter true for uploaded copies, which are no longer needed once stored */
public record IngestionRequested(String sessionId, Path source, boolean deleteSourceAfter) {
}

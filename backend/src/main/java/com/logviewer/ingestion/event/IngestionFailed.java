package com.logviewer.ingestion.event;

public record IngestionFailed(String sessionId, String reason) {
}

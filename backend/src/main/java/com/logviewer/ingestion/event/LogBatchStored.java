package com.logviewer.ingestion.event;

public record LogBatchStored(String sessionId, int entries) {
}

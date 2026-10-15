package com.logviewer.session;

import java.time.Instant;

public record SourceFile(String path, long size, Instant modified) {
}

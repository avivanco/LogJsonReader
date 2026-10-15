package com.logviewer.domain;

import java.util.List;

/** One page of results; {@code nextCursor} is null when there is nothing more to load. */
public record Page<T>(List<T> items, String nextCursor) {
}

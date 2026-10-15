package com.logviewer.domain;

/** Keyset page request: continue after {@code cursor} (null for the first page). */
public record PageRequest(Cursor cursor, int size, SortDirection sort) {

	public static final int MAX_SIZE = 500;

	public PageRequest {
		if (size < 1 || size > MAX_SIZE) {
			throw new IllegalArgumentException("size must be between 1 and " + MAX_SIZE);
		}
		sort = sort == null ? SortDirection.ASC : sort;
	}
}

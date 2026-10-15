package com.logviewer.store.duckdb.filter;

import java.util.List;

/** A parameterized SQL predicate. Values are never concatenated into {@code sql}; they travel in {@code params}. */
public record SqlFragment(String sql, List<Object> params) {

	public static SqlFragment of(String sql, Object... params) {
		return new SqlFragment(sql, List.of(params));
	}
}

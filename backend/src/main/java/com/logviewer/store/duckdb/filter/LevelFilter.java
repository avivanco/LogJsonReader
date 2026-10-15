package com.logviewer.store.duckdb.filter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.logviewer.domain.LogFilter;
import com.logviewer.domain.LogLevel;

/** Exact levels and/or a minimum severity. */
@Component
public class LevelFilter implements FilterStrategy {

	@Override
	public Optional<SqlFragment> toPredicate(LogFilter filter) {
		List<String> parts = new ArrayList<>();
		List<Object> params = new ArrayList<>();
		if (!filter.levels().isEmpty()) {
			parts.add("level IN (" + "?,".repeat(filter.levels().size() - 1) + "?)");
			filter.levels().forEach(level -> params.add(level.severity()));
		}
		if (filter.minLevel() != null && filter.minLevel() != LogLevel.UNKNOWN) {
			parts.add("level >= ?");
			params.add(filter.minLevel().severity());
		}
		return parts.isEmpty() ? Optional.empty() : Optional.of(new SqlFragment(String.join(" AND ", parts), params));
	}
}

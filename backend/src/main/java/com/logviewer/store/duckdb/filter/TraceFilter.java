package com.logviewer.store.duckdb.filter;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.logviewer.domain.LogFilter;

/** All entries of one trace. */
@Component
public class TraceFilter implements FilterStrategy {

	@Override
	public Optional<SqlFragment> toPredicate(LogFilter filter) {
		return filter.traceId() == null ? Optional.empty() : Optional.of(SqlFragment.of("trace_id = ?", filter.traceId()));
	}
}

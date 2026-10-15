package com.logviewer.store.duckdb.filter;

import java.util.Optional;

import com.logviewer.domain.LogFilter;

/** Strategy translating one criterion of a {@link LogFilter} into a SQL predicate; empty when the criterion is unset. */
public interface FilterStrategy {

	Optional<SqlFragment> toPredicate(LogFilter filter);
}

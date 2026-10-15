package com.logviewer.store.duckdb.filter;

import java.util.ArrayList;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.logviewer.domain.LogFilter;

/** Exact match on any of the selected logger names. */
@Component
public class LoggerFilter implements FilterStrategy {

	@Override
	public Optional<SqlFragment> toPredicate(LogFilter filter) {
		if (filter.loggers().isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(new SqlFragment("logger IN (" + "?,".repeat(filter.loggers().size() - 1) + "?)",
				new ArrayList<>(filter.loggers())));
	}
}

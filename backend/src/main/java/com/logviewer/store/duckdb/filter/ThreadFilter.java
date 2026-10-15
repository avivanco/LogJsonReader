package com.logviewer.store.duckdb.filter;

import java.util.ArrayList;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.logviewer.domain.LogFilter;

/** Exact match on any of the selected thread names. */
@Component
public class ThreadFilter implements FilterStrategy {

	@Override
	public Optional<SqlFragment> toPredicate(LogFilter filter) {
		if (filter.threads().isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(new SqlFragment("thread IN (" + "?,".repeat(filter.threads().size() - 1) + "?)",
				new ArrayList<>(filter.threads())));
	}
}

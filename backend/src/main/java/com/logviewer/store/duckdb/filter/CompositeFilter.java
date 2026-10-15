package com.logviewer.store.duckdb.filter;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.logviewer.domain.LogFilter;

/** Composite: ANDs the predicates of every registered {@link FilterStrategy}. */
@Component
public class CompositeFilter {

	private final List<FilterStrategy> strategies;

	public CompositeFilter(List<FilterStrategy> strategies) {
		this.strategies = List.copyOf(strategies);
	}

	/** @return a predicate usable after {@code WHERE}; {@code TRUE} when the filter is empty */
	public SqlFragment build(LogFilter filter) {
		List<String> parts = new ArrayList<>();
		List<Object> params = new ArrayList<>();
		for (FilterStrategy strategy : strategies) {
			strategy.toPredicate(filter).ifPresent(fragment -> {
				parts.add("(" + fragment.sql() + ")");
				params.addAll(fragment.params());
			});
		}
		return new SqlFragment(parts.isEmpty() ? "TRUE" : String.join(" AND ", parts), params);
	}
}

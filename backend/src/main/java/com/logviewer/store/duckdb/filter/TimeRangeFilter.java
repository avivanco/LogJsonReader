package com.logviewer.store.duckdb.filter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.logviewer.domain.LogFilter;
import com.logviewer.domain.Timestamps;

/** Inclusive {@code from}/{@code to} bounds on the timestamp. */
@Component
public class TimeRangeFilter implements FilterStrategy {

	@Override
	public Optional<SqlFragment> toPredicate(LogFilter filter) {
		List<String> parts = new ArrayList<>();
		List<Object> params = new ArrayList<>();
		if (filter.from() != null) {
			parts.add("ts >= ?");
			params.add(Timestamps.toMicros(filter.from()));
		}
		if (filter.to() != null) {
			parts.add("ts <= ?");
			params.add(Timestamps.toMicros(filter.to()));
		}
		return parts.isEmpty() ? Optional.empty() : Optional.of(new SqlFragment(String.join(" AND ", parts), params));
	}
}

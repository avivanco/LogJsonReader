package com.logviewer.store.duckdb.filter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.logviewer.domain.LogFilter;

/** Exact match on dynamic attributes (e.g. {@code username=alice}). Keys are validated before they reach the JSON path. */
@Component
public class AttributeFilter implements FilterStrategy {

	private static final Pattern SAFE_KEY = Pattern.compile("[\\w.@\\-]{1,64}");

	@Override
	public Optional<SqlFragment> toPredicate(LogFilter filter) {
		if (filter.attributes().isEmpty()) {
			return Optional.empty();
		}
		List<String> parts = new ArrayList<>();
		List<Object> params = new ArrayList<>();
		for (Map.Entry<String, String> attribute : filter.attributes().entrySet()) {
			if (!SAFE_KEY.matcher(attribute.getKey()).matches()) {
				throw new IllegalArgumentException("Invalid attribute name: " + attribute.getKey());
			}
			parts.add("json_extract_string(attributes, ?) = ?");
			params.add("$.\"" + attribute.getKey() + "\"");
			params.add(attribute.getValue());
		}
		return Optional.of(new SqlFragment(String.join(" AND ", parts), params));
	}
}

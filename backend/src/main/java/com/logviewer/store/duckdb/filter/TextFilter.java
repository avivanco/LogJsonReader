package com.logviewer.store.duckdb.filter;

import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.logviewer.domain.LogFilter;

/** Case-insensitive substring search over the whole raw line (message, attributes and stack trace included). */
@Component
public class TextFilter implements FilterStrategy {

	@Override
	public Optional<SqlFragment> toPredicate(LogFilter filter) {
		if (filter.text() == null) {
			return Optional.empty();
		}
		return Optional.of(SqlFragment.of("contains(lower(raw), ?)", filter.text().toLowerCase(Locale.ROOT)));
	}
}

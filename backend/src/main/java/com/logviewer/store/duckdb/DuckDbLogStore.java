package com.logviewer.store.duckdb;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import org.duckdb.DuckDBAppender;
import org.duckdb.DuckDBConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import com.logviewer.config.LogViewerProperties;
import com.logviewer.domain.Cursor;
import com.logviewer.domain.Facets;
import com.logviewer.domain.LogEntry;
import com.logviewer.domain.LogFilter;
import com.logviewer.domain.LogLevel;
import com.logviewer.domain.LogSummary;
import com.logviewer.domain.Page;
import com.logviewer.domain.PageRequest;
import com.logviewer.domain.SessionNotFoundException;
import com.logviewer.domain.SortDirection;
import com.logviewer.domain.Timestamps;
import com.logviewer.store.LogStore;
import com.logviewer.store.StoreException;
import com.logviewer.store.duckdb.filter.CompositeFilter;
import com.logviewer.store.duckdb.filter.SqlFragment;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * One embedded DuckDB database file per session. A master connection keeps each database open; every operation
 * works on a {@link DuckDBConnection#duplicate() duplicate} of it, so ingestion and queries can run concurrently.
 */
@Component
public class DuckDbLogStore implements LogStore, InitializingBean, DisposableBean {

	private static final Logger log = LoggerFactory.getLogger(DuckDbLogStore.class);

	private static final TypeReference<Map<String, String>> ATTRIBUTES = new TypeReference<>() {
	};

	private static final Pattern MEMORY_LIMIT = Pattern.compile("\\d+(\\.\\d+)?\\s?[A-Za-z]{2,3}");

	private static final String SUMMARY_COLUMNS = "seq, ts, level, logger, thread, left(message, 2000) AS message, "
			+ "trace_id, span_id, stack_trace IS NOT NULL AS has_stack, attributes";

	private static final String ENTRY_COLUMNS = "seq, ts, level, logger, thread, message, trace_id, span_id, "
			+ "stack_trace, attributes, raw";

	private static final long[] BUCKET_MILLIS = {1, 10, 100, 500, 1_000, 5_000, 10_000, 30_000, 60_000, 300_000,
			600_000, 1_800_000, 3_600_000, 10_800_000, 21_600_000, 43_200_000, 86_400_000, 604_800_000};

	private static final int TARGET_BUCKETS = 80;

	private static final int TOP_VALUES = 25;

	private final LogViewerProperties properties;

	private final CompositeFilter filters;

	private final JsonMapper mapper;

	private final Map<String, DuckDBConnection> masters = new ConcurrentHashMap<>();

	public DuckDbLogStore(LogViewerProperties properties, CompositeFilter filters, JsonMapper mapper) {
		this.properties = properties;
		this.filters = filters;
		this.mapper = mapper;
	}

	/** Sessions live in memory only, so database files left by a previous run are garbage. */
	@Override
	public void afterPropertiesSet() throws IOException {
		Path dir = properties.dataDir();
		Files.createDirectories(dir);
		try (DirectoryStream<Path> stale = Files.newDirectoryStream(dir, "*.{duckdb,duckdb.wal,upload}")) {
			for (Path file : stale) {
				Files.deleteIfExists(file);
			}
		}
	}

	@Override
	public void createSession(String sessionId) {
		if (!MEMORY_LIMIT.matcher(properties.duckdbMemoryLimit()).matches()) {
			throw new IllegalStateException("Invalid logviewer.duckdb-memory-limit");
		}
		try {
			Files.createDirectories(properties.dataDir());
			DuckDBConnection master = (DuckDBConnection) DriverManager
					.getConnection("jdbc:duckdb:" + databaseFile(sessionId));
			try (Statement statement = master.createStatement()) {
				statement.execute("SET memory_limit = '" + properties.duckdbMemoryLimit() + "'");
				statement.execute("""
						CREATE TABLE logs (
							seq BIGINT NOT NULL,
							ts BIGINT NOT NULL,
							level TINYINT NOT NULL,
							logger VARCHAR,
							thread VARCHAR,
							message VARCHAR,
							trace_id VARCHAR,
							span_id VARCHAR,
							stack_trace VARCHAR,
							attributes VARCHAR,
							raw VARCHAR
						)""");
			}
			masters.put(sessionId, master);
		}
		catch (SQLException | IOException ex) {
			throw new StoreException("Could not create the log database", ex);
		}
	}

	@Override
	public void append(String sessionId, List<LogEntry> batch) {
		try (DuckDBConnection connection = connection(sessionId);
				DuckDBAppender appender = connection.createAppender("main", "logs")) {
			for (LogEntry entry : batch) {
				appender.beginRow()
						.append(entry.seq())
						.append(Timestamps.toMicros(entry.timestamp()))
						.append((byte) entry.level().severity());
				string(appender, entry.logger());
				string(appender, entry.thread());
				string(appender, entry.message());
				string(appender, entry.traceId());
				string(appender, entry.spanId());
				string(appender, entry.stackTrace());
				string(appender, entry.attributes().isEmpty() ? null : mapper.writeValueAsString(entry.attributes()));
				string(appender, entry.raw());
				appender.endRow();
			}
		}
		catch (SQLException ex) {
			throw new StoreException("Could not append log entries", ex);
		}
	}

	@Override
	public Page<LogSummary> query(String sessionId, LogFilter filter, PageRequest page) {
		SqlFragment where = filters.build(filter);
		boolean ascending = page.sort() == SortDirection.ASC;
		String direction = ascending ? "ASC" : "DESC";
		StringBuilder sql = new StringBuilder("SELECT ").append(SUMMARY_COLUMNS)
				.append(" FROM logs WHERE ").append(where.sql());
		List<Object> params = new ArrayList<>(where.params());
		if (page.cursor() != null) {
			String operator = ascending ? ">" : "<";
			sql.append(" AND (ts ").append(operator).append(" ? OR (ts = ? AND seq ").append(operator).append(" ?))");
			params.add(page.cursor().ts());
			params.add(page.cursor().ts());
			params.add(page.cursor().seq());
		}
		sql.append(" ORDER BY ts ").append(direction).append(", seq ").append(direction).append(" LIMIT ?");
		params.add(page.size() + 1);

		List<LogSummary> rows;
		try (DuckDBConnection connection = connection(sessionId)) {
			rows = select(connection, sql.toString(), params, this::readSummary);
		}
		catch (SQLException ex) {
			throw new StoreException("Query failed", ex);
		}
		if (rows.size() <= page.size()) {
			return new Page<>(rows, null);
		}
		List<LogSummary> items = new ArrayList<>(rows.subList(0, page.size()));
		LogSummary last = items.get(items.size() - 1);
		return new Page<>(items, new Cursor(Timestamps.toMicros(last.timestamp()), last.seq()).encode());
	}

	@Override
	public Optional<LogEntry> find(String sessionId, long seq) {
		try (DuckDBConnection connection = connection(sessionId)) {
			return select(connection, "SELECT " + ENTRY_COLUMNS + " FROM logs WHERE seq = ?", List.of(seq),
					this::readEntry).stream().findFirst();
		}
		catch (SQLException ex) {
			throw new StoreException("Lookup failed", ex);
		}
	}

	@Override
	public Facets facets(String sessionId, LogFilter filter) {
		SqlFragment full = filters.build(filter);
		SqlFragment withoutLevels = filters.build(filter.withoutLevels());
		SqlFragment withoutTime = filters.build(filter.withoutTimeRange());
		try (DuckDBConnection connection = connection(sessionId)) {
			long total = select(connection, "SELECT count(*) FROM logs WHERE " + full.sql(), full.params(),
					rs -> rs.getLong(1)).get(0);

			Map<LogLevel, Long> levelCounts = new EnumMap<>(LogLevel.class);
			select(connection, "SELECT level, count(*) FROM logs WHERE " + withoutLevels.sql() + " GROUP BY level",
					withoutLevels.params(), rs -> levelCounts.put(LogLevel.fromSeverity(rs.getInt(1)), rs.getLong(2)));

			return new Facets(total, levelCounts,
					topValues(connection, "logger", full), topValues(connection, "thread", full),
					attributeKeys(connection), histogram(connection, withoutTime));
		}
		catch (SQLException ex) {
			throw new StoreException("Facet query failed", ex);
		}
	}

	@Override
	public void export(String sessionId, LogFilter filter, Consumer<LogEntry> sink) {
		SqlFragment where = filters.build(filter);
		String sql = "SELECT " + ENTRY_COLUMNS + " FROM logs WHERE " + where.sql() + " ORDER BY seq";
		try (DuckDBConnection connection = connection(sessionId);
				PreparedStatement statement = connection.prepareStatement(sql)) {
			bind(statement, where.params());
			try (ResultSet rs = statement.executeQuery()) {
				while (rs.next()) {
					sink.accept(readEntry(rs));
				}
			}
		}
		catch (SQLException ex) {
			throw new StoreException("Export failed", ex);
		}
	}

	@Override
	public void dropSession(String sessionId) {
		DuckDBConnection master = masters.remove(sessionId);
		if (master != null) {
			try {
				master.close();
			}
			catch (SQLException ex) {
				log.warn("Could not close database of session {}", sessionId, ex);
			}
		}
		Path file = databaseFile(sessionId);
		try {
			Files.deleteIfExists(file);
			Files.deleteIfExists(Path.of(file + ".wal"));
		}
		catch (IOException ex) {
			log.warn("Could not delete database file {}", file, ex);
		}
	}

	@Override
	public void destroy() {
		List.copyOf(masters.keySet()).forEach(this::dropSession);
	}

	// --- facets ---------------------------------------------------------------------------------------------------

	private List<Facets.ValueCount> topValues(Connection connection, String column, SqlFragment where)
			throws SQLException {
		String sql = "SELECT " + column + ", count(*) AS c FROM logs WHERE (" + where.sql() + ") AND " + column
				+ " IS NOT NULL GROUP BY " + column + " ORDER BY c DESC, " + column + " LIMIT " + TOP_VALUES;
		return select(connection, sql, where.params(), rs -> new Facets.ValueCount(rs.getString(1), rs.getLong(2)));
	}

	/** Attribute names are taken from a sample of the data; scanning every row would be wasteful. */
	private List<String> attributeKeys(Connection connection) throws SQLException {
		String sql = "SELECT DISTINCT unnest(json_keys(attributes)) AS k FROM "
				+ "(SELECT attributes FROM logs WHERE attributes IS NOT NULL LIMIT 20000) ORDER BY k LIMIT 100";
		return select(connection, sql, List.of(), rs -> rs.getString(1));
	}

	private Facets.Histogram histogram(Connection connection, SqlFragment where) throws SQLException {
		String scope = "(" + where.sql() + ") AND ts > 0";
		long[] range = select(connection, "SELECT min(ts), max(ts) FROM logs WHERE " + scope, where.params(),
				rs -> new long[] {rs.getLong(1), rs.getLong(2), rs.getObject(1) == null ? 0 : 1}).get(0);
		if (range[2] == 0) {
			return null;
		}
		long bucketMillis = chooseBucketMillis((range[1] - range[0]) / 1_000);
		long bucketMicros = bucketMillis * 1_000;

		List<Object> params = new ArrayList<>(List.of(bucketMicros, bucketMicros));
		params.addAll(where.params());
		String sql = "SELECT (ts // ?) * ? AS bucket, level, count(*) FROM logs WHERE " + scope
				+ " GROUP BY bucket, level ORDER BY bucket";
		Map<Long, Map<LogLevel, Long>> byBucket = new LinkedHashMap<>();
		select(connection, sql, params, rs -> byBucket
				.computeIfAbsent(rs.getLong(1), key -> new EnumMap<>(LogLevel.class))
				.put(LogLevel.fromSeverity(rs.getInt(2)), rs.getLong(3)));

		List<Facets.Bucket> buckets = new ArrayList<>(byBucket.size());
		byBucket.forEach((start, counts) -> buckets.add(new Facets.Bucket(Timestamps.fromMicros(start), counts)));
		return new Facets.Histogram(Timestamps.fromMicros(range[0]), Timestamps.fromMicros(range[1]), bucketMillis,
				buckets);
	}

	private static long chooseBucketMillis(long spanMillis) {
		for (long candidate : BUCKET_MILLIS) {
			if (spanMillis / candidate + 1 <= TARGET_BUCKETS) {
				return candidate;
			}
		}
		return BUCKET_MILLIS[BUCKET_MILLIS.length - 1];
	}

	// --- row mapping & JDBC helpers -----------------------------------------------------------------------------

	private LogSummary readSummary(ResultSet rs) throws SQLException {
		return new LogSummary(rs.getLong("seq"), Timestamps.fromMicros(rs.getLong("ts")),
				LogLevel.fromSeverity(rs.getInt("level")), rs.getString("logger"), rs.getString("thread"),
				rs.getString("message"), rs.getString("trace_id"), rs.getString("span_id"),
				rs.getBoolean("has_stack"), readAttributes(rs.getString("attributes")));
	}

	private LogEntry readEntry(ResultSet rs) throws SQLException {
		Instant timestamp = Timestamps.fromMicros(rs.getLong("ts"));
		return new LogEntry(rs.getLong("seq"), timestamp, LogLevel.fromSeverity(rs.getInt("level")),
				rs.getString("logger"), rs.getString("thread"), rs.getString("message"), rs.getString("trace_id"),
				rs.getString("span_id"), rs.getString("stack_trace"), readAttributes(rs.getString("attributes")),
				rs.getString("raw"));
	}

	private Map<String, String> readAttributes(String json) {
		if (json == null) {
			return Map.of();
		}
		try {
			return mapper.readValue(json, ATTRIBUTES);
		}
		catch (JacksonException ex) {
			return Map.of();
		}
	}

	private static void string(DuckDBAppender appender, String value) throws SQLException {
		if (value == null) {
			appender.appendNull();
		}
		else {
			appender.append(value);
		}
	}

	private DuckDBConnection connection(String sessionId) throws SQLException {
		DuckDBConnection master = masters.get(sessionId);
		if (master == null) {
			throw new SessionNotFoundException(sessionId);
		}
		return master.duplicate();
	}

	private Path databaseFile(String sessionId) {
		return properties.dataDir().resolve(sessionId + ".duckdb");
	}

	private static void bind(PreparedStatement statement, List<Object> params) throws SQLException {
		for (int i = 0; i < params.size(); i++) {
			statement.setObject(i + 1, params.get(i));
		}
	}

	private static <T> List<T> select(Connection connection, String sql, List<Object> params, RowMapper<T> mapper)
			throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			bind(statement, params);
			try (ResultSet rs = statement.executeQuery()) {
				List<T> rows = new ArrayList<>();
				while (rs.next()) {
					rows.add(mapper.map(rs));
				}
				return rows;
			}
		}
	}

	@FunctionalInterface
	private interface RowMapper<T> {

		T map(ResultSet rs) throws SQLException;
	}
}

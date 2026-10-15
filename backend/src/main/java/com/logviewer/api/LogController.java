package com.logviewer.api;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.logviewer.domain.Cursor;
import com.logviewer.domain.Facets;
import com.logviewer.domain.LogEntry;
import com.logviewer.domain.LogFilter;
import com.logviewer.domain.LogSummary;
import com.logviewer.domain.Page;
import com.logviewer.domain.PageRequest;
import com.logviewer.domain.SortDirection;
import com.logviewer.query.QueryService;

import tools.jackson.databind.json.JsonMapper;

@RestController
@RequestMapping("/api/sessions/{id}")
class LogController {

	private static final MediaType NDJSON = MediaType.parseMediaType("application/x-ndjson");

	private static final MediaType CSV = MediaType.parseMediaType("text/csv");

	private static final List<String> CSV_HEADER = List.of("timestamp", "level", "logger", "thread", "message",
			"trace_id", "span_id", "attributes", "stack_trace");

	private final QueryService queries;

	private final JsonMapper mapper;

	LogController(QueryService queries, JsonMapper mapper) {
		this.queries = queries;
		this.mapper = mapper;
	}

	@GetMapping("/logs")
	Page<LogSummary> logs(@PathVariable String id,
			@RequestParam(required = false) String cursor,
			@RequestParam(defaultValue = "100") int size,
			@RequestParam(defaultValue = "asc") String sort,
			@RequestParam MultiValueMap<String, String> params) {
		PageRequest page = new PageRequest(cursor == null || cursor.isBlank() ? null : Cursor.decode(cursor), size,
				sortDirection(sort));
		return queries.logs(id, LogFilterParser.parse(params), page);
	}

	@GetMapping("/logs/{seq}")
	ResponseEntity<LogEntry> entry(@PathVariable String id, @PathVariable long seq) {
		return queries.entry(id, seq).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
	}

	@GetMapping("/facets")
	Facets facets(@PathVariable String id, @RequestParam MultiValueMap<String, String> params) {
		return queries.facets(id, LogFilterParser.parse(params));
	}

	@GetMapping("/export")
	ResponseEntity<StreamingResponseBody> export(@PathVariable String id,
			@RequestParam(defaultValue = "ndjson") String format,
			@RequestParam MultiValueMap<String, String> params) {
		boolean csv = switch (format.toLowerCase(Locale.ROOT)) {
			case "csv" -> true;
			case "ndjson" -> false;
			default -> throw new IllegalArgumentException("format must be 'ndjson' or 'csv'");
		};
		LogFilter filter = LogFilterParser.parse(params);
		queries.requireSession(id);

		StreamingResponseBody body = out -> {
			Writer writer = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
			if (csv) {
				writeCsvRow(writer, CSV_HEADER);
			}
			queries.export(id, filter, entry -> write(writer, entry, csv));
			writer.flush();
		};
		return ResponseEntity.ok()
				.contentType(csv ? CSV : NDJSON)
				.header(HttpHeaders.CONTENT_DISPOSITION,
						ContentDisposition.attachment().filename("logviewer-export." + (csv ? "csv" : "ndjson")).build()
								.toString())
				.body(body);
	}

	private void write(Writer writer, LogEntry entry, boolean csv) {
		try {
			if (csv) {
				writeCsvRow(writer, List.of(
						entry.timestamp().toString(), entry.level().name(), nullToEmpty(entry.logger()),
						nullToEmpty(entry.thread()), nullToEmpty(entry.message()), nullToEmpty(entry.traceId()),
						nullToEmpty(entry.spanId()),
						entry.attributes().isEmpty() ? "" : mapper.writeValueAsString(entry.attributes()),
						nullToEmpty(entry.stackTrace())));
			}
			else {
				writer.write(entry.raw());
				writer.write('\n');
			}
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	private static void writeCsvRow(Writer writer, List<String> cells) throws IOException {
		StringBuilder row = new StringBuilder();
		for (String cell : cells) {
			if (!row.isEmpty()) {
				row.append(',');
			}
			row.append('"').append(neutralizeFormula(cell).replace("\"", "\"\"")).append('"');
		}
		writer.write(row.append("\r\n").toString());
	}

	/** Prevents spreadsheet applications from evaluating log content as a formula. */
	private static String neutralizeFormula(String cell) {
		if (!cell.isEmpty() && "=+-@\t\r".indexOf(cell.charAt(0)) >= 0) {
			return "'" + cell;
		}
		return cell;
	}

	private static String nullToEmpty(String value) {
		return value == null ? "" : value;
	}

	private static SortDirection sortDirection(String sort) {
		return switch (sort.toLowerCase(Locale.ROOT)) {
			case "asc" -> SortDirection.ASC;
			case "desc" -> SortDirection.DESC;
			default -> throw new IllegalArgumentException("sort must be 'asc' or 'desc'");
		};
	}
}

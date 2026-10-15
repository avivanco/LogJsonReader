package com.logviewer.ingestion;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.logviewer.config.LogViewerProperties;
import com.logviewer.domain.LogEntry;
import com.logviewer.ingestion.event.IngestionCompleted;
import com.logviewer.ingestion.event.IngestionFailed;
import com.logviewer.ingestion.event.IngestionRequested;
import com.logviewer.ingestion.event.IngestionStarted;
import com.logviewer.ingestion.event.LogBatchStored;
import com.logviewer.ingestion.parser.LogFormatParser;
import com.logviewer.ingestion.parser.LogParserFactory;
import com.logviewer.ingestion.parser.MalformedLogLineException;
import com.logviewer.session.LogSession;
import com.logviewer.session.SessionRegistry;
import com.logviewer.store.LogStore;

/**
 * Reacts to {@link IngestionRequested} on a virtual thread: streams the file, detects its format from the first
 * lines, parses every line and appends batches to the {@link LogStore}, publishing progress events along the way.
 */
@Service
public class IngestionService {

	private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

	private final LogViewerProperties properties;

	private final LogParserFactory parserFactory;

	private final LogStore store;

	private final SessionRegistry registry;

	private final ApplicationEventPublisher events;

	public IngestionService(LogViewerProperties properties, LogParserFactory parserFactory, LogStore store,
			SessionRegistry registry, ApplicationEventPublisher events) {
		this.properties = properties;
		this.parserFactory = parserFactory;
		this.store = store;
		this.registry = registry;
		this.events = events;
	}

	@Async("ingestionExecutor")
	@EventListener
	public void on(IngestionRequested request) {
		LogSession session = registry.peek(request.sessionId());
		if (session == null) {
			deleteQuietly(request);
			return;
		}
		events.publishEvent(new IngestionStarted(session.id()));
		try {
			ingest(session, request.source());
			session.markReady();
			events.publishEvent(new IngestionCompleted(session.id()));
		}
		catch (Exception ex) {
			log.warn("Ingestion of session {} failed", session.id(), ex);
			session.markFailed(ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName());
			events.publishEvent(new IngestionFailed(session.id(), session.toView().error()));
		}
		finally {
			deleteQuietly(request);
		}
	}

	private void ingest(LogSession session, Path source) throws IOException {
		try (CountingInputStream counter = new CountingInputStream(Files.newInputStream(source));
				InputStreamReader reader = new InputStreamReader(counter, StandardCharsets.UTF_8)) {
			LineReader lines = new LineReader(reader, properties.maxLineLength());

			List<NumberedLine> sample = new ArrayList<>(properties.sampleSize());
			long lineNumber = 0;
			String line;
			while (sample.size() < properties.sampleSize() && (line = lines.readLine()) != null) {
				lineNumber++;
				if (!line.isBlank()) {
					sample.add(new NumberedLine(lineNumber, line, lines.lastLineTruncated()));
				}
			}

			LogFormatParser parser = parserFactory.forSample(sample.stream().map(NumberedLine::text).toList());
			session.format(parser.formatName());
			Run run = new Run(session, parser, counter);
			sample.forEach(run::accept);
			while ((line = lines.readLine()) != null) {
				lineNumber++;
				if (!line.isBlank()) {
					run.accept(new NumberedLine(lineNumber, line, lines.lastLineTruncated()));
				}
			}
			run.flush();
		}
	}

	private void deleteQuietly(IngestionRequested request) {
		if (request.deleteSourceAfter()) {
			try {
				Files.deleteIfExists(request.source());
			}
			catch (IOException ex) {
				log.warn("Could not delete {}", request.source(), ex);
			}
		}
	}

	private record NumberedLine(long number, String text, boolean truncated) {
	}

	/** Mutable per-file state: the current batch, counters, and the last seen timestamp. */
	private final class Run {

		private final LogSession session;

		private final LogFormatParser parser;

		private final CountingInputStream counter;

		private final List<LogEntry> batch = new ArrayList<>(properties.batchSize());

		private long entries;

		private long parseErrors;

		private Instant lastTimestamp = Instant.EPOCH;

		Run(LogSession session, LogFormatParser parser, CountingInputStream counter) {
			this.session = session;
			this.parser = parser;
			this.counter = counter;
		}

		void accept(NumberedLine line) {
			LogEntry entry;
			try {
				if (line.truncated()) {
					throw new MalformedLogLineException("Line exceeds the maximum length");
				}
				entry = parser.parse(line.number(), line.text());
			}
			catch (MalformedLogLineException ex) {
				parseErrors++;
				entry = LogEntry.unparsed(line.number(), line.text(), lastTimestamp);
			}
			// Lines without a timestamp (stack trace fragments, plain text) stay next to their predecessor.
			if (entry.timestamp() == null) {
				entry = entry.withTimestamp(lastTimestamp);
			}
			lastTimestamp = entry.timestamp();
			batch.add(entry);
			if (batch.size() >= properties.batchSize()) {
				flush();
			}
		}

		void flush() {
			if (batch.isEmpty()) {
				return;
			}
			int size = batch.size();
			store.append(session.id(), batch);
			batch.clear();
			entries += size;
			session.progress(counter.count(), entries, parseErrors);
			events.publishEvent(new LogBatchStored(session.id(), size));
		}
	}
}

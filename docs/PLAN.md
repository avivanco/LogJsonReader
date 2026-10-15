# LogJsonReader — Plan

## Context
Engineers need a friendly way to read JSON-structured logs (one JSON object per line, NDJSON) instead of grepping raw files. The tool must load a log file, show it readably, filter by severity/date/other criteria, and be trivial to share with colleagues.

Decisions taken with the user: **React + TypeScript** frontend, **Spring Boot (Java 25)** backend, shipped as **one Docker image / fat JAR** (Spring serves the built SPA), sized for **files up to ~500 MB** via **upload or server-side path**.

The sample in `docs/logExample/logExample.log` is a Logstash-Logback-encoder line with OpenTelemetry MDC fields (single line, no trailing newline):
`@timestamp, level, thread_name, logger_name, message, trace_id, trace_flags, span_id, username`.
The design must therefore treat everything beyond a few core fields as **dynamic attributes**, and tolerate stack traces, malformed lines and a missing final newline.

## Architecture overview

```
 React SPA (Vite)  ──REST/SSE──►  Spring Boot  ──►  LogStore (port)  ──►  DuckDB (embedded, per-session file)
  upload, filters,                 ingestion │ query │ sessions
  virtual table, charts            events (ApplicationEventPublisher) ─► SSE progress
```

Monorepo layout:
```
LogJsonReader/
  backend/    Spring Boot 4 / Java 25, Gradle (Kotlin DSL)
  frontend/   Vite + React + TS
  docs/       sample logs, ADRs
  Dockerfile  multi-stage: node build -> gradle build (SPA copied to static/) -> temurin 25 JRE
  compose.yml
```

### Backend packages (hexagonal-lite, `com.logviewer`)
- `session` — `LogSession` (id, source, status, stats, TTL), `SessionRegistry`, scheduled cleanup.
- `ingestion` — `IngestionService`, `LogSource` (upload stream / allow-listed path), batching, error counting.
  - `ingestion.parser` — format handling (patterns below).
  - `ingestion.event` — domain events.
- `store` — `LogStore` port + `DuckDbLogStore` adapter (+ `InMemoryLogStore` for tests).
- `query` — `LogQuery`, filter strategies, `QueryService`, facets/histogram.
- `api` — controllers, DTOs, error handling (RFC 9457 `ProblemDetail`).
- `config` — properties (`logviewer.*`), profiles, SPA fallback routing.

### Design patterns (where each earns its keep)
| Pattern | Use |
|---|---|
| **Strategy** | `LogFormatParser` implementations: `LogstashParser` (the sample), `LogbackJsonParser`, `PinoBunyanParser` (numeric levels 10–60), `GenericJsonParser` (user-supplied field mapping). Also `FilterStrategy` per criterion: `LevelFilter`, `TimeRangeFilter`, `TextFilter`, `LoggerFilter`, `FieldFilter`, `TraceFilter` — each contributes a SQL predicate + params. |
| **Template Method** | `AbstractJsonLogParser.parse(line)`: `readTree → extractTimestamp → extractLevel → extractMessage → extractCoreFields → collectAttributes → postProcess()`. Subclasses override only the hooks (field names, level normalization). |
| **Chain of Responsibility** | Format detection: each `FormatDetector` inspects the first N sample lines and either claims the file or passes on; the chain ends at the generic parser. Also used for `LineSanitizer`/post-processing steps (e.g. truncate huge messages, redact configured keys). |
| **Factory** | `LogParserFactory.forSample(lines)` runs the detector chain and returns the parser; `FilterFactory` builds the filter strategy list from request params. |
| **Composite/Specification** | `CompositeFilter` ANDs the strategies into one parameterized `WHERE`. |
| **Observer / event-driven** | See below. |

### Event-driven ingestion
Spring `ApplicationEventPublisher` (no broker needed for a single-node tool; the events are the seam if a broker is wanted later):
`IngestionRequested → IngestionStarted → LogBatchStored(count, bytesRead) → IngestionCompleted(stats) | IngestionFailed(reason)`.
- `IngestionService` runs on **virtual threads**, streams the source with Jackson `JsonParser`/`BufferedReader`, builds batches (e.g. 10k) and appends via the DuckDB Appender.
- A `ProgressListener` turns events into **SSE** messages (`GET /api/sessions/{id}/events`) so the UI shows a progress bar and can open the table as soon as the first batch lands.
- A `SessionCleanupListener` reacts to `SessionExpired` to delete the DuckDB file.

### Data model (`LogEntry`, normalized)
`seq` (line number, tiebreaker), `timestamp` (TIMESTAMP_NS), `level` (enum TRACE<DEBUG<INFO<WARN<ERROR<FATAL, UNKNOWN; aliases WARNING/SEVERE/numeric), `logger`, `thread`, `message`, `traceId`, `spanId`, `stackTrace`, `attributes` (JSON — e.g. `username`, `trace_flags`), `raw` (original line). Malformed lines are kept as `UNKNOWN` entries with the raw text and counted in `parseErrors`.

### Storage choice
DuckDB embedded (columnar, fast filtered scans/aggregations over millions of rows, JSON functions for dynamic attributes). Hidden behind `LogStore` so H2/in-memory can replace it. Risk: native JDBC library — use a glibc-based base image (`eclipse-temurin:25-jre`), not Alpine.

### REST API
- `POST /api/sessions` (multipart upload, size cap via `spring.servlet.multipart` + streaming) → `{sessionId}`
- `POST /api/sessions/from-path {path}` — **local profile only**, restricted to `logviewer.allowed-dirs`
- `GET /api/sessions/{id}` status/stats · `DELETE /api/sessions/{id}` · `GET /api/sessions/{id}/events` (SSE)
- `GET /api/sessions/{id}/logs?level=&minLevel=&from=&to=&q=&logger=&thread=&traceId=&attr.username=&sort=&cursor=&size=` — **keyset pagination** on `(timestamp, seq)`, never OFFSET
- `GET /api/sessions/{id}/logs/{seq}` full entry (raw, stack trace, attributes)
- `GET /api/sessions/{id}/facets` — counts per level, top loggers/threads, attribute keys, time histogram (bucket size derived from range) under the *current* filters
- `GET /api/sessions/{id}/export?format=ndjson|csv` + same filter params

### Frontend (`frontend/src`)
Stack: Vite, React 19, TypeScript, TanStack Query (server state), TanStack Table + TanStack Virtual (virtualized grid), Zustand or URL-state for filters, Tailwind + Radix/shadcn, Vitest + Testing Library, Playwright.
- `features/upload` — drag & drop / path input, SSE progress.
- `features/filters` — severity chips with counts, date-range picker (+ quick ranges), debounced full-text, logger/thread selectors, dynamic attribute filters, "filter by this value" actions from any cell.
- `features/timeline` — stacked-by-level histogram; brush to set the date range (use the `dataviz` skill for palette/accessibility; severity colors must not rely on color alone — add level labels/icons).
- `features/logtable` — virtualized infinite table, sticky header, color-coded level badge, relative/absolute time toggle, column picker, wrap/no-wrap, row expand/detail drawer with pretty JSON + collapsible stack trace, copy raw/JSON.
- `features/trace` — click a `trace_id` to pivot to correlated logs across the file.
- Dark mode, keyboard shortcuts (`/` search, `j/k` navigate).
- Message text is always rendered as text (never `innerHTML`) to avoid XSS from log content.

### Sharing & collaboration
- **One artifact**: `docker run -p 8080:8080 -v ./logs:/logs logviewer` or `java -jar logviewer.jar`; binds to localhost by default.
- **Shareable filter state in the URL** (`?level=ERROR&from=…&q=…`) — with a shared server deployment colleagues open the same view; saved views exportable/importable as JSON for local use.
- **Export filtered subset** (NDJSON/CSV) to hand a trimmed file to a colleague.
- Later: `server` profile (auth via Spring Security/OIDC, persistent sessions, permalinks to sessions).

### Security & robustness
Path allow-list + canonicalization (no traversal) for `from-path`; upload size limit; session TTL & disk quota; parameterized SQL only (attribute keys validated against a whitelist regex); bound line length (truncate giant lines); CORS only for Vite dev server; never mutate the source file.

## Execution kickoff (first actions after approval)
Toolchain verified: JDK 25.0.2, Gradle 9.8 (wrapper), Node 24.19 / npm 11.17, Docker 29.8 (no git repo yet).
1. Copy this plan to `docs/PLAN.md` in the project (exact copy of this file).
2. `git init` and add a `.gitignore` (Java, Node, DuckDB files, IDE). Do not commit unless asked.
3. Execute **M0 → M1** from the roadmap: scaffold `backend/` (Spring Boot + Java 25, Gradle Kotlin DSL + wrapper) and `frontend/` (Vite + React + TS), then implement the parsers, `LogStore`/DuckDB, ingestion events + SSE, verified with `./gradlew build` against `docs/logExample/logExample.log`.
4. Continue M2 → M5 in order, building and testing at each milestone; report progress at each milestone boundary.

## Delivery roadmap
1. **M0 Scaffold** — Gradle project (Spring Boot, Java 25), Vite app, CI build, Dockerfile skeleton.
2. **M1 Ingestion** — parser strategies + detector chain, DuckDB store, events + SSE progress. Tests against `docs/logExample/logExample.log` + generated fixtures.
3. **M2 Query API** — filter strategies, keyset pagination, facets/histogram.
4. **M3 Frontend MVP** — upload, virtual table, severity + date + text filters, detail drawer.
5. **M4 Insights** — timeline brush, attribute filters, trace pivot, export, URL state.
6. **M5 Packaging** — SPA bundled into the JAR, Docker image, README/usage docs.
7. **M6 Optional** — live tail (file watcher → events → SSE), multi-file merge, server profile with auth.

## Critical files to create (initial)
`backend/build.gradle.kts`, `backend/src/main/java/com/logviewer/ingestion/parser/{LogFormatParser,AbstractJsonLogParser,LogstashParser,LogParserFactory,FormatDetector}.java`, `.../store/{LogStore,DuckDbLogStore}.java`, `.../query/{LogQuery,FilterStrategy,CompositeFilter}.java`, `.../api/SessionController.java`, `frontend/src/features/*`, `Dockerfile`.

## Verification
- **Backend**: JUnit 5 unit tests per parser/filter (sample line, malformed line, no trailing newline, numeric levels, nanosecond timestamps); Spring `MockMvc` integration tests for upload → query → facets; `./gradlew build`.
- **Performance**: generate a ~500 MB / ~3M-line fixture; assert ingestion completes with bounded heap (`-Xmx512m`), first page returns <300 ms, a filtered query <1 s.
- **Frontend**: Vitest for filter-state/URL serialization; Playwright e2e: upload sample → filter `ERROR` → set date range → open detail → click trace id.
- **Packaging**: `docker build` and `docker run`, open `http://localhost:8080`, upload the sample file and confirm the whole flow in a browser.

## Open items (decide during implementation)
- Whether to use DuckDB or H2 after a quick spike on Windows/Docker native-library behavior.
- Exact Spring Boot version (4.x GA preferred if Java 25 support is confirmed in the BOM).

---

## Implementation notes (status after M0–M5)

Built as planned, with these deliberate deviations and decisions:

- **Build tool:** Gradle (Kotlin DSL) instead of Maven. The UI is built by Gradle tasks (`installFrontend`, `buildFrontend`) and
  copied into the jar under `/static`; `-PskipFrontend` skips them (used by the Docker build and for backend-only work).
  An untracked `backend/local.properties` can move `build/` out of a OneDrive/Dropbox folder, whose sync client locks files.
- **Package layout:** the domain model lives in `com.logviewer.domain`; the SQL `FilterStrategy` implementations and
  `CompositeFilter` live next to the DuckDB adapter (`store.duckdb.filter`) because they emit SQL, which keeps `query` free of
  persistence details. There is no `FilterFactory`: `LogFilterParser` (API layer) builds the immutable `LogFilter` and the
  Spring-injected strategy list does the rest.
- **Storage:** DuckDB (validated on Windows and in a glibc image; H2 was not needed). Timestamps are stored as epoch
  microseconds in a `BIGINT` (`ts`) instead of `TIMESTAMP_NS`; the original line (with nanoseconds) is kept in `raw`. Levels are
  stored as their numeric severity so "minimum level" is a plain comparison.
- **Lines without a timestamp** (stack-trace fragments, plain text) inherit the previous line's timestamp, so they stay next to
  their parent and keep the `(ts, seq)` keyset ordering total.
- **Upload:** multipart upload copied to the data dir, ingested on a virtual thread, then deleted. Server-side files are opened
  in place and never modified or deleted.
- **Not done yet (M6):** live tail, multi-file merge, authentication / `server` profile.
- **Measured** (2.5M lines, 840 MB, `-Xmx512m`): load completes in roughly 70 seconds (polled every 2 s, so approximate) with a usable table throughout; first page
  ~0.25 s; filtered pages 0.2–0.8 s; facets 0.3 s, but facets combined with free-text search ~2 s (candidate for a materialised
  filter or a text index).

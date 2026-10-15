# LogJsonReader

Open a JSON log file (one JSON object per line) and read it comfortably: filter by severity, time range, logger, user or
any field, follow a trace across the file, and share the exact view with a link.

- **Backend:** Spring Boot 4 / Java 25, DuckDB as an embedded per-session store, Gradle.
- **Frontend:** React 19 + TypeScript (Vite, TanStack Query/Virtual, Tailwind).
- **Shipped as one artifact:** the UI is bundled into the jar, so `java -jar` or one Docker image is all a colleague needs.

The full design (patterns, API, roadmap) is in [`docs/PLAN.md`](docs/PLAN.md).

## Features

| | |
|---|---|
| **Load** | Drag & drop an upload (with progress), or open a file from the server's log folder. Files of several hundred MB are fine (an 840 MB / 2.5M-line file loads with a 512 MB heap). The table is usable while the file is still loading. |
| **Formats** | Auto-detected from the first lines: Logstash / ECS (the sample in `docs/logExample`), Logback JSON, pino / bunyan, and a generic fallback. Lines that are not JSON are kept as `UNKNOWN` entries instead of being dropped. |
| **Filter** | Severity chips with live counts, date range (UTC or local) + quick ranges, free-text search, logger, thread, trace, and any other field (`username`, ...) via "Filter" buttons in the detail panel. |
| **Explore** | Timeline stacked by level (drag to select a time range), virtualized table, column picker, wrap toggle, detail drawer with pretty JSON and stack trace, jump to a whole trace, keyboard navigation (`/`, `j`/`k`, `Esc`). |
| **Share** | The session, filters and the open entry live in the URL: **Share link** reopens the same view. **Export** downloads the filtered subset as NDJSON or CSV. |

## Run it

### Docker (recommended for sharing)

```bash
mkdir logs && cp my.log logs/            # optional: files here can be opened without uploading
docker compose up --build                # http://localhost:8080
```

The compose file publishes the port on `127.0.0.1` only because there is **no authentication yet**; widen it to
`8080:8080` only on a trusted network.

### Jar

```bash
cd backend
./gradlew bootJar                         # also builds the UI (needs Node 24+)
java -jar build/libs/logviewer.jar \
  --logviewer.allowed-dirs=/path/to/logs  # optional, enables "Files on the server"
```

### Development

```bash
cd backend  && ./gradlew bootRun -PskipFrontend    # API on :8080
cd frontend && npm install && npm run dev          # UI on :5173, proxies /api to :8080
```

## Configuration

Set as `--logviewer.*` arguments, `LOGVIEWER_*` environment variables, or in `application.properties`.

| Property | Default | Meaning |
|---|---|---|
| `server.address` | `127.0.0.1` | Bind address (the Docker image uses `0.0.0.0`) |
| `logviewer.allowed-dirs` | *(empty = off)* | Folders the server may open files from (symlinks and `..` cannot escape them) |
| `logviewer.data-dir` | `${java.io.tmpdir}/logviewer` | Per-session databases; stale files are removed on startup |
| `logviewer.session-ttl` | `12h` | Idle time before a session is discarded |
| `logviewer.max-line-length` | `262144` | Longer lines are truncated and counted as parse errors |
| `logviewer.batch-size` | `10000` | Rows per store append |
| `logviewer.duckdb-memory-limit` | `1GB` | DuckDB memory limit per session |
| `spring.servlet.multipart.max-file-size` | `2GB` | Upload limit |

## API

All under `/api`. Filters are query parameters shared by `logs`, `facets` and `export`:
`level` (repeatable), `from`, `to` (ISO-8601), `q`, `logger`, `thread`, `traceId`, `attr.<name>=<value>`.

| Endpoint | Purpose |
|---|---|
| `POST /sessions` (multipart `file`) | Upload and start loading |
| `POST /sessions/from-path` `{"path"}` | Load a file from an allowed server folder |
| `GET /sessions/{id}` · `DELETE /sessions/{id}` | Status and progress · discard |
| `GET /sessions/{id}/events` | Server-sent progress events |
| `GET /sessions/{id}/logs?cursor=&size=&sort=asc\|desc` | Keyset-paginated rows |
| `GET /sessions/{id}/logs/{seq}` | One entry with raw line and stack trace |
| `GET /sessions/{id}/facets` | Level counts, top loggers/threads, attribute names, time histogram |
| `GET /sessions/{id}/export?format=ndjson\|csv` | Download the filtered subset |
| `GET /config` · `GET /files` | Feature flags · files in the allowed folders |

## Tests

```bash
cd backend  && ./gradlew test                      # parsers, line reader, full HTTP flow with DuckDB
cd frontend && npm test                            # unit + component tests (Vitest)
cd frontend && npm run e2e                         # Playwright against a running app (see playwright.config.ts)
node scripts/generate-sample-log.mjs big.log 2500000   # synthetic load-test file (~840 MB)
```

## Working in a OneDrive / Dropbox folder

Sync clients lock files under `build/` and break Gradle. Copy `backend/local.properties.example` to
`backend/local.properties` and point `buildDir` outside the synced folder. `node_modules` is also best excluded from
sync.

## Known limitations

- No authentication or multi-user permissions: run it locally or on a trusted network.
- Sessions live in memory (their data in files under `logviewer.data-dir`) and are lost on restart or after the TTL.
- Free-text search scans the raw lines; on a multi-million-line file a search combined with the facet counts can take a
  couple of seconds.
- Timestamps are stored with microsecond precision (the raw line keeps the original nanoseconds).

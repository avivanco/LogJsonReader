package com.logviewer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;

/**
 * End to end through the HTTP API: upload -> asynchronous ingestion -> list / filter / facets / detail / export.
 * Fixture: 1000 JSON lines (levels cycle INFO, INFO, DEBUG, WARN, ERROR; one second apart; 4 users; 3 loggers) plus one
 * non-JSON line, so INFO=400, DEBUG=200, WARN=200, ERROR=200, UNKNOWN=1.
 */
@SpringBootTest(properties = {"logviewer.batch-size=100", "logviewer.sample-size=20"})
@AutoConfigureMockMvc
class LogViewerIntegrationTest {

	private static final Instant START = Instant.parse("2026-10-15T00:00:00Z");

	private static Path dataDir;

	private static Path logsDir;

	@Autowired
	private MockMvc mvc;

	@BeforeAll
	static void createDirectories() throws IOException {
		dataDir = Files.createTempDirectory("logviewer-test-data");
		logsDir = Files.createTempDirectory("logviewer-test-logs");
		Files.writeString(logsDir.resolve("server.log"), fixture(), StandardCharsets.UTF_8);
	}

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) throws IOException {
		if (dataDir == null) {
			dataDir = Files.createTempDirectory("logviewer-test-data");
			logsDir = Files.createTempDirectory("logviewer-test-logs");
			Files.writeString(logsDir.resolve("server.log"), fixture(), StandardCharsets.UTF_8);
		}
		registry.add("logviewer.data-dir", () -> dataDir.toString());
		registry.add("logviewer.allowed-dirs", () -> logsDir.toString());
	}

	private static String fixture() {
		String[] levels = {"INFO", "INFO", "DEBUG", "WARN", "ERROR"};
		StringBuilder log = new StringBuilder();
		for (int i = 0; i < 1000; i++) {
			log.append("{\"@timestamp\":\"").append(START.plusSeconds(i))
					.append("\",\"level\":\"").append(levels[i % 5])
					.append("\",\"thread_name\":\"worker-").append(i % 2)
					.append("\",\"logger_name\":\"com.acme.Service").append(i % 3)
					.append("\",\"message\":\"event number ").append(i)
					.append("\",\"trace_id\":\"trace-").append(i % 50)
					.append("\",\"username\":\"user").append(i % 4)
					.append("\"}\n");
		}
		log.append("this line is not json");
		return log.toString();
	}

	private String uploadFixtureAndWait() throws Exception {
		String body = mvc.perform(multipart("/api/sessions")
						.file(new MockMultipartFile("file", "server.log", "text/plain",
								fixture().getBytes(StandardCharsets.UTF_8))))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.id").isNotEmpty())
				.andReturn().getResponse().getContentAsString();
		String id = JsonPath.read(body, "$.id");
		waitUntilReady(id);
		return id;
	}

	private void waitUntilReady(String id) throws Exception {
		long deadline = System.currentTimeMillis() + 20_000;
		while (System.currentTimeMillis() < deadline) {
			String status = JsonPath.read(mvc.perform(get("/api/sessions/{id}", id)).andReturn().getResponse()
					.getContentAsString(), "$.status");
			if ("READY".equals(status)) {
				return;
			}
			assertThat(status).isEqualTo("INGESTING");
			Thread.sleep(50);
		}
		throw new AssertionError("Ingestion did not finish in time");
	}

	/** Performs a request answered with a StreamingResponseBody and returns the fully streamed body. */
	private String streamed(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
			throws Exception {
		MvcResult started = mvc.perform(request).andExpect(request().asyncStarted()).andReturn();
		return mvc.perform(asyncDispatch(started)).andExpect(status().isOk()).andReturn().getResponse()
				.getContentAsString(StandardCharsets.UTF_8);
	}

	private int total(String id, String query) throws Exception {
		return JsonPath.read(mvc.perform(get("/api/sessions/{id}/facets" + query, id)).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString(), "$.total");
	}

	@Test
	void uploadIngestsEverythingAndReportsFormatAndParseErrors() throws Exception {
		String id = uploadFixtureAndWait();

		mvc.perform(get("/api/sessions/{id}", id))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("READY"))
				.andExpect(jsonPath("$.format").value("Logstash / ECS"))
				.andExpect(jsonPath("$.entries").value(1001))
				.andExpect(jsonPath("$.parseErrors").value(1))
				.andExpect(jsonPath("$.name").value("server.log"));
	}

	@Test
	void keysetPaginationWalksEveryEntryExactlyOnceInBothDirections() throws Exception {
		String id = uploadFixtureAndWait();

		for (String sort : List.of("asc", "desc")) {
			List<Integer> seqs = new ArrayList<>();
			String cursor = null;
			do {
				String url = "/api/sessions/" + id + "/logs?size=250&sort=" + sort
						+ (cursor == null ? "" : "&cursor=" + cursor);
				String body = mvc.perform(get(url)).andExpect(status().isOk()).andReturn().getResponse()
						.getContentAsString();
				seqs.addAll(JsonPath.<List<Integer>>read(body, "$.items[*].seq"));
				cursor = JsonPath.read(body, "$.nextCursor");
			}
			while (cursor != null);

			assertThat(seqs).hasSize(1001).doesNotHaveDuplicates();
			assertThat(seqs.get(0)).isEqualTo("asc".equals(sort) ? 1 : 1001);
			assertThat(seqs.get(1000)).isEqualTo("asc".equals(sort) ? 1001 : 1);
		}
	}

	@Test
	void filtersBySeverityTimeTextAttributeLoggerAndTrace() throws Exception {
		String id = uploadFixtureAndWait();

		assertThat(total(id, "")).isEqualTo(1001);
		assertThat(total(id, "?level=ERROR")).isEqualTo(200);
		assertThat(total(id, "?level=ERROR&level=WARN")).isEqualTo(400);
		assertThat(total(id, "?level=error,warn")).isEqualTo(400);
		assertThat(total(id, "?minLevel=WARN")).isEqualTo(400);
		assertThat(total(id, "?from=2026-10-15T00:00:00Z&to=2026-10-15T00:01:39Z")).isEqualTo(100);
		assertThat(total(id, "?q=EVENT NUMBER 999")).isEqualTo(1);
		assertThat(total(id, "?attr.username=user1")).isEqualTo(250);
		assertThat(total(id, "?logger=com.acme.Service0")).isEqualTo(334);
		assertThat(total(id, "?thread=worker-1")).isEqualTo(500);
		assertThat(total(id, "?traceId=trace-7")).isEqualTo(20);
		assertThat(total(id, "?level=ERROR&attr.username=user0&logger=com.acme.Service1")).isEqualTo(
				expectedCombined());
	}

	/** i%5==4 (ERROR), i%4==0 and i%3==1: i == 4 mod 5, 0 mod 4, 1 mod 3 -> every 60th value starting at 4. */
	private static int expectedCombined() {
		int count = 0;
		for (int i = 0; i < 1000; i++) {
			if (i % 5 == 4 && i % 4 == 0 && i % 3 == 1) {
				count++;
			}
		}
		return count;
	}

	@Test
	void listingHonoursFiltersAndTruncatesNothingImportant() throws Exception {
		String id = uploadFixtureAndWait();

		mvc.perform(get("/api/sessions/{id}/logs?level=ERROR&size=3", id))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items", hasSize(3)))
				.andExpect(jsonPath("$.items[0].level").value("ERROR"))
				.andExpect(jsonPath("$.items[0].seq").value(5))
				.andExpect(jsonPath("$.items[0].attributes.username").value("user0"))
				.andExpect(jsonPath("$.items[0].traceId").value("trace-4"))
				.andExpect(jsonPath("$.nextCursor").isNotEmpty());

		mvc.perform(get("/api/sessions/{id}/logs?level=UNKNOWN", id))
				.andExpect(jsonPath("$.items", hasSize(1)))
				.andExpect(jsonPath("$.items[0].message").value("this line is not json"))
				.andExpect(jsonPath("$.nextCursor", nullValue()));
	}

	@Test
	void facetsIgnoreTheLevelFilterForLevelCountsAndTheTimeFilterForTheHistogram() throws Exception {
		String id = uploadFixtureAndWait();

		mvc.perform(get("/api/sessions/{id}/facets?level=ERROR&from=2026-10-15T00:00:00Z&to=2026-10-15T00:00:59Z", id))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.total").value(12))
				.andExpect(jsonPath("$.levelCounts.INFO").value(24))
				.andExpect(jsonPath("$.levelCounts.ERROR").value(12))
				.andExpect(jsonPath("$.loggers[0].count").isNumber())
				.andExpect(jsonPath("$.attributeKeys[0]").value("username"))
				.andExpect(jsonPath("$.histogram.start").value("2026-10-15T00:00:04Z"))
				.andExpect(jsonPath("$.histogram.end").value("2026-10-15T00:16:39Z"))
				.andExpect(jsonPath("$.histogram.bucketMillis").isNumber())
				.andExpect(jsonPath("$.histogram.buckets").isNotEmpty());
	}

	@Test
	void histogramBucketsAddUpToTheFilteredTotal() throws Exception {
		String id = uploadFixtureAndWait();

		String body = mvc.perform(get("/api/sessions/{id}/facets?level=WARN", id)).andReturn().getResponse()
				.getContentAsString();
		List<java.util.Map<String, Integer>> counts = JsonPath.read(body, "$.histogram.buckets[*].counts");
		int sum = counts.stream().flatMap(map -> map.values().stream()).mapToInt(Integer::intValue).sum();

		// The histogram ignores the time range but not the level filter.
		assertThat(sum).isEqualTo(200);
	}

	@Test
	void entryDetailIncludesRawLine() throws Exception {
		String id = uploadFixtureAndWait();

		mvc.perform(get("/api/sessions/{id}/logs/{seq}", id, 3))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.level").value("DEBUG"))
				.andExpect(jsonPath("$.message").value("event number 2"))
				.andExpect(jsonPath("$.raw").value(org.hamcrest.Matchers.containsString("\"event number 2\"")));
		mvc.perform(get("/api/sessions/{id}/logs/{seq}", id, 99999)).andExpect(status().isNotFound());
	}

	@Test
	void exportStreamsFilteredNdjsonAndCsv() throws Exception {
		String id = uploadFixtureAndWait();

		String ndjson = streamed(get("/api/sessions/{id}/export?level=ERROR", id));
		assertThat(ndjson.lines().toList()).hasSize(200)
				.allSatisfy(line -> assertThat(line).contains("\"level\":\"ERROR\""));

		String csv = streamed(get("/api/sessions/{id}/export?format=csv&level=UNKNOWN", id));
		assertThat(csv.lines().toList()).hasSize(2);
		assertThat(csv).startsWith("\"timestamp\",\"level\"").contains("\"this line is not json\"");
	}

	@Test
	void loadsAllowedServerSideFilesButNothingElse() throws Exception {
		mvc.perform(get("/api/config")).andExpect(jsonPath("$.pathSourceEnabled").value(true));
		mvc.perform(get("/api/files")).andExpect(status().isOk())
				.andExpect(jsonPath("$[0].path").value(org.hamcrest.Matchers.endsWith("server.log")));

		String body = mvc.perform(post("/api/sessions/from-path").contentType(MediaType.APPLICATION_JSON)
						.content("{\"path\":\"server.log\"}"))
				.andExpect(status().isAccepted())
				.andReturn().getResponse().getContentAsString();
		String id = JsonPath.read(body, "$.id");
		waitUntilReady(id);
		assertThat(total(id, "")).isEqualTo(1001);

		// The source file must survive ingestion (only uploaded copies are deleted).
		assertThat(logsDir.resolve("server.log")).exists();

		mvc.perform(post("/api/sessions/from-path").contentType(MediaType.APPLICATION_JSON)
						.content("{\"path\":\"../../../../Windows/win.ini\"}"))
				.andExpect(status().isForbidden());
		mvc.perform(post("/api/sessions/from-path").contentType(MediaType.APPLICATION_JSON)
						.content("{\"path\":\"" + dataDir.toString().replace("\\", "\\\\") + "\"}"))
				.andExpect(status().isForbidden());
	}

	@Test
	void deletedSessionsDisappear() throws Exception {
		String id = uploadFixtureAndWait();

		mvc.perform(delete("/api/sessions/{id}", id)).andExpect(status().isNoContent());

		mvc.perform(get("/api/sessions/{id}", id)).andExpect(status().isNotFound());
		mvc.perform(get("/api/sessions/{id}/logs", id)).andExpect(status().isNotFound());
		assertThat(dataDir.resolve(id + ".duckdb")).doesNotExist();
	}

	@Test
	void rejectsInvalidInput() throws Exception {
		String id = uploadFixtureAndWait();

		mvc.perform(get("/api/sessions/{id}/logs?level=LOUD", id)).andExpect(status().isBadRequest());
		mvc.perform(get("/api/sessions/{id}/logs?from=yesterday", id)).andExpect(status().isBadRequest());
		mvc.perform(get("/api/sessions/{id}/logs?size=100000", id)).andExpect(status().isBadRequest());
		mvc.perform(get("/api/sessions/{id}/logs?cursor=garbage", id)).andExpect(status().isBadRequest());
		mvc.perform(get("/api/sessions/{id}/logs?attr.bad%22key=x", id)).andExpect(status().isBadRequest());
		mvc.perform(get("/api/sessions/{id}/export?format=xml", id)).andExpect(status().isBadRequest());
		mvc.perform(multipart("/api/sessions").file(new MockMultipartFile("file", "empty.log", "text/plain",
				new byte[0]))).andExpect(status().isBadRequest());
	}
}

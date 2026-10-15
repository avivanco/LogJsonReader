package com.logviewer.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class LineReaderTest {

	private static List<String> readAll(String input, int maxLength) throws IOException {
		LineReader reader = new LineReader(new StringReader(input), maxLength);
		List<String> lines = new ArrayList<>();
		String line;
		while ((line = reader.readLine()) != null) {
			lines.add(line);
		}
		return lines;
	}

	@Test
	void handlesAllLineTerminators() throws IOException {
		assertThat(readAll("a\nb\r\nc\rd", 100)).containsExactly("a", "b", "c", "d");
	}

	@Test
	void returnsFinalLineWithoutTerminator() throws IOException {
		assertThat(readAll("only", 100)).containsExactly("only");
	}

	@Test
	void keepsEmptyLinesAndIgnoresTrailingTerminator() throws IOException {
		assertThat(readAll("a\n\nb\n", 100)).containsExactly("a", "", "b");
		assertThat(readAll("", 100)).isEmpty();
	}

	@Test
	void truncatesOverlongLinesAndReportsIt() throws IOException {
		LineReader reader = new LineReader(new StringReader("x".repeat(200_000) + "\nshort\n"), 1000);

		String first = reader.readLine();
		assertThat(first).hasSize(1000);
		assertThat(reader.lastLineTruncated()).isTrue();

		assertThat(reader.readLine()).isEqualTo("short");
		assertThat(reader.lastLineTruncated()).isFalse();
	}

	@Test
	void crlfSplitAcrossBufferBoundaryIsOneTerminator() throws IOException {
		String input = "a".repeat((1 << 16) - 1) + "\r\nnext";

		List<String> lines = readAll(input, 1 << 20);

		assertThat(lines).hasSize(2);
		assertThat(lines.get(1)).isEqualTo("next");
	}
}

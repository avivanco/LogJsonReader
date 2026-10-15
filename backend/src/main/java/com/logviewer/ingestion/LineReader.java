package com.logviewer.ingestion;

import java.io.IOException;
import java.io.Reader;

/**
 * Reads lines (LF, CRLF or CR terminated) while keeping memory bounded: characters beyond {@code maxLineLength} are
 * dropped and {@link #lastLineTruncated()} reports it. A final line without terminator is returned as well.
 */
final class LineReader {

	private final Reader reader;

	private final int maxLineLength;

	private final char[] buffer = new char[1 << 16];

	private int position;

	private int limit;

	private boolean skipLineFeed;

	private boolean truncated;

	LineReader(Reader reader, int maxLineLength) {
		this.reader = reader;
		this.maxLineLength = maxLineLength;
	}

	boolean lastLineTruncated() {
		return truncated;
	}

	/** @return the next line without its terminator, or null at end of input */
	String readLine() throws IOException {
		StringBuilder line = new StringBuilder();
		boolean anyCharacter = false;
		truncated = false;
		while (true) {
			if (position >= limit) {
				limit = reader.read(buffer, 0, buffer.length);
				position = 0;
				if (limit < 0) {
					limit = 0;
					return anyCharacter ? line.toString() : null;
				}
			}
			if (skipLineFeed) {
				skipLineFeed = false;
				if (position < limit && buffer[position] == '\n') {
					position++;
				}
				if (position >= limit) {
					continue;
				}
			}
			int start = position;
			while (position < limit && buffer[position] != '\n' && buffer[position] != '\r') {
				position++;
			}
			if (position > start) {
				anyCharacter = true;
				append(line, start, position - start);
			}
			if (position < limit) {
				char terminator = buffer[position++];
				skipLineFeed = terminator == '\r';
				return line.toString();
			}
		}
	}

	private void append(StringBuilder line, int offset, int length) {
		int room = maxLineLength - line.length();
		if (length > room) {
			truncated = true;
			length = Math.max(room, 0);
		}
		line.append(buffer, offset, length);
	}
}

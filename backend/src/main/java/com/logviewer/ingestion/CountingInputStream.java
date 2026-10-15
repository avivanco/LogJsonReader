package com.logviewer.ingestion;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/** Counts the bytes consumed so ingestion can report progress against the file size. */
final class CountingInputStream extends FilterInputStream {

	private long count;

	CountingInputStream(InputStream in) {
		super(in);
	}

	long count() {
		return count;
	}

	@Override
	public int read() throws IOException {
		int value = super.read();
		if (value >= 0) {
			count++;
		}
		return value;
	}

	@Override
	public int read(byte[] bytes, int offset, int length) throws IOException {
		int read = super.read(bytes, offset, length);
		if (read > 0) {
			count += read;
		}
		return read;
	}
}

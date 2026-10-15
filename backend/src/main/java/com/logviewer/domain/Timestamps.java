package com.logviewer.domain;

import java.time.Instant;

/** Conversions between {@link Instant} and the epoch-microsecond representation used by the store. */
public final class Timestamps {

	private Timestamps() {
	}

	public static long toMicros(Instant instant) {
		return Math.addExact(Math.multiplyExact(instant.getEpochSecond(), 1_000_000L), instant.getNano() / 1_000L);
	}

	public static Instant fromMicros(long micros) {
		return Instant.ofEpochSecond(Math.floorDiv(micros, 1_000_000L), Math.floorMod(micros, 1_000_000L) * 1_000L);
	}
}

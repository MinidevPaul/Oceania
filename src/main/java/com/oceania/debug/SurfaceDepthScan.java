package com.oceania.debug;

import com.oceania.Oceania;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.world.level.block.state.BlockState;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

public final class SurfaceDepthScan {
	public static final boolean ENABLED = Boolean.parseBoolean(System.getProperty("oceania.surface.scan", "false"));

	private static final int BUCKET = 32;
	private static final int BUCKETS = 48;

	private static final AtomicLongArray CALLS = new AtomicLongArray(BUCKETS);

	private static final AtomicLongArray APPLIED = new AtomicLongArray(BUCKETS);

	private static final AtomicLongArray INTERESTING = new AtomicLongArray(BUCKETS);
	private static final AtomicLong DEEPEST_INTERESTING = new AtomicLong(Long.MIN_VALUE);
	private static final AtomicLong TOTAL = new AtomicLong();

	private SurfaceDepthScan() {
	}

	public static void init() {
		if (!ENABLED) {
			return;
		}
		Oceania.LOGGER.info("[surfscan] surface depth scan on (counters add work; do not read timings from this run)");
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> report());
	}

	public static void record(final int depth, final BlockState result) {
		int b = Math.min(BUCKETS - 1, Math.max(0, depth / BUCKET));
		CALLS.incrementAndGet(b);
		TOTAL.incrementAndGet();
		if (result == null) {
			return;
		}
		APPLIED.incrementAndGet(b);
		String name = result.getBlock().toString();
		boolean deepRock = name.contains("deepslate") || name.contains("stone") || name.contains("tuff");
		if (!deepRock) {
			INTERESTING.incrementAndGet(b);
			DEEPEST_INTERESTING.accumulateAndGet(depth, Math::max);
		}
	}

	public static void report() {
		long total = TOTAL.get();
		if (total == 0) {
			Oceania.LOGGER.info("[surfscan] no rule evaluations recorded");
			return;
		}
		Oceania.LOGGER.info("[surfscan] {} rule evaluations; deepest non-deep-rock result at depth {}",
			total, DEEPEST_INTERESTING.get() == Long.MIN_VALUE ? "(none)" : DEEPEST_INTERESTING.get());
		Oceania.LOGGER.info("[surfscan] depth below column top | evaluations | applied a block | applied something other than deep rock");
		long cum = 0;
		for (int b = 0; b < BUCKETS; b++) {
			long calls = CALLS.get(b);
			if (calls == 0) {
				continue;
			}
			cum += calls;
			Oceania.LOGGER.info("[surfscan]   {}..{} | {} ({}% of all, {}% cumulative) | {} | {}",
				b * BUCKET, b * BUCKET + BUCKET - 1, calls, String.format("%.1f", 100.0 * calls / total),
				String.format("%.1f", 100.0 * cum / total), APPLIED.get(b), INTERESTING.get(b));
		}
	}
}

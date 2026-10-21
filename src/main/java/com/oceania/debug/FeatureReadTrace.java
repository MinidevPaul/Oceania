package com.oceania.debug;

import com.oceania.Oceania;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

public final class FeatureReadTrace {
	public static final boolean ENABLED = Boolean.parseBoolean(System.getProperty("oceania.feature.reads", "false"));

	private static final Map<String, LongAdder> COUNTS = new ConcurrentHashMap<>();

	private FeatureReadTrace() {
	}

	public static void init() {
		if (!ENABLED) {
			return;
		}
		Oceania.LOGGER.info("[reads] cross-chunk read trace on");
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> report());
	}

	public static void record(final String step, final String what, final int dx, final int dz) {
		String key = step + " | " + (what == null ? "(no feature: the step itself)" : what) + " | d" + Math.max(Math.abs(dx), Math.abs(dz));
		COUNTS.computeIfAbsent(key, k -> new LongAdder()).increment();
	}

	public static void report() {
		if (COUNTS.isEmpty()) {
			Oceania.LOGGER.info("[reads] no cross-chunk reads recorded");
			return;
		}
		List<Map.Entry<String, LongAdder>> rows = new ArrayList<>(COUNTS.entrySet());
		rows.sort((a, b) -> Long.compare(b.getValue().sum(), a.getValue().sum()));
		long total = rows.stream().mapToLong(r -> r.getValue().sum()).sum();
		Oceania.LOGGER.info("[reads] {} cross-chunk block reads during generation, by step, feature and chunk distance:", total);
		for (Map.Entry<String, LongAdder> row : rows) {
			Oceania.LOGGER.info("[reads]   {} x  {}", row.getValue().sum(), row.getKey());
		}
	}
}

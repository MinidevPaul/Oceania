package com.oceania.debug;

import java.util.List;
import java.util.concurrent.atomic.LongAdder;

public final class GenStats {
	public static final Stage NOISE = new Stage("noise fill");
	public static final Stage SURFACE = new Stage("surface");
	public static final Stage CARVERS = new Stage("carvers");
	public static final Stage FEATURES = new Stage("features");
	public static final Stage LIGHT = new Stage("light (light thread batches)");
	public static final Stage FLOW = new Stage("flow tiles (per tile build)");
	public static final LongAdder CHUNKS_LIT = new LongAdder();

	public static final List<Stage> STAGES = List.of(NOISE, SURFACE, CARVERS, FEATURES, LIGHT, FLOW);

	private GenStats() {
	}

	public static void resetAll() {
		STAGES.forEach(Stage::reset);
		CHUNKS_LIT.reset();
	}

	public static String report() {
		StringBuilder sb = new StringBuilder();
		for (Stage s : STAGES) {
			sb.append(String.format("  %-30s calls=%6d  total=%8.1f ms  avg=%7.3f ms%n", s.name, s.count.sum(), s.ns.sum() / 1e6, s.avgMs()));
		}
		sb.append(String.format("  %-30s %d%n", "chunks lit", CHUNKS_LIT.sum()));

		return sb.toString();
	}

	public static final class Stage {
		public final String name;
		public final LongAdder ns = new LongAdder();
		public final LongAdder count = new LongAdder();

		Stage(String name) {
			this.name = name;
		}

		public void add(long nanos) {
			ns.add(nanos);
			count.increment();
		}

		public double avgMs() {
			long c = count.sum();
			return c == 0 ? 0 : ns.sum() / 1e6 / c;
		}

		void reset() {
			ns.reset();
			count.reset();
		}
	}
}

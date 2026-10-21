package com.oceania.perf;

public final class PerfFlags {

	public static final boolean SURFACE_FAST_WRITE = flag("oceania.surface.fastwrite", true);

	public static final int WORKER_THREADS = intFlag("oceania.worker.threads", 0);

	public static final boolean DEEP_ROCK_FILL = flag("oceania.deeprock.fill", true);
	public static final int DEEP_ROCK_DEPTH = intFlag("oceania.deeprock.depth", 136);

	public static final boolean SURFACE_DEEP_SKIP = flag("oceania.surface.deepskip", true);
	public static final int SURFACE_DEEP_SKIP_DEPTH = intFlag("oceania.surface.deepskip.depth", 216);

	public static final boolean SURFACE_DEEP_SKIP_VERIFY = flag("oceania.surface.deepskip.verify", false);

	public static final boolean FLUID_EMPTY_CACHE = flag("oceania.fluid.empty.cache", false);

	public static final boolean SURFACE_FAST_WRITE_HEIGHTMAPS = flag("oceania.surface.fastwrite.heightmaps", true);

	private PerfFlags() {
	}

	private static int intFlag(final String key, final int fallback) {
		String value = System.getProperty(key);
		try {
			return value == null || value.isEmpty() ? fallback : Integer.parseInt(value.trim());
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	private static boolean flag(final String key, final boolean fallback) {
		String value = System.getProperty(key);
		return value == null || value.isEmpty() ? fallback : Boolean.parseBoolean(value);
	}

	public static final java.util.concurrent.atomic.LongAdder DEEP_ROCK_PLACED = new java.util.concurrent.atomic.LongAdder();
	public static final java.util.concurrent.atomic.LongAdder WRITES_SKIPPED = new java.util.concurrent.atomic.LongAdder();
	public static final java.util.concurrent.atomic.LongAdder WRITES_SEEN = new java.util.concurrent.atomic.LongAdder();

	public static final java.util.concurrent.atomic.LongAdder FLUID_CACHE_FILLS = new java.util.concurrent.atomic.LongAdder();
	public static final java.util.concurrent.atomic.LongAdder DEEP_SKIP_CHUNKS = new java.util.concurrent.atomic.LongAdder();
	public static final java.util.concurrent.atomic.LongAdder DEEP_SKIP_HITS = new java.util.concurrent.atomic.LongAdder();
	public static final java.util.concurrent.atomic.LongAdder DEEP_SKIP_OK = new java.util.concurrent.atomic.LongAdder();
	public static final java.util.concurrent.atomic.LongAdder DEEP_SKIP_MISMATCH = new java.util.concurrent.atomic.LongAdder();
	private static final java.util.concurrent.atomic.AtomicReference<String> DEEP_SKIP_FIRST_MISMATCH =
		new java.util.concurrent.atomic.AtomicReference<>();

	public static void recordDeepSkipMismatch(final int x, final int y, final int z, final Object actual) {
		DEEP_SKIP_MISMATCH.increment();
		DEEP_SKIP_FIRST_MISMATCH.compareAndSet(null, x + "," + y + "," + z + " -> " + actual);
	}

	public static String engagement() {
		return "deeprock placed=" + DEEP_ROCK_PLACED.sum()
			+ ", surface writes seen=" + WRITES_SEEN.sum()
			+ ", skipped=" + WRITES_SKIPPED.sum()
			+ ", fluid cache fills=" + FLUID_CACHE_FILLS.sum()
			+ ", deepskip chunks=" + DEEP_SKIP_CHUNKS.sum()
			+ ", hits=" + DEEP_SKIP_HITS.sum()
			+ ", verify ok=" + DEEP_SKIP_OK.sum()
			+ ", MISMATCH=" + DEEP_SKIP_MISMATCH.sum()
			+ (DEEP_SKIP_FIRST_MISMATCH.get() == null ? "" : " first=" + DEEP_SKIP_FIRST_MISMATCH.get());
	}

	public static String report() {
		return "surface.fastwrite=" + SURFACE_FAST_WRITE + " heightmaps=" + SURFACE_FAST_WRITE_HEIGHTMAPS
			+ " deeprock.fill=" + DEEP_ROCK_FILL + "@" + DEEP_ROCK_DEPTH
			+ " fluid.empty.cache=" + FLUID_EMPTY_CACHE
			+ " surface.deepskip=" + SURFACE_DEEP_SKIP + "@" + SURFACE_DEEP_SKIP_DEPTH
			+ (SURFACE_DEEP_SKIP_VERIFY ? " (VERIFY MODE: chain still evaluated, nothing skipped)" : "") + " worker.threads=" + (WORKER_THREADS > 0 ? String.valueOf(WORKER_THREADS) : "vanilla")
			+ " (vanilla would allow " + net.minecraft.util.Util.maxAllowedExecutorThreads() + ")";
	}
}

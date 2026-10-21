package com.oceania.worldgen.field;

import com.oceania.Oceania;
import com.oceania.debug.GenStats;
import net.minecraft.util.Mth;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

public final class FlowTiles {
	public static final boolean ENABLED = !"false".equalsIgnoreCase(System.getProperty("oceania.flow", "true"));
	public static final int TILE = 2048;
	public static final int MARGIN = 256;
	public static final int RES = 4;

	public static final double FLOOR_MIN_RADIUS =
		Math.max(0, Math.min(64, Double.parseDouble(System.getProperty("oceania.cave.lateral.radius", "4"))));

	private static final int FLOOR_MIN_CELLS = FLOOR_MIN_RADIUS <= 0 ? 0 : (int) Math.ceil(FLOOR_MIN_RADIUS / RES);
	public static final int N = (TILE + 2 * MARGIN) / RES;
	private static final int CELLS = N * N;
	private static final double CELL_AREA = (double) RES * RES;
	private static final int MAX_TILES = 32;
	private static final int PREFETCH = 320;
	private static final double JITTER = 0.4;

	public static final double PUDDLE_FILL = 40;
	public static final double LAND_FILL_ABOVE = 55;
	public static final int SLOPE_STEPS = 6;

	public static final double AREA_MIN = 3000, AREA_MIN_LAND = 800;
	public static final double INITIATION = 8000, INITIATION_LAND = 700;
	public static final double DEPTH_A0 = 1200;
	public static final double DEPTH_K = 6.5, DEPTH_MIN = 4, DEPTH_MAX = 70;
	public static final double WIDTH_K = 0.2, WIDTH_MIN = 8, WIDTH_MAX = 80;
	public static final double FULL_DEPTH_SLOPE = 0.1, FLAT_DEPTH = 0.35;
	public static final int HEAD_RAMP = 8;

	public static final double FAN_RUN = 4;

	public static final double WALL_CUT = 1.2;

	public static final double LAND_WIDTH_PER_DEPTH = 4;

	private static final double SQRT2 = Math.sqrt(2);
	private static final int[] DX = {1, 1, 0, -1, -1, -1, 0, 1};
	private static final int[] DZ = {0, 1, 1, 1, 0, -1, -1, -1};
	private static final double[] DIST = {1, SQRT2, 1, SQRT2, 1, SQRT2, 1, SQRT2};

	private static final int[] DIR_OF = {5, 4, 3, 6, -1, 2, 7, 0, 1};

	public static volatile Path dumpDir;

	private static final int POOL_SIZE = Mth.clamp(Runtime.getRuntime().availableProcessors() - 1, 1, 16);
	private static final ExecutorService POOL = Executors.newFixedThreadPool(POOL_SIZE, r -> {
		Thread t = new Thread(r, "Oceania-Flow");
		t.setDaemon(true);
		t.setPriority(Thread.NORM_PRIORITY - 1);
		return t;
	});

	private final OceanFields fields;
	private final ConcurrentHashMap<Long, Entry> tiles = new ConcurrentHashMap<>();
	private final ThreadLocal<Cursor> cursor = ThreadLocal.withInitial(Cursor::new);
	private final AtomicLong builds = new AtomicLong(), buildNanos = new AtomicLong(), channelCells = new AtomicLong();
	private volatile long firstBuildNanos = -1;

	FlowTiles(OceanFields fields) {
		this.fields = fields;
	}

	static final class Tile {
		final int tx, tz, x0, z0;
		final float[] carve;
		final float[] terrainSlope;
		final float[] floorMin;
		final int channels;

		Tile(int tx, int tz, int x0, int z0, float[] carve, float[] terrainSlope, float[] floorMin, int channels) {
			this.tx = tx; this.tz = tz; this.x0 = x0; this.z0 = z0; this.carve = carve; this.terrainSlope = terrainSlope;
			this.floorMin = floorMin; this.channels = channels;
		}
	}

	private static final class Entry {
		final int tx, tz;
		final CompletableFuture<Tile> future = new CompletableFuture<>();
		volatile long lastUse = System.nanoTime();

		Entry(int tx, int tz) {
			this.tx = tx; this.tz = tz;
		}
	}

	private static final class Cursor {
		Tile tile;
		double gradient;
		double terrainSlope;
		double floorMin;
	}

	public double carveAt(double x, double z) {
		int tx = Mth.floor(x / TILE), tz = Mth.floor(z / TILE);
		Cursor cur = cursor.get();
		Tile t = cur.tile;
		if (t == null || t.tx != tx || t.tz != tz) {
			t = tile(tx, tz);
			cur.tile = t;
		}
		double lx = x - (double) tx * TILE, lz = z - (double) tz * TILE;
		if (lx < PREFETCH || lx >= TILE - PREFETCH || lz < PREFETCH || lz >= TILE - PREFETCH) prefetch(tx, tz, lx, lz);
		double u = (x - t.x0) / RES - 0.5, v = (z - t.z0) / RES - 0.5;
		int i = Mth.floor(u), j = Mth.floor(v);
		double fu = u - i, fv = v - j;
		int k = j * N + i;
		float[] g = t.carve;
		double dTop = g[k + 1] - g[k], dBottom = g[k + N + 1] - g[k + N];
		double top = g[k] + dTop * fu, bottom = g[k + N] + dBottom * fu;
		double gx = (dTop * (1 - fv) + dBottom * fv) / RES;
		double gz = (bottom - top) / RES;
		cur.gradient = Math.hypot(gx, gz);
		float[] sl = t.terrainSlope;
		double sTop = sl[k] + (sl[k + 1] - sl[k]) * fu, sBottom = sl[k + N] + (sl[k + N + 1] - sl[k + N]) * fu;
		cur.terrainSlope = sTop + (sBottom - sTop) * fv;
		float[] fm = t.floorMin;
		double mTop = fm[k] + (fm[k + 1] - fm[k]) * fu, mBottom = fm[k + N] + (fm[k + N + 1] - fm[k + N]) * fu;
		cur.floorMin = mTop + (mBottom - mTop) * fv;
		return top + (bottom - top) * fv;
	}

	public double lastGradient() {
		return cursor.get().gradient;
	}

	public double lastTerrainSlope() {
		return cursor.get().terrainSlope;
	}

	public double lastFloorMin() {
		return cursor.get().floorMin;
	}

	public String stats() {
		long n = builds.get();
		if (n == 0) return "no flow tiles built";
		return String.format("%d flow tiles built, avg %.0f ms, first %.0f ms, channel cells %.2f%%, %d cached",
			n, buildNanos.get() / 1e6 / n, firstBuildNanos / 1e6, 100.0 * channelCells.get() / (n * (double) CELLS), tiles.size());
	}

	public void release() {
		tiles.clear();
	}

	private static long key(int tx, int tz) {
		return ((long) tx << 32) ^ (tz & 0xffffffffL);
	}

	private Tile tile(int tx, int tz) {
		Entry e = ensure(tx, tz, false);
		Tile t = e.future.join();
		e.lastUse = System.nanoTime();
		return t;
	}

	private void prefetch(int tx, int tz, double lx, double lz) {
		int ax = lx < PREFETCH ? -1 : lx >= TILE - PREFETCH ? 1 : 0;
		int az = lz < PREFETCH ? -1 : lz >= TILE - PREFETCH ? 1 : 0;
		if (ax != 0) ensure(tx + ax, tz, true);
		if (az != 0) ensure(tx, tz + az, true);
		if (ax != 0 && az != 0) ensure(tx + ax, tz + az, true);
	}

	private Entry ensure(int tx, int tz, boolean async) {
		long key = key(tx, tz);
		Entry e = tiles.get(key);
		if (e != null) return e;
		Entry fresh = new Entry(tx, tz);
		e = tiles.putIfAbsent(key, fresh);
		if (e != null) return e;
		if (async) POOL.execute(() -> buildInto(fresh));
		else buildInto(fresh);
		return fresh;
	}

	private void buildInto(Entry e) {
		try {
			long t0 = System.nanoTime();
			Tile t = build(e.tx, e.tz);
			long dt = System.nanoTime() - t0;
			GenStats.FLOW.add(dt);
			if (builds.getAndIncrement() == 0) firstBuildNanos = dt;
			buildNanos.addAndGet(dt);
			channelCells.addAndGet(t.channels);
			e.lastUse = System.nanoTime();
			e.future.complete(t);
			evict();
		} catch (Throwable ex) {
			tiles.remove(key(e.tx, e.tz), e);
			e.future.completeExceptionally(ex);
			Oceania.LOGGER.error("[flow] tile {},{} failed", e.tx, e.tz, ex);
		}
	}

	private void evict() {
		if (tiles.size() <= MAX_TILES) return;
		synchronized (tiles) {
			while (tiles.size() > MAX_TILES) {
				Entry oldest = null;
				for (Entry e : tiles.values()) {
					if (!e.future.isDone()) continue;
					if (oldest == null || e.lastUse < oldest.lastUse) oldest = e;
				}
				if (oldest == null) return;
				tiles.remove(key(oldest.tx, oldest.tz), oldest);
			}
		}
	}

	private Tile build(int tx, int tz) {
		long t0 = System.nanoTime();
		int x0 = tx * TILE - MARGIN, z0 = tz * TILE - MARGIN;
		float[] h = new float[CELLS];
		sample(h, x0, z0);
		long t1 = System.nanoTime();
		float[] hf = new float[CELLS];
		int[] parent = new int[CELLS];
		flood(h, hf, parent);
		float[] he = new float[CELLS];
		byte[] dir = new byte[CELLS];
		directions(h, hf, parent, he, dir, x0, z0);
		float[] slope = slopes(he, dir);
		long t2 = System.nanoTime();
		float[] carve = new float[CELLS];
		float[] acc = new float[CELLS];
		int channels = network(h, he, dir, slope, acc, carve);
		long t3 = System.nanoTime();
		float[] slopeGrid = terrainSlopes(h);
		float[] floorMin = floorMins(h, carve);
		long t4 = System.nanoTime();
		float max = 0;
		for (float c : carve) max = Math.max(max, c);
		Oceania.LOGGER.info("[flow] tile {},{} built in {} ms (sample {}, drainage {}, channels {}, slope+floormin {}): {} channel cells ({}%), max carve {}",
			tx, tz, (t4 - t0) / 1_000_000, (t1 - t0) / 1_000_000, (t2 - t1) / 1_000_000, (t3 - t2) / 1_000_000,
			(t4 - t3) / 1_000_000,
			channels, String.format("%.2f", 100.0 * channels / CELLS), String.format("%.1f", max));
		Path dump = dumpDir;
		if (dump != null) dump(dump, tx, tz, h, hf, he, slope, acc, carve);
		return new Tile(tx, tz, x0, z0, carve, slopeGrid, floorMin, channels);
	}

	private static float[] floorMins(float[] h, float[] carve) {
		float[] row = new float[CELLS];
		for (int j = 0; j < N; j++) {
			for (int i = 0; i < N; i++) {
				float m = Float.MAX_VALUE;
				for (int d = -FLOOR_MIN_CELLS; d <= FLOOR_MIN_CELLS; d++) {
					int ii = Math.max(0, Math.min(N - 1, i + d));
					int c = j * N + ii;
					m = Math.min(m, h[c] - carve[c]);
				}
				row[j * N + i] = m;
			}
		}
		float[] out = new float[CELLS];
		for (int j = 0; j < N; j++) {
			for (int i = 0; i < N; i++) {
				float m = Float.MAX_VALUE;
				for (int d = -FLOOR_MIN_CELLS; d <= FLOOR_MIN_CELLS; d++) {
					int jj = Math.max(0, Math.min(N - 1, j + d));
					m = Math.min(m, row[jj * N + i]);
				}
				out[j * N + i] = m;
			}
		}
		return out;
	}

	private static float[] terrainSlopes(float[] h) {
		float[] sm = new float[CELLS];
		for (int j = 0; j < N; j++) {
			int jm = Math.max(0, j - 1), jp = Math.min(N - 1, j + 1);
			for (int i = 0; i < N; i++) {
				int im = Math.max(0, i - 1), ip = Math.min(N - 1, i + 1);
				sm[j * N + i] = (h[jm * N + im] + h[jm * N + i] + h[jm * N + ip] + h[j * N + im] + h[j * N + i] + h[j * N + ip]
					+ h[jp * N + im] + h[jp * N + i] + h[jp * N + ip]) / 9f;
			}
		}
		float[] s = new float[CELLS];
		for (int j = 0; j < N; j++) {
			int jm = Math.max(0, j - 1), jp = Math.min(N - 1, j + 1);
			for (int i = 0; i < N; i++) {
				int im = Math.max(0, i - 1), ip = Math.min(N - 1, i + 1);
				double dx = (sm[j * N + ip] - sm[j * N + im]) / ((ip - im) * RES);
				double dz = (sm[jp * N + i] - sm[jm * N + i]) / ((jp - jm) * RES);
				s[j * N + i] = (float) Math.hypot(dx, dz);
			}
		}
		return s;
	}

	private static void dump(Path dir, int tx, int tz, float[] h, float[] hf, float[] he, float[] slope, float[] acc, float[] carve) {
		try {
			Files.createDirectories(dir);
			String tag = "flow-tile-" + tx + "_" + tz + "-";
			ImageIO.write(grey(h, -700, 200, false), "png", dir.resolve(tag + "floor.png").toFile());
			float[] fill = new float[CELLS];
			for (int c = 0; c < CELLS; c++) fill[c] = hf[c] - h[c];
			ImageIO.write(grey(fill, 0, 40, false), "png", dir.resolve(tag + "fill.png").toFile());
			ImageIO.write(grey(slope, 0, 1, false), "png", dir.resolve(tag + "slope.png").toFile());
			ImageIO.write(grey(acc, 1, 100000, true), "png", dir.resolve(tag + "area.png").toFile());
			ImageIO.write(grey(carve, 0, 40, false), "png", dir.resolve(tag + "carve.png").toFile());
			float[] lake = new float[CELLS];
			for (int c = 0; c < CELLS; c++) lake[c] = he[c] < hf[c] ? 1 : 0;
			ImageIO.write(grey(lake, 0, 1, false), "png", dir.resolve(tag + "sinks.png").toFile());
		} catch (IOException e) {
			Oceania.LOGGER.warn("[flow] dump failed", e);
		}
	}

	private static BufferedImage grey(float[] f, double lo, double hi, boolean log) {
		BufferedImage img = new BufferedImage(N, N, BufferedImage.TYPE_INT_RGB);
		for (int j = 0; j < N; j++) {
			for (int i = 0; i < N; i++) {
				double v = f[j * N + i];
				double t = log ? Math.log(Math.max(v, lo) / lo) / Math.log(hi / lo) : (v - lo) / (hi - lo);
				int g = (int) (255 * Mth.clamp(t, 0, 1));
				img.setRGB(i, j, g << 16 | g << 8 | g);
			}
		}
		return img;
	}

	private void sample(float[] h, int x0, int z0) {
		AtomicInteger next = new AtomicInteger();
		AtomicInteger done = new AtomicInteger();
		Runnable rows = () -> {
			OceanFields.Sample s = new OceanFields.Sample();
			int j;
			while ((j = next.getAndIncrement()) < N) {
				double z = z0 + (j + 0.5) * RES;
				int base = j * N;
				for (int i = 0; i < N; i++) {
					double x = x0 + (i + 0.5) * RES;
					h[base + i] = (float) (fields.baseFloor(x, z, s) + jitter(x0 / RES + i, z0 / RES + j));
				}
				done.incrementAndGet();
			}
		};
		for (int k = 0; k < POOL_SIZE; k++) POOL.execute(rows);
		rows.run();

		while (done.get() < N) LockSupport.parkNanos(200_000);
	}

	private static double jitter(int cx, int cz) {
		return (hash01(cx, cz, 1) - 0.5) * JITTER;
	}

	private static double hash01(int cx, int cz, int salt) {
		long k = cx * 0x9E3779B97F4A7C15L + cz * 0xC2B2AE3D27D4EB4FL + salt * 0x165667B19E3779F9L;
		k ^= k >>> 33; k *= 0xff51afd7ed558ccdL; k ^= k >>> 33; k *= 0xc4ceb9fe1a85ec53L; k ^= k >>> 33;
		return (k >>> 11) * 0x1.0p-53;
	}

	private static void flood(float[] h, float[] hf, int[] parent) {
		boolean[] closed = new boolean[CELLS];
		Arrays.fill(parent, -1);
		Heap heap = new Heap(CELLS);
		for (int c = 0; c < CELLS; c++) {
			int ci = c % N, cj = c / N;
			if (ci == 0 || cj == 0 || ci == N - 1 || cj == N - 1) {
				closed[c] = true;
				hf[c] = h[c];
				heap.push(h[c], c);
			}
		}
		while (heap.size > 0) {
			int c = heap.pop();
			float hc = hf[c];
			int ci = c % N, cj = c / N;
			for (int d = 0; d < 8; d++) {
				int ni = ci + DX[d], nj = cj + DZ[d];
				if (ni < 0 || ni >= N || nj < 0 || nj >= N) continue;
				int n = nj * N + ni;
				if (closed[n]) continue;
				closed[n] = true;
				hf[n] = Math.max(h[n], hc);
				parent[n] = c;
				heap.push(hf[n], n);
			}
		}
	}

	private static void directions(float[] h, float[] hf, int[] parent, float[] he, byte[] dir, int x0, int z0) {
		for (int c = 0; c < CELLS; c++) {
			double limit = h[c] > LAND_FILL_ABOVE ? Double.MAX_VALUE : PUDDLE_FILL;
			he[c] = hf[c] - h[c] > limit ? h[c] : hf[c];
		}
		double[] weight = new double[8];
		for (int c = 0; c < CELLS; c++) {
			int ci = c % N, cj = c / N;
			float hc = he[c];
			double total = 0;
			for (int d = 0; d < 8; d++) {
				int ni = ci + DX[d], nj = cj + DZ[d];
				double drop = ni < 0 || ni >= N || nj < 0 || nj >= N ? 0 : (hc - he[nj * N + ni]) / DIST[d];
				weight[d] = drop > 0 ? drop * drop : 0;
				total += weight[d];
			}
			int best = -1;
			if (total > 0) {
				double r = hash01(x0 / RES + ci, z0 / RES + cj, 2) * total;
				for (int d = 0; d < 8; d++) {
					r -= weight[d];
					if (r < 0 || d == 7) {
						best = weight[d] > 0 ? d : best;
						break;
					}
				}
				if (best < 0) {
					for (int d = 7; d >= 0; d--) {
						if (weight[d] > 0) {
							best = d;
							break;
						}
					}
				}
			} else if (he[c] == hf[c] && parent[c] >= 0 && he[parent[c]] <= hc) {
				int p = parent[c];
				best = DIR_OF[(p % N - ci + 1) * 3 + (p / N - cj + 1)];
			}
			dir[c] = (byte) best;
		}
	}

	private static float[] slopes(float[] he, byte[] dir) {
		float[] slope = new float[CELLS];
		for (int c = 0; c < CELLS; c++) {
			int cur = c;
			double dist = 0;
			for (int k = 0; k < SLOPE_STEPS; k++) {
				int d = dir[cur];
				if (d < 0) break;
				dist += DIST[d];
				cur += DX[d] + DZ[d] * N;
			}
			slope[c] = dist == 0 ? 0 : (float) ((he[c] - he[cur]) / (dist * RES));
		}
		return slope;
	}

	private static int network(float[] h, float[] he, byte[] dir, float[] slope, float[] acc, float[] carve) {
		int[] target = new int[CELLS];
		int[] indeg = new int[CELLS];
		for (int c = 0; c < CELLS; c++) {
			int d = dir[c];
			int t = d < 0 ? -1 : c + DX[d] + DZ[d] * N;
			target[c] = t;
			if (t >= 0) indeg[t]++;
		}
		int[] queue = new int[CELLS];
		int head = 0, tail = 0;
		for (int c = 0; c < CELLS; c++) {
			if (indeg[c] == 0) queue[tail++] = c;
		}
		boolean[] chan = new boolean[CELLS];
		short[] len = new short[CELLS];
		float[] thal = new float[CELLS];
		float[] width = new float[CELLS];
		Arrays.fill(thal, Float.POSITIVE_INFINITY);
		int channels = 0;
		while (head < tail) {
			int c = queue[head++];
			acc[c] += 1;
			double area = acc[c] * CELL_AREA;
			double s = slope[c];
			boolean land = h[c] > LAND_FILL_ABOVE;
			boolean ch = chan[c] || (area >= (land ? AREA_MIN_LAND : AREA_MIN) && area * s >= (land ? INITIATION_LAND : INITIATION));
			if (ch) {
				chan[c] = true;
				channels++;
				int l = Math.min(Short.MAX_VALUE, len[c] + 1);
				len[c] = (short) l;
				double damp = FLAT_DEPTH + (1 - FLAT_DEPTH) * Mth.clamp(s / FULL_DEPTH_SLOPE, 0, 1);
				double depth = Mth.clamp(DEPTH_K * Math.log(area / DEPTH_A0), DEPTH_MIN, DEPTH_MAX) * damp * Math.min(1.0, l / (double) HEAD_RAMP);
				thal[c] = (float) Math.min(he[c] - depth, thal[c]);
				double w = Mth.clamp(WIDTH_K * Math.sqrt(area), WIDTH_MIN, WIDTH_MAX);
				if (land) w = Math.max(w, LAND_WIDTH_PER_DEPTH * (he[c] - thal[c]));
				width[c] = (float) w;
			}
			int t = target[c];
			if (t >= 0) {
				acc[t] += acc[c];
				if (ch) {
					chan[t] = true;
					if (len[c] > len[t]) len[t] = len[c];
					if (thal[c] < thal[t]) thal[t] = thal[c];
				}
				if (--indeg[t] == 0) queue[tail++] = t;
			}
		}

		int[] toEnd = new int[CELLS];
		for (int q = tail - 1; q >= 0; q--) {
			int c = queue[q];
			int t = target[c];
			int ci = c % N, cj = c / N;
			boolean border = ci == 0 || cj == 0 || ci == N - 1 || cj == N - 1;
			toEnd[c] = t < 0 ? (border ? Integer.MAX_VALUE / 2 : 0) : Math.min(Integer.MAX_VALUE / 2, toEnd[t] + 1);
		}

		for (int c = 0; c < CELLS; c++) {
			if (!chan[c]) continue;
			double dep = he[c] - thal[c];
			if (dep <= 0) continue;
			double fan = Math.min(1.0, toEnd[c] * RES / (FAN_RUN * dep));
			dep *= fan;
			if (dep < 0.5) continue;
			double rim = he[c];
			double hw = width[c] * 0.5 / RES;
			int r = (int) Math.ceil(hw);
			int ci = c % N, cj = c / N;
			for (int dj = -r; dj <= r; dj++) {
				int nj = cj + dj;
				if (nj < 0 || nj >= N) continue;
				for (int di = -r; di <= r; di++) {
					int ni = ci + di;
					if (ni < 0 || ni >= N) continue;
					double d2 = (di * di + dj * dj) / (hw * hw);
					if (d2 >= 1) continue;
					int n = nj * N + ni;
					double cv = Math.min(h[n] - (rim - dep * (1 - d2)), WALL_CUT * dep * (1 - d2));
					if (cv > carve[n]) carve[n] = (float) cv;
				}
			}
		}
		return channels;
	}

	private static final class Heap {
		private final float[] keys;
		private final int[] vals;
		int size;

		Heap(int capacity) {
			keys = new float[capacity];
			vals = new int[capacity];
		}

		void push(float k, int v) {
			int i = size++;
			while (i > 0) {
				int p = (i - 1) >> 1;
				if (keys[p] <= k) break;
				keys[i] = keys[p];
				vals[i] = vals[p];
				i = p;
			}
			keys[i] = k;
			vals[i] = v;
		}

		int pop() {
			int top = vals[0];
			float k = keys[--size];
			int v = vals[size];
			int i = 0;
			while (true) {
				int l = 2 * i + 1;
				if (l >= size) break;
				int r = l + 1;
				int m = r < size && keys[r] < keys[l] ? r : l;
				if (keys[m] >= k) break;
				keys[i] = keys[m];
				vals[i] = vals[m];
				i = m;
			}
			if (size > 0) {
				keys[i] = k;
				vals[i] = v;
			}
			return top;
		}
	}
}

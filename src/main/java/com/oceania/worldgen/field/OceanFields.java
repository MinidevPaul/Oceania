package com.oceania.worldgen.field;

import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class OceanFields {
	public static final int SEA_LEVEL = 63;

	public static final int OPEN_SPAWN_STEP = 384;
	public static final int OPEN_SPAWN_RINGS = 20;

	public static final double OPEN_SPAWN_MIN_DEPTH = 45;

	public static final double OPEN_SPAWN_GROUP_CLEAR = 450;

	public static final int OPEN_SPAWN_MAX_SWEEPS = 64;

	public static final double OPEN_SPAWN_LAND_CLEAR = 400;

	public static final double BASE_DEPTH = -190;
	public static final double BASIN_AMPLITUDE = 110;
	public static final double PLATEAU_TOP = 38;
	public static final double PLATEAU_VARIATION = 14;
	public static final double PIT_DEPTH = 230;
	public static final double CANYON_DEPTH = 110;
	public static final double WARP_AMPLITUDE = 160;
	public static final double BIG_WARP_AMPLITUDE = 650;

	public static final double COAST_WARP = 18;
	public static final double COAST_WARP_FINE = 3;
	public static final double TEXTURE = 14;

	public static final int PROVINCE_CELL = 3000;
	public static final double PROVINCE_BLEND = 500;
	public static final int FAULT_CELL = 700;
	public static final double FAULT_EDGE = 12;

	public static final int PLATE_CELL = 2048;
	public static final double RIDGE_HEIGHT = 80;
	public static final double RIDGE_WIDTH = 170;
	public static final double TRENCH_DEPTH = 420;
	public static final double TRENCH_WIDTH = 110;
	public static final double TRENCH_OFFSET = 40;
	public static final double ARC_OFFSET = 230;
	public static final double ARC_WIDTH = 170;
	public static final double HOTSPOT_WIDTH = 300;
	public static final double HOTSPOT_LENGTH = 6000;

	public static final int GROUP_CELL = 768;
	public static final int MINOR_CELL = 256;

	public static final double LAVA_CHAMBER_TOP = SEA_LEVEL - 120;

	public static final double LAVA_SEA_DEPTH = num("oceania.lava.depth", 240, 0, 1024);

	public static final double LAVA_POCKET_DEPTH = num("oceania.lava.pocket", 120, 0, 1024);

	public static final double LAND_CARVE_CUT = 0.5;

	public static final double GROUP_BASE_PROB = 0.55;

	public static final double ROCK_CLIFF_SLOPE = 0.6;

	public static final double STEEP_SLOPE = 1.0;

	public static final double WALL_SLOPE = 1.2, WALL_SLOPE_FULL = 2.0;

	public static final double CLIFF_MIN_FLOOR = 58;

	public static final double RELIEF_MAX = 26;

	public static final double FINE_TEXTURE_LAND = 1.3, FINE_TEXTURE_SEA = 1.8, LAGOON_TEXTURE = 3;

	public static final int TYPE_NONE = 0, TYPE_BARREN = 1, TYPE_LARGE = 2, TYPE_CLUSTER = 3, TYPE_GUYOT = 4;

	public static final boolean WALLS = flag("oceania.walls");
	public static final boolean CAVES_ISLAND = flag("oceania.caves.island");
	public static final boolean CAVES_FLOOR = flag("oceania.caves.floor");

	public static final boolean CAVES_FLOOR_AIR = flag("oceania.caves.floor.air");
	public static final boolean SEA_CAVES = flag("oceania.caves.sea");
	public static final boolean BLUE_HOLES = flag("oceania.bluehole");

	public static final boolean WET_CAVES = flag("oceania.caves.wet");

	public static final boolean CAVE_BIOMES = flag("oceania.cavebiomes");

	public static final boolean VEINS = flag("oceania.veins");

	public static final double CAVE_DEPTH_SCALE = num("oceania.cave.scale", 16, 1, 128);

	public static final double WALL_RELIEF = num("oceania.wall.relief", 1.2, 0, 24);

	public static final double WALL_NOISE_SLOPE = num("oceania.wall.slope", WALL_SLOPE, 0, 8);
	public static final double WALL_NOISE_SPAN = WALL_SLOPE_FULL - WALL_SLOPE;

	public static final double CAVE_CORE_RAMP = 20;

	public static final double CAVE_SEA_FADE = 8;

	public static final double SEA_CAVE_RELIEF = num("oceania.seacave.relief", 5, 0, 24);

	public static final double SEA_CAVE_SLOPE = 1.0, SEA_CAVE_FLOOR_LO = 55, SEA_CAVE_FLOOR_HI = 95, SEA_CAVE_EDGE = 3;

	public static final double BLUE_HOLE_THR = num("oceania.bluehole.thr", 0.72, -1, 1), BLUE_HOLE_SPAN = 0.03;

	public static final double CAVE_AIR_MARGIN = 8;

	public static final double CAVE_SEAL = num("oceania.cave.seal", 7, 0, 256);

	public static final double CAVE_COVER = num("oceania.cave.cover", 7, 0, 64);

	public static final double CAVE_COVER_MAX = num("oceania.cave.cover.max", 200, 0, 512);

	public static final double CAVE_WALL_COVER = num("oceania.cave.wall.cover", 10, 0, 128);

	public static final double CAVE_LATERAL = num("oceania.cave.lateral", 4, 0, 128);

	public static final double CAVE_ISLAND_TOP = -40;

	public static final double CAVE_LAND_LOW = 8, CAVE_LAND_SPAN = 8;

	public static final double CAVE_AIR_NEVER = 4096;

	public static final double SEA_CAVE_FLOODED = 48, BLUE_HOLE_FLOODED = 120;

	public static final double WET_SEAL = num("oceania.caves.wet.seal", 3, 0, 32);

	public static final double WET_DEPTH = 32;

	public static final double WET_SHELL = 12;

	public static final double WET_CAVE_SCALE = num("oceania.caves.wet.scale", 4, 1, 48);

	public static final double WET_THR = num("oceania.caves.wet.thr", 0.30, -1, 1);
	public static final double WET_SPAN = num("oceania.caves.wet.span", 0.12, 0.02, 1);

	public static final double WET_CORE_SPAN = num("oceania.caves.wet.corespan", 0.02, 0.01, 1);

	private static boolean flag(String key) {
		return !"false".equalsIgnoreCase(System.getProperty(key, "true"));
	}

	private static double num(String key, double fallback, double min, double max) {
		String v = System.getProperty(key);
		if (v == null) return fallback;
		try {
			return Mth.clamp(Double.parseDouble(v), min, max);
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	public enum Province {

		ABYSSAL_PLAIN(-235, 35, 9.0, 0.60, 160, 9.0, 0, 12, 1.0, 6, 0.8, 0.75, 0, 28, 18),
		SEAMOUNT_FIELD(-200, 70, 9.0, 0.55, 150, 0.90, 60, 25, 1.0, 12, 3.0, 0.8, 0, 10, 18),
		FRACTURE_ZONE(-210, 50, 9.0, 9.0, 0, 0.86, 90, 120, 4.0, 10, 0.6, 0.6, 0, 0, 15),
		CARBONATE_PLATFORM(36, 10, 9.0, 0.62, 90, 9.0, 0, 15, 1.0, 5, 0.2, 1.3, 0, 0, 12),
		RIDGE_PROVINCE(-150, 60, 9.0, 9.0, 0, 0.88, 70, 35, 3.0, 12, 1.2, 0.7, 75, 0, 12),
		BASIN_AND_RANGE(-180, 120, 0.34, 0.50, 200, 0.88, 110, 90, 2.0, 14, 1.0, 1.0, 0, 0, 18);

		public final double base, basinAmp, plateauThr, pitThr, pitDepth, canyonThr, canyonDepth, faultAmp, faultAniso, texture, minorMult, groupMult, ridgeAmp, hillAmp;
		public final int weight;

		Province(double base, double basinAmp, double plateauThr, double pitThr, double pitDepth, double canyonThr, double canyonDepth,
				 double faultAmp, double faultAniso, double texture, double minorMult, double groupMult, double ridgeAmp, double hillAmp, int weight) {
			this.base = base; this.basinAmp = basinAmp; this.plateauThr = plateauThr; this.pitThr = pitThr; this.pitDepth = pitDepth;
			this.canyonThr = canyonThr; this.canyonDepth = canyonDepth; this.faultAmp = faultAmp; this.faultAniso = faultAniso;
			this.texture = texture; this.minorMult = minorMult; this.groupMult = groupMult; this.ridgeAmp = ridgeAmp; this.hillAmp = hillAmp; this.weight = weight;
		}

		static final Province[] VALUES = values();
		static final int TOTAL_WEIGHT = Arrays.stream(VALUES).mapToInt(p -> p.weight).sum();

		static Province pick(double r) {
			int t = (int) (r * TOTAL_WEIGHT);
			for (Province p : VALUES) {
				t -= p.weight;
				if (t < 0) return p;
			}
			return ABYSSAL_PLAIN;
		}
	}

	private static final Map<Long, OceanFields> INSTANCES = new ConcurrentHashMap<>();

	public static OceanFields of(long seed) {
		return INSTANCES.computeIfAbsent(seed, OceanFields::new);
	}

	private final long seed;
	private final Fbm basin, plateau, pit, canyon, warpA, warpB, bigWarpA, bigWarpB, coastA, coastB, coastC, coastD, texture, landRelief, ridgeTex, gully, fine, hills, lagoonTex, wetCave;
	private final Map<Long, Cone[]> groupCache = new ConcurrentHashMap<>();
	private final Map<Long, Cone[]> minorCache = new ConcurrentHashMap<>();
	private final ThreadLocal<SampleCache> cache = ThreadLocal.withInitial(SampleCache::new);
	private final ThreadLocal<ColumnCache> columns = ThreadLocal.withInitial(ColumnCache::new);
	private final FlowTiles flow;

	private OceanFields(long seed) {
		this.seed = seed;
		flow = new FlowTiles(this);
		basin = new Fbm(seed, 1, 3600, 4, 0.5);
		plateau = new Fbm(seed, 2, 1400, 3, 0.5);
		pit = new Fbm(seed, 3, 1000, 3, 0.5);
		canyon = new Fbm(seed, 4, 900, 3, 0.55);
		warpA = new Fbm(seed, 5, 700, 3, 0.5);
		warpB = new Fbm(seed, 6, 700, 3, 0.5);
		coastA = new Fbm(seed, 7, 260, 2, 0.35);
		coastB = new Fbm(seed, 8, 260, 2, 0.35);
		texture = new Fbm(seed, 9, 180, 4, 0.5);
		landRelief = new Fbm(seed, 10, 150, 3, 0.45);
		ridgeTex = new Fbm(seed, 11, 90, 3, 0.5);
		coastC = new Fbm(seed, 12, 55, 2, 0.5);
		coastD = new Fbm(seed, 13, 55, 2, 0.5);
		bigWarpA = new Fbm(seed, 14, 2600, 3, 0.5);
		bigWarpB = new Fbm(seed, 15, 2600, 3, 0.5);
		gully = new Fbm(seed, 16, 20, 3, 0.5);
		fine = new Fbm(seed, 17, 9, 2, 0.5);
		hills = new Fbm(seed, 18, 1, 2, 0.5);
		lagoonTex = new Fbm(seed, 19, 45, 2, 0.5);

		wetCave = new Fbm(seed, 20, 520, 3, 0.5);
	}

	public long seed() {
		return seed;
	}

	public FlowTiles flow() {
		return flow;
	}

	public static void releaseAll() {
		for (OceanFields f : INSTANCES.values()) {
			f.flow.release();
			f.groupCache.clear();
			f.minorCache.clear();
		}
	}

	static final class Fbm {
		private final SimplexNoise[] octaves;
		private final double[] amps;
		private final double baseFreq;
		private final double norm;

		Fbm(long seed, int salt, double period, int octaveCount, double gain) {
			XoroshiroRandomSource rs = new XoroshiroRandomSource(seed, 0x5EED0000L + salt);
			octaves = new SimplexNoise[octaveCount];
			amps = new double[octaveCount];
			double n = 0, a = 1;
			for (int i = 0; i < octaveCount; i++) {
				octaves[i] = new SimplexNoise(rs);
				amps[i] = a;
				n += a;
				a *= gain;
			}
			norm = n;
			baseFreq = 1.0 / period;
		}

		double get(double x, double z) {
			double f = baseFreq, sum = 0;
			for (int i = 0; i < octaves.length; i++) {
				sum += amps[i] * octaves[i].get(x * f, z * f);
				f *= 2;
			}
			return sum / norm;
		}

		double ridged(double x, double z) {
			return 1 - Math.abs(get(x, z));
		}
	}

	public static final class Sample {
		double keyX = Double.NaN, keyZ = Double.NaN;
		public double floorY;
		public double regionalFloor;
		public double ridgeT, trenchT, arc, hot;
		public double plateau, pit, canyon, fault;
		public Province province = Province.ABYSSAL_PLAIN;
		public double provinceWeight = 1;
		public int type;

		public int variant;
		public double age = -1;
		public boolean lagoon;
		public boolean core;
		public double landMask;

		public double cliff;

		public double carve;

		public boolean rock;

		public double craterLavaY = Double.NaN;

		public double wallNoise;

		public double relief;

		public double terrainSlope;

		public double coreT;

		public double pitRaw, platformW;

		public double wall;

		public double caveMask;

		public double seaCave;

		public double blueHole;

		public double wet;

		public double wetEdge;

		public double caveAir = CAVE_AIR_NEVER;

		public double seal = CAVE_SEAL;

		public double floorMin;

		public double continents() {
			double d = SEA_LEVEL - floorY;
			if (d <= -2.5) return 0.6 + 0.4 * Mth.clamp((-d - 2.5) / 40.0, 0, 1);
			if (d <= 2.5) return Mth.lerp((d + 2.5) / 5.0, 0.6, 0.45);
			if (d <= 4.5) return Mth.lerp((d - 2.5) / 2.0, 0.45, 0.3);
			if (d <= 18) return Mth.lerp((d - 4.5) / 13.5, 0.3, 0.05);
			if (d <= 60) return Mth.lerp((d - 18) / 42.0, 0.05, -0.15);
			if (d <= 180) return Mth.lerp((d - 60) / 120.0, -0.15, -0.55);
			if (d <= 330) return Mth.lerp((d - 180) / 150.0, -0.55, -0.85);
			return Mth.lerp(Mth.clamp((d - 330) / 170.0, 0, 1), -0.85, -1.0);
		}

		public double erosion() {
			return age < 0 ? 0 : age * 2 - 1;
		}

		public double weirdness() {
			if (lagoon) return -0.9;
			return ridgeT;
		}
	}

	private static final class SampleCache {
		static final int SIZE = 1024, MASK = SIZE - 1;
		final Sample[] samples = new Sample[SIZE];
	}

	public Sample sample(double x, double z) {
		SampleCache c = cache.get();
		int i = (int) (mix(Double.doubleToLongBits(x) * 0x9E3779B97F4A7C15L ^ Double.doubleToLongBits(z)) & SampleCache.MASK);
		Sample s = c.samples[i];
		if (s == null) {
			s = new Sample();
			c.samples[i] = s;
		} else if (s.keyX == x && s.keyZ == z) {
			return s;
		}
		compute(x, z, s);
		s.keyX = x;
		s.keyZ = z;
		return s;
	}

	private static final class ColumnCache {
		static final int SIZE = 4096, MASK = SIZE - 1;
		final long[] keys = new long[SIZE];
		final double[] floor = new double[SIZE];
		final boolean[] core = new boolean[SIZE];
		final float[] crater = new float[SIZE];
		final float[] caveAir = new float[SIZE];

		final double[] out = new double[4];

		ColumnCache() {
			Arrays.fill(keys, Long.MIN_VALUE);
		}
	}

	private int column(int x, int z) {
		return column(x, z, columns.get());
	}

	public double[] columnValues(int x, int z) {
		ColumnCache c = columns.get();
		int i = column(x, z, c);
		double[] o = c.out;
		o[0] = c.floor[i];
		o[1] = c.crater[i];
		o[2] = c.caveAir[i];
		o[3] = c.core[i] ? 1 : 0;
		return o;
	}

	private int column(int x, int z, ColumnCache c) {
		long key = ((long) x << 32) ^ (z & 0xffffffffL);
		int i = (int) (mix(key) & ColumnCache.MASK);
		if (c.keys[i] != key) {
			Sample s = sample(x, z);
			c.keys[i] = key;
			c.floor[i] = s.floorY;
			c.core[i] = s.core;
			c.crater[i] = (float) s.craterLavaY;
			c.caveAir[i] = (float) s.caveAir;
		}
		return i;
	}

	public double floorAt(int x, int z) {
		return columns.get().floor[column(x, z)];
	}

	public double craterLavaAt(int x, int z) {
		return columns.get().crater[column(x, z)];
	}

	public boolean coreAt(int x, int z) {
		return columns.get().core[column(x, z)];
	}

	public double caveAirAt(int x, int z) {
		return columns.get().caveAir[column(x, z)];
	}

	private static long mix(long h) {
		h ^= h >>> 33; h *= 0xff51afd7ed558ccdL; h ^= h >>> 33; h *= 0xc4ceb9fe1a85ec53L; h ^= h >>> 33;
		return h;
	}

	private long cellSeed(int cx, int cz, int salt) {
		return mix(seed ^ mix(cx * 0x9E3779B97F4A7C15L + cz * 0xC2B2AE3D27D4EB4FL + salt * 0x165667B19E3779F9L));
	}

	private double rand(int cx, int cz, int salt) {
		return (cellSeed(cx, cz, salt) >>> 11) * 0x1.0p-53;
	}

	private static double smoothstep(double t) {
		t = Mth.clamp(t, 0, 1);
		return t * t * (3 - 2 * t);
	}

	private static double smax(double a, double b, double k) {
		double h = Math.max(a, b);
		double d = Math.abs(a - b);
		return d < k ? h + (k - d) * (k - d) / (4 * k) : h;
	}

	public record PlateInfo(double ridgeT, double trenchT, double arc, double hot, double hotAge, int plateX, int plateZ) {
	}

	public PlateInfo plates(double x0, double z0) {
		double x = x0 + BIG_WARP_AMPLITUDE * bigWarpA.get(x0 + 7777, z0 - 3333);
		double z = z0 + BIG_WARP_AMPLITUDE * bigWarpB.get(x0 + 7777, z0 - 3333);
		int cx = Mth.floor(x / PLATE_CELL);
		int cz = Mth.floor(z / PLATE_CELL);
		double d1 = Double.MAX_VALUE, d2 = Double.MAX_VALUE;
		int p1x = 0, p1z = 0, p2x = 0, p2z = 0;
		double s1x = 0, s1z = 0, s2x = 0, s2z = 0;
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				int px = cx + dx, pz = cz + dz;
				double sx = (px + 0.15 + 0.7 * rand(px, pz, 1)) * PLATE_CELL;
				double sz = (pz + 0.15 + 0.7 * rand(px, pz, 2)) * PLATE_CELL;
				double d = Math.hypot(x - sx, z - sz);
				if (d < d1) {
					d2 = d1; p2x = p1x; p2z = p1z; s2x = s1x; s2z = s1z;
					d1 = d; p1x = px; p1z = pz; s1x = sx; s1z = sz;
				} else if (d < d2) {
					d2 = d; p2x = px; p2z = pz; s2x = sx; s2z = sz;
				}
			}
		}
		double edge = (d2 - d1) * 0.5;
		double nx = s2x - s1x, nz = s2z - s1z;
		double nl = Math.hypot(nx, nz);
		nx /= nl; nz /= nl;
		double a1 = rand(p1x, p1z, 3) * Math.PI * 2, a2 = rand(p2x, p2z, 3) * Math.PI * 2;
		double v1x = Math.cos(a1), v1z = Math.sin(a1), v2x = Math.cos(a2), v2z = Math.sin(a2);
		double rel = (v1x - v2x) * nx + (v1z - v2z) * nz;

		double ridgeT = 0, trenchT = 0, arc = 0;
		if (rel > 0.25) {
			double strength = Mth.clamp((rel - 0.25) / 0.5, 0, 1);
			boolean overriding = rand(p1x, p1z, 5) > rand(p2x, p2z, 5);
			if (overriding) {
				trenchT = Math.max(0, 1 - (edge + TRENCH_OFFSET) / TRENCH_WIDTH) * strength;
				arc = Math.max(0, 1 - Math.abs(edge - ARC_OFFSET) / ARC_WIDTH) * strength;
			} else {
				trenchT = Math.max(0, 1 - Math.abs(edge - TRENCH_OFFSET) / TRENCH_WIDTH) * strength;
			}
		} else if (rel < -0.25) {
			double strength = Mth.clamp((-rel - 0.25) / 0.5, 0, 1);
			ridgeT = Math.max(0, 1 - edge / RIDGE_WIDTH) * strength;
		}

		double hx = s1x + (rand(p1x, p1z, 6) - 0.5) * 0.7 * PLATE_CELL;
		double hz = s1z + (rand(p1x, p1z, 7) - 0.5) * 0.7 * PLATE_CELL;
		double along = (x - hx) * v1x + (z - hz) * v1z;
		double perp = Math.abs((x - hx) * -v1z + (z - hz) * v1x);
		double hot = 0, hotAge = 0;
		if (along > 0 && along < HOTSPOT_LENGTH) {
			hot = Math.max(0, 1 - perp / HOTSPOT_WIDTH);
			hotAge = along / HOTSPOT_LENGTH;
		}
		return new PlateInfo(ridgeT, trenchT, arc, hot, hotAge, p1x, p1z);
	}

	public record ProvinceSample(Province first, Province second, double w, double dirX, double dirZ) {
	}

	public ProvinceSample provinces(double x0, double z0) {
		double x = x0 + BIG_WARP_AMPLITUDE * bigWarpA.get(x0, z0);
		double z = z0 + BIG_WARP_AMPLITUDE * bigWarpB.get(x0, z0);
		int cx = Mth.floor(x / PROVINCE_CELL), cz = Mth.floor(z / PROVINCE_CELL);
		double d1 = Double.MAX_VALUE, d2 = Double.MAX_VALUE;
		int p1x = 0, p1z = 0, p2x = 0, p2z = 0;
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				int px = cx + dx, pz = cz + dz;
				double sx = (px + 0.15 + 0.7 * rand(px, pz, 300)) * PROVINCE_CELL;
				double sz = (pz + 0.15 + 0.7 * rand(px, pz, 301)) * PROVINCE_CELL;
				double d = Math.hypot(x - sx, z - sz);
				if (d < d1) {
					d2 = d1; p2x = p1x; p2z = p1z;
					d1 = d; p1x = px; p1z = pz;
				} else if (d < d2) {
					d2 = d; p2x = px; p2z = pz;
				}
			}
		}
		Province a = Province.pick(rand(p1x, p1z, 302));
		Province b = Province.pick(rand(p2x, p2z, 302));
		double w = 0.5 + 0.5 * smoothstep((d2 - d1) / PROVINCE_BLEND);
		double ang = rand(p1x, p1z, 303) * Math.PI;
		return new ProvinceSample(a, b, w, Math.cos(ang), Math.sin(ang));
	}

	private double faults(double x, double z, double amp, double aniso, double dirX, double dirZ) {
		if (amp <= 0) return 0;

		double u = (x * dirX + z * dirZ) / aniso;
		double v = -x * dirZ + z * dirX;
		int cu = Mth.floor(u / FAULT_CELL), cv = Mth.floor(v / FAULT_CELL);
		double d1 = Double.MAX_VALUE, d2 = Double.MAX_VALUE;
		double o1 = 0, o2 = 0;
		for (int du = -1; du <= 1; du++) {
			for (int dv = -1; dv <= 1; dv++) {
				int pu = cu + du, pv = cv + dv;
				double su = (pu + 0.25 + 0.5 * rand(pu, pv, 310)) * FAULT_CELL;
				double sv = (pv + 0.25 + 0.5 * rand(pu, pv, 311)) * FAULT_CELL;
				double d = Math.hypot((u - su) * aniso, v - sv);
				double o = (rand(pu, pv, 312) * 2 - 1) * amp;
				if (d < d1) {
					d2 = d1; o2 = o1;
					d1 = d; o1 = o;
				} else if (d < d2) {
					d2 = d; o2 = o;
				}
			}
		}
		double t = smoothstep((d2 - d1) / FAULT_EDGE);
		return Mth.lerp(0.5 + 0.5 * t, o2, o1);
	}

	private double regional(double wx, double wz, double x, double z, Sample s) {
		ProvinceSample ps = provinces(wx, wz);
		double fa = regionalFor(ps.first, ps, wx, wz, s, ps.w);
		double fb = ps.w >= 0.999 ? fa : regionalFor(ps.second, ps, wx, wz, s, 1 - ps.w);
		double floor = Mth.lerp(ps.w, fb, fa);
		PlateInfo p = plates(wx, wz);

		double ridgeScale = Mth.clamp((-floor - 20) / 100.0, 0, 1);
		floor += (RIDGE_HEIGHT * p.ridgeT * p.ridgeT + 25 * p.ridgeT * ridgeTex.ridged(x, z)) * ridgeScale;
		floor -= TRENCH_DEPTH * Math.pow(smoothstep(p.trenchT), 1.5);
		s.province = ps.first;
		s.provinceWeight = ps.w;
		s.ridgeT = p.ridgeT;
		s.trenchT = p.trenchT;
		s.arc = p.arc;
		s.hot = p.hot;
		return floor;
	}

	private double regionalFor(Province pr, ProvinceSample ps, double wx, double wz, Sample s, double weight) {
		double b = basin.get(wx, wz);
		double floor = pr.base + pr.basinAmp * b;
		double pl = pr.plateauThr > 1 ? 0 : smoothstep((plateau.get(wx, wz) - pr.plateauThr) / 0.14);
		if (pl > 0) floor = Mth.lerp(pl, floor, PLATEAU_TOP + PLATEAU_VARIATION * b);

		double pitN = pr.pitThr > 1 ? 0 : pit.get(wx, wz);
		double pt = pr.pitThr > 1 ? 0 : smoothstep((pitN - pr.pitThr) / 0.12);
		floor -= pr.pitDepth * pt;
		double cn = pr.canyonThr > 1 ? 0 : smoothstep((canyon.ridged(wx, wz) - pr.canyonThr) / 0.08);
		floor -= pr.canyonDepth * cn;
		double f = faults(wx, wz, pr.faultAmp, pr.faultAniso, ps.dirX, ps.dirZ);
		floor += f;
		if (pr.hillAmp > 0) {

			double along = wx * ps.dirX + wz * ps.dirZ;
			double across = -wx * ps.dirZ + wz * ps.dirX;
			floor += pr.hillAmp * (hills.ridged(across / 900, along / 3600) - 0.45);
		}
		if (pr == Province.CARBONATE_PLATFORM) {

			double rim = Math.exp(-Math.pow((weight - 0.68) / 0.09, 2));
			floor += 17 * rim - 6 * smoothstep((weight - 0.8) / 0.15);
			s.platformW += weight;
			s.pitRaw += pitN * weight;
		}
		if (pr.ridgeAmp > 0) {

			double along = wx * ps.dirX + wz * ps.dirZ;
			double across = -wx * ps.dirZ + wz * ps.dirX;
			floor += pr.ridgeAmp * (ridgeTex.ridged(across * 0.35, along * 0.08) - 0.5);
		}
		s.plateau += pl * weight;
		s.pit += pt * weight;
		s.canyon += cn * weight;
		s.fault += f * weight;
		return floor;
	}

	public static final class Cone {
		final double cx, cz;
		final int type;
		final double age;
		final double[] rs, ys;
		final double flankSlope;
		final double coreRadius;
		final double lagoonRadius;
		final double landRadius;
		final double[] harm;
		final double[] phase;
		double reliefScale = 1.0;

		int variant;
		double gully = 0.15;
		boolean rock;
		double craterRadius;
		double craterLavaY = Double.NaN;
		double aspect = 1;
		double axisX = 1, axisZ;

		Cone(double cx, double cz, int type, double age, double[] rs, double[] ys, double flankSlope,
			 double coreRadius, double lagoonRadius, double landRadius, double[] harm, double[] phase) {
			this.cx = cx; this.cz = cz; this.type = type; this.age = age;
			this.rs = rs; this.ys = ys; this.flankSlope = flankSlope;
			this.coreRadius = coreRadius; this.lagoonRadius = lagoonRadius; this.landRadius = landRadius;
			this.harm = harm; this.phase = phase;
		}

		double effectiveRadius(double x, double z) {
			double dx = x - cx, dz = z - cz;
			double ex = (dx * axisX + dz * axisZ) / aspect, ez = -dx * axisZ + dz * axisX;
			double r = Math.hypot(ex, ez);
			if (r < 1e-6) return 0;
			double theta = Math.atan2(ez, ex);
			double shape = 1;
			for (int k = 0; k < harm.length; k++) {
				shape += harm[k] * Math.cos((k + 1) * theta + phase[k]);
			}
			return r / Mth.clamp(shape, 0.5, 1.7);
		}

		double yAt(double r) {
			if (r <= rs[0]) return ys[0];
			for (int i = 1; i < rs.length; i++) {
				if (r <= rs[i]) {
					double t = (r - rs[i - 1]) / (rs[i] - rs[i - 1]);
					return Mth.lerp(t, ys[i - 1], ys[i]);
				}
			}
			int last = rs.length - 1;
			return ys[last] - (r - rs[last]) * flankSlope;
		}

		double slopeAt(double r) {
			for (int i = 1; i < rs.length; i++) {
				if (r <= rs[i]) return Math.abs((ys[i] - ys[i - 1]) / Math.max(1e-6, rs[i] - rs[i - 1]));
			}
			return flankSlope;
		}

		double reach(double floor) {
			int last = rs.length - 1;
			return (rs[last] + Math.max(0, (ys[last] - floor) / flankSlope)) * 1.7 * aspect;
		}
	}

	private double radiusWithGullies(Cone c, double x, double z) {
		double dx = x - c.cx, dz = z - c.cz;
		double ex = (dx * c.axisX + dz * c.axisZ) / c.aspect, ez = -dx * c.axisZ + dz * c.axisX;
		double r = Math.hypot(ex, ez);
		if (r < 1e-6) return 0;
		double theta = Math.atan2(ez, ex);
		double shape = 1;
		for (int k = 0; k < c.harm.length; k++) {
			shape += c.harm[k] * Math.cos((k + 1) * theta + c.phase[k]);
		}
		shape = Mth.clamp(shape, 0.5, 1.7);
		if (c.gully > 0) {
			double ct = Math.cos(theta), st = Math.sin(theta);

			double scale = c.landRadius > 0 ? c.landRadius : c.rs[c.rs.length - 1] * 0.6;
			double ring = Mth.clamp(scale * 0.244, 8, 80);
			double g = gully.get(c.cx * 0.37 + ct * ring, c.cz * 0.37 + st * ring);
			double g2 = gully.get(c.cz * 0.23 + ct * ring * 0.5 + r * 0.03, c.cx * 0.23 + st * ring * 0.5 - r * 0.02);

			double fade = Mth.clamp(r / Math.max(1.0, scale * 0.45), 0, 1);
			shape *= 1 + c.gully * fade * (0.7 * g + 0.3 * g2);
		}
		return r / shape;
	}

	private static double summit(double base, double peak, double t, double exponent) {
		return base + (peak - base) * Math.pow(Math.max(0, 1 - t), exponent);
	}

	private static Cone withGully(Cone c, double gully) {
		c.gully = gully;
		return c;
	}

	private double[] harmonics(int cx, int cz, int salt, double amplitude) {
		double[] h = new double[5];
		for (int k = 0; k < 5; k++) h[k] = rand(cx, cz, salt + k) * amplitude / (k + 1);
		return h;
	}

	private double[] phases(int cx, int cz, int salt) {
		double[] p = new double[5];
		for (int k = 0; k < 5; k++) p[k] = rand(cx, cz, salt + k) * Math.PI * 2;
		return p;
	}

	private Cone[] group(int gx, int gz) {
		return groupCache.computeIfAbsent(mix(((long) gx << 32) ^ (gz & 0xffffffffL)), k -> buildGroup(gx, gz));
	}

	private double groupCentreX(int gx, int gz) {
		return (gx + 0.25 + 0.5 * rand(gx, gz, 10)) * GROUP_CELL;
	}

	private double groupCentreZ(int gx, int gz) {
		return (gz + 0.25 + 0.5 * rand(gx, gz, 11)) * GROUP_CELL;
	}

	private boolean hasGroup(int gx, int gz, double cx, double cz) {
		PlateInfo p = plates(cx, cz);
		if (p.trenchT > 0.15) return false;
		double prob = (GROUP_BASE_PROB + 0.35 * p.arc + 0.4 * p.hot) * provinces(cx, cz).first.groupMult;
		if (p.ridgeT > 0.3) prob *= 0.5;
		return rand(gx, gz, 12) <= Math.min(prob, 0.95);
	}

	private Cone[] buildGroup(int gx, int gz) {
		double cx = groupCentreX(gx, gz), cz = groupCentreZ(gx, gz);
		if (!hasGroup(gx, gz, cx, cz)) return new Cone[0];
		PlateInfo p = plates(cx, cz);
		Province province = provinces(cx, cz).first;

		double room = Double.MAX_VALUE;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (dx == 0 && dz == 0) continue;
				double nx = groupCentreX(gx + dx, gz + dz), nz = groupCentreZ(gx + dx, gz + dz);
				if (hasGroup(gx + dx, gz + dz, nx, nz)) room = Math.min(room, Math.hypot(nx - cx, nz - cz) * 0.5);
			}
		}

		double r = rand(gx, gz, 13);
		double age = p.hot > 0.3 ? Mth.lerp(0.7, r, p.hotAge) : r;
		if (p.arc > 0.5) age *= 0.6;
		if (province == Province.CARBONATE_PLATFORM) age = Math.max(age, 0.62);
		List<Cone> cones = new ArrayList<>();
		if (age < 0.28) {

			boolean clustered = rand(gx, gz, 1000) < 0.35;
			int count = clustered ? 2 + (int) (rand(gx, gz, 1001) * 3) : 1;
			double spread = clustered ? Math.min(85 + 60 * rand(gx, gz, 1002), Math.max(45, room - 95)) : 0;
			List<double[]> sited = new ArrayList<>();
			for (int i = 0, tries = 0; i < count && tries < 40; tries++) {
				int salt = i * 200;
				double bx = cx, bz = cz;
				if (i > 0) {
					double ang = rand(gx, gz, 1010 + tries) * Math.PI * 2;
					double dist = spread * (0.45 + 0.55 * rand(gx, gz, 1060 + tries));
					bx = cx + Math.cos(ang) * dist;
					bz = cz + Math.sin(ang) * dist;
				}

				double sizeMul = clustered ? 0.5 + 0.35 * rand(gx, gz, 1110 + i) : 1.0;
				double peak = 68 + 27 * rand(gx, gz, 14 + salt);
				double land = Math.max(12, Math.min((24 + 36 * rand(gx, gz, 15 + salt)) * sizeMul, room - 30));
				boolean clear = true;
				for (double[] other : sited) {

					if (Math.hypot(other[0] - bx, other[1] - bz) < 1.45 * (other[2] + land) + 20) {
						clear = false;
						break;
					}
				}
				if (!clear) continue;
				sited.add(new double[]{bx, bz, land});

				boolean crater = rand(gx, gz, 81 + salt) < 0.5;
				double cr = land * (0.16 + 0.1 * rand(gx, gz, 82 + salt));
				double cd = 8 + 8 * rand(gx, gz, 83 + salt);

				double[] rs = crater
					? new double[]{0, cr * 0.6, cr, land * 0.3, land * 0.6, land * 0.85, land, land + 8, land + 16, land + 28}
					: new double[]{0, land * 0.3, land * 0.6, land * 0.85, land, land + 8, land + 16, land + 28};
				double[] ys = crater
					? new double[]{peak - cd, peak - cd + 1, peak + 2, summit(66, peak, 0.3, 1.4), summit(66, peak, 0.6, 1.4), 66, 63.6, 52, 40, 20}
					: new double[]{peak, summit(66, peak, 0.3, 1.4), summit(66, peak, 0.6, 1.4), 66, 63.6, 52, 40, 20};
				Cone b = new Cone(bx, bz, TYPE_BARREN, age, rs, ys, 1.4, land + 5, 0, land,
					harmonics(gx, gz, i == 0 ? 100 : 1200 + 10 * i, 0.4), phases(gx, gz, i == 0 ? 110 : 1250 + 10 * i));

				b.gully = clustered ? 0.14 : 0.2;
				b.rock = true;
				b.variant = clustered ? 1 : 0;
				if (crater) {
					b.craterRadius = cr;
					b.craterLavaY = peak - cd + 3 + 3 * rand(gx, gz, 84 + salt);
				}
				cones.add(b);
				i++;
			}
		} else if (age < 0.62) {

			double peak = 85 + 65 * rand(gx, gz, 14);

			boolean archipelago = rand(gx, gz, 1300) < 0.4;
			double land = (90 + 100 * rand(gx, gz, 15)) * (archipelago ? 0.68 : 1.0);
			double beach = 10 + 10 * rand(gx, gz, 19);
			double shelf = 35 + 35 * rand(gx, gz, 16);
			double shelfY = 56 - 6 * rand(gx, gz, 17);

			double fit = Math.min(1.0, (room - 30) / (land + beach + shelf + 16));
			if (fit < 1) {
				land *= fit;
				shelf *= fit;
				peak = 64 + (peak - 64) * Math.pow(fit, 0.7);
			}
			int morph = (int) (rand(gx, gz, 80) * 4);

			boolean rock = morph == 1 || rand(gx, gz, 85) < 0.33;
			double summitExp = rock ? 1.6 : 1.2;
			double[] rs, ys;
			double reliefScale;
			double sh = land + beach + shelf;
			switch (morph) {
				case 1 -> {
					double top = Math.min(64 + (peak - 64) * 0.55, 72 + 1.1 * 0.33 * land);
					rs = new double[]{0, land * 0.45, land * 0.78, land * 0.9, land, land + beach, land + beach + shelf * 0.5, sh, sh + 7, sh + 16};
					ys = new double[]{top, top - 2, 70, 66, 64.3, 61.5, shelfY, shelfY - 2, 30, 5};
					reliefScale = 0.25;
				}
				case 2 -> {
					double hillPeak = Math.min(74 + (peak - 74) * 0.8, 74 + 0.8 * land * 0.5);
					double e = rock ? 1.3 : 1.1;
					rs = new double[]{0, land * 0.12, land * 0.25, land * 0.38, land * 0.5, land * 0.85, land, land + beach, land + beach + shelf * 0.5, sh, sh + 7, sh + 16};
					ys = new double[]{hillPeak, summit(74, hillPeak, 0.24, e), summit(74, hillPeak, 0.5, e), summit(74, hillPeak, 0.76, e), 72, 68, 64.3, 61.5, shelfY, shelfY - 2, 30, 5};
					reliefScale = 0.5;
				}
				default -> {
					peak = Math.min(peak, 66.5 + 0.85 * land);
					rs = new double[]{0, land * 0.15, land * 0.35, land * 0.6, land * 0.85, land, land + beach, land + beach + shelf * 0.5, sh, sh + 7, sh + 16};
					ys = new double[]{peak, summit(66, peak, 0.15, summitExp), summit(66, peak, 0.35, summitExp), summit(66, peak, 0.6, summitExp), 66.5, 64.3, 61.5, shelfY, shelfY - 2, 30, 5};
					reliefScale = 1.0;
				}
			}
			if (rock) reliefScale *= 0.6;
			Cone main = new Cone(cx, cz, TYPE_LARGE, age, rs, ys, 1.0, land + 8, 0, land, harmonics(gx, gz, 100, 0.42), phases(gx, gz, 110));
			main.reliefScale = reliefScale;
			main.rock = rock;
			main.variant = archipelago ? 1 : 0;
			cones.add(main);
			int lobes = 2 + (int) (rand(gx, gz, 60) * 2);
			for (int i = 0; i < lobes; i++) {
				double ang = rand(gx, gz, 61 + i) * Math.PI * 2;
				double dist = land * (0.5 + 0.5 * rand(gx, gz, 64 + i));
				boolean twin = morph == 3 && i == 0;
				double ll = land * (twin ? 0.6 + 0.2 * rand(gx, gz, 67 + i) : 0.4 + 0.3 * rand(gx, gz, 67 + i));
				double lp = 64 + (peak - 64) * (twin ? 0.7 + 0.2 * rand(gx, gz, 70) : 0.25 + 0.25 * rand(gx, gz, 70 + i));

				lp = Math.min(lp, 66 + 0.8 * ll);
				double lsh = shelf * 0.8;
				Cone lobe = new Cone(cx + Math.cos(ang) * dist, cz + Math.sin(ang) * dist, TYPE_LARGE, age,
					new double[]{0, ll * 0.25, ll * 0.5, ll * 0.8, ll, ll + beach, ll + beach + lsh, ll + beach + lsh + 7, ll + beach + lsh + 16},
					new double[]{lp, summit(66, lp, 0.25, summitExp), summit(66, lp, 0.5, summitExp), summit(66, lp, 0.8, summitExp), 64.3, 61.5, shelfY - 1, 30, 5}, 1.0, ll + 6, 0, ll,
					harmonics(gx, gz, 120 + 10 * i, 0.4), phases(gx, gz, 125 + 10 * i));
				lobe.reliefScale = reliefScale;
				lobe.rock = rock;
				lobe.variant = main.variant;
				cones.add(lobe);
			}

			int sats = archipelago ? 4 + (int) (rand(gx, gz, 18) * 4) : 1 + (int) (rand(gx, gz, 18) * 3);
			for (int i = 0; i < sats; i++) {
				double ang = rand(gx, gz, 20 + i) * Math.PI * 2;

				double gap = archipelago ? 50 : 35;
				double maxOut = Math.max(land * 1.3, room - 40);
				double want = archipelago ? 24 + 32 * rand(gx, gz, 40 + i) : 15 + 20 * rand(gx, gz, 40 + i);
				double sl = Math.min(want, Math.max(10, (maxOut - land - gap) * 0.45));
				double dist = Math.min(land + sl + gap + rand(gx, gz, 30 + i) * land * 0.5, maxOut - sl);
				double sp = 65 + 9 * rand(gx, gz, 50 + i);
				Cone sat = new Cone(cx + Math.cos(ang) * dist, cz + Math.sin(ang) * dist, TYPE_LARGE, age,
					new double[]{0, sl * 0.6, sl, sl + 5, sl + 18, sl + 30}, new double[]{sp, 64 + (sp - 64) * 0.5, 63.6, 58, 50, 30}, 1.2, sl + 4, 0, sl,
					harmonics(gx, gz, 140 + 10 * i, 0.35), phases(gx, gz, 145 + 10 * i));
				sat.rock = rock;
				sat.variant = main.variant;
				cones.add(sat);
			}
		} else if (age < 0.9) {

			double plateauY = 51 + 7 * rand(gx, gz, 14);

			double densityRoll = rand(gx, gz, 1400);
			int mult = densityRoll < 0.55 ? 1 : densityRoll < 0.82 ? 2 : 3;
			double rl = Math.min((80 + 90 * rand(gx, gz, 15)) * (mult == 1 ? 1.0 : mult == 2 ? 1.25 : 1.45), room - 70);
			Cone plateau = withGully(new Cone(cx, cz, TYPE_CLUSTER, age,
				new double[]{0, rl, rl + 12, rl + 36, rl + 44, rl + 54},
				new double[]{plateauY, plateauY, plateauY - 2, plateauY - 8, 28, 5}, 1.0, 0, rl, 0,
				harmonics(gx, gz, 100, 0.22), phases(gx, gz, 110)), 0.05);
			plateau.variant = mult;
			cones.add(plateau);
			int want = (6 + (int) (rand(gx, gz, 18) * 7)) * mult;
			List<double[]> placed = new ArrayList<>();

			for (int i = 0, tries = 0; i < want && tries < 64 * mult; tries++) {
				double ang = rand(gx, gz, 1600 + tries) * Math.PI * 2;
				double dist = Math.sqrt(rand(gx, gz, 2600 + tries)) * (rl - 8);
				double ir = (8 + 20 * rand(gx, gz, 3600 + tries)) * (mult == 1 ? 1.0 : 0.8);
				double ix = cx + Math.cos(ang) * dist, iz = cz + Math.sin(ang) * dist;
				boolean clear = true;
				for (double[] other : placed) {

					if (Math.hypot(other[0] - ix, other[1] - iz) < 1.5 * (other[2] + ir) + 24) {
						clear = false;
						break;
					}
				}
				if (!clear) continue;
				placed.add(new double[]{ix, iz, ir});
				double ip = 64.5 + 3.5 * rand(gx, gz, 4600 + tries);
				Cone islet = new Cone(ix, iz, TYPE_CLUSTER, age,
					new double[]{0, ir * 0.6, ir, ir + 4, ir + 10}, new double[]{ip, 64 + (ip - 64) * 0.5, 63.6, 60, plateauY}, 1.2, ir + 3, 0, ir,
					harmonics(gx, gz, 160 + 10 * i, 0.4), phases(gx, gz, 165 + 10 * i));
				double aspect = 1 + 1.6 * rand(gx, gz, 5600 + tries);
				if (aspect > 1.3) {
					double axis = rand(gx, gz, 6600 + tries) * Math.PI;
					islet.aspect = aspect;
					islet.axisX = Math.cos(axis);
					islet.axisZ = Math.sin(axis);
				}

				islet.variant = mult;
				cones.add(islet);
				i++;
			}
			int patches = 2 + (int) (rand(gx, gz, 900) * 3);
			for (int q = 0; q < patches; q++) {
				double pa = rand(gx, gz, 901 + q) * Math.PI * 2;
				double pd = Math.sqrt(rand(gx, gz, 906 + q)) * rl;
				double pr = 8 + 12 * rand(gx, gz, 911 + q);
				double pp = 59 + 3 * rand(gx, gz, 916 + q);
				cones.add(new Cone(cx + Math.cos(pa) * pd, cz + Math.sin(pa) * pd, TYPE_CLUSTER, age,
					new double[]{0, pr * 0.6, pr, pr + 6}, new double[]{pp, pp - 1.5, pp - 4, plateauY}, 0.9, 0, 0, 0,
					harmonics(gx, gz, 500 + 10 * q, 0.35), phases(gx, gz, 505 + 10 * q)));
			}
		} else {
			double top = 15 + 30 * rand(gx, gz, 14);
			double rad = Math.min(80 + 80 * rand(gx, gz, 15), room - 30);
			cones.add(withGully(new Cone(cx, cz, TYPE_GUYOT, age, new double[]{0, rad, rad + 14}, new double[]{top, top, top - 45}, 1.0, 0, 0, 0,
				harmonics(gx, gz, 100, 0.2), phases(gx, gz, 110)), 0.06));
		}
		return cones.toArray(new Cone[0]);
	}

	private Cone[] minor(int mx, int mz) {
		return minorCache.computeIfAbsent(mix(((long) mx << 32) ^ (mz & 0xffffffffL)), k -> buildMinor(mx, mz));
	}

	private Cone[] buildMinor(int mx, int mz) {
		double cx = (mx + 0.2 + 0.6 * rand(mx, mz, 61)) * MINOR_CELL;
		double cz = (mz + 0.2 + 0.6 * rand(mx, mz, 62)) * MINOR_CELL;
		if (rand(mx, mz, 60) > 0.3 * provinces(cx, cz).first.minorMult) return new Cone[0];
		double kind = rand(mx, mz, 63);
		double slope = 0.8 + 0.7 * rand(mx, mz, 64);
		List<Cone> cones = new ArrayList<>();
		if (kind < 0.15) {

			double land = 6 + 10 * rand(mx, mz, 65);
			double peak = 64.5 + 7 * rand(mx, mz, 66);
			Cone rock = new Cone(cx, cz, TYPE_BARREN, 0.1,
				new double[]{0, land, land + 6, land + 14}, new double[]{peak, 63.6, 54, 40}, slope, land + 3, 0, land,
				harmonics(mx, mz, 200, 0.45), phases(mx, mz, 210));
			rock.rock = true;
			cones.add(rock);
		} else {
			double peak = kind < 0.35 ? -20 + 75 * rand(mx, mz, 65) : -160 + 140 * rand(mx, mz, 65);
			double crown = 6 + 10 * rand(mx, mz, 66);
			cones.add(withGully(new Cone(cx, cz, TYPE_NONE, -1, new double[]{0, crown * 0.4, crown, crown * 1.6}, new double[]{peak, peak - 1.5, peak - 5, peak - 5 - crown * 0.6 * slope}, slope, 0, 0, 0,
				harmonics(mx, mz, 200, 0.35), phases(mx, mz, 210)), 0.22));
			if (rand(mx, mz, 67) < 0.35) {
				double ang = rand(mx, mz, 68) * Math.PI * 2;
				double dist = 40 + 60 * rand(mx, mz, 69);
				double peak2 = peak - 30 * rand(mx, mz, 70);
				cones.add(new Cone(cx + Math.cos(ang) * dist, cz + Math.sin(ang) * dist, TYPE_NONE, -1,
					new double[]{0, 5}, new double[]{peak2, peak2 - 3}, slope * 1.1, 0, 0, 0,
					harmonics(mx, mz, 220, 0.35), phases(mx, mz, 230)));
			}
		}
		return cones.toArray(new Cone[0]);
	}

	private void compute(double x, double z, Sample s) {
		computeBase(x, z, s);
		s.carve = 0;
		s.terrainSlope = 0;
		s.floorMin = s.floorY;
		if (FlowTiles.ENABLED) {
			double carve = flow.carveAt(x, z);
			double slope = flow.lastTerrainSlope();
			s.terrainSlope = slope;
			s.floorMin = flow.lastFloorMin();
			double floor = s.floorY;

			if (floor > CLIFF_MIN_FLOOR) s.cliff = Math.max(s.cliff, Mth.clamp((slope - STEEP_SLOPE) / 0.6, 0, 1));

			double wall = Mth.clamp((slope - WALL_SLOPE) / (WALL_SLOPE_FULL - WALL_SLOPE), 0, 1);
			if (wall > 0) floor -= s.wallNoise * wall;
			if (carve > 0.01) {
				double land = Mth.clamp((s.floorY - 60) / 10.0, 0, 1);
				double factor = 1 - LAND_CARVE_CUT * land;
				carve *= factor;
				floor -= carve;
				s.carve = carve;
				if (floor > CLIFF_MIN_FLOOR) {

					double valleyWall = flow.lastGradient() * factor;
					s.cliff = Math.max(s.cliff, Mth.clamp((valleyWall - 0.9) / 0.8, 0, 1));
				}
			}
			if (floor != s.floorY) {
				s.floorY = floor;
				s.landMask = Mth.clamp((floor - 62.5) / 2.0, 0, 1);
				if (floor <= CLIFF_MIN_FLOOR) s.cliff = 0;
			}
		}
		tier4(x, z, s);
	}

	private void tier4(double x, double z, Sample s) {
		double slope = s.terrainSlope;
		s.wall = WALLS
			? Mth.clamp((slope - WALL_NOISE_SLOPE) / WALL_NOISE_SPAN, 0, 1) * Mth.clamp(WALL_RELIEF * slope / 48.0, 0, 1)
			: 0;

		double island = CAVES_ISLAND ? s.coreT : 0;
		double seaFloor = CAVES_FLOOR ? (1 - s.coreT) * Mth.clamp((SEA_LEVEL - s.floorY) / CAVE_SEA_FADE, 0, 1) : 0;
		s.caveMask = Math.max(island, seaFloor);

		s.seaCave = 0;
		if (SEA_CAVES && slope > SEA_CAVE_SLOPE && s.floorY > SEA_CAVE_FLOOR_LO - SEA_CAVE_EDGE && s.floorY < SEA_CAVE_FLOOR_HI + SEA_CAVE_EDGE) {
			double band = Mth.clamp((s.floorY - (SEA_CAVE_FLOOR_LO - SEA_CAVE_EDGE)) / SEA_CAVE_EDGE, 0, 1)
				* Mth.clamp(((SEA_CAVE_FLOOR_HI + SEA_CAVE_EDGE) - s.floorY) / SEA_CAVE_EDGE, 0, 1);
			s.seaCave = band * Mth.clamp((slope - SEA_CAVE_SLOPE) / 0.5, 0, 1) * Mth.clamp(SEA_CAVE_RELIEF * slope / 48.0, 0, 1);
		}

		s.blueHole = BLUE_HOLES && s.platformW > 0.5 && s.pitRaw > BLUE_HOLE_THR
			? smoothstep((s.pitRaw - BLUE_HOLE_THR) / BLUE_HOLE_SPAN) * smoothstep((s.platformW - 0.5) / 0.3)
			: 0;

		double flooded = Mth.clamp(Math.max(s.blueHole * 3, s.seaCave * 4), 0, 1);
		s.caveMask *= 1 - flooded;

		s.wet = 0;
		s.wetEdge = 0;
		if (WET_CAVES && CAVES_FLOOR_AIR && flooded <= 0 && s.coreT <= 0 && s.floorY < SEA_LEVEL - WET_DEPTH) {
			double n = wetCave.get(x, z);
			s.wet = smoothstep((n - WET_THR) / WET_CORE_SPAN);

			double ring = smoothstep((n - (WET_THR - 2 * WET_SPAN)) / WET_SPAN);
			s.wetEdge = ring * (1 - s.wet);
		}

		double seaSeal = CAVE_SEAL + Math.min(CAVE_COVER_MAX,
			Math.max(CAVE_WALL_COVER * s.wall, s.terrainSlope * CAVE_COVER));
		double aboveWater = Mth.clamp((s.floorY - (SEA_LEVEL + CAVE_LAND_LOW)) / CAVE_LAND_SPAN, 0, 1);
		double floodTo = Mth.lerp(flooded, 0, s.blueHole > 0 ? BLUE_HOLE_FLOODED : SEA_CAVE_FLOODED);

		double drop = Math.max(0, s.floorY - s.floorMin);
		double lateral = s.floorMin < SEA_LEVEL
			? CAVE_SEAL + drop + CAVE_LATERAL * Mth.clamp(drop / CAVE_LATERAL, 0, 1)
			: CAVE_ISLAND_TOP;
		s.seal = Math.max(Math.max(Mth.lerp(aboveWater, seaSeal, CAVE_ISLAND_TOP), lateral), floodTo);
		s.caveAir = CAVES_FLOOR_AIR ? s.seal : CAVE_AIR_NEVER;

		if (s.wet > 0) {
			s.seal = Mth.lerp(s.wet, s.seal, WET_SEAL);
			s.caveAir = Math.max(s.seal, Mth.lerp(s.wet, s.caveAir, WET_DEPTH));
		}
	}

	double baseFloor(double x, double z, Sample s) {
		computeBase(x, z, s);
		return s.floorY;
	}

	private void computeBase(double x, double z, Sample s) {
		s.plateau = 0; s.pit = 0; s.canyon = 0; s.fault = 0;
		s.pitRaw = 0; s.platformW = 0;
		double wx = x + WARP_AMPLITUDE * warpA.get(x, z);
		double wz = z + WARP_AMPLITUDE * warpB.get(x, z);
		double regional = regional(wx, wz, x, z, s);

		double cxw = x + COAST_WARP * coastA.get(x, z) + COAST_WARP_FINE * coastC.get(x, z);
		double czw = z + COAST_WARP * coastB.get(x, z) + COAST_WARP_FINE * coastD.get(x, z);
		double floor = regional;
		Cone dominant = null;
		double dominantY = regional;
		double dominantR = 0;

		int gx = Mth.floor(cxw / GROUP_CELL), gz = Mth.floor(czw / GROUP_CELL);
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				for (Cone c : group(gx + dx, gz + dz)) {
					double reach = c.reach(regional - 30);
					if (Math.abs(cxw - c.cx) > reach || Math.abs(czw - c.cz) > reach) continue;
					double r = radiusWithGullies(c, cxw, czw);
					double y = c.yAt(r);
					if (y <= regional - 30) continue;
					floor = smax(floor, y, 10);
					if (y > dominantY) {
						dominantY = y;
						dominant = c;
						dominantR = r;
					}
				}
			}
		}
		int mx = Mth.floor(cxw / MINOR_CELL), mz = Mth.floor(czw / MINOR_CELL);
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				for (Cone c : minor(mx + dx, mz + dz)) {
					double reach = c.reach(regional - 30);
					if (Math.abs(cxw - c.cx) > reach || Math.abs(czw - c.cz) > reach) continue;
					double r = radiusWithGullies(c, cxw, czw);
					double y = c.yAt(r);
					if (y <= regional - 30) continue;
					floor = smax(floor, y, 10);
					if (y > dominantY && c.landRadius > 0) {
						dominantY = y;
						dominant = c;
						dominantR = r;
					}
				}
			}
		}

		boolean lagoon = dominant != null && dominant.lagoonRadius > 0 && dominantR < dominant.lagoonRadius;
		boolean crater = dominant != null && dominant.craterRadius > 0 && dominantR < dominant.craterRadius;

		s.relief = 0;
		if (floor > 65) {
			double lm = Mth.clamp((floor - 65) / 8.0, 0, 1);
			if (dominant != null && dominant.craterRadius > 0) lm *= Mth.clamp((dominantR / dominant.craterRadius - 0.9) / 0.5, 0, 1);
			double amp = Math.min(RELIEF_MAX, 6 + 0.3 * (floor - 65)) * (dominant == null ? 1.0 : dominant.reliefScale);
			s.relief = lm * (landRelief.ridged(x, z) * amp - amp * 0.35);
			floor += s.relief;
		}
		double texScale = floor > 60 ? 0.15 : Mth.clamp((SEA_LEVEL - floor - 5) / 30.0, 0.3, 1.0);
		double tex = s.province.texture * texture.get(x, z) * texScale;
		floor += tex;
		if (lagoon) floor += LAGOON_TEXTURE * lagoonTex.get(x, z);
		double fineAmount = (floor > 60 ? FINE_TEXTURE_LAND : FINE_TEXTURE_SEA) * fine.get(x, z);
		floor += fineAmount;
		s.wallNoise = fineAmount + 0.7 * tex;

		s.floorY = floor;
		s.regionalFloor = regional;
		s.type = dominant == null ? TYPE_NONE : dominant.type;
		s.variant = dominant == null ? 0 : dominant.variant;
		s.age = dominant == null ? -1 : dominant.age;
		s.lagoon = lagoon;
		s.core = dominant != null && dominant.coreRadius > 0 && dominantR < dominant.coreRadius;

		s.coreT = 0;
		if (dominant != null && dominant.coreRadius > 0) {
			double ramp = Math.min(CAVE_CORE_RAMP, dominant.coreRadius * 0.5);
			s.coreT = ramp <= 0 ? 0 : Mth.clamp((dominant.coreRadius - dominantR) / ramp, 0, 1);
		}
		s.rock = dominant != null && dominant.rock;
		s.craterLavaY = crater ? dominant.craterLavaY : Double.NaN;
		s.landMask = Mth.clamp((floor - 62.5) / 2.0, 0, 1);
		double slope = dominant == null || !dominant.rock ? 0 : dominant.slopeAt(dominantR);
		s.cliff = floor > CLIFF_MIN_FLOOR ? Mth.clamp((slope - ROCK_CLIFF_SLOPE) / 0.5, 0, 1) : 0;
	}

	public double[] findNearestGroup(double x, double z, int wantedType, int maxCells) {
		return findNearestGroup(x, z, wantedType, -1, maxCells);
	}

	public java.util.List<double[]> groupsIn(double minX, double minZ, double maxX, double maxZ) {
		java.util.List<double[]> out = new ArrayList<>();
		int gx0 = Mth.floor(minX / GROUP_CELL) - 1, gx1 = Mth.floor(maxX / GROUP_CELL) + 1;
		int gz0 = Mth.floor(minZ / GROUP_CELL) - 1, gz1 = Mth.floor(maxZ / GROUP_CELL) + 1;
		for (int gx = gx0; gx <= gx1; gx++) {
			for (int gz = gz0; gz <= gz1; gz++) {
				double cx = groupCentreX(gx, gz), cz = groupCentreZ(gx, gz);
				if (cx < minX || cx > maxX || cz < minZ || cz > maxZ) continue;
				Cone[] g = group(gx, gz);
				if (g.length == 0) continue;
				Cone main = g[0];
				double reach = main.type == TYPE_CLUSTER ? main.lagoonRadius + 60 : main.landRadius;
				out.add(new double[]{main.cx, main.cz, reach, main.type, main.variant});
			}
		}
		return out;
	}

	public double[] groupAt(double x, double z) {
		int gx = Mth.floor(x / GROUP_CELL), gz = Mth.floor(z / GROUP_CELL);
		Cone[] g = group(gx, gz);
		if (g.length == 0) return null;
		Cone main = g[0];
		double reach = main.type == TYPE_CLUSTER ? main.lagoonRadius + 60 : main.landRadius;
		return new double[]{main.cx, main.cz, reach, main.type, main.variant};
	}

	public double[] findNearestGroup(double x, double z, int wantedType, int wantedVariant, int maxCells) {
		int gx = Mth.floor(x / GROUP_CELL), gz = Mth.floor(z / GROUP_CELL);
		for (int ring = 0; ring <= maxCells; ring++) {
			double[] best = null;
			double bestD = Double.MAX_VALUE;
			for (int dx = -ring; dx <= ring; dx++) {
				for (int dz = -ring; dz <= ring; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
					Cone[] g = group(gx + dx, gz + dz);
					if (g.length == 0) continue;
					Cone main = g[0];
					if (wantedType >= 0 && main.type != wantedType) continue;
					if (wantedVariant >= 0 && main.variant != wantedVariant) continue;
					double d = Math.hypot(main.cx - x, main.cz - z);
					if (d < bestD) {
						bestD = d;
						double reach = main.type == TYPE_CLUSTER ? main.lagoonRadius + 60 : main.landRadius;
						best = new double[]{main.cx, main.cz, reach, main.type, main.variant};
					}
				}
			}
			if (best != null) return best;
		}
		return null;
	}

	public double[] findOpenOceanSpawn(double nearX, double nearZ) {
		long started = System.nanoTime();
		int sweeps = 0;

		double bestX = nearX, bestZ = nearZ, bestScore = -Double.MAX_VALUE;
		boolean scored = false;

		for (int ring = 1; ring <= OPEN_SPAWN_RINGS; ring++) {
			int steps = ring * 6;
			for (int i = 0; i < steps; i++) {
				double ang = (2.0 * Math.PI * i) / steps + ring * 0.37;
				double px = nearX + Math.cos(ang) * ring * OPEN_SPAWN_STEP;
				double pz = nearZ + Math.sin(ang) * ring * OPEN_SPAWN_STEP;

				double floor = floorAt(Mth.floor(px), Mth.floor(pz));
				if (floor >= SEA_LEVEL) {
					continue;
				}

				double depth = SEA_LEVEL - floor;
				if (!scored || depth > bestScore) {
					if (!scored) {
						bestX = px;
						bestZ = pz;
						bestScore = depth;
						scored = true;
					}
				}

				if (depth < OPEN_SPAWN_MIN_DEPTH) {
					continue;
				}
				if (nearestGroupCentreDistance(px, pz) < OPEN_SPAWN_GROUP_CLEAR) {
					continue;
				}
				if (sweeps >= OPEN_SPAWN_MAX_SWEEPS) {
					continue;
				}
				sweeps++;

				double clear = landClearance(px, pz, OPEN_SPAWN_LAND_CLEAR);
				double score = OPEN_SPAWN_MIN_DEPTH + clear;
				if (score > bestScore) {
					bestX = px;
					bestZ = pz;
					bestScore = score;
				}
				if (clear >= OPEN_SPAWN_LAND_CLEAR) {
					return result(px, pz, clear, ring, sweeps, started);
				}
			}
		}

		return result(bestX, bestZ, bestScore - OPEN_SPAWN_MIN_DEPTH, OPEN_SPAWN_RINGS, sweeps, started);
	}

	private double[] result(double px, double pz, double clear, int ring, int sweeps, long started) {
		double yaw = rand(Mth.floor(px / GROUP_CELL), Mth.floor(pz / GROUP_CELL), 91) * 360.0 - 180.0;
		com.oceania.Oceania.LOGGER.info(
			"[spawn] open ocean spawn at {} {}, land clearance {} blocks, {} ring(s), {} sweep(s), {} ms",
			Mth.floor(px), Mth.floor(pz), Math.max(0, Mth.floor(clear)), ring, sweeps,
			(System.nanoTime() - started) / 1_000_000);
		return new double[]{px, pz, yaw};
	}

	private double nearestGroupCentreDistance(double x, double z) {
		double search = OPEN_SPAWN_GROUP_CLEAR + GROUP_CELL;
		double best = Double.MAX_VALUE;
		for (double[] g : groupsIn(x - search, z - search, x + search, z + search)) {
			best = Math.min(best, Math.hypot(g[0] - x, g[1] - z));
		}
		return best;
	}

	private double landClearance(double cx, double cz, double cap) {

		final int step = 24;
		if (floorAt(Mth.floor(cx), Mth.floor(cz)) >= SEA_LEVEL) {
			return 0;
		}
		for (double r = step; r <= cap; r += step) {
			int n = Math.max(8, (int) Math.ceil(2.0 * Math.PI * r / step));
			for (int i = 0; i < n; i++) {
				double a = (2.0 * Math.PI * i) / n;
				if (floorAt(Mth.floor(cx + Math.cos(a) * r), Mth.floor(cz + Math.sin(a) * r)) >= SEA_LEVEL) {
					return r - step;
				}
			}
		}
		return cap;
	}

	public double[] findSpawn(double nearX, double nearZ) {
		double[] g = findNearestGroup(nearX, nearZ, TYPE_LARGE, 24);
		if (g == null) g = findNearestGroup(nearX, nearZ, -1, 40);
		if (g == null) return null;
		double dist = g[2] * 1.6 + 70;
		int gx = Mth.floor(g[0] / GROUP_CELL), gz = Mth.floor(g[1] / GROUP_CELL);
		double start = rand(gx, gz, 90) * Math.PI * 2;
		for (int i = 0; i < 12; i++) {
			double ang = start + i * Math.PI / 6;
			double px = g[0] + Math.cos(ang) * dist, pz = g[1] + Math.sin(ang) * dist;
			if (sample(px, pz).floorY < 52) {
				double yaw = Math.toDegrees(Math.atan2(-(g[0] - px), g[1] - pz));
				return new double[]{px, pz, yaw};
			}
		}
		return new double[]{g[0] + dist, g[1], -90};
	}
}

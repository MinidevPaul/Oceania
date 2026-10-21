package com.oceania.debug;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.oceania.Oceania;
import com.oceania.worldgen.field.OceanFields;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CaveShares {
	private static final int AUTO_RADIUS = Integer.getInteger("oceania.cave.shares", -1);
	private static final int STEP = Integer.getInteger("oceania.cave.shares.step", 16);
	private static final String SEEDS = System.getProperty("oceania.cave.shares.seeds", "42");
	private static final String DATA_OVERRIDE = System.getProperty("oceania.vanilla.data", "");

	private static final int[] VANILLA_Y = {40, 32, 24, 16, 8, 0, -8, -16, -24, -32, -40, -48, -56};

	private static final int VANILLA_DEEP_FROM = 10;

	private static final int[] OURS_DEPTH = {32, 48, 64, 80, 96, 112, 128, 144, 160, 176, 192, 208, 224, 240, 256, 272, 288};

	private static final int OURS_DEEP_FROM = 6;

	private static final String[] CAVE_BIOMES = {"dripstone_caves", "lush_caves", "sulfur_caves", "deep_dark"};

	private static final String[] ISLAND_NAMES = {"", "barren cone", "large island", "atoll", "guyot"};

	private CaveShares() {
	}

	public static void init() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			if (AUTO_RADIUS <= 0) return;
			ServerLevel level = server.overworld();
			try {
				run(level, AUTO_RADIUS, STEP);
			} catch (Exception e) {
				Oceania.LOGGER.error("[shares] failed", e);
			}
			server.halt(false);
		});
	}

	private static void run(ServerLevel level, int radius, int step) throws IOException {
		int cx = level.getRespawnData().pos().getX(), cz = level.getRespawnData().pos().getZ();
		List<String> out = new ArrayList<>();
		out.add(String.format("cave-biome shares: %dx%d blocks at %d,%d, step %d (%d columns)",
			radius * 2, radius * 2, cx, cz, step, (2 * radius / step) * (2 * radius / step)));

		long t0 = System.nanoTime();
		OceanFields fields = MapDump.fields(level);
		BiomeResolver mine = level.getChunkSource().getGenerator().getBiomeSource()
			.createUncachedResolver(level.getChunkSource().randomState());
		Tally ours = sampleOurs(level, fields, mine,
			level.getChunkSource().randomState().createClimateSampler(SamplerContext.EMPTY_UNCACHED), cx, cz, radius, step);
		out.add(String.format("%nOURS, seed %d, %.1fs - depth below the local floor", level.getSeed(), (System.nanoTime() - t0) / 1e9));
		out.addAll(ours.report(OURS_DEPTH, "below floor", OURS_DEEP_FROM));

		Path settingsPath = vanillaSettings();
		out.add(String.format("%nVANILLA 26.3, from %s - absolute y", settingsPath));
		RegistryAccess ra = level.registryAccess();
		RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, ra);
		NoiseGeneratorSettings vanilla;
		try (Reader r = Files.newBufferedReader(settingsPath)) {
			vanilla = NoiseGeneratorSettings.DIRECT_CODEC.parse(ops, JsonParser.parseReader(r))
				.getOrThrow(m -> new IllegalStateException("vanilla noise_settings/overworld.json: " + m));
		}
		MultiNoiseBiomeSource vanillaSource = MultiNoiseBiomeSource.createFromPreset(
			ra.lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
				.getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD));
		for (String s : SEEDS.split(",")) {
			long seed = Long.parseLong(s.trim());
			t0 = System.nanoTime();
			RandomState rs = RandomState.create(ra.lookupOrThrow(Registries.NOISE), seed, vanilla);
			Tally t = sampleVanilla(vanillaSource.createUncachedResolver(rs),
				rs.createClimateSampler(SamplerContext.EMPTY_UNCACHED), cx, cz, radius, step);
			out.add(String.format("  seed %d, %.1fs:", seed, (System.nanoTime() - t0) / 1e9));
			out.addAll(t.report(VANILLA_Y, "y", VANILLA_DEEP_FROM));
		}

		out.add(String.format("%nLANDMARKS on seed %d, for aiming sections:", level.getSeed()));
		for (int type = 1; type <= 4; type++) {
			double[] g = fields.findNearestGroup(cx, cz, type, 40);
			out.add(g == null
				? String.format("  nearest %s: none within 40 group cells", ISLAND_NAMES[type])
				: String.format("  nearest %s: %d,%d radius %d (%d blocks away, floor %.0f at its centre)",
					ISLAND_NAMES[type], (int) g[0], (int) g[1], (int) g[2],
					(int) Math.hypot(g[0] - cx, g[1] - cz), fields.sample(g[0], g[1]).floorY));
		}
		out.add(String.format("  deepest floor in the square: %.0f at %d,%d", ours.deepestFloor, ours.deepX, ours.deepZ));

		String text = String.join(System.lineSeparator(), out);
		Oceania.LOGGER.info("[shares] {}", text);
		Path dir = level.getServer().getServerDirectory().resolve("oceania-maps");
		Files.createDirectories(dir);
		Path file = dir.resolve("cave-shares-" + level.getSeed() + "-" + cx + "_" + cz + "-r" + radius + ".txt");
		Files.writeString(file, text + System.lineSeparator());
		Oceania.LOGGER.info("[shares] written to {}", file);
	}

	private static Tally sampleOurs(ServerLevel level, OceanFields fields, BiomeResolver biomes,
									Climate.Sampler climate, int cx, int cz, int radius, int step) {
		Tally t = new Tally(OURS_DEPTH.length);
		int sea = level.getSeaLevel();
		Set<String> seen = new HashSet<>();
		for (int x = cx - radius; x < cx + radius; x += step) {
			for (int z = cz - radius; z < cz + radius; z += step) {
				OceanFields.Sample s = fields.sample(x, z);
				int floor = (int) s.floorY;
				boolean land = floor > sea;
				t.count(land);
				if (s.floorY < t.deepestFloor) {
					t.deepestFloor = s.floorY;
					t.deepX = x;
					t.deepZ = z;
				}
				t.axes.add(climate.sample(x >> 2, (floor - 80) >> 2, z >> 2));
				seen.clear();
				for (int i = 0; i < OURS_DEPTH.length; i++) {
					String n = name(biomes.getNoiseBiome(x >> 2, (floor - OURS_DEPTH[i]) >> 2, z >> 2));
					t.add(i, n, land);
					seen.add(n);
					t.nearest(n, x, z, cx, cz);
				}
				t.addAnyDepth(seen);
			}
		}
		return t;
	}

	private static Tally sampleVanilla(BiomeResolver biomes, Climate.Sampler climate, int cx, int cz, int radius, int step) {
		Tally t = new Tally(VANILLA_Y.length);
		Set<String> seen = new HashSet<>();
		for (int x = cx - radius; x < cx + radius; x += step) {
			for (int z = cz - radius; z < cz + radius; z += step) {
				String surface = name(biomes.getNoiseBiome(x >> 2, 64 >> 2, z >> 2));
				boolean land = !surface.contains("ocean") && !surface.contains("river");
				t.count(land);
				t.axes.add(climate.sample(x >> 2, 0, z >> 2));
				seen.clear();
				for (int i = 0; i < VANILLA_Y.length; i++) {
					String n = name(biomes.getNoiseBiome(x >> 2, VANILLA_Y[i] >> 2, z >> 2));
					t.add(i, n, land);
					seen.add(n);
				}
				t.addAnyDepth(seen);
			}
		}
		return t;
	}

	private static String name(Holder<Biome> b) {
		return b.unwrapKey().map(k -> k.identifier().getPath()).orElse("?");
	}

	private static final class Tally {
		private final int levels;
		private int columns, landColumns;
		private final Map<String, int[]> byLevel = new LinkedHashMap<>();
		private final Map<String, int[]> landByLevel = new LinkedHashMap<>();
		private final Map<String, Integer> anyDepth = new LinkedHashMap<>();
		private final Map<String, int[]> nearestAt = new LinkedHashMap<>();
		private final Axes axes = new Axes();
		private double deepestFloor = Double.MAX_VALUE;
		private int deepX, deepZ;

		Tally(int levels) {
			this.levels = levels;
		}

		void count(boolean land) {
			columns++;
			if (land) landColumns++;
		}

		void add(int level, String biome, boolean land) {
			byLevel.computeIfAbsent(biome, k -> new int[levels])[level]++;
			if (land) landByLevel.computeIfAbsent(biome, k -> new int[levels])[level]++;
		}

		void nearest(String biome, int x, int z, int cx, int cz) {
			double d = Math.hypot(x - cx, z - cz);
			int[] best = nearestAt.get(biome);
			if (best == null || d < Math.hypot(best[0] - cx, best[1] - cz)) nearestAt.put(biome, new int[]{x, z});
		}

		void addAnyDepth(Set<String> seen) {
			for (String n : seen) anyDepth.merge(n, 1, Integer::sum);
		}

		List<String> report(int[] ladder, String ladderName, int deepFrom) {
			List<String> lines = new ArrayList<>();
			if (columns == 0) return lines;
			StringBuilder head = new StringBuilder(String.format("    %-16s", ladderName));
			for (int v : ladder) head.append(String.format("%7d", v));
			lines.add(head + "   | cave layer | deep layer");
			for (String biome : CAVE_BIOMES) {
				int[] c = byLevel.get(biome);
				if (c == null) continue;
				StringBuilder row = new StringBuilder(String.format("    %-16s", biome));
				for (int i = 0; i < levels; i++) row.append(String.format("%6.2f%%", 100.0 * c[i] / columns));
				lines.add(row + String.format("   | %9.2f%% | %9.2f%%",
					layer(c, 0, deepFrom), layer(c, deepFrom, levels)));
			}
			StringBuilder plain = new StringBuilder(String.format("    %-16s", "plain (none)"));
			double[] plainLayer = new double[2];
			for (int i = 0; i < levels; i++) {
				int cave = 0;
				for (String biome : CAVE_BIOMES) {
					int[] c = byLevel.get(biome);
					if (c != null) cave += c[i];
				}
				plain.append(String.format("%6.2f%%", 100.0 * (columns - cave) / columns));
				plainLayer[i < deepFrom ? 0 : 1] += 100.0 * (columns - cave) / columns;
			}
			lines.add(plain + String.format("   | %9.2f%% | %9.2f%%",
				plainLayer[0] / deepFrom, plainLayer[1] / (levels - deepFrom)));
			StringBuilder any = new StringBuilder("    any depth:      ");
			for (String biome : CAVE_BIOMES) {
				any.append(String.format("%s %.2f%%  ", biome, 100.0 * anyDepth.getOrDefault(biome, 0) / columns));
			}
			lines.add(any.toString());
			lines.add(String.format("    columns: %d, of which land %.2f%%; the same four, land columns only:",
				columns, 100.0 * landColumns / columns));
			StringBuilder land = new StringBuilder("    land only:      ");
			for (String biome : CAVE_BIOMES) {
				int[] c = landByLevel.get(biome);
				land.append(String.format("%s cave layer %.2f%% deep layer %.2f%%  ", biome,
					landColumns == 0 || c == null ? 0 : layerOf(c, 0, deepFrom, landColumns),
					landColumns == 0 || c == null ? 0 : layerOf(c, deepFrom, levels, landColumns)));
			}
			lines.add(land.toString());
			StringBuilder near = new StringBuilder("    nearest column of each:");
			for (String biome : CAVE_BIOMES) {
				int[] at = nearestAt.get(biome);
				near.append(at == null ? String.format("  %s none in the square", biome)
					: String.format("  %s %d,%d", biome, at[0], at[1]));
			}
			lines.add(near.toString());
			lines.addAll(axes.report());
			return lines;
		}

		private double layer(int[] c, int from, int to) {
			return layerOf(c, from, to, columns);
		}

		private double layerOf(int[] c, int from, int to, int total) {
			long sum = 0;
			for (int i = from; i < to; i++) sum += c[i];
			return 100.0 * sum / ((double) total * (to - from));
		}
	}

	private static final class Axes {
		private static final int BUCKETS = 400;
		private final int[][] hist = new int[6][BUCKETS];
		private int n;
		private int dripBand, lushBand, sulfurBand;

		void add(Climate.TargetPoint p) {
			n++;
			double[] v = {p.temperature() / 10000.0, p.humidity() / 10000.0, p.continentalness() / 10000.0,
				p.erosion() / 10000.0, p.depth() / 10000.0, p.weirdness() / 10000.0};
			for (int a = 0; a < 6; a++) {
				int b = (int) Math.round((v[a] + 2) * 100);
				hist[a][Math.max(0, Math.min(BUCKETS - 1, b))]++;
			}
			if (v[2] >= 0.8) dripBand++;
			if (v[1] >= 0.7) lushBand++;
			if (v[5] >= -1.1 && v[5] <= -0.85) sulfurBand++;
		}

		List<String> report() {
			List<String> lines = new ArrayList<>();
			if (n == 0) return lines;
			String[] names = {"temperature", "humidity", "continental", "erosion", "depth", "weirdness"};
			StringBuilder sb = new StringBuilder("    climate axes at the cave layer (p10/p50/p90): ");
			for (int a = 0; a < 6; a++) {
				sb.append(String.format("%s %.2f/%.2f/%.2f  ", names[a], pct(a, 0.10), pct(a, 0.50), pct(a, 0.90)));
			}
			lines.add(sb.toString());
			lines.add(String.format("    vanilla key bands hold: continentalness>=0.8 (dripstone) %.2f%%, "
					+ "humidity>=0.7 (lush) %.2f%%, weirdness -1.1..-0.85 (sulfur) %.2f%% of columns",
				100.0 * dripBand / n, 100.0 * lushBand / n, 100.0 * sulfurBand / n));

			StringBuilder below = new StringBuilder("    selector CDF, share of columns with the humidity axis below:");
			for (double x = -0.75; x <= 0.751; x += 0.05) {
				below.append(String.format(" %+.2f:%.2f%%", x, 100.0 * cdf(1, x)));
			}
			lines.add(below.toString());
			return lines;
		}

		private double cdf(int axis, double x) {
			int bucket = (int) Math.round((x + 2) * 100);
			long seen = 0;
			for (int b = 0; b < Math.max(0, Math.min(BUCKETS, bucket)); b++) seen += hist[axis][b];
			return (double) seen / n;
		}

		private double pct(int axis, double q) {
			long target = (long) (q * n), seen = 0;
			for (int b = 0; b < BUCKETS; b++) {
				seen += hist[axis][b];
				if (seen >= target) return b / 100.0 - 2;
			}
			return 2;
		}
	}

	private static Path vanillaSettings() throws IOException {
		if (!DATA_OVERRIDE.isBlank()) return Path.of(DATA_OVERRIDE);
		String tail = "tools/vanilla-data-26.3/data/minecraft/worldgen/noise_settings/overworld.json";
		Path at = Path.of("").toAbsolutePath();
		for (int up = 0; up < 4 && at != null; up++, at = at.getParent()) {
			Path candidate = at.resolve(tail);
			if (Files.isReadable(candidate)) return candidate;
		}
		throw new IOException("vanilla 26.3 data not found: run tools/fetch_vanilla_data.py, or pass "
			+ "-PoceaniaVanillaData=<path to noise_settings/overworld.json>. Looked for " + tail
			+ " from " + Path.of("").toAbsolutePath() + " upwards.");
	}
}

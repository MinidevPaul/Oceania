package com.oceania.debug;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.oceania.Oceania;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.Comparator;
import java.util.PriorityQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.FluidTags;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class OreScan {
	private static final int AUTO_RADIUS = Integer.getInteger("oceania.ore.radius", -1);
	private static final Integer AUTO_X = Integer.getInteger("oceania.ore.x");
	private static final Integer AUTO_Z = Integer.getInteger("oceania.ore.z");

	private static final int[] BANDS = {Integer.MIN_VALUE, -400, -250, -150, -60, 0, 63, Integer.MAX_VALUE};

	private static final Map<String, Block[]> WATCH = new LinkedHashMap<>();

	static {
		WATCH.put("coal", new Block[]{Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE});
		WATCH.put("iron", new Block[]{Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE});
		WATCH.put("copper", new Block[]{Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE});
		WATCH.put("gold", new Block[]{Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE});
		WATCH.put("redstone", new Block[]{Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE});
		WATCH.put("lapis", new Block[]{Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE});
		WATCH.put("diamond", new Block[]{Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE});
		WATCH.put("emerald", new Block[]{Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE});
		WATCH.put("granite", new Block[]{Blocks.GRANITE});
		WATCH.put("diorite", new Block[]{Blocks.DIORITE});
		WATCH.put("andesite", new Block[]{Blocks.ANDESITE});
		WATCH.put("tuff", new Block[]{Blocks.TUFF});
		WATCH.put("dirt", new Block[]{Blocks.DIRT});
		WATCH.put("gravel", new Block[]{Blocks.GRAVEL});
		WATCH.put("clay", new Block[]{Blocks.CLAY});

		WATCH.put("magma", new Block[]{Blocks.MAGMA_BLOCK});

		WATCH.put("jungle_log", new Block[]{Blocks.JUNGLE_LOG, Blocks.JUNGLE_LEAVES});
		WATCH.put("grass/flower", new Block[]{Blocks.SHORT_GRASS, Blocks.TALL_GRASS, Blocks.DANDELION, Blocks.POPPY});
		WATCH.put("bamboo/cane", new Block[]{Blocks.BAMBOO, Blocks.SUGAR_CANE});
		WATCH.put("cactus", new Block[]{Blocks.CACTUS});
		WATCH.put("kelp", new Block[]{Blocks.KELP, Blocks.KELP_PLANT});
		WATCH.put("seagrass", new Block[]{Blocks.SEAGRASS, Blocks.TALL_SEAGRASS});
		WATCH.put("coral", new Block[]{Blocks.TUBE_CORAL_BLOCK, Blocks.BRAIN_CORAL_BLOCK, Blocks.BUBBLE_CORAL_BLOCK,
			Blocks.FIRE_CORAL_BLOCK, Blocks.HORN_CORAL_BLOCK});
		WATCH.put("sea_pickle", new Block[]{Blocks.SEA_PICKLE});

		WATCH.put("vent rock", new Block[]{Blocks.BLACKSTONE});
		WATCH.put("vent core", new Block[]{Blocks.SOUL_SAND, Blocks.MAGMA_BLOCK});
		WATCH.put("bubbles", new Block[]{Blocks.BUBBLE_COLUMN});
		WATCH.put("glow_lichen", new Block[]{Blocks.GLOW_LICHEN});
		WATCH.put("nodule Fe/Cu", new Block[]{Blocks.RAW_IRON_BLOCK, Blocks.RAW_COPPER_BLOCK});
		WATCH.put("nodule Au/Nt", new Block[]{Blocks.RAW_GOLD_BLOCK, Blocks.ANCIENT_DEBRIS});

		WATCH.put("dripstone", new Block[]{Blocks.DRIPSTONE_BLOCK, Blocks.POINTED_DRIPSTONE});
		WATCH.put("amethyst", new Block[]{Blocks.AMETHYST_BLOCK, Blocks.BUDDING_AMETHYST, Blocks.AMETHYST_CLUSTER});
		WATCH.put("calcite", new Block[]{Blocks.CALCITE});
		WATCH.put("sculk", new Block[]{Blocks.SCULK, Blocks.SCULK_VEIN, Blocks.SCULK_CATALYST, Blocks.SCULK_SENSOR});
		WATCH.put("shrieker", new Block[]{Blocks.SCULK_SHRIEKER});
		WATCH.put("moss", new Block[]{Blocks.MOSS_BLOCK, Blocks.MOSS_CARPET});
		WATCH.put("cave_vines", new Block[]{Blocks.CAVE_VINES, Blocks.CAVE_VINES_PLANT});
		WATCH.put("azalea", new Block[]{Blocks.AZALEA, Blocks.FLOWERING_AZALEA, Blocks.AZALEA_LEAVES, Blocks.FLOWERING_AZALEA_LEAVES});
		WATCH.put("dripleaf", new Block[]{Blocks.BIG_DRIPLEAF, Blocks.SMALL_DRIPLEAF});
		WATCH.put("sulfur", new Block[]{Blocks.SULFUR, Blocks.POTENT_SULFUR});
		WATCH.put("sulfur_spike", new Block[]{Blocks.SULFUR_SPIKE});
		WATCH.put("cinnabar", new Block[]{Blocks.CINNABAR});
		WATCH.put("spawner", new Block[]{Blocks.SPAWNER});
		WATCH.put("lava", new Block[]{Blocks.LAVA});
		WATCH.put("city stone", new Block[]{Blocks.DEEPSLATE_BRICKS, Blocks.DEEPSLATE_TILES, Blocks.REINFORCED_DEEPSLATE});
		WATCH.put("trial stone", new Block[]{Blocks.TUFF_BRICKS, Blocks.POLISHED_TUFF, Blocks.CHISELED_TUFF, Blocks.TRIAL_SPAWNER, Blocks.VAULT});
	}

	private OreScan() {
	}

	public static void init() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			if (AUTO_RADIUS <= 0) return;
			ServerLevel level = server.overworld();
			BlockPos spawn = level.getRespawnData().pos();
			int cx = AUTO_X != null ? AUTO_X : spawn.getX(), cz = AUTO_Z != null ? AUTO_Z : spawn.getZ();
			Oceania.LOGGER.info("[ore] {}", scan(level, cx, cz, AUTO_RADIUS));
			server.halt(false);
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
			Commands.literal("oceania").requires(DebugCommands::visibleTo)
				.then(Commands.literal("ore")
					.executes(ctx -> run(ctx.getSource(), 6))
					.then(Commands.argument("radius", IntegerArgumentType.integer(1, 24))
						.executes(ctx -> run(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "radius")))))));
	}

	private static int run(CommandSourceStack src, int radius) {
		src.sendSystemMessage(Component.literal(scan(src.getLevel(), (int) src.getPosition().x, (int) src.getPosition().z, radius)));
		return 1;
	}

	private static String ventCheck(ServerLevel level, int cx0, int cz0, int side) {
		long floating = 0, total = 0, perched = 0;
		BlockPos first = null;
		BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
		for (int dx = 0; dx < side; dx++) {
			for (int dz = 0; dz < side; dz++) {
				LevelChunk chunk = level.getChunk(cx0 + dx, cz0 + dz);
				LevelChunkSection[] sections = chunk.getSections();
				for (int i = 0; i < sections.length; i++) {
					LevelChunkSection section = sections[i];
					if (section.hasOnlyAir() || !section.maybeHas(OreScan::isChimney)) continue;
					int y0 = chunk.getMinY() + (i << 4);
					for (int ly = 0; ly < 16; ly++) {
						for (int lx = 0; lx < 16; lx++) {
							for (int lz = 0; lz < 16; lz++) {
								if (!isChimney(section.getBlockState(lx, ly, lz))) continue;
								total++;
								int x = ((cx0 + dx) << 4) + lx, y = y0 + ly, z = ((cz0 + dz) << 4) + lz;
								if (!level.getBlockState(probe.set(x, y - 1, z)).getFluidState().is(FluidTags.WATER)) continue;
								perched++;
								int open = 0;
								for (Direction d : Direction.Plane.HORIZONTAL) {
									probe.set(x, y, z).move(d);
									if (level.getBlockState(probe).getFluidState().is(FluidTags.WATER)) open++;
								}
								if (open < 2) continue;
								floating++;
								if (first == null) first = new BlockPos(x, y, z);
							}
						}
					}
				}
			}
		}
		return String.format("vent invariant: %d of %d chimney blocks sit on water, %d of those with two or more open"
				+ " sides%s%n", perched, total, floating,
			first == null ? " (clean)" : " - first at " + first.toShortString());
	}

	private static String bubbleCheck(ServerLevel level, int cx0, int cz0, int side) {
		long columns = 0, orphans = 0, airPockets = 0, seaBreach = 0, undecided = 0;
		BlockPos first = null, firstAir = null;
		StringBuilder examples = new StringBuilder();
		int shown = 0;
		long[] breachByBand = new long[16];

		Long2ByteMap verdicts = new Long2ByteOpenHashMap();
		BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
		int sea = level.getSeaLevel();
		for (int dx = 0; dx < side; dx++) {
			for (int dz = 0; dz < side; dz++) {
				LevelChunk chunk = level.getChunk(cx0 + dx, cz0 + dz);
				LevelChunkSection[] sections = chunk.getSections();
				for (int i = 0; i < sections.length; i++) {
					LevelChunkSection section = sections[i];
					if (section.hasOnlyAir()) continue;
					int y0 = chunk.getMinY() + (i << 4);
					if (y0 > sea) continue;
					boolean anyBubble = section.maybeHas(s -> s.is(Blocks.BUBBLE_COLUMN));
					boolean anyAir = section.maybeHas(net.minecraft.world.level.block.state.BlockState::isAir);
					if (!anyBubble && !anyAir) continue;
					for (int ly = 0; ly < 16; ly++) {
						int y = y0 + ly;

						if (y > sea - 3) break;
						for (int lx = 0; lx < 16; lx++) {
							for (int lz = 0; lz < 16; lz++) {
								var state = section.getBlockState(lx, ly, lz);
								int x = ((cx0 + dx) << 4) + lx, z = ((cz0 + dz) << 4) + lz;
								if (state.is(Blocks.BUBBLE_COLUMN)) {
									columns++;
									boolean sourced = false;
									for (int d = 1; d <= 64 && !sourced; d++) {
										probe.set(x, y - d, z);
										var below = level.getBlockState(probe);
										if (below.is(Blocks.SOUL_SAND) || below.is(Blocks.MAGMA_BLOCK)) sourced = true;
										else if (!below.is(Blocks.BUBBLE_COLUMN) && below.getFluidState().isEmpty()) break;
									}
									if (!sourced) {
										orphans++;
										if (first == null) first = new BlockPos(x, y, z);
									}
								} else if (state.isAir()) {

									boolean touching = false, toSea = false, unsure = false;
									for (Direction d : Direction.values()) {
										probe.set(x, y, z).move(d);
										if (!level.getBlockState(probe).getFluidState().is(FluidTags.WATER)) continue;
										touching = true;
										byte v = bodyVerdict(level, probe.getX(), probe.getY(), probe.getZ(), sea, verdicts);
										if (v == BODY_OPEN) {
											toSea = true;
											break;
										}
										if (v == BODY_OVER_CAP) unsure = true;
									}
									if (unsure && !toSea) undecided++;
									if (touching) {
										airPockets++;
										if (firstAir == null) firstAir = new BlockPos(x, y, z);
									}
									if (toSea) {
										seaBreach++;
										breachByBand[Math.min(15, Math.max(0, (sea - y) / 32))]++;
										if (shown < 6) {
											examples.append(shown == 0 ? "" : ", ").append(x).append(",").append(y)
												.append(",").append(z);
											shown++;
										}
									}
								}
							}
						}
					}
				}
			}
		}
		StringBuilder bands = new StringBuilder();
		for (int b = 0; b < breachByBand.length; b++) {
			if (breachByBand[b] == 0) continue;
			bands.append(bands.length() == 0 ? "" : " ").append(b * 32).append("-").append(b * 32 + 32)
				.append(":").append(breachByBand[b]);
		}
		return String.format("bubble invariant: %d bubble columns, %d orphaned (no source below)%s"
				+ "%n  air touching water under the sea: %d block%s%s"
				+ "%n  of those OPEN TO THE SEA: %d%s%s%n", columns, orphans,
			first == null ? "" : " - first at " + first.toShortString(),
			airPockets, airPockets == 1 ? "" : "s",
			firstAir == null ? " (clean)" : " - first at " + firstAir.toShortString(),
			seaBreach,
			shown == 0 ? (undecided == 0 ? " (clean)" : "") : " - at " + examples,
			bands.length() == 0 ? "" : "; by depth below sea [" + bands + "]")
			+ (undecided == 0 ? "" : String.format("  WARNING: %d air block%s touched a water body larger than the"
				+ " %d-block fill cap, verdict unknown%n", undecided, undecided == 1 ? "" : "s", FILL_CAP));
	}

	private static final byte BODY_CLOSED = 1, BODY_OPEN = 2, BODY_OVER_CAP = 3;

	private static final int FILL_CAP = Integer.getInteger("oceania.ore.fill.cap", 400000);

	private static byte bodyVerdict(ServerLevel level, int x, int y, int z, int sea, Long2ByteMap verdicts) {
		long start = BlockPos.asLong(x, y, z);
		byte known = verdicts.get(start);
		if (known != 0) return known;
		BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();

		PriorityQueue<Long> queue = new PriorityQueue<>(Comparator.comparingInt(p -> -BlockPos.getY(p)));
		LongSet seen = new LongOpenHashSet();
		queue.add(start);
		seen.add(start);
		byte verdict = BODY_CLOSED;
		while (!queue.isEmpty()) {
			if (seen.size() > FILL_CAP) {
				verdict = BODY_OVER_CAP;
				break;
			}
			long cur = queue.poll();
			int cx = BlockPos.getX(cur), cy = BlockPos.getY(cur), cz = BlockPos.getZ(cur);

			if (cy >= sea - 1) {
				verdict = BODY_OPEN;
				break;
			}
			for (Direction d : Direction.values()) {
				probe.set(cx, cy, cz).move(d);
				if (!level.getBlockState(probe).getFluidState().is(FluidTags.WATER)) continue;
				long next = probe.asLong();
				if (seen.add(next)) queue.add(next);
			}
		}
		for (LongIterator it = seen.iterator(); it.hasNext(); ) verdicts.put(it.nextLong(), verdict);
		return verdict;
	}

	private static String shoreCheck(ServerLevel level, int cx0, int cz0, int side) {
		Map<String, Integer> tally = new LinkedHashMap<>();
		BlockPos first = null;
		int total = 0;
		int sea = level.getSeaLevel();
		for (int dx = 0; dx < side; dx++) {
			for (int dz = 0; dz < side; dz++) {
				LevelChunk chunk = level.getChunk(cx0 + dx, cz0 + dz);
				for (int lx = 0; lx < 16; lx++) {
					for (int lz = 0; lz < 16; lz++) {
						int x = ((cx0 + dx) << 4) + lx, z = ((cz0 + dz) << 4) + lz;

						int y = chunk.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR_WG, lx, lz);
						if (y < sea - 8 || y > sea + 4) continue;

						BlockPos at = new BlockPos(x, y, z);
						var state = level.getBlockState(at);
						if (state.isAir() || !state.getFluidState().isEmpty()) continue;
						String name = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
						tally.merge(name, 1, Integer::sum);
						total++;
						if (first == null && (state.is(Blocks.STONE) || state.is(Blocks.DEEPSLATE))) first = at;
					}
				}
			}
		}
		final int counted = total;
		StringBuilder sb = new StringBuilder(String.format("shore surface (y %d..%d), %d columns:", sea - 8, sea + 4, counted));
		tally.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(8)
			.forEach(e -> sb.append(String.format(" %s %.1f%%,", e.getKey(), 100.0 * e.getValue() / Math.max(1, counted))));
		if (first != null) sb.append(" first bare stone at ").append(first.toShortString());
		return sb.append(System.lineSeparator()).toString();
	}

	private static boolean isChimney(net.minecraft.world.level.block.state.BlockState state) {
		return state.is(Blocks.BLACKSTONE) || state.is(Blocks.BASALT) || state.is(Blocks.MAGMA_BLOCK);
	}

	public static String scan(ServerLevel level, int centreX, int centreZ, int radius) {
		long start = System.nanoTime();
		int cx0 = (centreX >> 4) - radius, cz0 = (centreZ >> 4) - radius;
		int side = radius * 2 + 1;
		int bands = BANDS.length - 1;
		Map<String, long[]> counts = new LinkedHashMap<>();
		for (String name : WATCH.keySet()) counts.put(name, new long[bands]);

		Map<String, BlockPos> found = new LinkedHashMap<>();

		Map<Block, List<String>> lookup = new LinkedHashMap<>();
		WATCH.forEach((name, blocks) -> {
			for (Block b : blocks) lookup.computeIfAbsent(b, k -> new ArrayList<>()).add(name);
		});

		long[] solid = new long[bands];

		for (int dx = 0; dx < side; dx++) {
			for (int dz = 0; dz < side; dz++) {
				int chunkX = cx0 + dx, chunkZ = cz0 + dz;
				LevelChunk chunk = level.getChunk(chunkX, chunkZ);
				LevelChunkSection[] sections = chunk.getSections();
				for (int i = 0; i < sections.length; i++) {
					LevelChunkSection section = sections[i];
					if (section.hasOnlyAir()) continue;
					int y = chunk.getMinY() + (i << 4) + 8;
					int band = bandOf(y);
					section.getStates().count((state, n) -> {
						List<String> names = lookup.get(state.getBlock());
						if (names != null) {
							for (String name : names) {
								counts.get(name)[band] += n;
								found.putIfAbsent(name, new BlockPos((chunkX << 4) + 8, y, (chunkZ << 4) + 8));
							}
						}
						if (!state.isAir() && state.getFluidState().isEmpty()) solid[band] += n;
					});
				}
			}
		}

		int chunks = side * side;
		StringBuilder sb = new StringBuilder();
		sb.append(ventCheck(level, cx0, cz0, side));
		sb.append(bubbleCheck(level, cx0, cz0, side));
		sb.append(shoreCheck(level, cx0, cz0, side));
		sb.append(String.format("ore scan %d by %d chunks at %d,%d: %.1fs, %d chunks%n  %-9s", side, side, centreX, centreZ,
			(System.nanoTime() - start) / 1e9, chunks, "band y"));
		List<String> labels = new ArrayList<>();
		for (int b = 0; b < bands; b++) {
			labels.add(String.format("%s..%s",
				BANDS[b] == Integer.MIN_VALUE ? "bottom" : String.valueOf(BANDS[b]),
				BANDS[b + 1] == Integer.MAX_VALUE ? "top" : String.valueOf(BANDS[b + 1])));
			sb.append(String.format("%14s", labels.get(b)));
		}
		sb.append(String.format("%n  %-9s", "rock/ch"));
		for (int b = 0; b < bands; b++) sb.append(String.format("%14.0f", (double) solid[b] / chunks));

		for (Map.Entry<String, long[]> e : counts.entrySet()) {
			long total = 0;
			for (long v : e.getValue()) total += v;
			if (total == 0) continue;
			sb.append(String.format("%n  %-9s", e.getKey()));
			for (int b = 0; b < bands; b++) {
				double perChunk = (double) e.getValue()[b] / chunks;
				double perM = solid[b] == 0 ? 0 : 1e6 * e.getValue()[b] / solid[b];
				sb.append(String.format("%14s", e.getValue()[b] == 0 ? "." : String.format("%.1f(%.0f)", perChunk, perM)));
			}
			sb.append(String.format("   total %.1f/ch", (double) total / chunks));
		}
		sb.append("\n  cells are blocks per chunk (per million blocks of rock in that band)");

		StringBuilder where = new StringBuilder();
		for (String key : new String[]{"vent core", "nodule Au/Nt", "nodule Fe/Cu", "kelp", "coral"}) {
			BlockPos at = found.get(key);
			if (at != null) where.append(String.format("  %s near %d,%d (y %d)", key, at.getX(), at.getZ(), at.getY()));
		}
		if (where.length() > 0) sb.append(String.format("%n  first seen:%s", where));		return sb.toString();
	}

	private static int bandOf(int y) {
		for (int b = BANDS.length - 2; b >= 0; b--) {
			if (y >= BANDS[b]) return b;
		}
		return 0;
	}
}

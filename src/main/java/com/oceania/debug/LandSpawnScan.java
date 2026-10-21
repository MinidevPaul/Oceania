package com.oceania.debug;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.oceania.Oceania;
import com.oceania.village.VillageGuard;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.WorldgenRandom;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class LandSpawnScan {
	private static final int AUTO_RADIUS = Integer.getInteger("oceania.landspawn.radius", -1);
	private static final Integer AUTO_X = Integer.getInteger("oceania.landspawn.x");
	private static final Integer AUTO_Z = Integer.getInteger("oceania.landspawn.z");

	private static final int AUTO_WIDE = Integer.getInteger("oceania.landspawn.wide", -1);

	private static final int TRIALS = 400;

	private static final List<EntityType<? extends Mob>> MONSTERS = List.of(
		EntityTypes.SPIDER, EntityTypes.ZOMBIE, EntityTypes.ZOMBIE_VILLAGER, EntityTypes.SKELETON,
		EntityTypes.CREEPER, EntityTypes.SLIME, EntityTypes.ENDERMAN, EntityTypes.WITCH, EntityTypes.OCELOT,
		EntityTypes.CAVE_SPIDER);
	private static final List<EntityType<? extends Mob>> CREATURES = List.of(
		EntityTypes.SHEEP, EntityTypes.PIG, EntityTypes.CHICKEN, EntityTypes.COW,
		EntityTypes.PARROT, EntityTypes.PANDA, EntityTypes.TURTLE);

	private static final List<EntityType<? extends Mob>> AMBIENT = List.of(EntityTypes.BAT);
	private static final List<EntityType<? extends Mob>> ALL =
		concat(concat(MONSTERS, CREATURES), AMBIENT);

	private static final String[] WANTED = {"island_jungle", "island_cliff", "island_sand", "island_barren"};

	private static List<EntityType<? extends Mob>> concat(List<EntityType<? extends Mob>> a, List<EntityType<? extends Mob>> b) {
		List<EntityType<? extends Mob>> out = new ArrayList<>(a);
		out.addAll(b);
		return out;
	}

	private record Site(String label, int x, int z) {
	}

	private static final List<Site> SITES = new ArrayList<>();
	private static int tick = -1;

	private LandSpawnScan() {
	}

	public static void init() {
		if (AUTO_RADIUS > 0) {
			ServerLifecycleEvents.SERVER_STARTED.register(LandSpawnScan::setUp);
			ServerTickEvents.END_SERVER_TICK.register(LandSpawnScan::drive);
		}
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
			Commands.literal("oceania").requires(DebugCommands::visibleTo)
				.then(Commands.literal("landspawn")
					.executes(ctx -> run(ctx.getSource(), 4))
					.then(Commands.argument("radius", IntegerArgumentType.integer(1, 24))
						.executes(ctx -> run(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "radius")))))));
	}

	private static void setUp(MinecraftServer server) {
		ServerLevel level = server.overworld();
		if (AUTO_X != null && AUTO_Z != null) {
			SITES.add(new Site("pinned", AUTO_X, AUTO_Z));
		} else {
			BlockPos spawn = level.getRespawnData().pos();
			for (String want : WANTED) {
				Site at = find(level, spawn.getX(), spawn.getZ(), want);
				if (at == null) {
					Oceania.LOGGER.warn("[landspawn] no {} found within the search radius - no window for it", want);
				} else {
					SITES.add(at);
				}
			}
			Oceania.LOGGER.info("[landspawn] sites: {}", SITES);
		}
		tick = 0;
	}

	private static Site find(ServerLevel level, int fromX, int fromZ, String want) {
		BiomeResolver biomes = level.getChunkSource().getGenerator().getBiomeSource()
			.createUncachedResolver(level.getChunkSource().randomState());
		int step = 48, maxRings = 160;
		for (int ring = 0; ring <= maxRings; ring++) {
			for (int dx = -ring; dx <= ring; dx++) {
				for (int dz = -ring; dz <= ring; dz++) {

					if (ring > 0 && Math.abs(dx) != ring && Math.abs(dz) != ring) continue;
					int x = fromX + dx * step, z = fromZ + dz * step;
					String name = biomes.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(62), QuartPos.fromBlock(z))
						.unwrapKey().map(k -> k.identifier().getPath()).orElse("?");
					if (want.equals(name)) return new Site(want, x, z);
				}
			}
		}
		return null;
	}

	private static void drive(MinecraftServer server) {
		if (tick < 0) return;
		tick++;
		ServerLevel level = server.overworld();
		switch (tick) {
			case 1 -> time(server, "noon");
			case 4 -> scanAll(level, "DAY");
			case 5 -> time(server, "midnight");
			case 8 -> scanAll(level, "NIGHT");
			case 9 -> {
				int wide = AUTO_WIDE > 0 ? AUTO_WIDE : AUTO_RADIUS;
				int cx = SITES.isEmpty() ? level.getRespawnData().pos().getX() : SITES.get(0).x();
				int cz = SITES.isEmpty() ? level.getRespawnData().pos().getZ() : SITES.get(0).z();
				Oceania.LOGGER.info("[landspawn] {}", spawners(level, cx, cz, wide));
				Oceania.LOGGER.info("[landspawn] {}", surface(level, cx, cz, wide));

				Oceania.LOGGER.info("[landspawn] {}", slimes(level, cx, cz, AUTO_RADIUS * 2, 4000));

				for (Site site : SITES) {
					Oceania.LOGGER.info("[landspawn] {} {}: {}", site.label(), "quiet",
						quiet(level, site.x(), site.z(), 24));
				}
				tick = -1;
				server.halt(false);
			}
			default -> {
			}
		}
	}

	private static void scanAll(ServerLevel level, String pass) {
		for (Site site : SITES) {
			Oceania.LOGGER.info("[landspawn] {} at {} {},{}: {}", pass, site.label(), site.x(), site.z(),
				scan(level, site.x(), site.z(), AUTO_RADIUS));
		}
	}

	private static void time(MinecraftServer server, String when) {
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "time set " + when);
	}

	private static int run(CommandSourceStack src, int radius) {
		ServerLevel level = src.getLevel();
		int x = (int) src.getPosition().x, z = (int) src.getPosition().z;
		src.sendSystemMessage(Component.literal(scan(level, x, z, radius)));
		src.sendSystemMessage(Component.literal(spawners(level, x, z, radius)));
		src.sendSystemMessage(Component.literal(surface(level, x, z, radius)));
		src.sendSystemMessage(Component.literal(slimes(level, x, z, radius, 2000)));
		src.sendSystemMessage(Component.literal(quiet(level, x, z, radius)));
		return 1;
	}

	private static final int PLACEMENT = 0, RULE = 1, BOX = 2, CLEAR = 3, PER_TYPE = 4;

	private static final int DRAWS = 0, QUIET = 1, PER_BIOME = 2;

	public static String scan(ServerLevel level, int centreX, int centreZ, int radius) {
		long start = System.nanoTime();
		RandomSource random = level.getRandom();
		int cx0 = (centreX >> 4) - radius, cz0 = (centreZ >> 4) - radius;
		int side = radius * 2 + 1;
		Map<String, long[]> byBiome = new LinkedHashMap<>();
		Map<String, long[]> byBiomeType = new LinkedHashMap<>();

		for (int dx = 0; dx < side; dx++) {
			for (int dz = 0; dz < side; dz++) {
				int chunkX = cx0 + dx, chunkZ = cz0 + dz;
				level.getChunk(chunkX, chunkZ);
				for (int i = 0; i < TRIALS; i++) {
					int x = (chunkX << 4) + random.nextInt(16);
					int z = (chunkZ << 4) + random.nextInt(16);

					int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) + 1;
					int y = Mth.randomBetweenInclusive(random, level.getMinY(), top);
					BlockPos pos = new BlockPos(x, y, z);
					String biome = level.getBiome(pos).unwrapKey().map(k -> k.identifier().getPath()).orElse("?");
					long[] row = byBiome.computeIfAbsent(biome, k -> new long[PER_BIOME]);
					row[DRAWS]++;

					if (VillageGuard.quiet(level, pos)) row[QUIET]++;

					for (EntityType<? extends Mob> type : ALL) {
						long[] cell = byBiomeType.computeIfAbsent(biome + "\u0000" + key(type), k -> new long[PER_TYPE]);
						if (!SpawnPlacements.isSpawnPositionOk(type, level, pos)) continue;
						cell[PLACEMENT]++;
						if (!SpawnPlacements.checkSpawnRules(type, level, EntitySpawnReason.NATURAL, pos, random)) continue;
						cell[RULE]++;
						if (!level.noCollision(type.getSpawnAABB(x + 0.5, y, z + 0.5))) continue;
						cell[BOX]++;

						Mob probe = type.create(level, EntitySpawnReason.NATURAL);
						if (probe == null) continue;
						probe.snapTo(x + 0.5, y, z + 0.5, 0.0F, 0.0F);
						boolean ok = probe.checkSpawnRules(level, EntitySpawnReason.NATURAL)
							&& probe.checkSpawnObstruction(level);
						probe.discard();
						if (ok) cell[CLEAR]++;
					}
				}
			}
		}

		StringBuilder sb = new StringBuilder();
		sb.append(String.format("land spawn scan %d by %d chunks at %d,%d: %.1fs, skyDarken=%d (0 is full day, 11 is night)",
			side, side, centreX, centreZ, (System.nanoTime() - start) / 1e9, level.getSkyDarken()));
		byBiome.entrySet().stream()
			.sorted((a, b) -> Long.compare(b.getValue()[DRAWS], a.getValue()[DRAWS]))
			.forEach(e -> {
				long[] row = e.getValue();
				double d = Math.max(1, row[DRAWS]);
				sb.append(String.format("%n  %s: %d draws, %.1f%% in a village's quiet ground", e.getKey(),
					row[DRAWS], 100.0 * row[QUIET] / d));
				sb.append(String.format("%n    %-18s %-9s %9s %8s %8s %11s %8s", "type", "category",
					"placement", "rule", "box", "spawnable", "count"));
				for (EntityType<? extends Mob> type : ALL) {
					long[] cell = byBiomeType.get(e.getKey() + "\u0000" + key(type));
					if (cell == null) continue;
					sb.append(String.format("%n    %-18s %-9s %8.2f%% %7.2f%% %7.2f%% %10.3f%% %8d", key(type),
						type.getCategory().getName(), 100.0 * cell[PLACEMENT] / d, 100.0 * cell[RULE] / d,
						100.0 * cell[BOX] / d, 100.0 * cell[CLEAR] / d, cell[CLEAR]));
				}
			});
		sb.append("\n  Columns are cumulative shares of every draw in that biome: the placement type, then the"
			+ "\n  type's own spawn rule, then a clear box, then checkSpawnRules and checkSpawnObstruction on a"
			+ "\n  real entity. The last is the whole chain, so it is the SUPPLY of legal positions - not a rate"
			+ "\n  and not a density. A cliff between two columns names the gate that is closing, and the count"
			+ "\n  is there because 0.000% and 0 are different readings."
			+ "\n  What this cannot see: the category cap (/oceania mobs, and it needs a player in the world),"
			+ "\n  and that a biome entry must also exist - a type with 5% supply and no entry spawns nothing.");
		return sb.toString();
	}

	public static String spawners(ServerLevel level, int centreX, int centreZ, int radius) {
		int cx0 = (centreX >> 4) - radius, cz0 = (centreZ >> 4) - radius;
		int side = radius * 2 + 1;
		Map<String, Integer> byMob = new LinkedHashMap<>();
		List<String> where = new ArrayList<>();
		int chunks = 0;

		for (int dx = 0; dx < side; dx++) {
			for (int dz = 0; dz < side; dz++) {
				LevelChunk chunk = level.getChunk(cx0 + dx, cz0 + dz);
				chunks++;
				for (Map.Entry<BlockPos, BlockEntity> e : chunk.getBlockEntities().entrySet()) {
					if (!(e.getValue() instanceof SpawnerBlockEntity spawner)) continue;
					String mob = "?";
					try {
						var display = spawner.getSpawner().getOrCreateDisplayEntity(level, e.getKey());
						if (display != null) mob = key(display.getType());
					} catch (RuntimeException ex) {
						mob = "unreadable (" + ex.getClass().getSimpleName() + ")";
					}
					byMob.merge(mob, 1, Integer::sum);
					if (where.size() < 12 && "cave_spider".equals(mob)) {
						where.add(String.format("%s at %d %d %d in %s", mob, e.getKey().getX(), e.getKey().getY(),
							e.getKey().getZ(),
							level.getBiome(e.getKey()).unwrapKey().map(k -> k.identifier().getPath()).orElse("?")));
					}
				}
			}
		}

		StringBuilder sb = new StringBuilder(String.format("spawner blocks in %d chunks around %d,%d: %d",
			chunks, centreX, centreZ, byMob.values().stream().mapToInt(Integer::intValue).sum()));
		if (byMob.isEmpty()) {
			sb.append("\n  NONE. For cave spiders that is the answer, not a rarity: their spawners come from"
				+ "\n  MineshaftPieces' cobweb corridors, so none here means either no mineshaft generated in"
				+ "\n  this window (frequency 0.004, so about 0.4% of chunks) or FloorMineshaftStructure refused"
				+ "\n  every site. Widen the radius before concluding anything.");
		} else {
			byMob.forEach((mob, n) -> sb.append(String.format("%n  %-24s %4d", mob, n)));
			sb.append("\n  -- the cave_spider ones, up to twelve:");
			where.forEach(w -> sb.append("\n  ").append(w));
		}

		sb.append("\n  cave_spider => mineshaft spider corridor, always. zombie/skeleton/spider at about 2:1:1"
			+ "\n  => monster rooms, which is MonsterRoomFeature.MOBS = {SKELETON, ZOMBIE, ZOMBIE, SPIDER}.");
		return sb.toString();
	}

	public static String slimes(ServerLevel level, int centreX, int centreZ, int radius, int perChunk) {
		RandomSource random = level.getRandom();
		int cx0 = (centreX >> 4) - radius, cz0 = (centreZ >> 4) - radius;
		int side = radius * 2 + 1;
		long chunks = 0, slimeChunks = 0, draws = 0, placement = 0, rule = 0, box = 0, clear = 0;
		Map<String, Integer> byBiome = new LinkedHashMap<>();
		List<String> examples = new ArrayList<>();

		for (int dx = 0; dx < side; dx++) {
			for (int dz = 0; dz < side; dz++) {
				int chunkX = cx0 + dx, chunkZ = cz0 + dz;
				chunks++;

				if (WorldgenRandom.seedSlimeChunk(chunkX, chunkZ, level.getSeed(), 987234911L).nextInt(10) != 0) {
					continue;
				}
				slimeChunks++;
				level.getChunk(chunkX, chunkZ);
				for (int i = 0; i < perChunk; i++) {
					int x = (chunkX << 4) + random.nextInt(16);
					int z = (chunkZ << 4) + random.nextInt(16);
					int y = Mth.randomBetweenInclusive(random, level.getMinY(), 39);
					BlockPos pos = new BlockPos(x, y, z);
					draws++;
					if (!SpawnPlacements.isSpawnPositionOk(EntityTypes.SLIME, level, pos)) continue;
					placement++;
					if (!SpawnPlacements.checkSpawnRules(EntityTypes.SLIME, level, EntitySpawnReason.NATURAL, pos, random)) continue;
					rule++;
					if (!level.noCollision(EntityTypes.SLIME.getSpawnAABB(x + 0.5, y, z + 0.5))) continue;
					box++;
					Mob probe = EntityTypes.SLIME.create(level, EntitySpawnReason.NATURAL);
					if (probe == null) continue;
					probe.snapTo(x + 0.5, y, z + 0.5, 0.0F, 0.0F);
					boolean ok = probe.checkSpawnRules(level, EntitySpawnReason.NATURAL)
						&& probe.checkSpawnObstruction(level);
					probe.discard();
					if (!ok) continue;
					clear++;
					String biome = level.getBiome(pos).unwrapKey().map(k -> k.identifier().getPath()).orElse("?");
					byBiome.merge(biome, 1, Integer::sum);
					if (examples.size() < 8) examples.add(String.format("%d %d %d in %s", x, y, z, biome));
				}
			}
		}

		StringBuilder sb = new StringBuilder(String.format(
			"slime probe: %d chunks, %d of them slime chunks (%.1f%%, vanilla's hash says 10%%), %d draws"
				+ " with y forced into [%d, 39]", chunks, slimeChunks, 100.0 * slimeChunks / Math.max(1, chunks),
			draws, level.getMinY()));
		double d = Math.max(1, draws);
		sb.append(String.format("%n  placement %.2f%%  rule %.3f%%  box %.3f%%  spawnable %.3f%%  count %d",
			100.0 * placement / d, 100.0 * rule / d, 100.0 * box / d, 100.0 * clear / d, clear));
		if (clear == 0) {
			sb.append("\n  ZERO legal slime positions. Read the columns to see which gate closed: a rule column at"
				+ "\n  about a tenth of the placement column is the nextInt(10) roll working, and a box column far"
				+ "\n  below it means the 2.04-block cube does not fit - which is cave roominess, not the entry."
				+ "\n  The spider column in the main scan is the control for that, being the next widest mob.");
		} else {
			byBiome.forEach((b, n) -> sb.append(String.format("%n  %-20s %4d", b, n)));
			sb.append("\n  --");
			examples.forEach(e -> sb.append("\n  ").append(e));
		}
		sb.append("\n  NOT a spawn rate: the chunk lottery and the y draw were removed on purpose. It answers"
			+ "\n  whether a legal slime position exists underground, which the main scan cannot sample.");
		return sb.toString();
	}

	public static String quiet(ServerLevel level, int centreX, int centreZ, int radius) {
		int cx0 = (centreX >> 4) - radius, cz0 = (centreZ >> 4) - radius;
		int side = radius * 2 + 1;
		int seaLevel = level.getSeaLevel();
		Map<String, long[]> byBiome = new LinkedHashMap<>();
		BlockPos quietColumn = null;
		int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;

		for (int dx = 0; dx < side; dx++) {
			for (int dz = 0; dz < side; dz++) {
				int chunkX = cx0 + dx, chunkZ = cz0 + dz;
				LevelChunk chunk = level.getChunk(chunkX, chunkZ);
				for (int ox = 0; ox < 16; ox += 4) {
					for (int oz = 0; oz < 16; oz += 4) {
						int x = (chunkX << 4) + ox, z = (chunkZ << 4) + oz;

						if (level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z) <= seaLevel) continue;
						int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
						BlockPos ground = new BlockPos(x, h, z);
						if (!level.getFluidState(ground).isEmpty()) continue;
						String biome = level.getBiome(ground).unwrapKey().map(k -> k.identifier().getPath()).orElse("?");
						long[] row = byBiome.computeIfAbsent(biome, k -> new long[5]);
						row[0]++;

						boolean horizontal = VillageGuard.villageNear(level, ground) != null;
						if (horizontal) row[1]++;

						boolean quietHere = VillageGuard.quiet(level, chunk, ground);
						if (quietHere) row[2]++;

						BlockPos deep = new BlockPos(x, h - 150, z);
						if (horizontal && level.getFluidState(deep).isEmpty()) row[3]++;
						if (VillageGuard.quiet(level, chunk, deep)) row[4]++;

						if (quietColumn == null && quietHere) {
							quietColumn = ground;
						}
						minX = Math.min(minX, x);
						maxX = Math.max(maxX, x);
						minZ = Math.min(minZ, z);
						maxZ = Math.max(maxZ, z);
					}
				}
			}
		}

		StringBuilder sb = new StringBuilder(String.format(
			"village quiet census, %d by %d chunks at %d,%d (%d blocks across), dry land columns only,"
				+ " every 4 blocks; VillageGuard.MARGIN=%d, level.getHeight()=%d",
			side, side, centreX, centreZ, side * 16, VillageGuard.MARGIN, level.getHeight()));
		sb.append(String.format("%n  %-18s %10s | %14s %14s | %14s %14s", "biome", "land cols",
			"OLD surface", "NEW surface", "OLD at -150", "NEW at -150"));
		byBiome.entrySet().stream().sorted((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0]))
			.forEach(e -> {
				long[] r = e.getValue();
				double d = Math.max(1, r[0]);
				sb.append(String.format("%n  %-18s %10d | %12.1f%% %12.1f%% | %12.1f%% %12.1f%%", e.getKey(),
					r[0], 100.0 * r[1] / d, 100.0 * r[2] / d, 100.0 * r[3] / d, 100.0 * r[4] / d));
			});
		if (minX != Integer.MAX_VALUE) {
			sb.append(String.format("%n  land spans x %d..%d (%d blocks), z %d..%d (%d blocks)", minX, maxX,
				maxX - minX, minZ, maxZ, maxZ - minZ));
			boolean clipped = minX <= (cx0 << 4) + 4 || maxX >= ((cx0 + side) << 4) - 4
				|| minZ <= (cz0 << 4) + 4 || maxZ >= ((cz0 + side) << 4) - 4;
			sb.append(clipped
				? "\n  WINDOW CLIPPED: land reaches the edge, so these shares are of the WINDOW, not of the island."
				: "\n  Land is fully inside the window, so these shares are of the island.");
		}

		BoundingBox village = quietColumn == null ? null : VillageGuard.villageNear(level, quietColumn);
		if (village == null) {
			sb.append("\n  NO QUIET COLUMN in this window, so no vertical profile. If the shares above are all"
				+ "\n  0.0% that agrees and there is no village here; if any share is non-zero, this line is a"
				+ "\n  fault in the probe and not a finding about the guard.");
		} else {
			sb.append(String.format("%n  village box x %d..%d, y %d..%d, z %d..%d; SURFACE_DEPTH=%d."
					+ " Inside that box hut_village.json's spawn_overrides empty the monster list already,"
					+ " so the guard only has to cover the skirt around it.",
				village.minX(), village.maxX(), village.minY(), village.maxY(),
				village.minZ(), village.maxZ(), VillageGuard.SURFACE_DEPTH));
			int inX = (village.minX() + village.maxX()) / 2, inZ = (village.minZ() + village.maxZ()) / 2;
			profile(level, sb, "inside the footprint", inX, inZ);

			profile(level, sb, "in the margin", village.maxX() + VillageGuard.MARGIN / 2, inZ);
		}
		sb.append("\n  quiet surface is the share of dry land the guard silences."
			+ "\n  quiet at -150 is the same test 150 blocks under the floor. It is NOT zero because"
			+ "\n  VillageGuard.reaches inflates the village box by level.getHeight() in Y, so the quiet region"
			+ "\n  is a full-height prism rather than a skirt: the caves under a village are suppressed too."
			+ "\n  What this does NOT say: anything about spawn RATES outside the margin. Attempts are drawn one"
			+ "\n  per chunk per category per tick inside that chunk (NaturalSpawner.spawnCategoryForChunk calls"
			+ "\n  getRandomPosWithin once), so a cancelled attempt is lost, never moved to another chunk, and"
			+ "\n  ground outside the margin spawns exactly as vanilla would.");
		return sb.toString();
	}

	public static String surface(ServerLevel level, int centreX, int centreZ, int radius) {
		int cx0 = (centreX >> 4) - radius, cz0 = (centreZ >> 4) - radius;
		int side = radius * 2 + 1;
		String grassName = BuiltInRegistries.BLOCK.getKey(Blocks.GRASS_BLOCK).getPath();
		Map<String, Map<String, Integer>> byBiome = new LinkedHashMap<>();

		for (int dx = 0; dx < side; dx++) {
			for (int dz = 0; dz < side; dz++) {
				int chunkX = cx0 + dx, chunkZ = cz0 + dz;
				level.getChunk(chunkX, chunkZ);
				for (int ox = 2; ox < 16; ox += 5) {
					for (int oz = 2; oz < 16; oz += 5) {
						int x = (chunkX << 4) + ox, z = (chunkZ << 4) + oz;
						int h = level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z);
						BlockPos top = new BlockPos(x, h - 1, z);
						BlockState state = level.getBlockState(top);
						if (state.isAir()) continue;
						String biome = level.getBiome(top).unwrapKey().map(k -> k.identifier().getPath()).orElse("?");
						String block = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
						byBiome.computeIfAbsent(biome, k -> new LinkedHashMap<>()).merge(block, 1, Integer::sum);
					}
				}
			}
		}

		StringBuilder sb = new StringBuilder(String.format(
			"top block by biome, %d by %d chunks at %d,%d, OCEAN_FLOOR heightmap (grass_block is the ONLY"
				+ " block in #animals_spawnable_on)", side, side, centreX, centreZ));
		byBiome.forEach((biome, blocks) -> {
			int total = blocks.values().stream().mapToInt(Integer::intValue).sum();
			StringBuilder line = new StringBuilder();
			blocks.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(5).forEach(e ->
				line.append(String.format("%s %.1f%%  ", e.getKey(), 100.0 * e.getValue() / Math.max(1, total))));
			sb.append(String.format("%n  %-18s %-5s %s", biome,
				blocks.containsKey(grassName) ? "ANIM" : "-", line.toString().trim()));
		});
		sb.append("\n  ANIM marks a biome where at least one sampled column tops out in grass_block, so a farm"
			+ "\n  animal entry there has ground to stand on. A biome with no ANIM cannot spawn them at any weight.");
		return sb.toString();
	}

	private static void profile(ServerLevel level, StringBuilder sb, String label, int x, int z) {
		int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		int[] offsets = {2, 1, 0, -1, -2, -3, -4, -6, -10, -20, -40, -80, -150, -300};
		StringBuilder marks = new StringBuilder();
		for (int d : offsets) {
			BlockPos at = new BlockPos(x, ground + d, z);
			boolean fluid = !level.getFluidState(at).isEmpty();
			marks.append(fluid ? "~" : VillageGuard.quiet(level, at) ? "#" : ".");
		}
		sb.append(String.format("%n    %-22s at %d,%d ground y=%d  %s", label, x, z, ground, marks));
		sb.append(String.format("%n    %-22s offsets from ground:   ", ""));
		for (int d : offsets) sb.append(String.format("%d ", d));
	}

	private static String key(EntityType<?> type) {
		return BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath();
	}
}

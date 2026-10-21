package com.oceania.spawn;

import com.oceania.Oceania;
import com.oceania.config.OceaniaConfig;
import com.oceania.mob.GiantSquidEntity;
import com.oceania.registry.OceaniaEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Set;

public final class GiantSquidSpawner implements CustomSpawner {

	private static final int CYCLE_TICKS = Integer.getInteger("oceania.giantsquid.cycle", 200);

	private static final int RARITY = Integer.getInteger("oceania.giantsquid.rarity", 90);

	private static final int MIN_SQUID_DISTANCE =
		Integer.getInteger("oceania.giantsquid.spacing", 2000);

	private static final int SHELL_MIN = Integer.getInteger("oceania.giantsquid.shell.min", 80);
	private static final int SHELL_MAX = Integer.getInteger("oceania.giantsquid.shell.max", 144);

	private static final int LEVEL_CAP = Integer.getInteger("oceania.giantsquid.cap.level", 1);

	private static final Set<String> BIOMES = Set.of("trench", "abyssal_plain", "ridge");

	private static final int PLACED_MEMORY = 256;

	private static final Deque<long[]> PLACED = new ArrayDeque<>();

	private static final int MAX_SPAWN_DEPTH = Integer.getInteger("oceania.squid.depth.max", 160);

	private static int totalSpawned;
	private static int legalCandidates;
	private static int rejectedUnloaded;
	private static int rejectedBiome;
	private static int rejectedBand;
	private static int rejectedRules;
	private static int rejectedSpacing;
	private static int rejectedBlocked;
	private static int lastLevelCount;

	private int nextTick;

	@Override
	public void tick(ServerLevel level, boolean spawnEnemies) {
		tick(level, spawnEnemies, true);
	}

	public int tick(ServerLevel level, boolean spawnEnemies, boolean spawnFriendlies) {

		if (!OceaniaConfig.get().spawnGiantSquid) {
			return 0;
		}

		if (!spawnEnemies) {
			return 0;
		}
		if (--this.nextTick > 0) {
			return 0;
		}
		this.nextTick = CYCLE_TICKS;

		List<ServerPlayer> players = level.players();
		if (players.isEmpty()) {
			return 0;
		}

		int levelCount = level.getEntities(OceaniaEntities.GIANT_SQUID, e -> true).size();
		lastLevelCount = levelCount;
		if (levelCount >= LEVEL_CAP) {
			return 0;
		}

		int spawned = 0;
		for (ServerPlayer player : players) {
			if (player.isSpectator()) {
				continue;
			}
			if (levelCount + spawned >= LEVEL_CAP) {
				break;
			}
			if (this.tryPlace(level, player)) {
				spawned++;
				totalSpawned++;
			}
		}
		return spawned;
	}

	private boolean tryPlace(ServerLevel level, ServerPlayer player) {
		RandomSource random = level.getRandom();
		double angle = random.nextDouble() * Mth.TWO_PI;
		double radius = SHELL_MIN + random.nextDouble() * (SHELL_MAX - SHELL_MIN);
		int x = Mth.floor(player.getX() + Math.cos(angle) * radius);
		int z = Mth.floor(player.getZ() + Math.sin(angle) * radius);

		if (!level.hasChunkAt(new BlockPos(x, level.getSeaLevel(), z))) {
			rejectedUnloaded++;
			return false;
		}

		int floor = level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z);

		int bandLow = Math.max(floor + 2, level.getSeaLevel() - MAX_SPAWN_DEPTH);

		int bandHigh = level.getSeaLevel() - 91;
		if (bandHigh < bandLow) {
			rejectedBand++;
			return false;
		}
		int y = bandLow + random.nextInt(bandHigh - bandLow + 1);
		BlockPos pos = new BlockPos(x, y, z);

		String biome = level.getBiome(pos).unwrapKey()
			.map(key -> key.identifier().getPath()).orElse("");
		if (!BIOMES.contains(biome)) {
			rejectedBiome++;
			return false;
		}

		if (!GiantSquidEntity.checkGiantSquidSpawnRules(OceaniaEntities.GIANT_SQUID, level,
			EntitySpawnReason.NATURAL, pos, random)) {
			rejectedRules++;
			return false;
		}

		if (tooCloseToAnotherSquid(level, x + 0.5, z + 0.5)) {
			rejectedSpacing++;
			return false;
		}

		legalCandidates++;
		if (random.nextInt(RARITY) != 0) {
			return false;
		}

		if (!level.noCollision(OceaniaEntities.GIANT_SQUID.getSpawnAABB(x + 0.5, y, z + 0.5))) {
			rejectedBlocked++;
			return false;
		}

		GiantSquidEntity squid = OceaniaEntities.GIANT_SQUID.create(level, EntitySpawnReason.NATURAL);
		if (squid == null) {
			return false;
		}
		squid.snapTo(x + 0.5, y, z + 0.5, random.nextFloat() * 360.0F, 0.0F);
		squid.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.NATURAL, null);

		squid.setPersistenceRequired();

		if (!level.addFreshEntity(squid)) {
			return false;
		}
		PLACED.addFirst(new long[]{x, z});
		while (PLACED.size() > PLACED_MEMORY) {
			PLACED.removeLast();
		}
		return true;
	}

	private static boolean tooCloseToAnotherSquid(ServerLevel level, double x, double z) {
		double limitSqr = (double) MIN_SQUID_DISTANCE * MIN_SQUID_DISTANCE;
		for (long[] placed : PLACED) {
			double dx = placed[0] + 0.5 - x;
			double dz = placed[1] + 0.5 - z;
			if (dx * dx + dz * dz < limitSqr) {
				return true;
			}
		}

		AABB box = new AABB(x - MIN_SQUID_DISTANCE, level.getMinY(), z - MIN_SQUID_DISTANCE,
			x + MIN_SQUID_DISTANCE, level.getMaxY() + 1, z + MIN_SQUID_DISTANCE);
		for (GiantSquidEntity other : level.getEntities(OceaniaEntities.GIANT_SQUID, box, e -> true)) {
			double dx = other.getX() - x;
			double dz = other.getZ() - z;
			if (dx * dx + dz * dz < limitSqr) {
				return true;
			}
		}
		return false;
	}

	public static String summary() {
		return String.format(
			"giant squid: %d placed from %d legal candidates (1 in %d), %d alive in loaded chunks"
				+ " (cap %d, %d-block spacing, %d remembered placements); rejected %d unloaded,"
				+ " %d biome, %d band, %d rules, %d spacing, %d blocked",
			totalSpawned, legalCandidates, RARITY, lastLevelCount, LEVEL_CAP, MIN_SQUID_DISTANCE,
			PLACED.size(), rejectedUnloaded, rejectedBiome, rejectedBand, rejectedRules,
			rejectedSpacing, rejectedBlocked);
	}

	public static void announce(int spawnerCount) {
		Oceania.LOGGER.info(
			"[giant squid] spawner added to the overworld custom spawner list ({} spawners);"
				+ " {}-block spacing, 1 in {} legal candidates per {} ticks, cap {}",
			spawnerCount, MIN_SQUID_DISTANCE, RARITY, CYCLE_TICKS, LEVEL_CAP);
	}
}

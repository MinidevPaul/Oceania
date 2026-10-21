package com.oceania.spawn;

import com.oceania.Oceania;
import com.oceania.config.OceaniaConfig;
import com.oceania.mob.WhaleEntity;
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

import java.util.List;
import java.util.Set;

public final class WhaleSpawner implements CustomSpawner {

	private static final int CYCLE_TICKS = Integer.getInteger("oceania.whale.cycle", 100);

	private static final int ATTEMPTS = Integer.getInteger("oceania.whale.attempts", 6);

	private static final int MIN_WHALE_DISTANCE = Integer.getInteger("oceania.whale.spacing", 200);

	private static final int SHELL_MIN = Integer.getInteger("oceania.whale.shell.min", 112);
	private static final int SHELL_MAX = Integer.getInteger("oceania.whale.shell.max", 152);

	private static final int TARGET = Integer.getInteger("oceania.whale.target", 2);
	private static final int COUNT_RADIUS = Integer.getInteger("oceania.whale.count.radius", 200);

	private static final int LEVEL_CAP = Integer.getInteger("oceania.whale.cap.level", 24);

	private static final Set<String> BIOMES =
		Set.of("abyssal_plain", "ridge", "trench", "seamount_flank");

	private static int totalSpawned;
	private static int totalWanted;
	private static int rejectedUnloaded;
	private static int rejectedBiome;
	private static int rejectedBand;
	private static int rejectedRules;
	private static int rejectedSpacing;
	private static int rejectedBlocked;
	private static int cappedLevel;
	private static int lastLevelCount;

	private int nextTick;

	@Override
	public void tick(ServerLevel level, boolean spawnEnemies) {
		tick(level, spawnEnemies, true);
	}

	public int tick(ServerLevel level, boolean spawnEnemies, boolean spawnFriendlies) {

		if (!OceaniaConfig.get().spawnWhales) {
			return 0;
		}

		if (!spawnFriendlies) {
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

		int levelCount = level.getEntities(OceaniaEntities.WHALE, e -> true).size();
		lastLevelCount = levelCount;
		if (levelCount >= LEVEL_CAP) {
			cappedLevel++;
			return 0;
		}

		int spawned = 0;
		for (ServerPlayer player : players) {
			if (player.isSpectator()) {
				continue;
			}
			int near = countNear(level, player.getX(), player.getZ(), COUNT_RADIUS);
			int room = Math.min(TARGET - near, LEVEL_CAP - levelCount - spawned);
			if (room > 0) {
				totalWanted += room;
			}
			for (int attempt = 0; attempt < ATTEMPTS && room > 0; attempt++) {
				if (this.tryPlace(level, player)) {
					spawned++;
					totalSpawned++;

					near = countNear(level, player.getX(), player.getZ(), COUNT_RADIUS);
					room = Math.min(TARGET - near, LEVEL_CAP - levelCount - spawned);
				}
			}
		}
		return spawned;
	}

	private static int countNear(ServerLevel level, double x, double z, int radius) {
		AABB box = new AABB(x - radius, level.getMinY(), z - radius,
			x + radius, level.getMaxY() + 1, z + radius);
		int count = 0;
		for (WhaleEntity whale : level.getEntities(OceaniaEntities.WHALE, box, e -> true)) {
			double dx = whale.getX() - x;
			double dz = whale.getZ() - z;
			if (dx * dx + dz * dz < (double) radius * radius) {
				count++;
			}
		}
		return count;
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

		int bandLow = Math.max(floor + 3, level.getSeaLevel() - WhaleEntity.MAX_SPAWN_DEPTH);
		int bandHigh = level.getSeaLevel() - WhaleEntity.MIN_SPAWN_DEPTH;
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

		if (!WhaleEntity.checkWhaleSpawnRules(OceaniaEntities.WHALE, level,
			EntitySpawnReason.NATURAL, pos, random)) {
			rejectedRules++;
			return false;
		}

		if (countNear(level, x + 0.5, z + 0.5, MIN_WHALE_DISTANCE) > 0) {
			rejectedSpacing++;
			return false;
		}

		if (!level.noCollision(OceaniaEntities.WHALE.getSpawnAABB(x + 0.5, y, z + 0.5))) {
			rejectedBlocked++;
			return false;
		}

		WhaleEntity whale = OceaniaEntities.WHALE.create(level, EntitySpawnReason.NATURAL);
		if (whale == null) {
			return false;
		}
		whale.snapTo(x + 0.5, y, z + 0.5, random.nextFloat() * 360.0F, 0.0F);

		whale.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.NATURAL, null);

		return level.addFreshEntity(whale);
	}

	public static String summary() {
		return String.format(
			"whales: %d spawned of %d wanted, %d alive in loaded chunks (cap %d, target %d per"
				+ " player within %d blocks, %d-block spacing); rejected %d unloaded, %d biome,"
				+ " %d band, %d rules, %d spacing, %d blocked; %d cycles capped",
			totalSpawned, totalWanted, lastLevelCount, LEVEL_CAP, TARGET, COUNT_RADIUS,
			MIN_WHALE_DISTANCE, rejectedUnloaded, rejectedBiome, rejectedBand, rejectedRules,
			rejectedSpacing, rejectedBlocked, cappedLevel);
	}

	public static void announce(int spawnerCount) {
		Oceania.LOGGER.info(
			"[whale] spawner added to the overworld custom spawner list ({} spawners); {}-block"
				+ " spacing, {} blocks from land, target {} per player within {}, cap {}",
			spawnerCount, MIN_WHALE_DISTANCE, (int) WhaleEntity.MIN_LAND_DISTANCE, TARGET,
			COUNT_RADIUS, LEVEL_CAP);
	}
}

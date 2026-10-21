package com.oceania.spawn;

import com.oceania.Oceania;
import com.oceania.config.OceaniaConfig;
import com.oceania.mob.AbstractCrab;
import com.oceania.registry.OceaniaEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Set;

public class CrabSpawner implements CustomSpawner {

	private static final int CYCLE_TICKS = Integer.getInteger("oceania.crab.cycle", 40);

	private static final int ATTEMPTS = Integer.getInteger("oceania.crab.attempts", 10);

	private static final int LEVEL_CAP = Integer.getInteger("oceania.crab.cap.level", 180);

	private enum Habitat {

		SEA_FLOOR,

		LAND
	}

	private record Kind(String name, EntityType<? extends AbstractCrab> type, Set<String> biomes,
	                    Habitat habitat, int target, int minRadius, int maxRadius) {
	}

	private static final List<Kind> KINDS = List.of(
		new Kind("king", OceaniaEntities.KING_CRAB,
			Set.of("abyssal_plain", "ridge", "trench"), Habitat.SEA_FLOOR,
			Integer.getInteger("oceania.crab.king.target", 12), 26, 80),
		new Kind("reef", OceaniaEntities.REEF_CRAB,
			Set.of("reef", "lagoon", "sandy_shelf", "kelp_forest"), Habitat.SEA_FLOOR,
			Integer.getInteger("oceania.crab.reef.target", 14), 28, 64),
		new Kind("coconut", OceaniaEntities.COCONUT_CRAB,
			Set.of("island_sand", "island_jungle"), Habitat.LAND,
			Integer.getInteger("oceania.crab.coconut.target", 6), 24, 60));

	private int nextTick;

	@Override
	public void tick(ServerLevel level, boolean spawnEnemies) {
		tick(level, spawnEnemies, true);
	}

	public int tick(ServerLevel level, boolean spawnEnemies, boolean spawnFriendlies) {

		if (!OceaniaConfig.get().spawnCrabs) {
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

		int spawned = 0;
		for (Kind kind : KINDS) {
			spawned += this.tickKind(level, players, kind);
		}
		return spawned;
	}

	private int tickKind(ServerLevel level, List<ServerPlayer> players, Kind kind) {

		int levelCount = level.getEntities(kind.type(), e -> true).size();
		if (levelCount >= LEVEL_CAP) {
			return 0;
		}

		int spawned = 0;
		for (ServerPlayer player : players) {
			if (player.isSpectator()) {
				continue;
			}
			AABB box = player.getBoundingBox().inflate(kind.maxRadius());
			int near = level.getEntities(kind.type(), box, e -> true).size();
			int room = Math.min(kind.target() - near, LEVEL_CAP - levelCount - spawned);
			for (int attempt = 0; attempt < ATTEMPTS && room > 0; attempt++) {
				if (this.tryPlace(level, player, kind)) {
					room--;
					spawned++;
				}
			}
		}
		return spawned;
	}

	private boolean tryPlace(ServerLevel level, ServerPlayer player, Kind kind) {
		RandomSource random = level.getRandom();
		double angle = random.nextDouble() * Mth.TWO_PI;
		double radius = kind.minRadius()
			+ random.nextDouble() * (kind.maxRadius() - kind.minRadius());
		int x = Mth.floor(player.getX() + Math.cos(angle) * radius);
		int z = Mth.floor(player.getZ() + Math.sin(angle) * radius);

		if (!level.hasChunkAt(new BlockPos(x, level.getSeaLevel(), z))) {
			return false;
		}

		int y = level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z);
		BlockPos pos = new BlockPos(x, y, z);

		String biome = level.getBiome(pos).unwrapKey()
			.map(key -> key.identifier().getPath()).orElse("");
		if (!kind.biomes().contains(biome)) {
			return false;
		}

		BlockState floor = level.getBlockState(pos.below());

		if (!floor.isSolid() || floor.is(BlockTags.LEAVES)) {
			return false;
		}

		FluidState fluid = level.getFluidState(pos);
		if (kind.habitat() == Habitat.SEA_FLOOR) {
			if (!fluid.is(FluidTags.WATER)) {
				return false;
			}
		} else {
			if (!fluid.isEmpty() || y <= level.getSeaLevel()) {
				return false;
			}
		}

		if (!level.noCollision(kind.type().getSpawnAABB(x + 0.5, y, z + 0.5))) {
			return false;
		}

		AbstractCrab crab = kind.type().create(level, EntitySpawnReason.NATURAL);
		if (crab == null) {
			return false;
		}
		crab.snapTo(x + 0.5, y, z + 0.5, random.nextFloat() * 360.0F, 0.0F);
		crab.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.NATURAL, null);

		return level.addFreshEntity(crab);
	}

	public static void announce(int spawnerCount) {
		Oceania.LOGGER.info("[crab] spawner added to the overworld custom spawner list ({} spawners)",
			spawnerCount);
	}
}

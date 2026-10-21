package com.oceania.spawn;

import com.oceania.Oceania;
import com.oceania.config.OceaniaConfig;
import com.oceania.mob.AbstractJellyfish;
import com.oceania.registry.OceaniaEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.util.List;

public class JellyfishSpawner implements CustomSpawner {

	private static final int CYCLE_TICKS = Integer.getInteger("oceania.jellyfish.cycle", 20);

	private static final int ATTEMPTS = Integer.getInteger("oceania.jellyfish.attempts", 34);

	private static final int LEVEL_CAP = Integer.getInteger("oceania.jellyfish.cap.level", 400);

	private static final int TARGET_SHALLOW = Integer.getInteger("oceania.jellyfish.target.shallow", 23);
	private static final int TARGET_DEEP = Integer.getInteger("oceania.jellyfish.target.deep", 80);

	private static final int FULL_DEPTH = Integer.getInteger("oceania.jellyfish.depth.full", 160);

	private static final int MIN_SHALLOW = Integer.getInteger("oceania.jellyfish.radius.min.shallow", 48);
	private static final int MIN_DEEP = Integer.getInteger("oceania.jellyfish.radius.min.deep", 30);
	private static final int MAX_SHALLOW = Integer.getInteger("oceania.jellyfish.radius.max.shallow", 72);
	private static final int MAX_DEEP = Integer.getInteger("oceania.jellyfish.radius.max.deep", 56);

	private static final int LEAD_TICKS = Integer.getInteger("oceania.jellyfish.lead.ticks", 40);
	private static final int MAX_LEAD = Integer.getInteger("oceania.jellyfish.lead.max", 72);

	private static final int VERTICAL = Integer.getInteger("oceania.jellyfish.radius.vertical", 26);

	private static final int SURFACE_CLEARANCE = Integer.getInteger("oceania.jellyfish.surface.clearance", 6);

	private static final List<EntityType<? extends AbstractJellyfish>> SPECIES = List.of(
		OceaniaEntities.MOON_JELLY, OceaniaEntities.NETTLE_JELLY, OceaniaEntities.CROWN_JELLY);

	private int nextTick;

	@Override
	public void tick(ServerLevel level, boolean spawnEnemies) {
		tick(level, spawnEnemies, true);
	}

	public int tick(ServerLevel level, boolean spawnEnemies, boolean spawnFriendlies) {

		if (!OceaniaConfig.get().spawnJellyfish) {
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

		int levelCount = this.countInLevel(level);
		if (levelCount >= LEVEL_CAP) {
			return 0;
		}

		int spawned = 0;
		for (ServerPlayer player : players) {
			if (player.isSpectator()) {
				continue;
			}
			float deep = depthFraction(level, player.getY());

			float tight = deep * deep;
			int minRadius = Math.round(Mth.lerp(tight, MIN_SHALLOW, MIN_DEEP));
			int maxRadius = Math.max(minRadius + 1, Math.round(Mth.lerp(tight, MAX_SHALLOW, MAX_DEEP)));
			int target = Math.round(Mth.lerp(deep, TARGET_SHALLOW, TARGET_DEEP));

			int vertical = Math.min(VERTICAL, maxRadius);

			AABB shell = player.getBoundingBox().inflate(maxRadius);
			int near = 0;
			for (EntityType<? extends AbstractJellyfish> type : SPECIES) {
				near += level.getEntities(type, shell, e -> true).size();
			}

			int room = Math.min(target - near, LEVEL_CAP - levelCount - spawned);
			for (int attempt = 0; attempt < ATTEMPTS && room > 0; attempt++) {
				if (this.tryPlace(level, player, minRadius, maxRadius, vertical)) {
					room--;
					spawned++;
				}
			}
		}
		return spawned;
	}

	private static float depthFraction(ServerLevel level, double y) {
		double depth = level.getSeaLevel() - y;
		return Mth.clamp((float) (depth / FULL_DEPTH), 0.0F, 1.0F);
	}

	private int countInLevel(ServerLevel level) {
		int total = 0;
		for (EntityType<? extends AbstractJellyfish> type : SPECIES) {

			total += level.getEntities(type, e -> true).size();
		}
		return total;
	}

	private boolean tryPlace(ServerLevel level, ServerPlayer player,
	                         int minRadius, int maxRadius, int vertical) {
		RandomSource random = level.getRandom();
		double angle = random.nextDouble() * Mth.TWO_PI;
		double radius = minRadius + random.nextDouble() * (maxRadius - minRadius);

		net.minecraft.world.phys.Vec3 motion = player.getKnownMovement();
		double leadX = motion.x * LEAD_TICKS;
		double leadZ = motion.z * LEAD_TICKS;
		double leadLen = Math.sqrt(leadX * leadX + leadZ * leadZ);
		if (leadLen > MAX_LEAD) {
			leadX = leadX / leadLen * MAX_LEAD;
			leadZ = leadZ / leadLen * MAX_LEAD;
		}

		int x = Mth.floor(player.getX() + leadX + Math.cos(angle) * radius);
		int z = Mth.floor(player.getZ() + leadZ + Math.sin(angle) * radius);

		if (!level.hasChunkAt(new BlockPos(x, level.getSeaLevel(), z))) {
			return false;
		}

		int floor = level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z);
		int ceiling = level.getSeaLevel() - SURFACE_CLEARANCE;

		int bandLow = Math.max(floor + 1, Mth.floor(player.getY()) - vertical);
		int bandHigh = Math.min(ceiling, Mth.floor(player.getY()) + vertical);
		if (bandHigh < bandLow) {
			return false;
		}
		int y = bandLow + random.nextInt(bandHigh - bandLow + 1);

		BlockPos pos = new BlockPos(x, y, z);

		if (!level.getFluidState(pos).is(FluidTags.WATER)
			|| !level.getFluidState(pos.above()).is(FluidTags.WATER)) {
			return false;
		}

		EntityType<? extends AbstractJellyfish> type = SPECIES.get(random.nextInt(SPECIES.size()));
		if (!level.noCollision(type.getSpawnAABB(x + 0.5, y, z + 0.5))) {
			return false;
		}

		AbstractJellyfish jelly = type.create(level, EntitySpawnReason.NATURAL);
		if (jelly == null) {
			return false;
		}
		jelly.snapTo(x + 0.5, y, z + 0.5, random.nextFloat() * 360.0F, 0.0F);
		jelly.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.NATURAL, null);

		return level.addFreshEntity(jelly);
	}

	public static void announce(int spawnerCount) {
		Oceania.LOGGER.info(
			"[jellyfish] spawner added to the overworld custom spawner list ({} spawners); "
				+ "target {} at the surface rising to {} at {} blocks down, cap {}",
			spawnerCount, TARGET_SHALLOW, TARGET_DEEP, FULL_DEPTH, LEVEL_CAP);
	}
}

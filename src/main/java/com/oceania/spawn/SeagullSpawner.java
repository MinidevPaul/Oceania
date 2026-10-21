package com.oceania.spawn;

import com.oceania.worldgen.OceaniaWorld;
import com.oceania.Oceania;
import com.oceania.mob.SeagullEntity;
import com.oceania.registry.OceaniaEntities;
import com.oceania.worldgen.field.FieldFunction;
import com.oceania.worldgen.field.OceanFields;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class SeagullSpawner implements CustomSpawner {

	private static final int CYCLE_TICKS = Integer.getInteger("oceania.seagull.cycle", 20);

	private static final int SHELL_MIN = Integer.getInteger("oceania.seagull.shell.min", 135);
	private static final int SHELL_MAX = Integer.getInteger("oceania.seagull.shell.max", 155);

	private static final int TRACK_RANGE = 128;

	private static final int DESPAWN_RANGE = Integer.getInteger("oceania.seagull.despawn", 160);

	private static final int MARGIN = 5;

	private static final int PER_CYCLE = Integer.getInteger("oceania.seagull.percycle", 2);

	private static final int TARGET_NEAR = Integer.getInteger("oceania.seagull.target.near", 12);
	private static final int TARGET_FAR = Integer.getInteger("oceania.seagull.target.far", 1);
	private static final double FAR_DISTANCE = Integer.getInteger("oceania.seagull.target.far.distance", 900).doubleValue();

	private static final int PER_PLAYER_CAP = Integer.getInteger("oceania.seagull.cap.player", 16);
	private static final int LEVEL_CAP = Integer.getInteger("oceania.seagull.cap.level", 64);

	private static final int ALTITUDE_MIN = Integer.getInteger("oceania.seagull.altitude.min", 22);
	private static final int ALTITUDE_MAX = Integer.getInteger("oceania.seagull.altitude.max", 46);

	private static final int BEARING_CANDIDATES = 8;
	private static final double BEARING_SPREAD = Math.PI / 9;

	private static final double LOOK_WEIGHT = 1.0;
	private static final double LAND_WEIGHT = 0.5;

	private static final int PLACE_TRIES = 6;

	private static final double LAND_SEARCH = 1600;

	private static final boolean LOG = System.getProperty("oceania.seagull.log") != null;

	private static int totalSpawned;
	private static int totalWanted;
	private static int rejectedUnloaded;
	private static int rejectedBlocked;
	private static int rejectedGeometry;
	private static int cappedPlayer;
	private static int cappedLevel;
	private static int lastLevelCount;

	private int nextTick;

	@Override
	public void tick(ServerLevel level, boolean spawnEnemies) {
		tick(level, spawnEnemies, true);
	}

	public int tick(ServerLevel level, boolean spawnEnemies, boolean spawnFriendlies) {

		if (!spawnFriendlies) return 0;

		if (level.isThundering()) return 0;

		if (--this.nextTick > 0) return 0;
		this.nextTick = CYCLE_TICKS;

		OceanFields fields = fieldsFor(level);
		if (fields == null) return 0;

		List<ServerPlayer> players = level.players();
		if (players.isEmpty()) return 0;

		Set<SeagullEntity> alive = new HashSet<>();
		int[] nearCount = new int[players.size()];
		for (int i = 0; i < players.size(); i++) {
			ServerPlayer player = players.get(i);
			if (player.isSpectator()) continue;
			List<SeagullEntity> near = gullsNear(level, player);
			nearCount[i] = near.size();
			alive.addAll(near);
		}
		int levelCount = alive.size();
		lastLevelCount = levelCount;

		int spawned = 0;
		for (int i = 0; i < players.size(); i++) {
			ServerPlayer player = players.get(i);

			if (player.isSpectator()) continue;

			double[] land = nearestGroup(fields, player.getX(), player.getZ());
			double distance = landDistance(land, player.getX(), player.getZ());
			int target = Math.min(targetCount(distance), PER_PLAYER_CAP);
			int shortfall = target - nearCount[i];
			if (shortfall <= 0) continue;
			totalWanted += shortfall;
			if (target == PER_PLAYER_CAP) cappedPlayer++;

			int room = Math.min(shortfall, PER_CYCLE);
			if (levelCount + room > LEVEL_CAP) {
				cappedLevel++;
				room = Math.max(0, LEVEL_CAP - levelCount);
			}

			for (int n = 0; n < room; n++) {
				if (!trySpawnNear(level, fields, player, land)) break;
				spawned++;
				levelCount++;
			}

			if (LOG) {
				Oceania.LOGGER.info("[seagull] {} land {} target {} near {} spawned this cycle {} level {}/{}",
					player.getName().getString(),
					distance == Double.MAX_VALUE ? "none" : String.valueOf((int) distance),
					target, nearCount[i], spawned, levelCount, LEVEL_CAP);
			}
		}

		totalSpawned += spawned;
		return spawned;
	}

	private static List<SeagullEntity> gullsNear(ServerLevel level, ServerPlayer player) {
		double r = DESPAWN_RANGE;
		AABB box = AABB.ofSize(player.position(), r * 2, r * 2, r * 2);

		return level.getEntities(OceaniaEntities.SEAGULL, box, gull -> gull.isAlive() && gull.distanceToSqr(player) <= r * r);
	}

	public static int targetCount(double distance) {
		if (distance == Double.MAX_VALUE) return TARGET_FAR;
		double t = Mth.clamp(distance / FAR_DISTANCE, 0, 1);
		double f = (1 - t) * (1 - t);
		return TARGET_FAR + (int) Math.round((TARGET_NEAR - TARGET_FAR) * f);
	}

	public static double distanceToLand(OceanFields fields, double x, double z) {
		return landDistance(nearestGroup(fields, x, z), x, z);
	}

	public static double[] nearestGroup(OceanFields fields, double x, double z) {
		List<double[]> groups = fields.groupsIn(x - LAND_SEARCH, z - LAND_SEARCH, x + LAND_SEARCH, z + LAND_SEARCH);
		double[] best = null;
		double bestD = Double.MAX_VALUE;
		for (double[] group : groups) {
			double d = Math.hypot(group[0] - x, group[1] - z) - group[2];
			if (d < bestD) {
				bestD = d;
				best = group;
			}
		}
		return best;
	}

	private static double landDistance(double[] group, double x, double z) {
		if (group == null) return Double.MAX_VALUE;
		return Math.max(0, Math.hypot(group[0] - x, group[1] - z) - group[2]);
	}

	public static OceanFields fieldsFor(ServerLevel level) {

		if (!OceaniaWorld.isOceania(level)) {
			return null;
		}
		return FieldFunction.fields(level.getChunkSource().randomState());
	}

	private static boolean trySpawnNear(ServerLevel level, OceanFields fields, ServerPlayer player, double[] land) {
		RandomSource random = level.getRandom();
		double px = player.getX();
		double py = player.getY();
		double pz = player.getZ();

		double limit = DESPAWN_RANGE - MARGIN;
		double minHorizontal = Math.max(SHELL_MIN, TRACK_RANGE + MARGIN);
		double maxRise = Math.sqrt(limit * limit - minHorizontal * minHorizontal);
		if (level.getSeaLevel() + ALTITUDE_MIN - py > maxRise) {
			rejectedGeometry++;
			return false;
		}

		double bearing = pickBearing(player, random, land);

		for (int attempt = 0; attempt < PLACE_TRIES; attempt++) {
			double angle = bearing + (random.nextDouble() - 0.5) * BEARING_SPREAD;
			double dirX = Math.cos(angle);
			double dirZ = Math.sin(angle);

			double nominal = (SHELL_MIN + SHELL_MAX) / 2.0;
			double surface = surfaceAt(level, fields, px + dirX * nominal, pz + dirZ * nominal);
			int altitude = ALTITUDE_MIN + random.nextInt(Math.max(1, ALTITUDE_MAX - ALTITUDE_MIN + 1));
			double dy = surface + altitude - py;

			double maxHorizontal = Math.sqrt(Math.max(0, limit * limit - dy * dy));
			double hi = Math.min(SHELL_MAX, maxHorizontal);
			if (hi < minHorizontal) {
				rejectedGeometry++;
				continue;
			}
			double h = minHorizontal + random.nextDouble() * (hi - minHorizontal);

			double x = px + dirX * h;
			double z = pz + dirZ * h;
			int bx = Mth.floor(x);
			int bz = Mth.floor(z);
			int y = (int) Math.floor(surfaceAt(level, fields, x, z)) + altitude;
			y = Math.min(y, level.getMaxY() - 1);

			double realDy = y + 0.5 - py;
			if (h * h + realDy * realDy > limit * limit) {
				rejectedGeometry++;
				continue;
			}

			BlockPos pos = new BlockPos(bx, y, bz);

			if (!level.getChunkSource().hasChunk(bx >> 4, bz >> 4) || !level.isPositionEntityTicking(pos)) {
				rejectedUnloaded++;
				continue;
			}

			BlockState state = level.getBlockState(pos);
			FluidState fluid = level.getFluidState(pos);

			if (!NaturalSpawner.isValidEmptySpawnBlock(level, pos, state, fluid, OceaniaEntities.SEAGULL)) {
				rejectedBlocked++;
				continue;
			}

			SeagullEntity gull = OceaniaEntities.SEAGULL.create(level, EntitySpawnReason.NATURAL);
			if (gull == null) return false;

			float yaw = (float) Math.toDegrees(Math.atan2(-(px - x), pz - z));
			gull.snapTo(x, y + 0.5, z, yaw, 0.0F);
			gull.setYHeadRot(yaw);
			gull.setYBodyRot(yaw);

			gull.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.NATURAL, null);

			return level.addFreshEntity(gull);
		}
		return false;
	}

	private static double pickBearing(ServerPlayer player, RandomSource random, double[] land) {
		float yaw = player.getYRot() * Mth.DEG_TO_RAD;
		double lookX = -Mth.sin(yaw);
		double lookZ = Mth.cos(yaw);

		double landX = 0;
		double landZ = 0;
		if (land != null) {
			double dx = land[0] - player.getX();
			double dz = land[1] - player.getZ();
			double len = Math.hypot(dx, dz);

			if (len > 1.0E-4) {
				landX = dx / len;
				landZ = dz / len;
			}
		}

		double bestAngle = random.nextDouble() * Math.PI * 2;
		double bestScore = -Double.MAX_VALUE;
		for (int i = 0; i < BEARING_CANDIDATES; i++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double dirX = Math.cos(angle);
			double dirZ = Math.sin(angle);

			double score = -LOOK_WEIGHT * (dirX * lookX + dirZ * lookZ)
				- LAND_WEIGHT * (dirX * landX + dirZ * landZ);
			if (score > bestScore) {
				bestScore = score;
				bestAngle = angle;
			}
		}
		return bestAngle;
	}

	private static double surfaceAt(ServerLevel level, OceanFields fields, double x, double z) {
		return Math.max(level.getSeaLevel(), fields.floorAt(Mth.floor(x), Mth.floor(z)));
	}

	public static String summary() {
		return String.format(
			"seagulls: %d spawned of %d wanted, %d alive near players last cycle (level cap %d);"
				+ " rejected %d unloaded, %d blocked, %d geometry; capped %d player, %d level",
			totalSpawned, totalWanted, lastLevelCount, LEVEL_CAP,
			rejectedUnloaded, rejectedBlocked, rejectedGeometry, cappedPlayer, cappedLevel);
	}
}

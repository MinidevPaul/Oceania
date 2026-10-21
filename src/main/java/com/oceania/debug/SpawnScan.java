package com.oceania.debug;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.oceania.Oceania;
import com.oceania.mob.SharkEntity;
import com.oceania.registry.OceaniaEntities;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.LinkedHashMap;
import java.util.Map;

public final class SpawnScan {
	private static final int AUTO_RADIUS = Integer.getInteger("oceania.spawn.radius", -1);
	private static final Integer AUTO_X = Integer.getInteger("oceania.spawn.x");
	private static final Integer AUTO_Z = Integer.getInteger("oceania.spawn.z");

	private static final int TRIALS = 400;

	private SpawnScan() {
	}

	public static void init() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			if (AUTO_RADIUS <= 0) return;
			ServerLevel level = server.overworld();
			BlockPos spawn = level.getRespawnData().pos();
			int cx = AUTO_X != null ? AUTO_X : spawn.getX(), cz = AUTO_Z != null ? AUTO_Z : spawn.getZ();
			Oceania.LOGGER.info("[spawn] {}", scan(level, cx, cz, AUTO_RADIUS));
			server.halt(false);
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
			Commands.literal("oceania").requires(DebugCommands::visibleTo)
				.then(Commands.literal("spawn")
					.executes(ctx -> run(ctx.getSource(), 6))
					.then(Commands.argument("radius", IntegerArgumentType.integer(1, 24))
						.executes(ctx -> run(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "radius")))))));
	}

	private static int run(CommandSourceStack src, int radius) {
		src.sendSystemMessage(Component.literal(scan(src.getLevel(), (int) src.getPosition().x, (int) src.getPosition().z, radius)));
		return 1;
	}

	private static final int DRAWS = 0, IN_WATER = 1, PLACEMENT = 2, RULE = 3, BOX = 4, CLEAR = 5, DOLPHIN = 6, DROWNED = 7, COUNTERS = 8;

	public static String scan(ServerLevel level, int centreX, int centreZ, int radius) {
		long start = System.nanoTime();
		RandomSource random = level.getRandom();
		int cx0 = (centreX >> 4) - radius, cz0 = (centreZ >> 4) - radius;
		int side = radius * 2 + 1;
		Map<String, long[]> byBiome = new LinkedHashMap<>();
		long[] all = new long[COUNTERS];

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
					long[] row = byBiome.computeIfAbsent(biome, k -> new long[COUNTERS]);
					bump(row, all, DRAWS);

					if (level.getFluidState(pos).isEmpty()) continue;
					bump(row, all, IN_WATER);

					if (SpawnPlacements.isSpawnPositionOk(EntityTypes.DOLPHIN, level, pos)
						&& SpawnPlacements.checkSpawnRules(EntityTypes.DOLPHIN, level, EntitySpawnReason.NATURAL, pos, random)) {
						bump(row, all, DOLPHIN);
					}

					if (SpawnPlacements.isSpawnPositionOk(EntityTypes.DROWNED, level, pos)
						&& SpawnPlacements.checkSpawnRules(EntityTypes.DROWNED, level, EntitySpawnReason.NATURAL, pos, random)) {
						bump(row, all, DROWNED);
					}
					if (!SpawnPlacements.isSpawnPositionOk(OceaniaEntities.SHARK, level, pos)) continue;
					bump(row, all, PLACEMENT);
					if (!SharkEntity.checkSharkSpawnRules(OceaniaEntities.SHARK, level, EntitySpawnReason.NATURAL, pos, random)) continue;
					bump(row, all, RULE);
					if (!level.noCollision(OceaniaEntities.SHARK.getSpawnAABB(x + 0.5, y, z + 0.5))) continue;
					bump(row, all, BOX);

					SharkEntity probe = OceaniaEntities.SHARK.create(level, EntitySpawnReason.NATURAL);
					if (probe == null) continue;
					probe.snapTo(x + 0.5, y, z + 0.5, 0.0F, 0.0F);
					boolean ok = probe.checkSpawnRules(level, EntitySpawnReason.NATURAL) && probe.checkSpawnObstruction(level);
					probe.discard();
					if (!ok) continue;
					bump(row, all, CLEAR);
				}
			}
		}

		StringBuilder sb = new StringBuilder();
		sb.append(String.format("spawn scan %d by %d chunks at %d,%d: %.1fs, %d draws%n  %-16s %9s %8s %9s %7s %8s %9s %8s %8s",
			side, side, centreX, centreZ, (System.nanoTime() - start) / 1e9, all[DRAWS],
			"biome", "draws", "water", "placement", "rule", "box", "spawnable", "dolphin", "drowned"));
		byBiome.entrySet().stream()
			.sorted((a, b) -> Long.compare(b.getValue()[DRAWS], a.getValue()[DRAWS]))
			.forEach(e -> sb.append(row(e.getKey(), e.getValue())));
		sb.append(row("all", all));
		sb.append("\n  Columns are cumulative shares of every draw: water, then the placement type, the shark's own"
			+ "\n  rule, a clear 1.6x0.9 box, and finally checkSpawnObstruction. The last is the whole chain, so it"
			+ "\n  is the supply of legal positions - not density. Density is set by spawn_costs, which refuses a"
			+ "\n  position when sum(charge/distance) over the sharks already alive exceeds the biome's energy"
			+ "\n  budget (PotentialCalculator.java:16-27); it limits crowding and never blocks the first one.");
		return sb.toString();
	}

	private static void bump(long[] row, long[] all, int counter) {
		row[counter]++;
		all[counter]++;
	}

	private static String row(String name, long[] c) {
		double d = Math.max(1, c[DRAWS]);
		return String.format("%n  %-16s %9d %7.1f%% %8.2f%% %6.2f%% %7.2f%% %8.2f%% %7.2f%% %7.2f%%", name, c[DRAWS],
			100.0 * c[IN_WATER] / d, 100.0 * c[PLACEMENT] / d, 100.0 * c[RULE] / d,
			100.0 * c[BOX] / d, 100.0 * c[CLEAR] / d, 100.0 * c[DOLPHIN] / d, 100.0 * c[DROWNED] / d);
	}

}

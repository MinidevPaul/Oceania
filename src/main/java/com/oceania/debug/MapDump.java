package com.oceania.debug;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.oceania.Oceania;
import com.oceania.worldgen.OceaniaFluidPicker;
import com.oceania.worldgen.field.FieldFunction;
import com.oceania.worldgen.field.FlowTiles;
import com.oceania.worldgen.field.OceanFields;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.levelgen.RandomState;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class MapDump {
	private static final int AUTO_RADIUS = Integer.getInteger("oceania.map.radius", -1);
	private static final int AUTO_STEP = Integer.getInteger("oceania.map.step", 4);

	private static final Integer AUTO_X = Integer.getInteger("oceania.map.x");
	private static final Integer AUTO_Z = Integer.getInteger("oceania.map.z");

	private static final int CAVE_MID_DEPTH = 80, CAVE_DEEP_DEPTH = 200;

	private static final double[] SLOPE_BUCKETS = {1.0, 1.5, 2.0, 3.0, 5.0};

	private static final double[] PIT_BUCKETS = {0.58, 0.62, 0.66, 0.70, 0.74};

	private MapDump() {
	}

	public static void init() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			if (Boolean.getBoolean("oceania.flow.dump")) FlowTiles.dumpDir = mapsDir(server.overworld());
			if (AUTO_RADIUS > 0) {
				ServerLevel level = server.overworld();
				BlockPos spawn = level.getRespawnData().pos();
				int cx = AUTO_X != null ? AUTO_X : spawn.getX(), cz = AUTO_Z != null ? AUTO_Z : spawn.getZ();
				render(level, cx, cz, AUTO_RADIUS, AUTO_STEP, mapsDir(level), true);
				OceanFields f = fields(level);

				reportVariant(level, f, cx, cz, "barren cluster", OceanFields.TYPE_BARREN, 1);
				reportVariant(level, f, cx, cz, "large archipelago", OceanFields.TYPE_LARGE, 1);
				reportVariant(level, f, cx, cz, "large plain", OceanFields.TYPE_LARGE, 0);
				reportVariant(level, f, cx, cz, "dense atoll x2", OceanFields.TYPE_CLUSTER, 2);
				reportVariant(level, f, cx, cz, "dense atoll x3", OceanFields.TYPE_CLUSTER, 3);

				if (OceaniaFluidPicker.DRY) {
					Oceania.LOGGER.info("[map] structure searches skipped: this is a dry world, so there are"
						+ " none to find and each search would cost its whole radius");
				} else {
					for (String id : new String[]{"monument", "shipwreck", "ocean_ruin_cold", "ocean_ruin_warm"}) {
						BlockPos at = VillageLocate.nearest(level, new BlockPos(cx, level.getSeaLevel(), cz), 48,
							net.minecraft.resources.Identifier.withDefaultNamespace(id));
						Oceania.LOGGER.info("[map] vanilla structure {}: {}", id,
							at == null ? "NOT FOUND within 48 chunks" : at.toShortString());
					}

					BlockPos spawnPos = level.getRespawnData().pos();
					Oceania.LOGGER.info("[map] villages 1-3 from spawn: {}", StructureIndex.ordered(
						level, VillageLocate.VILLAGE_SET, Oceania.id("hut_village"), spawnPos, 3, 24));
					Oceania.LOGGER.info("[map] monuments 1-2 from spawn: {}", StructureIndex.ordered(
						level, VillageLocate.MONUMENT_SET,
						net.minecraft.resources.Identifier.withDefaultNamespace("monument"), spawnPos, 2, 16));
				}
				String[] names = {"", "barren", "large", "atoll", "guyot"};
				for (int t = 1; t <= 4; t++) {
					double[] g = f.findNearestGroup(cx, cz, t, 40);
					if (g == null) continue;

					OceanFields.Sample c = f.sample(g[0], g[1]);
					boolean rock = c.rock;
					int craterCols = 0;
					double craterFloorMin = Double.MAX_VALUE, craterFloorMax = -Double.MAX_VALUE, lava = Double.NaN, floorMin = Double.MAX_VALUE;
					for (int dx = -48; dx <= 48; dx += 2) {
						for (int dz = -48; dz <= 48; dz += 2) {
							OceanFields.Sample q = f.sample(g[0] + dx, g[1] + dz);
							floorMin = Math.min(floorMin, q.floorY);
							if (!Double.isNaN(q.craterLavaY)) {
								craterCols++;
								lava = q.craterLavaY;
								craterFloorMin = Math.min(craterFloorMin, q.floorY);
								craterFloorMax = Math.max(craterFloorMax, q.floorY);
							}
						}
					}
					String craterText = craterCols == 0 ? "no crater" : String.format("crater: %d of 2401 sampled columns, floor %.1f..%.1f, lava %.1f", craterCols, craterFloorMin, craterFloorMax, lava);

					int beachMin = Integer.MAX_VALUE, beachMax = 0, beachSum = 0;
					for (int k = 0; k < 16; k++) {
						double ang = k * Math.PI / 8;
						int beach = 0;
						for (int r = 0; r < g[2] * 2 + 60; r++) {
							OceanFields.Sample q = f.sample(g[0] + Math.cos(ang) * r, g[1] + Math.sin(ang) * r);
							double cont = q.continents();
							if (cont >= 0.45 && cont <= 0.6) beach++;
						}
						beachMin = Math.min(beachMin, beach);
						beachMax = Math.max(beachMax, beach);
						beachSum += beach;
					}

					int span = (int) (g[2] * 1.6) + 40;
					double steepest = 0;
					int sx = 0, sz = 0, sType = 0, landCells = 0, steepCells = 0, wallCells = 0;
					double sFloor = 0, sCarve = 0, sRelief = 0;
					for (int dx = -span; dx <= span; dx += 4) {
						for (int dz = -span; dz <= span; dz += 4) {
							double x0 = g[0] + dx, z0 = g[1] + dz;
							OceanFields.Sample q = f.sample(x0, z0);
							if (q.floorY < 63) continue;
							landCells++;
							double fl = q.floorY, carve = q.carve, relief = q.relief;
							int type = q.type;
							double ddx = (f.sample(x0 + 4, z0).floorY - f.sample(x0 - 4, z0).floorY) / 8;
							double ddz = (f.sample(x0, z0 + 4).floorY - f.sample(x0, z0 - 4).floorY) / 8;
							double sl = Math.hypot(ddx, ddz);
							if (sl > 1.5) steepCells++;
							if (sl > 2.0) wallCells++;
							if (sl > steepest) {
								steepest = sl;
								sx = (int) x0;
								sz = (int) z0;
								sType = type;
								sFloor = fl;
								sCarve = carve;
								sRelief = relief;
							}
						}
					}
					Oceania.LOGGER.info("[map] nearest {} group to {},{}: {},{} (radius {}); rock {}; within 48 blocks: {}, lowest floor {}; beach band along 16 rays: {}..{} blocks, mean {}; land cells {} of which slope>1.5: {}, slope>2: {}; steepest {} at {},{} (floor {}, type {}, carve {}, relief {})",
						names[t], cx, cz, (int) g[0], (int) g[1], (int) g[2], rock, craterText, String.format("%.1f", floorMin), beachMin, beachMax, beachSum / 16,
						landCells, steepCells, wallCells, String.format("%.2f", steepest), sx, sz, String.format("%.0f", sFloor), sType, String.format("%.1f", sCarve), String.format("%.1f", sRelief));
				}
				server.halt(false);
			}
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
			Commands.literal("oceania").requires(DebugCommands::visibleTo)
				.then(Commands.literal("map")
					.then(Commands.argument("radius", IntegerArgumentType.integer(64, 16384))
						.executes(ctx -> map(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "radius"), 4))
						.then(Commands.argument("step", IntegerArgumentType.integer(1, 64))
							.executes(ctx -> map(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "radius"), IntegerArgumentType.getInteger(ctx, "step"))))))
				.then(Commands.literal("island")
					.then(Commands.argument("type", IntegerArgumentType.integer(-1, 7))
						.executes(ctx -> island(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "type")))))
				.then(Commands.literal("probe")
					.executes(ctx -> probe(ctx.getSource(), 256))
					.then(Commands.argument("radius", IntegerArgumentType.integer(16, 2048))
						.executes(ctx -> probe(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "radius")))))));
	}

	private static void reportVariant(ServerLevel level, OceanFields f, int cx, int cz, String label, int type, int variant) {
		double[] g = f.findNearestGroup(cx, cz, type, variant, 40);
		if (g == null) {
			Oceania.LOGGER.info("[map] nearest {}: none within 40 group cells", label);
			return;
		}
		BlockPos centre = new BlockPos((int) g[0], level.getSeaLevel(), (int) g[1]);
		String got;
		if (OceaniaFluidPicker.DRY) {

			got = "village search skipped, this is a dry world";
		} else {
			BlockPos village = VillageLocate.nearest(level, centre, 24);
			got = village == null ? "no village within 24 chunks of it"
				: String.format("village at %d,%d, %d blocks from its centre (group radius %.0f)",
					village.getX(), village.getZ(),
					(int) Math.hypot(village.getX() - g[0], village.getZ() - g[1]), g[2]);
		}
		Oceania.LOGGER.info("[map] nearest {}: {},{} radius {} ({} blocks away) - {}",
			label, (int) g[0], (int) g[1], (int) g[2], (int) Math.hypot(g[0] - cx, g[1] - cz), got);
	}

	private static final java.util.Set<String> PAINTED = java.util.Set.of(
		"oceania:island_jungle", "oceania:ridge", "oceania:island_cliff", "oceania:island_sand",
		"oceania:sandy_shelf", "oceania:reef", "oceania:lagoon", "oceania:kelp_forest",
		"oceania:abyssal_plain", "oceania:island_barren", "oceania:barren_flank");

	private static final String[] ISLAND_NAMES = {
		"", "barren cone", "large island", "atoll", "guyot", "barren cluster", "large archipelago", "dense atoll"};

	private static int island(CommandSourceStack src, int choice) throws CommandSyntaxException {
		ServerLevel level = src.getLevel();
		OceanFields f = fields(level);
		double px = src.getPosition().x, pz = src.getPosition().z;

		double[] g;
		if (choice == 5) {
			g = f.findNearestGroup(px, pz, OceanFields.TYPE_BARREN, 1, 30);
		} else if (choice == 6) {
			g = f.findNearestGroup(px, pz, OceanFields.TYPE_LARGE, 1, 30);
		} else if (choice == 7) {

			double[] a = f.findNearestGroup(px, pz, OceanFields.TYPE_CLUSTER, 2, 30);
			double[] b = f.findNearestGroup(px, pz, OceanFields.TYPE_CLUSTER, 3, 30);
			g = a == null ? b : b == null ? a
				: Math.hypot(a[0] - px, a[1] - pz) <= Math.hypot(b[0] - px, b[1] - pz) ? a : b;
		} else {
			g = f.findNearestGroup(px, pz, choice, 30);
		}

		if (g == null) {
			src.sendFailure(Component.literal(choice >= 5
				? "no " + ISLAND_NAMES[choice] + " within 30 group cells - the variants are a third to two fifths of"
					+ " their archetype, so try again from somewhere else"
				: "no such island group within 30 cells"));
			return 0;
		}

		BlockPos landing = safeLanding(level, g[0], g[1], g[2]);
		src.getPlayerOrException().teleportTo(level, landing.getX() + 0.5, landing.getY(), landing.getZ() + 0.5, Set.of(), 0.0F, 0.0F, false);
		int distance = (int) Math.hypot(g[0] - px, g[1] - pz);
		String name = choice >= 5 ? ISLAND_NAMES[choice] : ISLAND_NAMES[Math.max(0, Math.min(4, (int) g[3]))];
		src.sendSystemMessage(Component.literal(String.format(
			"%s at %.0f, %.0f (radius %.0f, variant %.0f) - was %d blocks away", name, g[0], g[1], g[2], g[4], distance)));
		return 1;
	}

	private static BlockPos safeLanding(ServerLevel level, double cx, double cz, double reach) {
		int limit = (int) Math.max(24, reach);
		for (int r = 0; r <= limit; r += 6) {
			int steps = r == 0 ? 1 : Math.max(8, r / 3);
			for (int i = 0; i < steps; i++) {
				double ang = 2 * Math.PI * i / steps;
				int x = (int) (cx + Math.cos(ang) * r), z = (int) (cz + Math.sin(ang) * r);
				int y = Surface.y(level, x, z);
				BlockState top = level.getBlockState(new BlockPos(x, y - 1, z));
				if (y > level.getSeaLevel() && top.isSolid() && !top.is(Blocks.LAVA) && !top.is(Blocks.MAGMA_BLOCK)) {
					return new BlockPos(x, y, z);
				}
			}
		}

		int x = (int) cx, z = (int) cz;
		return new BlockPos(x, Surface.y(level, x, z), z);
	}

	private static int map(CommandSourceStack src, int radius, int step) {
		String result = render(src.getLevel(), (int) src.getPosition().x, (int) src.getPosition().z, radius, step, mapsDir(src.getLevel()), true);
		src.sendSystemMessage(Component.literal(result));
		return 1;
	}

	private static int probe(CommandSourceStack src, int radius) {
		ServerLevel level = src.getLevel();
		OceanFields f = fields(level);
		OceanFields.Sample s = f.sample(src.getPosition().x, src.getPosition().z);
		String biome = level.getBiome(BlockPos.containing(src.getPosition())).unwrapKey().map(k -> k.identifier().toString()).orElse("?");
		src.sendSystemMessage(Component.literal(String.format(
			"floor %.1f (regional %.1f, carve %.1f)  province %s (%.2f)  type %d age %.2f  land %.2f core %s lagoon %s cliff %.2f  plateau %.2f pit %.2f canyon %.2f fault %.0f trench %.2f ridge %.2f arc %.2f hot %.2f  cont %.2f  biome %s",
			s.floorY, s.regionalFloor, s.carve, s.province, s.provinceWeight, s.type, s.age, s.landMask, s.core, s.lagoon, s.cliff, s.plateau, s.pit, s.canyon, s.fault, s.trenchT, s.ridgeT, s.arc, s.hot, s.continents(), biome)));

		src.sendSystemMessage(Component.literal(String.format(
			"tier 4: slope %.2f -> wall %.3f (relief %.0f blocks from slope %.1f)  cave %.2f (coreT %.2f)  seaCave %.3f  blueHole %.2f (pit %.2f platform %.2f thr %.2f)  seal %.0f caveAir %.0f below floor  flags walls %s island %s floor %s air %s sea %s holes %s",
			s.terrainSlope, s.wall, OceanFields.WALL_RELIEF, OceanFields.WALL_NOISE_SLOPE, s.caveMask, s.coreT, s.seaCave,
			s.blueHole, s.pitRaw, s.platformW, OceanFields.BLUE_HOLE_THR, s.seal, s.caveAir,
			OceanFields.WALLS, OceanFields.CAVES_ISLAND, OceanFields.CAVES_FLOOR, OceanFields.CAVES_FLOOR_AIR,
			OceanFields.SEA_CAVES, OceanFields.BLUE_HOLES)));
		String result = render(level, (int) src.getPosition().x, (int) src.getPosition().z, radius, 1, mapsDir(level), false);
		src.sendSystemMessage(Component.literal(result));
		return 1;
	}

	private static Path mapsDir(ServerLevel level) {
		return level.getServer().getServerDirectory().resolve("oceania-maps");
	}

	public static OceanFields fields(ServerLevel level) {
		RandomState rs = level.getChunkSource().randomState();
		return FieldFunction.fields(rs);
	}

	public static String render(ServerLevel level, int centerX, int centerZ, int radius, int step, Path dir, boolean full) {
		long start = System.nanoTime();
		OceanFields fields = fields(level);
		RandomState rs = level.getChunkSource().randomState();

		BiomeResolver biomes = level.getChunkSource().getGenerator().getBiomeSource().createUncachedResolver(rs);
		int size = radius * 2 / step;
		BufferedImage heat = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
		BufferedImage typeImg = full ? new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB) : null;
		BufferedImage biomeImg = full ? new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB) : null;
		BufferedImage flowImg = full ? new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB) : null;

		BufferedImage midImg = full ? new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB) : null;
		BufferedImage deepImg = full ? new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB) : null;
		Map<String, Integer> midBiome = new LinkedHashMap<>(), deepBiome = new LinkedHashMap<>();
		Map<String, Integer> legend = new LinkedHashMap<>();
		Map<String, Integer> beachBiome = new LinkedHashMap<>();
		Map<String, Integer> shelfBiome = new LinkedHashMap<>();
		int[] typeCounts = new int[5];
		int land = 0;
		int channel = 0;
		double maxCarve = 0;
		double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;

		int wallCols = 0, caveCols = 0, seaCaveCols = 0, blueHoleCols = 0, platformCols = 0;

		int wetCols = 0, wetCoreCols = 0;
		double bestWet = 0;
		int wetX = Integer.MIN_VALUE, wetZ = 0;
		double maxSlope = 0;
		int maxSlopeX = 0, maxSlopeZ = 0, deepX = 0, deepZ = 0, bhX = Integer.MIN_VALUE, bhZ = 0, seaCaveX = Integer.MIN_VALUE, seaCaveZ = 0;
		double deepest = Double.MAX_VALUE, bestSeaCave = 0, bestBlueHole = 0;

		int sealMismatch = 0;
		double worstMismatch = 0;
		int mismatchX = 0, mismatchZ = 0;
		double[] cellFloor = new double[size * size];
		double[] cellCaveTop = new double[size * size];
		int[] slopeOver = new int[SLOPE_BUCKETS.length];
		int[] pitOver = new int[PIT_BUCKETS.length];
		int[] pitHist = new int[50];
		for (int pz = 0; pz < size; pz++) {
			for (int px = 0; px < size; px++) {
				int x = centerX - radius + px * step;
				int z = centerZ - radius + pz * step;
				OceanFields.Sample s = fields.sample(x, z);
				heat.setRGB(px, pz, heatColor(s.floorY));
				minY = Math.min(minY, s.floorY);
				maxY = Math.max(maxY, s.floorY);
				if (s.floorY >= 63) land++;
				if (s.wall > 0) wallCols++;
				if (s.caveMask > 0.5) caveCols++;
				if (s.wet > 0) {
					wetCols++;
					if (s.wet > 0.9) wetCoreCols++;
					if (s.wet > bestWet) { bestWet = s.wet; wetX = x; wetZ = z; }
				}
				if (s.seaCave > 0) {
					seaCaveCols++;

					if (s.seaCave > bestSeaCave) { bestSeaCave = s.seaCave; seaCaveX = x; seaCaveZ = z; }
				}
				if (s.blueHole > 0.5) {
					blueHoleCols++;
					if (s.blueHole > bestBlueHole) { bestBlueHole = s.blueHole; bhX = x; bhZ = z; }
				}
				cellFloor[pz * size + px] = s.floorY;

				cellCaveTop[pz * size + px] = s.caveMask > 0 && s.floorY < OceanFields.SEA_LEVEL
					? s.floorY - s.caveAir : Double.NaN;
				for (int i = 0; i < SLOPE_BUCKETS.length; i++) if (s.terrainSlope > SLOPE_BUCKETS[i]) slopeOver[i]++;
				if (s.terrainSlope > maxSlope) { maxSlope = s.terrainSlope; maxSlopeX = x; maxSlopeZ = z; }
				if (s.floorY < deepest) { deepest = s.floorY; deepX = x; deepZ = z; }
				if (s.platformW > 0.5) {
					platformCols++;
					pitHist[Mth.clamp((int) ((s.pitRaw + 1) * 25), 0, pitHist.length - 1)]++;
					for (int i = 0; i < PIT_BUCKETS.length; i++) if (s.pitRaw > PIT_BUCKETS[i]) pitOver[i]++;
				}
				if (full) {
					typeImg.setRGB(px, pz, typeColor(s));
					typeCounts[s.type]++;
					Holder<Biome> b = biomes.getNoiseBiome(x >> 2, 62 >> 2, z >> 2);
					String name = b.unwrapKey().map(k -> k.identifier().toString()).orElse("?");
					biomeImg.setRGB(px, pz, legend.computeIfAbsent(name, MapDump::biomeColor));
					String mid = biomes.getNoiseBiome(x >> 2, ((int) s.floorY - CAVE_MID_DEPTH) >> 2, z >> 2)
						.unwrapKey().map(k -> k.identifier().toString()).orElse("?");
					String deep = biomes.getNoiseBiome(x >> 2, ((int) s.floorY - CAVE_DEEP_DEPTH) >> 2, z >> 2)
						.unwrapKey().map(k -> k.identifier().toString()).orElse("?");
					midImg.setRGB(px, pz, legend.computeIfAbsent(mid, MapDump::biomeColor));
					deepImg.setRGB(px, pz, legend.computeIfAbsent(deep, MapDump::biomeColor));
					midBiome.merge(mid, 1, Integer::sum);
					deepBiome.merge(deep, 1, Integer::sum);

					if (s.floorY >= 60.5 && s.floorY <= 65.5) beachBiome.merge(name, 1, Integer::sum);

					else if (s.floorY >= 48 && s.floorY < 60.5) shelfBiome.merge(name, 1, Integer::sum);
					flowImg.setRGB(px, pz, flowColor(s, x, z, step));
					if (s.carve > 0.5) channel++;
					maxCarve = Math.max(maxCarve, s.carve);
				}
			}
		}

		int c = size / 2;
		for (int i = -6; i <= 6; i++) {
			if (c + i >= 0 && c + i < size) {
				heat.setRGB(c + i, c, 0xff0000);
				heat.setRGB(c, c + i, 0xff0000);
			}
		}
		try {
			Files.createDirectories(dir);
			String tag = level.getSeed() + "-" + centerX + "_" + centerZ + "-r" + radius;
			ImageIO.write(heat, "png", dir.resolve((full ? "floor-" : "probe-") + tag + ".png").toFile());
			StringBuilder sb = new StringBuilder();
			sb.append(String.format("%s %dx%d blocks at %d,%d step %d: %.1fs, floor %.0f..%.0f, land %.2f%%",
				full ? "map" : "probe", radius * 2, radius * 2, centerX, centerZ, step, (System.nanoTime() - start) / 1e9, minY, maxY, 100.0 * land / (size * size)));
			int cells = size * size;
			StringBuilder slopes = new StringBuilder();
			for (int i = 0; i < SLOPE_BUCKETS.length; i++) {
				slopes.append(String.format("%s>%.1f: %.2f%%", i == 0 ? "" : "  ", SLOPE_BUCKETS[i], 100.0 * slopeOver[i] / cells));
			}

			int reach = (int) Math.ceil(FlowTiles.FLOOR_MIN_RADIUS / step);
			for (int pz = 0; pz < size; pz++) {
				for (int px = 0; px < size; px++) {
					double top = cellCaveTop[pz * size + px];
					if (Double.isNaN(top)) continue;
					double minFloor = Double.MAX_VALUE;
					for (int dz = -reach; dz <= reach; dz++) {
						int qz = pz + dz;
						if (qz < 0 || qz >= size) continue;
						for (int dx = -reach; dx <= reach; dx++) {
							int qx = px + dx;
							if (qx < 0 || qx >= size) continue;
							minFloor = Math.min(minFloor, cellFloor[qz * size + qx]);
						}
					}
					double over = top - minFloor;
					if (over <= 0) continue;
					sealMismatch++;
					if (over > worstMismatch) {
						worstMismatch = over;
						mismatchX = centerX - radius + px * step;
						mismatchZ = centerZ - radius + pz * step;
					}
				}
			}
			sb.append(String.format("%n  tier 4: cover invariant (a dry cave top above a floor within %.0f blocks)"
					+ " broken on %d of %d columns%s%s"
					+ "%n  wall noise on %.2f%% of columns, caves under %.2f%%, sea caves %.3f%%, blue holes %.4f%% of %.2f%% platform"
					+ "%n  waterlogged sea caves: %.2f%% of columns touched, %.2f%% actually flooding (seal %.0f, water to %.0f, shell %.0f)"
					+ "%n  floor slope: %s; steepest %.2f at %d,%d; deepest floor %.0f at %d,%d",
				FlowTiles.FLOOR_MIN_RADIUS, sealMismatch, cells,
				sealMismatch == 0 ? "" : String.format(", worst %.0f blocks at %d,%d", worstMismatch, mismatchX, mismatchZ),
				step > FlowTiles.FLOOR_MIN_RADIUS
					? String.format(" [step %d is coarser than the %.0f-block radius, so this is a weak read]",
						step, FlowTiles.FLOOR_MIN_RADIUS) : "",
				100.0 * wallCols / cells, 100.0 * caveCols / cells, 100.0 * seaCaveCols / cells,
				100.0 * blueHoleCols / cells, 100.0 * platformCols / cells,
				100.0 * wetCols / cells, 100.0 * wetCoreCols / cells,
				OceanFields.WET_SEAL, OceanFields.WET_DEPTH, OceanFields.WET_SHELL,
				slopes, maxSlope, maxSlopeX, maxSlopeZ, deepest, deepX, deepZ));
			if (wetX != Integer.MIN_VALUE) sb.append(String.format("; strongest wet cave %.3f at %d,%d", bestWet, wetX, wetZ));
			if (seaCaveX != Integer.MIN_VALUE) sb.append(String.format("; strongest sea cave %.3f at %d,%d", bestSeaCave, seaCaveX, seaCaveZ));
			if (platformCols > 0) {
				StringBuilder pits = new StringBuilder();
				for (int i = 0; i < PIT_BUCKETS.length; i++) {
					pits.append(String.format("%s>%.2f: %.4f%%", i == 0 ? "" : "  ", PIT_BUCKETS[i], 100.0 * pitOver[i] / platformCols));
				}
				sb.append(String.format("%n  platform pit noise (share of platform columns): %s; p50 %.2f p90 %.2f p99 %.2f max %.2f; threshold %.2f%s",
					pits, pitAt(pitHist, platformCols, 0.50), pitAt(pitHist, platformCols, 0.90), pitAt(pitHist, platformCols, 0.99),
					pitAt(pitHist, platformCols, 1.0), OceanFields.BLUE_HOLE_THR,
					bhX == Integer.MIN_VALUE ? "; no blue hole in range" : String.format("; strongest blue hole at %d,%d", bhX, bhZ)));
			}
			if (full) {
				ImageIO.write(typeImg, "png", dir.resolve("type-" + tag + ".png").toFile());
				ImageIO.write(biomeImg, "png", dir.resolve("biome-" + tag + ".png").toFile());
				ImageIO.write(flowImg, "png", dir.resolve("flow-" + tag + ".png").toFile());
				ImageIO.write(midImg, "png", dir.resolve("biome-mid-" + tag + ".png").toFile());
				ImageIO.write(deepImg, "png", dir.resolve("biome-deep-" + tag + ".png").toFile());
				sb.append(String.format("%n  groups: barren %d large %d cluster %d guyot %d (samples)%n  biomes: ", typeCounts[1], typeCounts[2], typeCounts[3], typeCounts[4]));
				legend.forEach((n, col) -> sb.append(n).append(String.format(" #%06x  ", col & 0xffffff)));
				int beachTotal = beachBiome.values().stream().mapToInt(Integer::intValue).sum();
				sb.append(String.format("%n  waterline (floor 60.5..65.5), %d columns: ", beachTotal));
				beachBiome.entrySet().stream()
					.sorted((a, b2) -> b2.getValue() - a.getValue())
					.forEach(e -> sb.append(String.format("%s %.1f%%%s  ", e.getKey().replace("oceania:", ""),
						100.0 * e.getValue() / Math.max(1, beachTotal), PAINTED.contains(e.getKey()) ? "" : " (BARE STONE)")));
				int shelfTotal = shelfBiome.values().stream().mapToInt(Integer::intValue).sum();
				sb.append(String.format("%n  shallow shelf (floor 48..60.5), %d columns: ", shelfTotal));
				shelfBiome.entrySet().stream()
					.sorted((a, b2) -> b2.getValue() - a.getValue())
					.forEach(e -> sb.append(String.format("%s %.1f%%%s  ", e.getKey().replace("oceania:", ""),
						100.0 * e.getValue() / Math.max(1, shelfTotal), PAINTED.contains(e.getKey()) ? "" : " (BARE STONE)")));
				sb.append(String.format("%n  cave layer %d below the floor: ", CAVE_MID_DEPTH));
				midBiome.entrySet().stream().sorted((a, b2) -> b2.getValue() - a.getValue())
					.forEach(e -> sb.append(String.format("%s %.1f%%  ", e.getKey().replace("oceania:", ""), 100.0 * e.getValue() / cells)));
				sb.append(String.format("%n  deep layer %d below the floor: ", CAVE_DEEP_DEPTH));
				deepBiome.entrySet().stream().sorted((a, b2) -> b2.getValue() - a.getValue())
					.forEach(e -> sb.append(String.format("%s %.1f%%  ", e.getKey().replace("oceania:", ""), 100.0 * e.getValue() / cells)));
				sb.append(String.format("%n  channels: %.2f%% of samples carved, max carve %.1f; %s", 100.0 * channel / (size * size), maxCarve, fields.flow().stats()));
				Files.writeString(dir.resolve("legend-" + tag + ".txt"), sb.toString());
			}
			sb.append(String.format("%n  written to %s", dir.toAbsolutePath()));
			Oceania.LOGGER.info("[map] {}", sb);
			return sb.toString();
		} catch (IOException e) {
			Oceania.LOGGER.error("[map] failed", e);
			return "map failed: " + e;
		}
	}

	private static double pitAt(int[] hist, int total, double q) {
		int want = (int) Math.ceil(q * total);
		int seen = 0;
		for (int i = 0; i < hist.length; i++) {
			seen += hist[i];
			if (seen >= want) return i / 25.0 - 1;
		}
		return 1;
	}

	static int heatColor(double y) {
		if (y < -500) return lerp(0x000000, 0x120a3a, (y + 700) / 200);
		if (y < -250) return lerp(0x120a3a, 0x1e2a8a, (y + 500) / 250);
		if (y < -100) return lerp(0x1e2a8a, 0x2a6fd0, (y + 250) / 150);
		if (y < 0) return lerp(0x2a6fd0, 0x30b8d8, (y + 100) / 100);
		if (y < 45) return lerp(0x30b8d8, 0x66e6e0, y / 45);
		if (y < 63) return lerp(0x66e6e0, 0xb0fff0, (y - 45) / 18);
		if (y < 67) return 0xe6d890;
		if (y < 100) return lerp(0x8fc85a, 0xd8d060, (y - 67) / 33);
		if (y < 150) return lerp(0xd8d060, 0xf0a040, (y - 100) / 50);
		if (y < 220) return lerp(0xf0a040, 0xffffff, (y - 150) / 70);
		return 0xffffff;
	}

	static int flowColor(OceanFields.Sample s, int x, int z, int step) {
		if (Math.floorMod(x, FlowTiles.TILE) < step || Math.floorMod(z, FlowTiles.TILE) < step) return 0x606060;
		if (s.carve < 0.5) return dim(heatColor(s.floorY), 0.4);
		return lerp(0xfff060, 0xff2020, s.carve / 40.0);
	}

	static int typeColor(OceanFields.Sample s) {
		if (s.trenchT > 0.3) return 0x000000;
		int base = switch (s.type) {
			case OceanFields.TYPE_BARREN -> 0xd04030;
			case OceanFields.TYPE_LARGE -> 0x30b040;
			case OceanFields.TYPE_CLUSTER -> 0xf0e040;
			case OceanFields.TYPE_GUYOT -> 0x8040c0;
			default -> s.ridgeT > 0.3 ? 0xff8000 : s.pit > 0.5 ? 0x101018 : s.canyon > 0.5 ? 0x303048 : s.plateau > 0.5 ? 0x506070 : provinceColor(s);
		};
		if (s.type != OceanFields.TYPE_NONE && s.floorY < 63) base = dim(base, 0.45);
		return base;
	}

	static int provinceColor(OceanFields.Sample s) {
		int c = switch (s.province) {
			case ABYSSAL_PLAIN -> 0x1c2230;
			case SEAMOUNT_FIELD -> 0x2c3a4a;
			case FRACTURE_ZONE -> 0x4a3030;
			case CARBONATE_PLATFORM -> 0x3a4a30;
			case RIDGE_PROVINCE -> 0x4a4030;
			case BASIN_AND_RANGE -> 0x30304a;
		};
		return s.hot > 0.3 ? dim(c, 1.6) : c;
	}

	static int biomeColor(String name) {
		int h = name.hashCode();
		return 0x404040 + ((h & 0x7f) << 16 | ((h >> 8) & 0x7f) << 8 | ((h >> 16) & 0x7f));
	}

	private static int dim(int rgb, double f) {
		int r = (int) Math.min(255, ((rgb >> 16) & 255) * f), g = (int) Math.min(255, ((rgb >> 8) & 255) * f), b = (int) Math.min(255, (rgb & 255) * f);
		return r << 16 | g << 8 | b;
	}

	private static int lerp(int a, int b, double t) {
		t = Math.max(0, Math.min(1, t));
		int r = (int) mix((a >> 16) & 255, (b >> 16) & 255, t), g = (int) mix((a >> 8) & 255, (b >> 8) & 255, t), bl = (int) mix(a & 255, b & 255, t);
		return r << 16 | g << 8 | bl;
	}

	private static double mix(int a, int b, double t) {
		return a + (b - a) * t;
	}
}

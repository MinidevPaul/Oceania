package com.oceania.debug;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.datafixers.util.Pair;
import com.oceania.Oceania;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import com.oceania.worldgen.field.FieldFunction;
import com.oceania.worldgen.field.OceanFields;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.structure.Structure;

public final class VillageLocate {

	private static final int DEFAULT_RADIUS = 256;

	public static final ResourceKey<StructureSet> VILLAGE_SET =
		ResourceKey.create(Registries.STRUCTURE_SET, Oceania.id("hut_villages"));
	public static final ResourceKey<StructureSet> MONUMENT_SET =
		ResourceKey.create(Registries.STRUCTURE_SET, Identifier.withDefaultNamespace("ocean_monuments"));

	private VillageLocate() {
	}

	private static final int AUTO_LIST = Integer.getInteger("oceania.village.list", 0);

	public static void init() {
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			if (AUTO_LIST <= 0) return;
			ServerLevel level = server.overworld();
			BlockPos spawn = level.getRespawnData().pos();
			long start = System.nanoTime();
			List<BlockPos> villages = StructureIndex.ordered(level, VILLAGE_SET, Oceania.id("hut_village"), spawn, AUTO_LIST, 24);
			StringBuilder sb = new StringBuilder(String.format("village index from spawn %d,%d (%d found in %.1fs):",
				spawn.getX(), spawn.getZ(), villages.size(), (System.nanoTime() - start) / 1e9));
			for (int i = 0; i < villages.size(); i++) {
				BlockPos at = villages.get(i);
				sb.append(String.format(" #%d %d,%d", i + 1, at.getX(), at.getZ()));
			}
			List<BlockPos> monuments = StructureIndex.ordered(level, MONUMENT_SET, Identifier.withDefaultNamespace("monument"), spawn, 4, 16);
			sb.append(String.format("%n  monument index (%d found):", monuments.size()));
			for (int i = 0; i < monuments.size(); i++) {
				BlockPos at = monuments.get(i);
				sb.append(String.format(" #%d %d,%d", i + 1, at.getX(), at.getZ()));
			}

			ResourceKey<StructureSet> cities = ResourceKey.create(Registries.STRUCTURE_SET, Identifier.withDefaultNamespace("ancient_cities"));
			ResourceKey<StructureSet> chambers = ResourceKey.create(Registries.STRUCTURE_SET, Identifier.withDefaultNamespace("trial_chambers"));
			long t2 = System.nanoTime();
			List<BlockPos> cityList = StructureIndex.ordered(level, cities, Identifier.withDefaultNamespace("ancient_city"), spawn, 2, 6);
			List<BlockPos> chamberList = StructureIndex.ordered(level, chambers, Identifier.withDefaultNamespace("trial_chambers"), spawn, 2, 5);
			sb.append(String.format("%n  ancient cities (%d found in %.1fs):", cityList.size(), (System.nanoTime() - t2) / 1e9));
			for (int i = 0; i < cityList.size(); i++) sb.append(String.format(" #%d %d,%d", i + 1, cityList.get(i).getX(), cityList.get(i).getZ()));
			sb.append(String.format("%n  trial chambers (%d found):", chamberList.size()));
			for (int i = 0; i < chamberList.size(); i++) sb.append(String.format(" #%d %d,%d", i + 1, chamberList.get(i).getX(), chamberList.get(i).getZ()));
			Oceania.LOGGER.info("[village] {}", sb);
			java.nio.file.Path maps = server.getServerDirectory().resolve("oceania-maps");
			if (!cityList.isEmpty()) {
				BlockPos c = cityList.get(0);
				int floor = (int) com.oceania.debug.MapDump.fields(level).sample(c.getX(), c.getZ()).floorY;
				Oceania.LOGGER.info("[village] ancient city section: {}", SliceDump.render(level, c.getX(), c.getZ(), 256, true, maps, floor - 230, floor - 40));
				BlockPos spot = StructureIndex.standInside(level, c, Identifier.withDefaultNamespace("ancient_city"));
				Oceania.LOGGER.info("[village] /oceania city 1 would land at {} ({} below the floor at {})", spot, floor - spot.getY(), floor);
			}
			if (!chamberList.isEmpty()) {
				BlockPos c = chamberList.get(0);
				int floor = (int) com.oceania.debug.MapDump.fields(level).sample(c.getX(), c.getZ()).floorY;
				Oceania.LOGGER.info("[village] trial chamber section: {}", SliceDump.render(level, c.getX(), c.getZ(), 256, true, maps, floor - 180, floor));
				BlockPos spot = StructureIndex.standInside(level, c, Identifier.withDefaultNamespace("trial_chambers"));
				Oceania.LOGGER.info("[village] /oceania chamber 1 would land at {} ({} below the floor at {})", spot, floor - spot.getY(), floor);
			}

			if (!villages.isEmpty()) {
				BlockPos v = villages.get(0);
				Oceania.LOGGER.info("[village] /oceania village 1 would land at {}, {}, {}", v.getX(), Surface.y(level, v.getX(), v.getZ()), v.getZ());
			}

			StringBuilder sr = new StringBuilder();
			long t3 = System.nanoTime();

			List<BlockPos> rings = StructureIndex.ringPositions(level, STRONGHOLD_SET, spawn);
			boolean byRings = !rings.isEmpty();
			if (!byRings) {
				rings = StructureIndex.ordered(level, STRONGHOLD_SET, Identifier.withDefaultNamespace("stronghold"), spawn, 3, 24);
			}
			sr.append(String.format("stronghold %s: %d; nearest three:", byRings ? "ring positions" : "candidates (random_spread)", rings.size()));
			for (int i = 0; i < Math.min(3, rings.size()); i++) {
				BlockPos r = rings.get(i);
				sr.append(String.format(" #%d %d,%d (%d from spawn)", i + 1, r.getX(), r.getZ(), (int) Math.sqrt(r.distSqr(new BlockPos(spawn.getX(), 0, spawn.getZ())))));
			}
			Identifier strongholdId = Identifier.withDefaultNamespace("stronghold");
			for (int i = 0; i < Math.min(3, rings.size()); i++) {
				BlockPos r = rings.get(i);
				var st = StructureIndex.startAt(level, r, strongholdId);
				if (st == null) {
					sr.append(String.format("%n  stronghold #%d at %d,%d: NOT BUILT", i + 1, r.getX(), r.getZ()));
					continue;
				}
				var box = st.getBoundingBox();
				int floor = (int) com.oceania.debug.MapDump.fields(level).sample(r.getX(), r.getZ()).floorY;
				BlockPos spot = StructureIndex.standInside(level, r, strongholdId);
				sr.append(String.format("%n  stronghold #%d at %d,%d: built, y %d..%d (top %d below the floor at %d), %d by %d; /oceania stronghold %d would land at %s",
					i + 1, r.getX(), r.getZ(), box.minY(), box.maxY(), floor - box.maxY(), floor, box.getXSpan(), box.getZSpan(), i + 1, spot));
				if (i == 0) {
					sr.append(String.format("%n  stronghold section: %s", SliceDump.render(level, r.getX(), r.getZ(), 256, true, maps, box.minY() - 20, floor + 10)));
				}
			}
			sr.append(String.format("%n  (strongholds in %.1fs)", (System.nanoTime() - t3) / 1e9));
			long t4 = System.nanoTime();
			List<BlockPos> shafts = StructureIndex.ordered(level, MINESHAFT_SET, Identifier.withDefaultNamespace("mineshaft"), spawn, 2, 12);
			sr.append(String.format("%n  mineshafts within 12 chunks of spawn (%d found in %.1fs):", shafts.size(), (System.nanoTime() - t4) / 1e9));
			for (int i = 0; i < shafts.size(); i++) sr.append(String.format(" #%d %d,%d", i + 1, shafts.get(i).getX(), shafts.get(i).getZ()));
			if (!shafts.isEmpty()) {
				BlockPos m = shafts.get(0);
				var st = StructureIndex.startAt(level, m, Identifier.withDefaultNamespace("mineshaft"));
				int floor = (int) com.oceania.debug.MapDump.fields(level).sample(m.getX(), m.getZ()).floorY;
				if (st != null) {
					var box = st.getBoundingBox();
					sr.append(String.format("%n  mineshaft #1: y %d..%d (top %d below the floor at %d), %d by %d", box.minY(), box.maxY(), floor - box.maxY(), floor, box.getXSpan(), box.getZSpan()));
					sr.append(String.format("%n  mineshaft section: %s", SliceDump.render(level, m.getX(), m.getZ(), 256, true, maps, box.minY() - 20, floor + 10)));
				}
				sr.append(String.format("%n  /oceania mineshaft 1 would land at %s", StructureIndex.standInside(level, m, Identifier.withDefaultNamespace("mineshaft"))));
			}
			long t6 = System.nanoTime();
			List<BlockPos> outposts = StructureIndex.ordered(level, OUTPOST_SET, Identifier.withDefaultNamespace("pillager_outpost"), spawn, 2, 10);
			sr.append(String.format("%n  pillager outposts within 10 cells of spawn (%d found in %.1fs):", outposts.size(), (System.nanoTime() - t6) / 1e9));
			for (int i = 0; i < outposts.size(); i++) {
				BlockPos p = outposts.get(i);
				sr.append(String.format(" #%d %d,%d (lands at %s)", i + 1, p.getX(), p.getZ(), landing(level, p)));
			}

			long t7 = System.nanoTime();
			List<BlockPos> temples = StructureIndex.ordered(level, TEMPLE_SET, Identifier.withDefaultNamespace("jungle_pyramid"), spawn, 6, 24);
			sr.append(String.format("%n  jungle temples within 24 cells of spawn (%d found in %.1fs):", temples.size(), (System.nanoTime() - t7) / 1e9));
			for (int i = 0; i < temples.size(); i++) {
				BlockPos p = temples.get(i);
				sr.append(String.format(" #%d %d,%d (lands at %s)", i + 1, p.getX(), p.getZ(), landing(level, p)));
			}

			sr.append(islandCensus(level, spawn, 2304));
			Oceania.LOGGER.info("[structures] {}", sr);

			if (!villages.isEmpty()) {
				BlockPos centre = villages.get(0);
				for (int cx = (centre.getX() >> 4) - 5; cx <= (centre.getX() >> 4) + 5; cx++)
					for (int cz = (centre.getZ() >> 4) - 5; cz <= (centre.getZ() >> 4) + 5; cz++) level.getChunk(cx, cz);
				net.minecraft.util.RandomSource random = net.minecraft.util.RandomSource.create(42L);
				var placement = net.minecraft.world.entity.SpawnPlacements.getPlacementType(net.minecraft.world.entity.EntityTypes.RAVAGER);
				StringBuilder rs = new StringBuilder(String.format("raid wave spots at village 1 (%d,%d), 200 draws per ring:", centre.getX(), centre.getZ()));
				for (int ring = 0; ring < 3; ring++) {
					int k = ring == 0 ? 2 : 2 - ring;
					int ok = 0, water = 0, village = 0;
					for (int t = 0; t < 200; t++) {
						float f = random.nextFloat() * (float) (Math.PI * 2);
						int x = centre.getX() + net.minecraft.util.Mth.floor(net.minecraft.util.Mth.cos(f) * 32.0F * k) + random.nextInt(5);
						int z = centre.getZ() + net.minecraft.util.Mth.floor(net.minecraft.util.Mth.sin(f) * 32.0F * k) + random.nextInt(5);
						BlockPos at = new BlockPos(x, level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x, z), z);
						boolean inVillage = level.isVillage(at);
						if (inVillage && ring < 2) { village++; continue; }
						if (level.getBlockState(at.below()).getFluidState().isSource() || !level.getFluidState(at.below()).isEmpty()) water++;
						if (placement.isSpawnPositionOk(level, at, net.minecraft.world.entity.EntityTypes.RAVAGER)) ok++;
					}
					rs.append(String.format(" ring %d: %d ok, %d over water, %d inside the village;", 32 * k, ok, water, village));
				}
				Oceania.LOGGER.info("[structures] {}", rs);
			}

			server.halt(false);
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
			Commands.literal("oceania").requires(DebugCommands::visibleTo)
				.then(Commands.literal("village")
					.executes(ctx -> run(ctx.getSource(), DEFAULT_RADIUS, true))
					.then(Commands.literal("locate")
						.executes(ctx -> run(ctx.getSource(), DEFAULT_RADIUS, false)))

					.then(Commands.argument("n", IntegerArgumentType.integer(1, 64))
						.executes(ctx -> nth(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "n")))))
				.then(Commands.literal("monument")
					.executes(ctx -> monument(ctx.getSource(), 1))
					.then(Commands.argument("n", IntegerArgumentType.integer(1, 64))
						.executes(ctx -> monument(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "n")))))

				.then(Commands.literal("city")
					.executes(ctx -> underground(ctx.getSource(), 1, CITY_SET, "ancient_city", "ancient city", 10))
					.then(Commands.argument("n", IntegerArgumentType.integer(1, 64))
						.executes(ctx -> underground(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "n"), CITY_SET, "ancient_city", "ancient city", 10))))
				.then(Commands.literal("chamber")
					.executes(ctx -> underground(ctx.getSource(), 1, CHAMBER_SET, "trial_chambers", "trial chamber", 8))
					.then(Commands.argument("n", IntegerArgumentType.integer(1, 64))
						.executes(ctx -> underground(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "n"), CHAMBER_SET, "trial_chambers", "trial chamber", 8))))

				.then(Commands.literal("stronghold")
					.executes(ctx -> stronghold(ctx.getSource(), 1))
					.then(Commands.argument("n", IntegerArgumentType.integer(1, 128))
						.executes(ctx -> stronghold(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "n")))))
				.then(Commands.literal("mineshaft")
					.executes(ctx -> underground(ctx.getSource(), 1, MINESHAFT_SET, "mineshaft", "mineshaft", 24))
					.then(Commands.argument("n", IntegerArgumentType.integer(1, 64))
						.executes(ctx -> underground(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "n"), MINESHAFT_SET, "mineshaft", "mineshaft", 24))))
				.then(Commands.literal("outpost")
					.executes(ctx -> surface(ctx.getSource(), 1, OUTPOST_SET, List.of(Identifier.withDefaultNamespace("pillager_outpost")), "pillager outpost", 6))
					.then(Commands.argument("n", IntegerArgumentType.integer(1, 64))
						.executes(ctx -> surface(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "n"), OUTPOST_SET, List.of(Identifier.withDefaultNamespace("pillager_outpost")), "pillager outpost", 6))))

				.then(Commands.literal("temple")
					.executes(ctx -> surface(ctx.getSource(), 1, TEMPLE_SET, List.of(Identifier.withDefaultNamespace("jungle_pyramid")), "jungle temple", 24))
					.then(Commands.argument("n", IntegerArgumentType.integer(1, 64))
						.executes(ctx -> surface(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "n"), TEMPLE_SET, List.of(Identifier.withDefaultNamespace("jungle_pyramid")), "jungle temple", 24))))));
	}

	private static String islandCensus(ServerLevel level, BlockPos centre, int half) {
		OceanFields fields = FieldFunction.fields(level.getChunkSource().randomState());
		List<double[]> groups = fields.groupsIn(centre.getX() - half, centre.getZ() - half,
			centre.getX() + half, centre.getZ() + half);
		String[] typeNames = {"none", "barren", "large", "cluster", "guyot"};
		StringBuilder out = new StringBuilder();
		out.append(String.format("%n  islands within %d blocks of spawn (%d groups):", half, groups.size()));
		int jungle = 0;
		int qualify = 0;
		for (double[] g : groups) {
			int x = (int) g[0];
			int z = (int) g[1];
			double reach = g[2];
			int type = (int) g[3];
			int y = level.getChunkSource().getGenerator().getFirstOccupiedHeight(x, z,
				Heightmap.Types.WORLD_SURFACE_WG, level, level.getChunkSource().randomState());
			Holder<Biome> biome = level.getChunkSource().getGenerator().getBiomeSource()
				.createUncachedResolver(level.getChunkSource().randomState())
				.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z));
			String name = biome.unwrapKey().map(k -> k.identifier().getPath()).orElse("?");
			boolean isJungle = "island_jungle".equals(name);
			boolean passes = isJungle && type == OceanFields.TYPE_LARGE && reach >= 100.0;
			if (isJungle) {
				jungle++;
			}
			if (passes) {
				qualify++;
			}
			out.append(String.format("%n    %6d,%-6d %-8s reach %3.0f  y %3d  %-14s%s",
				x, z, type >= 0 && type < typeNames.length ? typeNames[type] : "?", reach, y, name,
				passes ? "  <- temple gate" : ""));
		}
		out.append(String.format("%n  counts: %d islands, %d jungle, %d pass the temple gate"
			+ " (%s of jungle islands)", groups.size(), jungle, qualify,
			jungle == 0 ? "n/a" : String.format("1 in %.1f", (double) jungle / Math.max(1, qualify))));
		return out.toString();
	}

	public static final ResourceKey<StructureSet> STRONGHOLD_SET =
		ResourceKey.create(Registries.STRUCTURE_SET, Identifier.withDefaultNamespace("strongholds"));
	public static final ResourceKey<StructureSet> MINESHAFT_SET =
		ResourceKey.create(Registries.STRUCTURE_SET, Identifier.withDefaultNamespace("mineshafts"));
	public static final ResourceKey<StructureSet> OUTPOST_SET =
		ResourceKey.create(Registries.STRUCTURE_SET, Identifier.withDefaultNamespace("pillager_outposts"));
	public static final ResourceKey<StructureSet> TEMPLE_SET =
		ResourceKey.create(Registries.STRUCTURE_SET, Identifier.withDefaultNamespace("jungle_temples"));

	private static int stronghold(CommandSourceStack src, int n) throws CommandSyntaxException {
		ServerLevel level = src.getLevel();
		BlockPos spawn = level.getRespawnData().pos();

		List<BlockPos> rings = StructureIndex.ringPositions(level, STRONGHOLD_SET, spawn);
		if (rings.isEmpty()) {
			rings = StructureIndex.ordered(level, STRONGHOLD_SET, Identifier.withDefaultNamespace("stronghold"), spawn, n, 24);
		}
		if (rings.size() < n) {
			src.sendFailure(Component.literal(String.format("only %d stronghold%s found; asked for number %d", rings.size(), rings.size() == 1 ? "" : "s", n)));
			return 0;
		}
		BlockPos at = rings.get(n - 1);
		Identifier id = Identifier.withDefaultNamespace("stronghold");
		long start = System.nanoTime();
		var structureStart = StructureIndex.startAt(level, at, id);
		double seconds = (System.nanoTime() - start) / 1e9;
		if (structureStart == null) {
			src.sendFailure(Component.literal(String.format(
				"stronghold #%d has a ring position at %d,%d (%d blocks from spawn) but no stronghold built there (%.1fs)",
				n, at.getX(), at.getZ(), (int) Math.sqrt(at.distSqr(new BlockPos(spawn.getX(), 0, spawn.getZ()))), seconds)));
			return 0;
		}
		BlockPos spot = StructureIndex.standInside(level, at, id);
		int floor = Surface.y(level, spot.getX(), spot.getZ());
		ServerPlayer player = src.getPlayerOrException();
		player.teleportTo(level, spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, java.util.Set.of(), player.getYRot(), 0.0F, false);
		var box = structureStart.getBoundingBox();
		src.sendSuccess(() -> Component.literal(String.format(
			"stronghold #%d at %d, %d, %d - %d blocks below the surface, %d blocks from spawn; it spans y %d..%d and %d by %d blocks; the portal room is somewhere inside (%.1fs)",
			n, spot.getX(), spot.getY(), spot.getZ(), floor - spot.getY(),
			(int) Math.sqrt(at.distSqr(new BlockPos(spawn.getX(), at.getY(), spawn.getZ()))),
			box.minY(), box.maxY(), box.getXSpan(), box.getZSpan(), seconds)), false);
		return 1;
	}

	private static int surface(CommandSourceStack src, int n, ResourceKey<StructureSet> set, List<Identifier> ids, String what, int rings) throws CommandSyntaxException {
		ServerLevel level = src.getLevel();
		BlockPos spawn = level.getRespawnData().pos();
		long start = System.nanoTime();
		List<BlockPos> found = StructureIndex.ordered(level, set, ids, spawn, n, rings, COMMAND_INDEX_BUDGET_NANOS);
		double seconds = (System.nanoTime() - start) / 1e9;
		if (found.size() < n) {
			src.sendFailure(Component.literal(String.format(
				"only found %d %s%s from spawn in %.1fs; asked for number %d", found.size(), what, found.size() == 1 ? "" : "s", seconds, n)));
			return 0;
		}
		BlockPos at = found.get(n - 1);
		BlockPos spot = landing(level, at);
		ServerPlayer player = src.getPlayerOrException();
		player.teleportTo(level, spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, java.util.Set.of(), player.getYRot(), 0.0F, false);
		src.sendSuccess(() -> Component.literal(String.format(
			"%s #%d at %d, %d, %d - %d blocks from spawn, found in %.1fs",
			what, n, spot.getX(), spot.getY(), spot.getZ(),
			(int) Math.sqrt(at.distSqr(new BlockPos(spawn.getX(), at.getY(), spawn.getZ()))), seconds)), false);
		return 1;
	}

	static BlockPos landing(ServerLevel level, BlockPos at) {
		int surface = Surface.y(level, at.getX(), at.getZ());
		if (surface > level.getSeaLevel() + 1) return new BlockPos(at.getX(), surface, at.getZ());
		return StructureIndex.topOfStructure(level, at);
	}

	public static final ResourceKey<StructureSet> CITY_SET =
		ResourceKey.create(Registries.STRUCTURE_SET, Identifier.withDefaultNamespace("ancient_cities"));
	public static final ResourceKey<StructureSet> CHAMBER_SET =
		ResourceKey.create(Registries.STRUCTURE_SET, Identifier.withDefaultNamespace("trial_chambers"));

	private static int underground(CommandSourceStack src, int n, ResourceKey<StructureSet> set, String id, String what, int rings) throws CommandSyntaxException {
		ServerLevel level = src.getLevel();
		BlockPos spawn = level.getRespawnData().pos();
		long start = System.nanoTime();
		Identifier structure = Identifier.withDefaultNamespace(id);
		List<BlockPos> found = StructureIndex.ordered(level, set, structure, spawn, n, rings,
			COMMAND_INDEX_BUDGET_NANOS);
		double seconds = (System.nanoTime() - start) / 1e9;
		if (found.size() < n) {
			src.sendFailure(Component.literal(String.format(
				"only found %d %s%s from spawn in %.1fs; asked for number %d", found.size(), what, found.size() == 1 ? "" : "s", seconds, n)));
			return 0;
		}
		BlockPos at = found.get(n - 1);
		BlockPos spot = StructureIndex.standInside(level, at, structure);
		int floor = Surface.y(level, spot.getX(), spot.getZ());
		ServerPlayer player = src.getPlayerOrException();
		player.teleportTo(level, spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, java.util.Set.of(), player.getYRot(), 0.0F, false);
		src.sendSuccess(() -> Component.literal(String.format(
			"%s #%d at %d, %d, %d - %d blocks below the surface, %d blocks from spawn, found in %.1fs",
			what, n, spot.getX(), spot.getY(), spot.getZ(), floor - spot.getY(),
			(int) Math.sqrt(at.distSqr(new BlockPos(spawn.getX(), at.getY(), spawn.getZ()))), seconds)), false);
		return 1;
	}

	private static int nth(CommandSourceStack src, int n) throws CommandSyntaxException {
		ServerLevel level = src.getLevel();
		BlockPos spawn = level.getRespawnData().pos();
		long start = System.nanoTime();
		List<BlockPos> found = StructureIndex.ordered(level, VILLAGE_SET, Oceania.id("hut_village"), spawn, n, 24,
			COMMAND_INDEX_BUDGET_NANOS);
		double seconds = (System.nanoTime() - start) / 1e9;

		if (found.size() < n) {
			src.sendFailure(Component.literal(String.format(
				"only found %d hut village%s from spawn in %.1fs; asked for number %d",
				found.size(), found.size() == 1 ? "" : "s", seconds, n)));
			return 0;
		}
		BlockPos at = found.get(n - 1);
		int y = Surface.y(level, at.getX(), at.getZ());
		src.getPlayerOrException().teleportTo(level, at.getX() + 0.5, y, at.getZ() + 0.5, java.util.Set.of(), src.getPlayerOrException().getYRot(), 0.0F, false);
		src.sendSuccess(() -> Component.literal(String.format(
			"hut village #%d at %d, %d, %d - %d blocks from spawn, found in %.1fs",
			n, at.getX(), y, at.getZ(), (int) Math.sqrt(at.distSqr(new BlockPos(spawn.getX(), at.getY(), spawn.getZ()))), seconds)), false);
		return 1;
	}

	private static int monument(CommandSourceStack src, int n) throws CommandSyntaxException {
		ServerLevel level = src.getLevel();
		BlockPos spawn = level.getRespawnData().pos();
		long start = System.nanoTime();
		List<BlockPos> found = StructureIndex.ordered(level, MONUMENT_SET,
			Identifier.withDefaultNamespace("monument"), spawn, n, 16, COMMAND_INDEX_BUDGET_NANOS);
		double seconds = (System.nanoTime() - start) / 1e9;

		if (found.size() < n) {
			src.sendFailure(Component.literal(String.format(
				"only found %d ocean monument%s from spawn in %.1fs; asked for number %d. They are gated to the"
					+ " abyssal plain, so they are sparse.", found.size(), found.size() == 1 ? "" : "s", seconds, n)));
			return 0;
		}
		BlockPos at = found.get(n - 1);
		BlockPos top = StructureIndex.topOfStructure(level, at);
		src.getPlayerOrException().teleportTo(level, top.getX() + 0.5, top.getY(), top.getZ() + 0.5,
			java.util.Set.of(), src.getPlayerOrException().getYRot(), 0.0F, false);
		src.sendSuccess(() -> Component.literal(String.format(
			"ocean monument #%d at %d, %d, %d - on its roof, %d blocks below sea level, %d blocks from spawn",
			n, top.getX(), top.getY(), top.getZ(), level.getSeaLevel() - top.getY(),
			(int) Math.sqrt(at.distSqr(new BlockPos(spawn.getX(), at.getY(), spawn.getZ()))))), false);
		return 1;
	}

	public static BlockPos nearest(ServerLevel level, BlockPos from, int radiusChunks) {
		return nearest(level, from, radiusChunks, Oceania.id("hut_village"));
	}

	public static BlockPos nearest(ServerLevel level, BlockPos from, int radiusChunks, net.minecraft.resources.Identifier id) {
		ResourceKey<Structure> key = ResourceKey.create(Registries.STRUCTURE, id);
		Holder.Reference<Structure> holder = level.registryAccess().lookupOrThrow(Registries.STRUCTURE)
			.get(key).orElse(null);
		if (holder == null) return null;
		Pair<BlockPos, Holder<Structure>> hit = level.getChunkSource().getGenerator()
			.findNearestMapStructure(level, HolderSet.direct(holder), from, radiusChunks, false);
		return hit == null ? null : hit.getFirst();
	}

	private static final int SLICE_CHUNKS = 8;

	private static final long BUDGET_NANOS = 60L * 1_000_000_000L;

	private static final long COMMAND_INDEX_BUDGET_NANOS = 20L * 1_000_000_000L;

	private static int run(CommandSourceStack src, int radius, boolean teleport) {
		ServerLevel level = src.getLevel();
		BlockPos from = BlockPos.containing(src.getPosition());

		ResourceKey<Structure> key = ResourceKey.create(Registries.STRUCTURE, Oceania.id("hut_village"));
		Holder.Reference<Structure> holder = level.registryAccess().lookupOrThrow(Registries.STRUCTURE)
			.get(key)
			.orElse(null);
		if (holder == null) {
			src.sendFailure(Component.literal("oceania:hut_village is not in the structure registry - the data pack did not load"));
			return 0;
		}

		src.sendSuccess(() -> Component.literal(String.format(
			"searching out to %d chunks (%d blocks) a slice at a time - the server keeps ticking, this is not"
				+ " frozen", radius, radius * 16)), false);
		slice(src, from, holder, Math.min(SLICE_CHUNKS, radius), radius, teleport, System.nanoTime());
		return 1;
	}

	private static void slice(CommandSourceStack src, BlockPos from, Holder<Structure> holder,
			int reach, int limit, boolean teleport, long started) {
		ServerLevel level = src.getLevel();
		long sliceStart = System.nanoTime();
		Pair<BlockPos, Holder<Structure>> hit = level.getChunkSource().getGenerator()
			.findNearestMapStructure(level, HolderSet.direct(holder), from, reach, false);
		double sliceSeconds = (System.nanoTime() - sliceStart) / 1e9;
		double seconds = (System.nanoTime() - started) / 1e9;
		Oceania.LOGGER.info("[village] locate reached {} chunks in {}s this slice, {}s in total{}",
			reach, String.format("%.2f", sliceSeconds), String.format("%.1f", seconds),
			hit == null ? "" : ", found");

		if (hit == null) {
			if (reach >= limit) {
				src.sendFailure(Component.literal(String.format(
					"no hut village within %d chunks (%d blocks), searched in %.1fs. They are gated to large"
						+ " islands, so try a bigger radius: /oceania village 1024",
					limit, limit * 16, seconds)));
				return;
			}
			if (System.nanoTime() - started > BUDGET_NANOS) {
				src.sendFailure(Component.literal(String.format(
					"gave up after %.1fs, searched out to %d chunks (%d blocks) of the %d asked for. Run it"
						+ " again to carry on from a cold start, or ask for a smaller radius.",
					seconds, reach, reach * 16, limit)));
				return;
			}
			int next = Math.min(limit, reach + SLICE_CHUNKS);

			level.getServer().execute(() -> slice(src, from, holder, next, limit, teleport, started));
			return;
		}

		BlockPos at = hit.getFirst();
		int distance = (int) Math.sqrt(at.distSqr(new BlockPos(from.getX(), at.getY(), from.getZ())));
		int y = Surface.y(level, at.getX(), at.getZ());

		if (!teleport) {
			src.sendSuccess(() -> Component.literal(String.format(
				"nearest hut village at %d, %d, %d - %d blocks away, found in %.1fs",
				at.getX(), y, at.getZ(), distance, seconds)), false);
			return;
		}

		if (!(src.getEntity() instanceof ServerPlayer player)) {
			src.sendSuccess(() -> Component.literal(String.format(
				"nearest hut village at %d, %d, %d - %d blocks away (console cannot teleport)",
				at.getX(), y, at.getZ(), distance)), false);
			return;
		}

		player.teleportTo(level, at.getX() + 0.5, y, at.getZ() + 0.5, java.util.Set.of(), player.getYRot(), player.getXRot(), false);
		src.sendSuccess(() -> Component.literal(String.format(
			"hut village at %d, %d, %d - was %d blocks away, found in %.1fs",
			at.getX(), y, at.getZ(), distance, seconds)), false);
	}
}

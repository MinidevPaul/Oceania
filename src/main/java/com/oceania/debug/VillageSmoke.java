package com.oceania.debug;

import com.oceania.Oceania;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;

public final class VillageSmoke {
	private static final boolean ENABLED = "village".equals(System.getProperty("oceania.smoke", ""));
	private static final ResourceKey<StructureSet> SET = ResourceKey.create(Registries.STRUCTURE_SET, Oceania.id("hut_villages"));
	private static final Identifier HUT_VILLAGE = Oceania.id("hut_village");
	private static final Pattern NAME = Pattern.compile("hut_village/([a-z0-9_]+)");
	private static final int VILLAGES = Integer.getInteger("oceania.village.smoke.count", 2);
	private static final int MAX_GAP = 16;

	private static final int CUT_RING = 6;
	private static final int WALL_RING = 2;

	private static final Set<Block> NATURAL = Set.of(Blocks.STONE, Blocks.DIRT, Blocks.GRASS_BLOCK, Blocks.SAND,
		Blocks.SANDSTONE, Blocks.MUD, Blocks.PODZOL, Blocks.DEEPSLATE, Blocks.ANDESITE, Blocks.DIORITE, Blocks.GRANITE,
		Blocks.CLAY, Blocks.ROOTED_DIRT);

	private VillageSmoke() {
	}

	public static void init() {
		if (!ENABLED) return;
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			try {
				run(server.overworld());
			} catch (Exception e) {
				Oceania.LOGGER.error("[village] smoke failed", e);
			} finally {
				server.halt(false);
			}
		});
	}

	private static void run(ServerLevel level) {
		BlockPos spawn = level.getRespawnData().pos();
		List<BlockPos> found = StructureIndex.ordered(level, SET, HUT_VILLAGE, spawn, VILLAGES, 12);
		log("%d village(s) indexed near spawn %s", found.size(), spawn.toShortString());
		int n = 0;
		for (BlockPos at : found) {
			StructureStart start = StructureIndex.startAt(level, at, HUT_VILLAGE);
			if (start == null) {
				log("village at %s has no start", at.toShortString());
				continue;
			}
			measure(level, start, ++n);
		}
		log("DONE");
	}

	private record Building(String name, BoundingBox box) {
	}

	private static void measure(ServerLevel level, StructureStart start, int n) {
		BoundingBox box = start.getBoundingBox();
		int cx0 = SectionPos.blockToSectionCoord(box.minX()) - 1;
		int cz0 = SectionPos.blockToSectionCoord(box.minZ()) - 1;
		int cx1 = SectionPos.blockToSectionCoord(box.maxX()) + 1;
		int cz1 = SectionPos.blockToSectionCoord(box.maxZ()) + 1;
		for (int cx = cx0; cx <= cx1; cx++) {
			for (int cz = cz0; cz <= cz1; cz++) {
				level.getChunk(cx, cz);
			}
		}
		log("village %d at chunk %s: box %s, %d pieces, %d chunks generated", n, start.getChunkPos(), box,
			start.getPieces().size(), (cx1 - cx0 + 1) * (cz1 - cz0 + 1));

		ChunkGenerator gen = level.getChunkSource().getGenerator();
		RandomState rs = level.getChunkSource().randomState();
		List<Building> buildings = new ArrayList<>();
		List<BoundingBox> footprints = new ArrayList<>();
		int columns = 0;
		int floating = 0;
		int buried = 0;
		int groundTotal = 0;
		int coralTotal = 0;
		int waterTotal = 0;
		int coralBelowTotal = 0;
		int wetBuildings = 0;
		int cutBuildings = 0;
		int walledBuildings = 0;
		TreeMap<Integer, Integer> gapHist = new TreeMap<>();
		for (StructurePiece piece : start.getPieces()) {
			if (!(piece instanceof PoolElementStructurePiece pool)) continue;
			footprints.add(pool.getBoundingBox());
			String name = shortName(pool.getElement().toString());
			StructureTemplatePool.Projection projection = pool.getElement().getProjection();
			BoundingBox b = pool.getBoundingBox();
			if (name.contains("dock")) {
				dock(level, gen, rs, pool, name);
				continue;
			}
			if (projection != StructureTemplatePool.Projection.RIGID || !isBuilding(name)) continue;
			buildings.add(new Building(name, b));
			int cols = 0;
			int gapSum = 0;
			int gapMax = 0;
			int floatCols = 0;
			int buriedCols = 0;
			int onGround = 0;
			int onCoral = 0;
			int onWater = 0;
			int coralBelow = 0;
			BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
			for (int x = b.minX(); x <= b.maxX(); x++) {
				for (int z = b.minZ(); z <= b.maxZ(); z++) {
					cols++;

					BlockState under = level.getBlockState(p.set(x, b.minY() - 1, z));
					if (isCoral(under)) onCoral++;
					else if (under.getFluidState().isSource()) onWater++;
					else if (NATURAL.contains(under.getBlock())) onGround++;
					for (int y = b.minY() - 1; y >= b.minY() - 4; y--) {
						if (isCoral(level.getBlockState(p.set(x, y, z)))) {
							coralBelow++;
							break;
						}
					}
					int gap = 0;
					for (int y = b.minY() - 1; y > level.getMinY() && gap < MAX_GAP; y--) {
						BlockState s = level.getBlockState(p.set(x, y, z));
						if (s.isSolid()) break;
						gap++;
					}
					gapSum += gap;
					gapMax = Math.max(gapMax, gap);
					gapHist.merge(gap, 1, Integer::sum);
					if (gap >= 2) floatCols++;
					boolean ground = false;
					for (int y = b.minY() + pool.getGroundLevelDelta() + 1; y <= b.maxY() && !ground; y++) {
						ground = NATURAL.contains(level.getBlockState(p.set(x, y, z)).getBlock());
					}
					if (ground) buriedCols++;
				}
			}
			columns += cols;
			floating += floatCols;
			buried += buriedCols;
			groundTotal += onGround;
			coralTotal += onCoral;
			waterTotal += onWater;
			coralBelowTotal += coralBelow;

			int floor = b.minY() + pool.getGroundLevelDelta();
			int cut = 0;
			int rise = Integer.MIN_VALUE;
			int ringCols = 0;
			for (int x = b.minX() - CUT_RING; x <= b.maxX() + CUT_RING; x += 2) {
				for (int z = b.minZ() - CUT_RING; z <= b.maxZ() + CUT_RING; z += 2) {
					if (x >= b.minX() && x <= b.maxX() && z >= b.minZ() && z <= b.maxZ()) continue;
					ringCols++;
					int real = naturalTop(level, x, z, p);
					int pred = gen.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, rs) - 1;
					cut = Math.max(cut, pred - real);
					boolean near = x >= b.minX() - WALL_RING && x <= b.maxX() + WALL_RING
						&& z >= b.minZ() - WALL_RING && z <= b.maxZ() + WALL_RING;
					if (near) rise = Math.max(rise, real - floor);
				}
			}
			boolean wet = onWater > 0 || onCoral > 0;
			if (wet) wetBuildings++;
			if (cut >= 3) cutBuildings++;
			if (rise >= 2) walledBuildings++;
			log("  %-28s rot %-18s at %-18s y %d..%d delta %d  gap mean %.1f max %2d  floating %3d/%3d  ground inside %3d/%3d  under: ground %3d coral %3d water %3d other %3d, coral within 4 below %3d  ring(%d): cut %2d rise %3d%s%s%s",
				name, pool.getRotation(), b.getCenter().toShortString(), b.minY(), b.maxY(), pool.getGroundLevelDelta(),
				cols == 0 ? 0.0 : (double) gapSum / cols, gapMax, floatCols, cols, buriedCols, cols,
				onGround, onCoral, onWater, cols - onGround - onCoral - onWater, coralBelow, ringCols, cut, rise,
				wet ? "  WET" : "", cut >= 3 ? "  CUT" : "", rise >= 2 ? "  WALL" : "");
		}
		log("village %d: under the buildings' lowest rows: ground %d, coral %d, water %d, other %d of %d columns; coral within four blocks below %d (%.1f%%)",
			n, groundTotal, coralTotal, waterTotal, columns - groundTotal - coralTotal - waterTotal, columns, coralBelowTotal,
			columns == 0 ? 0.0 : 100.0 * coralBelowTotal / columns);
		log("village %d: %d of %d buildings over water or reef, %d with the beard's cut 3 or deeper within %d, %d with ground 2 or more above the floor within %d",
			n, wetBuildings, buildings.size(), cutBuildings, CUT_RING, walledBuildings, WALL_RING);
		StringBuilder hist = new StringBuilder();
		gapHist.forEach((gap, count) -> hist.append(gap).append(":").append(count).append(" "));
		log("village %d: %d buildings, %d columns, %d floating (gap >= 2, %.0f%%), %d with ground inside (%.0f%%); gap histogram %s",
			n, buildings.size(), columns, floating, columns == 0 ? 0.0 : 100.0 * floating / columns,
			buried, columns == 0 ? 0.0 : 100.0 * buried / columns, hist.toString().trim());

		int twins = 0;
		for (int i = 0; i < buildings.size(); i++) {
			for (int j = i + 1; j < buildings.size(); j++) {
				Building a = buildings.get(i);
				Building c = buildings.get(j);
				if (a.name().equals(c.name()) && a.box().inflatedBy(4).intersects(c.box())) {
					twins++;
					log("  twins: %s at %s and %s", a.name(), a.box().getCenter().toShortString(), c.box().getCenter().toShortString());
				}
			}
		}
		log("village %d: %d twin pair(s)", n, twins);

		TreeMap<Integer, Integer> errHist = new TreeMap<>();
		int samples = 0;
		int dryMismatch = 0;
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int x = box.minX() - 8; x <= box.maxX() + 8; x += 4) {
			for (int z = box.minZ() - 8; z <= box.maxZ() + 8; z += 4) {
				boolean covered = false;
				for (BoundingBox f : footprints) {
					if (x >= f.minX() && x <= f.maxX() && z >= f.minZ() && z <= f.maxZ()) {
						covered = true;
						break;
					}
				}
				if (covered) continue;
				int predFloor = gen.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, rs);
				int predSurface = gen.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, rs);
				int realFloor = groundTop(level, x, z, p) + 1;
				int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
				boolean realDry = !level.getBlockState(p.set(x, top, z)).getFluidState().isSource()
					&& !level.getBlockState(p.set(x, realFloor, z)).getFluidState().isSource();
				boolean predDry = predFloor == predSurface;
				samples++;
				errHist.merge(realFloor - predFloor, 1, Integer::sum);
				if (realDry != predDry) dryMismatch++;
			}
		}
		StringBuilder eh = new StringBuilder();
		errHist.forEach((err, count) -> eh.append(err).append(":").append(count).append(" "));
		log("village %d: prediction check on %d open columns: real floor minus predicted %s; dry/wet disagreements %d",
			n, samples, eh.toString().trim(), dryMismatch);
	}

	private static int naturalTop(ServerLevel level, int x, int z, BlockPos.MutableBlockPos p) {
		int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
		for (; y > level.getMinY(); y--) {
			if (NATURAL.contains(level.getBlockState(p.set(x, y, z)).getBlock())) return y;
		}
		return y;
	}

	private static int groundTop(ServerLevel level, int x, int z, BlockPos.MutableBlockPos p) {
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
		for (; y > level.getMinY(); y--) {
			BlockState s = level.getBlockState(p.set(x, y, z));
			if (s.isSolid() && !s.is(BlockTags.LOGS) && !s.is(BlockTags.LEAVES)) return y;
		}
		return y;
	}

	private static void dock(ServerLevel level, ChunkGenerator gen, RandomState rs, PoolElementStructurePiece pool, String name) {
		BoundingBox b = pool.getBoundingBox();
		boolean alongX = (b.maxX() - b.minX()) >= (b.maxZ() - b.minZ());
		int rows = alongX ? b.maxX() - b.minX() + 1 : b.maxZ() - b.minZ() + 1;
		int across = alongX ? b.maxZ() - b.minZ() + 1 : b.maxX() - b.minX() + 1;
		log("  dock %s: rot %s box %s, %s, rows along %s", name, pool.getRotation(), b, pool.getElement().getProjection(), alongX ? "x" : "z");
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int r = 0; r < rows; r++) {
			int blocks = 0;
			int onWater = 0;
			int onGround = 0;
			int gravel = 0;
			int predDry = 0;
			int realDry = 0;
			int cols = 0;
			int yMin = Integer.MAX_VALUE;
			int yMax = Integer.MIN_VALUE;
			for (int c = 0; c < across; c++) {
				int x = alongX ? b.minX() + r : b.minX() + c;
				int z = alongX ? b.minZ() + c : b.minZ() + r;
				cols++;
				int predFloor = gen.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, rs);
				int predSurface = gen.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, rs);
				if (predFloor == predSurface) predDry++;
				int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
				if (!level.getBlockState(p.set(x, top, z)).getFluidState().isSource()) realDry++;
				for (int y = b.maxY() + 2; y >= b.minY() - 8; y--) {
					BlockState s = level.getBlockState(p.set(x, y, z));
					if (s.is(Blocks.JUNGLE_PLANKS) || s.is(Blocks.GRAVEL)) {
						blocks++;
						if (s.is(Blocks.GRAVEL)) gravel++;
						yMin = Math.min(yMin, y);
						yMax = Math.max(yMax, y);
						BlockState under = level.getBlockState(p.set(x, y - 1, z));
						if (under.getFluidState().isSource()) onWater++;
						else if (under.isSolid()) onGround++;
						break;
					}
				}
			}
			String ys = blocks == 0 ? "-" : yMin == yMax ? String.valueOf(yMin) : yMin + ".." + yMax;
			log("    row %2d: %d deck blocks (%d gravel) y %s, %d on water, %d on ground; columns predicted dry %d/%d, really dry %d/%d",
				r, blocks, gravel, ys, onWater, onGround, predDry, cols, realDry, cols);
		}
	}

	private static boolean isCoral(BlockState state) {
		return state.is(BlockTags.CORAL_BLOCKS) || state.is(BlockTags.CORALS) || state.is(BlockTags.WALL_CORALS);
	}

	private static boolean isBuilding(String name) {
		return name.length() > 3 && Character.isDigit(name.charAt(0)) && Character.isDigit(name.charAt(1)) && name.charAt(2) == '_';
	}

	private static String shortName(String elementString) {
		Matcher m = NAME.matcher(elementString);
		return m.find() ? m.group(1) : elementString;
	}

	private static void log(String fmt, Object... args) {
		Oceania.LOGGER.info("[village] " + String.format(Locale.ROOT, fmt, args));
	}
}

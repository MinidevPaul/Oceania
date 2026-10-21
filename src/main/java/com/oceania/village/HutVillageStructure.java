package com.oceania.village;

import com.oceania.Oceania;
import com.oceania.worldgen.field.FieldFunction;
import com.oceania.worldgen.field.OceanFields;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import java.util.ArrayList;
import java.util.Comparator;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.pools.DimensionPadding;
import net.minecraft.world.level.levelgen.structure.pools.JigsawJunction;
import net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasLookup;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.JigsawBlock;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import com.oceania.mixin.village.SinglePoolElementAccessor;

public class HutVillageStructure extends Structure {
	public static final MapCodec<HutVillageStructure> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			settingsCodec(instance),
			StructureTemplatePool.CODEC.fieldOf("start_pool").forGetter(s -> s.startPool),
			Codec.intRange(0, 20).fieldOf("size").forGetter(s -> s.size),
			Codec.intRange(1, 116).fieldOf("max_distance_from_center").forGetter(s -> s.maxDistanceFromCenter),
			Codec.intRange(8, 256).fieldOf("shore_search").forGetter(s -> s.shoreSearch),
			Codec.intRange(0, 128).fieldOf("max_height_spread").forGetter(s -> s.maxHeightSpread),
			Identifier.CODEC.fieldOf("dock").forGetter(s -> s.dock),
			Codec.intRange(0, 32).fieldOf("dock_landward").forGetter(s -> s.dockLandward),
			Requirement.CODEC.listOf().fieldOf("required_buildings").forGetter(s -> s.required),
			Identifier.CODEC.fieldOf("path_link").forGetter(s -> s.pathLink),
			Identifier.CODEC.listOf().listOf().fieldOf("shore_buildings").forGetter(s -> s.shoreBuildings),
			Codec.intRange(1, 4).fieldOf("shore_ground_delta").forGetter(s -> s.shoreGroundDelta),
			Codec.intRange(1, 64).fieldOf("spacing").forGetter(s -> s.spacing),
			Codec.intRange(0, 63).fieldOf("separation").forGetter(s -> s.separation),
			Codec.INT.fieldOf("salt").forGetter(s -> s.salt))
		.apply(instance, HutVillageStructure::new));

	private static final boolean SEPARATE =
		!"false".equals(System.getProperty("oceania.village.separate", "true"));

	private static final int DOOR_REACH = 6;

	private static final boolean LAND =
		!"false".equals(System.getProperty("oceania.village.land", "true"));

	private static final boolean FACE_PATHS =
		!"false".equals(System.getProperty("oceania.village.doorfacing", "true"));

	private static final int TORCH_SPACING = 9;

	private static final Identifier TORCH_POST = Oceania.id("hut_village/torch_post");

	private static final ResourceKey<StructureTemplatePool> HUTS =
		ResourceKey.create(Registries.TEMPLATE_POOL, Oceania.id("hut_village/huts"));
	private static final int TWIN_REACH = 4;

	private static final int MIN_WATER_DEPTH = 3;

	private static final int MAX_SHORE_RISE = 6;

	private static final int FLATNESS_RANGE = 24;
	private static final int STEP = 4;

	private static final int SATELLITE_ROOM = 220;

	private static final int MAX_SITES_TRIED = 12;

	private static final int MAX_FOOTPRINT_SLOPE = 3;

	private static final int CARVE_RING = 5;
	private static final int MAX_RISE = 1;

	private static final int[] RING_RISE = {2, 2, 3, 4, 4};

	private static final int CENTRE_SLIDE = 24;
	private static final int CENTRE_STEP = 12;

	private static final int SLIDE_SHALLOWS = 6;

	private static final int HALL_REACH = 9;
	private static final int HALL_SPREAD = 3;

	private static final int HALL_SPREAD_MAX = 5;

	private static final int HALL_BAND = 12;

	private static final int MAX_REPLACED = 8;
	private static final int BED_RING_MIN = 14;
	private static final int BED_RING_MAX = 44;

	private static final int SHORE_SIDEWAYS_MIN = 6;

	private static final int SHORE_SIDEWAYS_MAX = 52;
	private static final int SHORE_INLAND_MIN = 4;

	private static final int SHORE_INLAND_MAX = 44;

	private static final int LINK_SEGMENT = 1;

	private static final int LINK_ARRIVED = 6;

	private static final int MAX_LINK_SEGMENTS = 120;

	private static final int MAX_STITCHES = 12;

	private static final int MAX_STUB_ROUNDS = 24;

	private static final int MAX_STUBS_PRUNED = 64;

	private static final int BUILDING_REACH = 1;
	private static final boolean DEBUG = Boolean.getBoolean("oceania.village.debug");

	private final Holder<StructureTemplatePool> startPool;
	private final int size;
	private final int maxDistanceFromCenter;
	private final int shoreSearch;
	private final int maxHeightSpread;
	private final Identifier dock;
	private final int dockLandward;
	private final List<Requirement> required;
	private final Identifier pathLink;
	private final List<List<Identifier>> shoreBuildings;
	private final int shoreGroundDelta;
	private final int spacing;
	private final int separation;
	private final int salt;

	public HutVillageStructure(Structure.StructureSettings settings, Holder<StructureTemplatePool> startPool, int size,
			int maxDistanceFromCenter, int shoreSearch, int maxHeightSpread, Identifier dock,
			int dockLandward, List<Requirement> required, Identifier pathLink,
			List<List<Identifier>> shoreBuildings, int shoreGroundDelta, int spacing, int separation, int salt) {
		super(settings);
		this.startPool = startPool;
		this.size = size;
		this.maxDistanceFromCenter = maxDistanceFromCenter;
		this.shoreSearch = shoreSearch;
		this.maxHeightSpread = maxHeightSpread;
		this.dock = dock;
		this.dockLandward = dockLandward;
		this.required = required;
		this.pathLink = pathLink;
		this.shoreBuildings = shoreBuildings;
		this.shoreGroundDelta = shoreGroundDelta;
		this.spacing = spacing;
		this.separation = separation;
		this.salt = salt;
	}

	private record Site(BlockPos centre, BlockPos shore, Direction toWater, int spread, int hallSpread) {

		boolean flatterThan(Site other) {
			return this.hallSpread < other.hallSpread
				|| (this.hallSpread == other.hallSpread && this.spread < other.spread);
		}
	}

	@Override
	protected Optional<Structure.GenerationStub> findGenerationPoint(Structure.GenerationContext context) {

		forgetColumns();
		ChunkPos chunkPos = context.chunkPos();
		int centreX = chunkPos.getMiddleBlockX();
		int centreZ = chunkPos.getMiddleBlockZ();

		OceanFields fields = fields(context);
		double[] island = fields.groupAt(centreX, centreZ);
		if (island == null || (int) island[3] != OceanFields.TYPE_LARGE) {
			reject(centreX, centreZ, island == null ? "open ocean" : "island type " + (int) island[3] + ", not a large island");
			return Optional.empty();
		}
		Chosen chosen = chooseSite(context, fields, island);
		if (chosen == null) {
			reject(centreX, centreZ, "no settleable spot anywhere on this island");
			return Optional.empty();
		}
		if (chosen.chunk().x() != chunkPos.x() || chosen.chunk().z() != chunkPos.z()) {
			reject(centreX, centreZ, "this island settles at chunk " + chosen.chunk().x() + "," + chosen.chunk().z());
			return Optional.empty();
		}
		Site site = chosen.site();
		Oceania.LOGGER.info("[village] {} settles at {},{}: hall ground within {}, site within {}, sea {} to the {}",
			chunkPos, site.centre().getX(), site.centre().getZ(), site.hallSpread(), site.spread(),
			site.shore().distManhattan(site.centre()), site.toWater());
		Optional<Structure.GenerationStub> village = JigsawPlacement.addPieces(
			context, this.startPool, Optional.empty(), this.size, site.centre(), false,
			Optional.of(Heightmap.Types.WORLD_SURFACE_WG), new JigsawStructure.MaxDistance(this.maxDistanceFromCenter),
			PoolAliasLookup.EMPTY, DimensionPadding.ZERO, LiquidSettings.APPLY_WATERLOGGING);
		return village.map(stub -> new Structure.GenerationStub(stub.position(), builder -> {
			long started = System.nanoTime();
			stub.generator().ifLeft(inner -> inner.accept(builder));

			separateBuildings(context, builder);

			Losses lost = groundBuildings(context, builder);

			try {
				placeShore(context, builder, site, lost);
				varyTwins(context, builder);
				stitchPaths(context, builder);

				placeTorchPosts(context, builder);
				census(context, builder);
			} finally {

				Oceania.LOGGER.info("[village] {} assembled in {} ms, {} column(s) probed",
					context.chunkPos(), (System.nanoTime() - started) / 1_000_000L, SURFACE_HEIGHTS.get().size());

				forgetColumns();
			}
		}));
	}

	private static void census(Structure.GenerationContext context, StructurePiecesBuilder builder) {
		int pieces = 0;
		int huts = 0;
		int beds = 0;
		for (StructurePiece piece : builder.build().pieces()) {
			pieces++;
			if (!(piece instanceof PoolElementStructurePiece pool)) {
				continue;
			}

			if (!isBuilding(piece)) {
				continue;
			}
			huts++;
			String id = pool.getElement().toString();
			for (String bed : BED_HUTS) {
				if (id.contains(bed)) {
					beds++;
					break;
				}
			}
		}
		Oceania.LOGGER.info("[village] {} built: {} pieces, {} buildings, {} with a bed",
			context.chunkPos(), pieces, huts, beds);
	}

	private static final String[] BED_HUTS = {"01_", "02_", "03_", "04_", "07_", "10_"};

	private record Chosen(ChunkPos chunk, Site site) {
	}

	private record IslandKey(long seed, int x, int z) {
	}

	private static final int ISLANDS_REMEMBERED = 2048;
	private static final java.util.Map<IslandKey, Optional<Chosen>> ISLANDS = java.util.Collections.synchronizedMap(
		new java.util.LinkedHashMap<>(64, 0.75F, true) {
			@Override
			protected boolean removeEldestEntry(java.util.Map.Entry<IslandKey, Optional<Chosen>> eldest) {
				return size() > ISLANDS_REMEMBERED;
			}
		});

	private static OceanFields fields(Structure.GenerationContext context) {
		return FieldFunction.fields(context.randomState());
	}

	private ChunkPos candidateOfRegion(long seed, int regionX, int regionZ) {
		WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
		random.setLargeFeatureWithSalt(seed, regionX, regionZ, this.salt);
		int bound = this.spacing - this.separation;
		return new ChunkPos(regionX * this.spacing + random.nextInt(bound), regionZ * this.spacing + random.nextInt(bound));
	}

	private Chosen chooseSite(Structure.GenerationContext context, OceanFields fields, double[] island) {
		double islandX = island[0];
		double islandZ = island[1];
		IslandKey key = new IslandKey(context.seed(), (int) islandX, (int) islandZ);
		Optional<Chosen> remembered = ISLANDS.get(key);
		if (remembered != null) {
			return remembered.orElse(null);
		}
		long started = System.nanoTime();
		Chosen chosen = chooseSiteUncached(context, fields, island);
		ISLANDS.put(key, Optional.ofNullable(chosen));
		if (chosen != null) {
			Oceania.LOGGER.info("[village] island at {},{} settles at chunk {} (centre {},{}) in {} ms: hall ground within {}, site within {}",
				(int) islandX, (int) islandZ, chosen.chunk(), chosen.site().centre().getX(), chosen.site().centre().getZ(),
				(System.nanoTime() - started) / 1_000_000L, chosen.site().hallSpread(), chosen.site().spread());
		} else {
			Oceania.LOGGER.info("[village] island at {},{} has nowhere to build ({} ms)", (int) islandX, (int) islandZ,
				(System.nanoTime() - started) / 1_000_000L);
		}
		return chosen;
	}

	private Chosen chooseSiteUncached(Structure.GenerationContext context, OceanFields fields, double[] island) {
		double islandX = island[0];
		double islandZ = island[1];

		int range = (int) island[2] + SATELLITE_ROOM;
		int fromRegion = Math.floorDiv(SectionPos.blockToSectionCoord((int) islandX - range), this.spacing);
		int toRegion = Math.floorDiv(SectionPos.blockToSectionCoord((int) islandX + range), this.spacing);
		int fromRegionZ = Math.floorDiv(SectionPos.blockToSectionCoord((int) islandZ - range), this.spacing);
		int toRegionZ = Math.floorDiv(SectionPos.blockToSectionCoord((int) islandZ + range), this.spacing);

		List<ChunkPos> candidates = new ArrayList<>();
		for (int regionX = fromRegion; regionX <= toRegion; regionX++) {
			for (int regionZ = fromRegionZ; regionZ <= toRegionZ; regionZ++) {
				candidates.add(candidateOfRegion(context.seed(), regionX, regionZ));
			}
		}
		candidates.sort(Comparator.comparingDouble(c -> distanceSquared(c, islandX, islandZ)));

		int surveyed = 0;
		Chosen best = null;
		for (ChunkPos candidate : candidates) {
			if (surveyed >= MAX_SITES_TRIED) {
				break;
			}
			int x = candidate.getMiddleBlockX();
			int z = candidate.getMiddleBlockZ();
			double[] owner = fields.groupAt(x, z);
			if (owner == null || owner[0] != islandX || owner[1] != islandZ) {
				continue;
			}
			surveyed++;
			Site site = survey(context, x, z);

			if (site == null && LAND && columnHeight(context, x, z, Heightmap.Types.OCEAN_FLOOR_WG)
					>= context.chunkGenerator().getSeaLevel() - SLIDE_SHALLOWS) {
				for (int[] offset : SLIDE_OFFSETS) {
					Site slid = survey(context, x + offset[0], z + offset[1]);
					if (slid != null && (site == null || slid.flatterThan(site))) {
						site = slid;
					}
					if (site != null && site.hallSpread() <= HALL_SPREAD) {
						break;
					}
				}
			}

			if (site != null && (best == null || site.flatterThan(best.site()))) {
				best = new Chosen(candidate, site);
			}
		}
		return best;
	}

	private static final int[][] SLIDE_OFFSETS = slideOffsets();

	private static int[][] slideOffsets() {
		List<int[]> out = new ArrayList<>();
		for (int dx = -CENTRE_SLIDE; dx <= CENTRE_SLIDE; dx += CENTRE_STEP) {
			for (int dz = -CENTRE_SLIDE; dz <= CENTRE_SLIDE; dz += CENTRE_STEP) {
				if (dx != 0 || dz != 0) {
					out.add(new int[] {dx, dz});
				}
			}
		}
		out.sort(Comparator.comparingInt((int[] o) -> o[0] * o[0] + o[1] * o[1])
			.thenComparingInt(o -> o[0]).thenComparingInt(o -> o[1]));
		return out.toArray(new int[0][]);
	}

	private static double distanceSquared(ChunkPos chunk, double x, double z) {
		double dx = chunk.getMiddleBlockX() - x;
		double dz = chunk.getMiddleBlockZ() - z;
		return dx * dx + dz * dz;
	}

	private Site survey(Structure.GenerationContext context, int centreX, int centreZ) {
		ChunkGenerator generator = context.chunkGenerator();
		int sea = generator.getSeaLevel();

		int centreFloor = columnHeight(context, centreX, centreZ, Heightmap.Types.OCEAN_FLOOR_WG) + 1;
		if (centreFloor < sea + 2) {
			reject(centreX, centreZ, "not dry land, ground at " + centreFloor + " against sea " + sea);
			return null;
		}

		int lowest = centreFloor;
		int highest = centreFloor;
		Direction nearest = null;
		int nearestDistance = Integer.MAX_VALUE;
		BlockPos nearestShore = null;

		for (Direction direction : Direction.Plane.HORIZONTAL) {
			int lastLandX = centreX;
			int lastLandZ = centreZ;
			for (int distance = STEP; distance <= this.shoreSearch; distance += STEP) {
				int x = centreX + direction.getStepX() * distance;
				int z = centreZ + direction.getStepZ() * distance;

				int floor = columnHeight(context, x, z, Heightmap.Types.OCEAN_FLOOR_WG) + 1;
				if (sea - floor >= MIN_WATER_DEPTH) {
					if (distance < nearestDistance) {
						nearestDistance = distance;
						nearest = direction;
						nearestShore = new BlockPos(lastLandX, sea, lastLandZ);
					}
					break;
				}
				lastLandX = x;
				lastLandZ = z;
				if (distance <= FLATNESS_RANGE) {
					lowest = Math.min(lowest, floor);
					highest = Math.max(highest, floor);
				}
			}
		}

		if (nearest == null) {
			reject(centreX, centreZ, "no sea within " + this.shoreSearch);
			return null;
		}

		int shoreFloor = columnHeight(context, nearestShore.getX(), nearestShore.getZ(), Heightmap.Types.OCEAN_FLOOR_WG) + 1;
		if (shoreFloor - sea > MAX_SHORE_RISE) {
			reject(centreX, centreZ, "the shore stands " + (shoreFloor - sea) + " above the water, limit "
				+ MAX_SHORE_RISE + "; this is a cliff, not a beach");
			return null;
		}
		if (highest - lowest > this.maxHeightSpread) {
			reject(centreX, centreZ, "ground rises " + (highest - lowest) + " within " + FLATNESS_RANGE
				+ ", limit " + this.maxHeightSpread + "; sea " + nearestDistance + " to the " + nearest);
			return null;
		}

		int hallLow = Integer.MAX_VALUE;
		int hallHigh = Integer.MIN_VALUE;
		int hallWet = 0;
		int hallSamples = 0;
		for (int step = 2; LAND && step >= 1; step--) {
			for (int dx = -HALL_REACH; dx <= HALL_REACH; dx += step) {
				for (int dz = -HALL_REACH; dz <= HALL_REACH; dz += step) {
					if (step == 1 && dx % 2 == 0 && dz % 2 == 0) {
						continue;
					}
					BlockPos column = new BlockPos(centreX + dx, 0, centreZ + dz);
					hallSamples++;
					if (!isDry(context, column)) {
						hallWet++;
						continue;
					}
					int ground = groundAt(context, column);
					hallLow = Math.min(hallLow, ground);
					hallHigh = Math.max(hallHigh, ground);
				}
			}
			if (hallWet > 0) {
				reject(centreX, centreZ, "the hall would stand in water: " + hallWet + " of " + hallSamples
					+ " columns wet within " + HALL_REACH);
				return null;
			}
			if (hallHigh - hallLow > HALL_SPREAD_MAX) {
				reject(centreX, centreZ, "the hall's ground rises " + (hallHigh - hallLow) + " within " + HALL_REACH
					+ ", limit " + HALL_SPREAD_MAX);
				return null;
			}
		}
		if (!LAND) {
			hallLow = 0;
			hallHigh = 0;
		}
		for (int d = HALL_REACH + 1; LAND && d <= HALL_BAND; d++) {
			int limit = hallHigh + RING_RISE[RING_RISE.length - 1];
			for (int t = -d; t <= d; t += 2) {
				if (groundAt(context, new BlockPos(centreX + t, 0, centreZ - d)) > limit
						|| groundAt(context, new BlockPos(centreX + t, 0, centreZ + d)) > limit
						|| groundAt(context, new BlockPos(centreX - d, 0, centreZ + t)) > limit
						|| groundAt(context, new BlockPos(centreX + d, 0, centreZ + t)) > limit) {
					reject(centreX, centreZ, "ground stands more than " + RING_RISE[RING_RISE.length - 1]
						+ " above the hall's square " + d + " out");
					return null;
				}
			}
		}
		if (DEBUG) {
			Oceania.LOGGER.info("[village] {} {} accepted: sea {} to the {}, ground rises {}, hall ground rises {}",
				centreX, centreZ, nearestDistance, nearest, highest - lowest, hallHigh - hallLow);
		}
		return new Site(new BlockPos(centreX, 0, centreZ), nearestShore, nearest, highest - lowest, hallHigh - hallLow);
	}

	private static void reject(int x, int z, String reason) {
		if (DEBUG) {
			Oceania.LOGGER.info("[village] {} {} refused: {}", x, z, reason);
		}
	}

	private static Rotation turn(Direction from, Direction to) {
		for (Rotation rotation : Rotation.values()) {
			if (rotation.rotate(from) == to) {
				return rotation;
			}
		}
		return Rotation.NONE;
	}

	private static Rotation facing(Direction toWater) {
		return turn(Direction.SOUTH, toWater);
	}

	private void placeShore(Structure.GenerationContext context, StructurePiecesBuilder builder, Site site, Losses lost) {
		Rotation rotation = facing(site.toWater());
		int sea = context.chunkGenerator().getSeaLevel();
		BlockPos centre = site.centre();

		List<BlockPos> linkFrom = new ArrayList<>();

		StructurePoolElement dockElement = StructurePoolElement.single(this.dock.toString(), LiquidSettings.IGNORE_WATERLOGGING)
			.apply(StructureTemplatePool.Projection.TERRAIN_MATCHING);

		boolean docked = false;
		BoundingBox reserved = null;

		int[] offsets = new int[] {this.dockLandward, this.dockLandward + 2, this.dockLandward + 4,
			this.dockLandward + 6, this.dockLandward - 2, this.dockLandward - 4, 0};

		int[] sideways = new int[] {0, 4, -4, 8, -8, 12, -12, 16, -16, 20, -20, 24, -24, 28, -28, 32, -32};
		Direction alongShore = site.toWater().getClockWise();

		for (int pass = 0; pass < 2 && !docked; pass++) {
		for (int side = 0; side < sideways.length && !docked; side++) {
		for (int which = 0; which < offsets.length && !docked; which++) {
			int back = offsets[which];
			if (back < 0) {
				continue;
			}
			BlockPos foot = site.shore().relative(alongShore, sideways[side])
				.relative(site.toWater().getOpposite(), back);
			if (pass == 0 && !isDry(context, foot)) {
				continue;
			}
			BlockPos dockOrigin = anchor(context, dockElement, rotation, foot, site.toWater().getOpposite(), sea - 1);
			BoundingBox dockBox = dockElement.getBoundingBox(context.structureTemplateManager(), dockOrigin, rotation);

			BoundingBox dockClaim = new BoundingBox(dockBox.minX(), sea - 4, dockBox.minZ(),
				dockBox.maxX(), sea + 10, dockBox.maxZ()).inflatedBy(1, 0, 1);

			if (builder.findCollisionPiece(dockClaim) == null
					&& obstacleAt(builder, dockClaim, null, true) == null) {
				builder.addPiece(new PoolElementStructurePiece(context.structureTemplateManager(), dockElement, dockOrigin, 0,
					rotation, dockClaim, LiquidSettings.IGNORE_WATERLOGGING));

				linkFrom.add(foot.relative(site.toWater().getOpposite(), 1));
				reserved = dockClaim;
				docked = true;
				Oceania.LOGGER.info("[village] {} dock seated {} blocks inland and {} along the shore, land end at y={} (sea {}){}",
					context.chunkPos(), back, sideways[side], groundAt(context, foot), sea,
					isDry(context, foot) ? "" : "  <- WET FOOTED, nowhere dry was clear");
			}
		}
		}
		}
		if (!docked) {
			Oceania.LOGGER.info("[village] {} has no dock: every landward offset collided with a piece already placed",
				context.chunkPos());
		}

		topUpRequired(context, builder, centre, linkFrom, lost);
		refill(context, builder, centre, linkFrom, lost.other());

		Direction across = site.toWater().getClockWise();
		for (List<Identifier> group : this.shoreBuildings) {
			if (group.isEmpty()) {
				continue;
			}
			Identifier id = group.get(Math.floorMod(
				context.chunkPos().x() * 31 + context.chunkPos().z(), group.size()));
			StructurePoolElement element = StructurePoolElement.single(id.toString()).apply(StructureTemplatePool.Projection.RIGID);
			BlockPos stood = tryShoreBuilding(context, builder, element, rotation, site, across, centre);
			if (stood != null) {
				linkFrom.add(stood);
			} else {
				Oceania.LOGGER.info("[village] {} found no dry, clear spot for {}, either side of the dock",
					context.chunkPos(), id);
			}
			across = across.getOpposite();
		}

		List<BoundingBox> network = jigsawPaths(builder);
		for (BlockPos from : linkFrom) {
			linkToVillage(context, builder, from, aimFor(network, from, centre), network, reserved);
		}
	}

	private Losses groundBuildings(Structure.GenerationContext context, StructurePiecesBuilder builder) {
		if (!LAND) {
			Oceania.LOGGER.info("[village] {} land test off (-PoceaniaVillageLand=false)", context.chunkPos());
			return new Losses(new int[this.required.size()], 0);
		}
		List<StructurePiece> pieces = builder.build().pieces();
		List<StructurePiece> out = new ArrayList<>(pieces.size());
		List<BoundingBox> vacated = new ArrayList<>();
		List<BoundingBox> shiftedBoxes = new ArrayList<>();
		List<Integer> shiftedBy = new ArrayList<>();
		int[] lost = new int[this.required.size()];
		int[] lostOther = {0};
		int wetBuildings = 0;
		int reseated = 0;
		int uneven = 0;
		int wetPaths = 0;
		int hallKept = 0;
		boolean hallSeen = false;
		for (StructurePiece piece : pieces) {
			if (!(piece instanceof PoolElementStructurePiece pool)) {
				out.add(piece);
				continue;
			}
			if (isPath(pool)) {
				Set<Long> own = new HashSet<>();
				columnsOf(context, pool, Blocks.GRAVEL, own);
				boolean wet = false;
				for (long key : own) {
					if (!isDry(context, new BlockPos(unpackX(key), 0, unpackZ(key)))) {
						wet = true;
						break;
					}
				}
				if (wet) {
					wetPaths++;
					continue;
				}
				out.add(piece);
				continue;
			}
			if (!isBuilding(piece) || pool.getElement().getProjection() != StructureTemplatePool.Projection.RIGID) {
				out.add(piece);
				continue;
			}

			boolean hall = !hallSeen;
			hallSeen = true;
			BoundingBox box = pool.getBoundingBox();
			String id = idOf(pool.getElement());
			if (!hall && wetColumns(context, box) > 0) {
				wetBuildings++;
				vacated.add(box);
				count(lost, lostOther, id);
				continue;
			}
			int floor = box.minY() + pool.getGroundLevelDelta();
			Under under = groundUnder(context, box);
			int slope = hall ? HALL_SPREAD_MAX : MAX_FOOTPRINT_SLOPE;
			if (seated(context, box, under, floor, slope)) {
				out.add(piece);
				continue;
			}
			int seat = under.high();
			if (!seated(context, box, under, seat, slope)) {
				if (hall) {

					hallKept++;
					Oceania.LOGGER.info("[village] {} hall {} kept on uneven ground: box {}..{} under floor {}",
						context.chunkPos(), id, under.low(), under.high(), floor);
					out.add(piece);
					continue;
				}
				uneven++;
				vacated.add(box);
				count(lost, lostOther, id);
				continue;
			}
			int dy = seat - floor;
			PoolElementStructurePiece moved = new PoolElementStructurePiece(context.structureTemplateManager(),
				pool.getElement(), pool.getPosition().offset(0, dy, 0), pool.getGroundLevelDelta(), pool.getRotation(),
				box.moved(0, dy, 0), LiquidSettings.APPLY_WATERLOGGING);

			for (JigsawJunction junction : pool.getJunctions()) {
				moved.addJunction(new JigsawJunction(junction.getSourceX(), junction.getSourceGroundY() + dy,
					junction.getSourceZ(), junction.getDeltaY(), junction.getDestProjection()));
			}
			shiftedBoxes.add(box);
			shiftedBy.add(dy);
			reseated++;
			if (DEBUG) {
				Oceania.LOGGER.info("[village] {} {} at {} re-seated by {} (ground {}..{}, floor was {})",
					context.chunkPos(), id, box.getCenter().toShortString(), dy, under.low(), under.high(), floor);
			}
			out.add(moved);
		}
		if (wetBuildings + reseated + uneven + wetPaths == 0) {
			Oceania.LOGGER.info("[village] {} land test: every building dry and level as placed, no path in the water",
				context.chunkPos());
			return new Losses(lost, lostOther[0]);
		}

		List<StructurePiece> kept = new ArrayList<>(out.size());
		int markersDropped = 0;
		int markersMoved = 0;
		for (StructurePiece piece : out) {
			if (!(piece instanceof PoolElementStructurePiece pool) || !isMarker(pool)) {
				kept.add(piece);
				continue;
			}
			BoundingBox box = piece.getBoundingBox();
			boolean gone = false;
			for (BoundingBox vacant : vacated) {
				if (vacant.isInside(box.minX(), box.minY(), box.minZ())) {
					gone = true;
					break;
				}
			}
			if (gone) {
				markersDropped++;
				continue;
			}
			int dy = 0;
			for (int i = 0; i < shiftedBoxes.size(); i++) {
				if (shiftedBoxes.get(i).isInside(box.minX(), box.minY(), box.minZ())) {
					dy = shiftedBy.get(i);
					break;
				}
			}
			if (dy == 0) {
				kept.add(piece);
				continue;
			}
			markersMoved++;
			kept.add(new PoolElementStructurePiece(context.structureTemplateManager(), pool.getElement(),
				pool.getPosition().offset(0, dy, 0), pool.getGroundLevelDelta(), pool.getRotation(),
				box.moved(0, dy, 0), LiquidSettings.APPLY_WATERLOGGING));
		}
		builder.clear();
		for (StructurePiece piece : kept) {
			builder.addPiece(piece);
		}
		Oceania.LOGGER.info("[village] {} land test: {} building(s) dropped for standing in water, {} re-seated,"
			+ " {} dropped for ground the beard would wall in, {} path piece(s) dropped out of the water;"
			+ " {} marker(s) dropped, {} moved{}", context.chunkPos(), wetBuildings, reseated, uneven, wetPaths,
			markersDropped, markersMoved, hallKept > 0 ? "; THE HALL STANDS ON GROUND THAT FAILED" : "");
		return new Losses(lost, lostOther[0]);
	}

	private record Losses(int[] byGroup, int other) {
	}

	private void count(int[] lost, int[] other, String id) {
		int group = groupOf(id);
		if (group >= 0) {
			lost[group]++;
		} else {
			other[0]++;
		}
	}

	private void refill(Structure.GenerationContext context, StructurePiecesBuilder builder, BlockPos centre,
			List<BlockPos> linkFrom, int wanted) {
		if (wanted <= 0) {
			return;
		}
		int want = Math.min(wanted, MAX_REPLACED);
		List<StructurePoolElement> draw = new ArrayList<>();
		context.registryAccess().lookupOrThrow(Registries.TEMPLATE_POOL).get(HUTS).ifPresent(pool -> {
			for (Pair<StructurePoolElement, Integer> entry : pool.value().getTemplates()) {
				StructurePoolElement element = entry.getFirst();
				String id = idOf(element);
				if (!(element instanceof SinglePoolElement) || isShore(id) || groupOf(id) >= 0
						|| element.getProjection() != StructureTemplatePool.Projection.RIGID
						|| doorwayOf(context, element) == null) {
					continue;
				}
				for (int w = 0; w < entry.getSecond(); w++) {
					draw.add(element);
				}
			}
		});
		if (draw.isEmpty()) {
			Oceania.LOGGER.info("[village] {} refill: nothing in the hut pool to draw, {} building(s) stay lost",
				context.chunkPos(), wanted);
			return;
		}
		Set<Long> paths = pathColumns(context, builder, true);
		int added = 0;
		for (int n = 0; n < want; n++) {
			StructurePoolElement element = draw.get((int) Math.floorMod(
				Mth.getSeed(centre.offset(n, 0, n)) ^ context.seed(), (long) draw.size()));

			BlockPos stood = tryNearCentre(context, builder, element, centre, n + 3, paths, doorwayOf(context, element));
			if (stood != null) {
				linkFrom.add(stood);
				added++;
			}
		}
		Oceania.LOGGER.info("[village] {} refill: the land test dropped {} building(s) of no required kind, put back {}",
			context.chunkPos(), wanted, added);
	}

	private static boolean seated(Structure.GenerationContext context, BoundingBox box, Under under, int seat, int slope) {
		return under.high() <= seat + MAX_RISE && under.low() >= seat - slope
			&& ringLevel(context, box, seat);
	}

	private static boolean ringLevel(Structure.GenerationContext context, BoundingBox box, int seat) {
		for (int d = 1; d <= CARVE_RING; d++) {
			int limit = seat + RING_RISE[d - 1];
			int x0 = box.minX() - d;
			int x1 = box.maxX() + d;
			int z0 = box.minZ() - d;
			int z1 = box.maxZ() + d;
			for (int x = x0; x <= x1; x += 2) {
				if (groundAt(context, new BlockPos(x, 0, z0)) > limit || groundAt(context, new BlockPos(x, 0, z1)) > limit) {
					return false;
				}
			}
			for (int z = z0 + 1; z < z1; z += 2) {
				if (groundAt(context, new BlockPos(x0, 0, z)) > limit || groundAt(context, new BlockPos(x1, 0, z)) > limit) {
					return false;
				}
			}
		}
		return true;
	}

	private void varyTwins(Structure.GenerationContext context, StructurePiecesBuilder builder) {
		Optional<Holder.Reference<StructureTemplatePool>> huts =
			context.registryAccess().lookupOrThrow(Registries.TEMPLATE_POOL).get(HUTS);
		if (huts.isEmpty()) {
			Oceania.LOGGER.info("[village] {} no huts pool, twins left as drawn", context.chunkPos());
			return;
		}
		List<StructurePoolElement> stock = new ArrayList<>();
		Set<String> stockIds = new HashSet<>();
		for (Pair<StructurePoolElement, Integer> entry : huts.get().value().getTemplates()) {
			StructurePoolElement element = entry.getFirst();
			String id = idOf(element);
			if (element instanceof SinglePoolElement
					&& element.getProjection() == StructureTemplatePool.Projection.RIGID
					&& !isShore(id) && doorwayOf(context, element) != null && stockIds.add(id)) {
				stock.add(element);
			}
		}
		List<StructurePiece> pieces = builder.build().pieces();
		List<StructurePiece> out = new ArrayList<>(pieces.size());
		List<PoolElementStructurePiece> seen = new ArrayList<>();
		List<BoundingBox> vacated = new ArrayList<>();
		List<StructurePiece> markers = new ArrayList<>();
		int pairs = 0;
		int redrawn = 0;
		for (int i = 0; i < pieces.size(); i++) {
			StructurePiece piece = pieces.get(i);
			if (!(piece instanceof PoolElementStructurePiece pool) || !isBuilding(piece)
					|| pool.getElement().getProjection() != StructureTemplatePool.Projection.RIGID) {
				out.add(piece);
				continue;
			}
			String id = idOf(pool.getElement());
			List<PoolElementStructurePiece> near = new ArrayList<>();
			boolean twin = false;
			for (PoolElementStructurePiece other : seen) {
				if (other.getBoundingBox().inflatedBy(TWIN_REACH).intersects(pool.getBoundingBox())) {
					near.add(other);
					twin |= idOf(other.getElement()).equals(id);
				}
			}
			PoolElementStructurePiece keep = pool;
			if (twin && stockIds.contains(id)) {
				pairs++;

				List<StructurePiece> current = new ArrayList<>(out);
				current.addAll(pieces.subList(i + 1, pieces.size()));
				Redrawn drawn = redraw(context, current, pool, id, near, stock);
				if (drawn != null) {
					keep = drawn.piece();
					markers.addAll(drawn.markers());
					vacated.add(pool.getBoundingBox());
					redrawn++;
					if (DEBUG) {
						Oceania.LOGGER.info("[village] {} twin {} at {} re-drawn as {}", context.chunkPos(), id,
							pool.getBoundingBox().getCenter().toShortString(), idOf(keep.getElement()));
					}
				}
			}
			out.add(keep);
			seen.add(keep);
		}
		if (redrawn == 0) {
			Oceania.LOGGER.info("[village] {} twins: {} found, none re-drawn", context.chunkPos(), pairs);
			return;
		}

		List<StructurePiece> kept = new ArrayList<>(out.size() + markers.size());
		int dropped = 0;
		for (StructurePiece piece : out) {
			if (piece instanceof PoolElementStructurePiece pool && isMarker(pool)) {
				BoundingBox box = piece.getBoundingBox();
				boolean inside = false;
				for (BoundingBox gone : vacated) {
					if (gone.isInside(box.minX(), box.minY(), box.minZ())) {
						inside = true;
						break;
					}
				}
				if (inside) {
					dropped++;
					continue;
				}
			}
			kept.add(piece);
		}
		kept.addAll(markers);
		builder.clear();
		for (StructurePiece piece : kept) {
			builder.addPiece(piece);
		}
		Oceania.LOGGER.info("[village] {} twins: {} found, {} re-drawn; {} marker(s) dropped with the old templates, {} placed by the new",
			context.chunkPos(), pairs, redrawn, dropped, markers.size());
	}

	private record Redrawn(PoolElementStructurePiece piece, List<StructurePiece> markers) {
	}

	private Redrawn redraw(Structure.GenerationContext context, List<StructurePiece> current, PoolElementStructurePiece old,
			String id, List<PoolElementStructurePiece> near, List<StructurePoolElement> stock) {
		Doorway oldDoor = doorwayOf(context, old.getElement());
		if (oldDoor == null) {
			return null;
		}
		BlockPos doorCell = oldDoor.cell(old.getPosition(), old.getRotation());
		Direction doorFacing = oldDoor.facing(old.getRotation());
		int groundY = old.getBoundingBox().minY() + old.getGroundLevelDelta();
		int group = groupOf(id);
		Set<String> nearIds = new HashSet<>();
		for (PoolElementStructurePiece other : near) {
			nearIds.add(idOf(other.getElement()));
		}
		List<StructurePoolElement> options = new ArrayList<>();
		for (StructurePoolElement element : stock) {
			String candidate = idOf(element);
			if (!candidate.equals(id) && !nearIds.contains(candidate) && groupOf(candidate) == group) {
				options.add(element);
			}
		}
		if (options.isEmpty()) {
			return null;
		}
		int start = (int) Math.floorMod(Mth.getSeed(old.getPosition()) ^ context.seed(), (long) options.size());
		for (int k = 0; k < options.size(); k++) {
			StructurePoolElement element = options.get((start + k) % options.size());
			Doorway door = doorwayOf(context, element);
			Rotation rotation = turn(door.front(), doorFacing);
			BlockPos origin = doorCell.subtract(StructureTemplate.transform(door.local(), Mirror.NONE, rotation, BlockPos.ZERO));
			BoundingBox box = element.getBoundingBox(context.structureTemplateManager(), origin, rotation);
			if (blocked(current, old, box)) {
				continue;
			}
			PoolElementStructurePiece piece = new PoolElementStructurePiece(context.structureTemplateManager(), element,
				origin, groundY - box.minY(), rotation, box, LiquidSettings.APPLY_WATERLOGGING);
			for (JigsawJunction junction : old.getJunctions()) {
				piece.addJunction(junction);
			}
			return new Redrawn(piece, markersFor(context, element, origin, rotation, groundY));
		}
		return null;
	}

	private static boolean blocked(List<StructurePiece> current, PoolElementStructurePiece old, BoundingBox box) {
		BoundingBox oldBox = old.getBoundingBox();
		for (StructurePiece piece : current) {
			if (piece == old) {
				continue;
			}
			BoundingBox other = piece.getBoundingBox();
			if (piece instanceof PoolElementStructurePiece pool && isMarker(pool)
					&& oldBox.isInside(other.minX(), other.minY(), other.minZ())) {
				continue;
			}
			if (isBuilding(piece)
					? other.intersects(box.minX(), box.minZ(), box.maxX(), box.maxZ())
					: other.intersects(box)) {
				return true;
			}
		}
		return false;
	}

	private static List<StructurePiece> markersFor(Structure.GenerationContext context, StructurePoolElement element,
			BlockPos origin, Rotation rotation, int groundY) {
		List<StructurePiece> markers = new ArrayList<>();
		if (!(element instanceof SinglePoolElement single)) {
			return markers;
		}
		StructureTemplate template = ((SinglePoolElementAccessor) single).oceania$template()
			.map(id -> context.structureTemplateManager().getOrCreate(id), tpl -> tpl);
		StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(rotation);
		var pools = context.registryAccess().lookupOrThrow(Registries.TEMPLATE_POOL);
		for (StructureTemplate.StructureBlockInfo info : template.filterBlocks(origin, settings, Blocks.JIGSAW)) {
			Direction front = JigsawBlock.getFrontFacing(info.state());
			if (!front.getAxis().isVertical() || info.nbt() == null) {
				continue;
			}
			Identifier poolId = Identifier.tryParse(info.nbt().getStringOr("pool", "minecraft:empty"));
			if (poolId == null) {
				continue;
			}
			Optional<Holder.Reference<StructureTemplatePool>> pool = pools.get(ResourceKey.create(Registries.TEMPLATE_POOL, poolId));
			if (pool.isEmpty()) {
				continue;
			}
			List<StructurePoolElement> draw = new ArrayList<>();
			for (Pair<StructurePoolElement, Integer> entry : pool.get().value().getTemplates()) {
				for (int w = 0; w < entry.getSecond(); w++) {
					draw.add(entry.getFirst());
				}
			}
			if (draw.isEmpty()) {
				continue;
			}
			BlockPos cell = info.pos().relative(front);
			StructurePoolElement marker = draw.get((int) Math.floorMod(Mth.getSeed(cell) ^ context.seed(), (long) draw.size()));
			BoundingBox box = marker.getBoundingBox(context.structureTemplateManager(), cell, rotation);
			markers.add(new PoolElementStructurePiece(context.structureTemplateManager(), marker, cell,
				groundY - box.minY(), rotation, box, LiquidSettings.APPLY_WATERLOGGING));
		}
		return markers;
	}

	private static String idOf(StructurePoolElement element) {
		if (element instanceof SinglePoolElement single) {
			return ((SinglePoolElementAccessor) single).oceania$template().map(Identifier::toString, tpl -> element.toString());
		}
		return element.toString();
	}

	private static boolean isMarker(PoolElementStructurePiece piece) {
		String id = piece.getElement().toString();
		return id.contains("/villager_") || id.contains("/cat_");
	}

	private int groupOf(String id) {
		for (int g = 0; g < this.required.size(); g++) {
			for (Identifier kind : this.required.get(g).buildings()) {
				if (kind.toString().equals(id)) {
					return g;
				}
			}
		}
		return -1;
	}

	private boolean isShore(String id) {
		for (List<Identifier> group : this.shoreBuildings) {
			for (Identifier kind : group) {
				if (kind.toString().equals(id)) {
					return true;
				}
			}
		}
		return false;
	}

	private record Requirement(List<Identifier> buildings, int minimum) {
		static final Codec<Requirement> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Identifier.CODEC.listOf().fieldOf("buildings").forGetter(Requirement::buildings),
				Codec.intRange(0, 8).fieldOf("minimum").forGetter(Requirement::minimum))
			.apply(instance, Requirement::new));
	}

	private void topUpRequired(Structure.GenerationContext context, StructurePiecesBuilder builder,
			BlockPos centre, List<BlockPos> linkFrom, Losses lost) {

		Set<Long> paths = pathColumns(context, builder, true);
		for (int g = 0; g < this.required.size(); g++) {
			Requirement want = this.required.get(g);
			if (want.buildings().isEmpty()) {
				continue;
			}
			int have = 0;
			for (StructurePiece piece : builder.build().pieces()) {
				if (piece instanceof PoolElementStructurePiece pool) {
					String id = pool.getElement().toString();
					for (Identifier kind : want.buildings()) {
						if (id.contains(kind.getPath())) {
							have++;
							break;
						}
					}
				}
			}
			String label = want.buildings().get(0).getPath();

			int target = Math.max(want.minimum(), have + Math.min(lost.byGroup()[g], MAX_REPLACED));
			if (have >= target) {
				Oceania.LOGGER.info("[village] {} has {} of {}, no top-up needed",
					context.chunkPos(), have, label);
				continue;
			}
			int added = 0;
			for (int n = 0; n < target - have; n++) {
				Identifier id = want.buildings().get(n % want.buildings().size());
				StructurePoolElement element = StructurePoolElement.single(id.toString())
					.apply(StructureTemplatePool.Projection.RIGID);
				BlockPos stood = tryNearCentre(context, builder, element, centre, n + have,
					paths, doorwayOf(context, element));
				if (stood != null) {
					linkFrom.add(stood);
					added++;
				}
			}
			Oceania.LOGGER.info("[village] {} had {} of {} ({} dropped by the land test), added {} to reach {}",
				context.chunkPos(), have, label, lost.byGroup()[g], added, target);
		}
	}

	private Rotation[] rankRotations(Structure.GenerationContext context, StructurePoolElement element,
			BlockPos spot, int bottom, Direction away, BlockPos centre, Set<Long> paths, Doorway doorway) {
		Rotation[] order = Rotation.values().clone();
		if (!FACE_PATHS || doorway == null) {
			return new Rotation[] {Rotation.NONE};
		}
		int[] ahead = new int[order.length];
		long[] toCentre = new long[order.length];
		for (int r = 0; r < order.length; r++) {
			BlockPos probe = anchor(context, element, order[r], spot.atY(bottom), away, bottom);
			BlockPos door = doorway.cell(probe, order[r]);
			int found = pathAhead(paths, door, doorway.facing(order[r]), DOOR_REACH);
			ahead[r] = found < 0 ? Integer.MAX_VALUE : found;
			toCentre[r] = Math.abs((long) door.getX() - centre.getX())
				+ Math.abs((long) door.getZ() - centre.getZ());
		}
		for (int a = 1; a < order.length; a++) {
			for (int b = a; b > 0 && (ahead[b] < ahead[b - 1]
					|| (ahead[b] == ahead[b - 1] && toCentre[b] < toCentre[b - 1])); b--) {
				Rotation swapR = order[b];
				order[b] = order[b - 1];
				order[b - 1] = swapR;
				int swapA = ahead[b];
				ahead[b] = ahead[b - 1];
				ahead[b - 1] = swapA;
				long swapC = toCentre[b];
				toCentre[b] = toCentre[b - 1];
				toCentre[b - 1] = swapC;
			}
		}
		return order;
	}

	private record Doorway(BlockPos local, Direction front) {

		BlockPos cell(BlockPos origin, Rotation rotation) {
			return StructureTemplate.transform(this.local, Mirror.NONE, rotation, BlockPos.ZERO).offset(origin);
		}

		Direction facing(Rotation rotation) {
			return rotation.rotate(this.front);
		}
	}

	private static Doorway doorwayOf(Structure.GenerationContext context, StructurePoolElement element) {
		if (!(element instanceof SinglePoolElement single)) {
			return null;
		}
		StructureTemplate template = ((SinglePoolElementAccessor) single).oceania$template()
			.map(id -> context.structureTemplateManager().getOrCreate(id), tpl -> tpl);
		StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(Rotation.NONE);
		for (StructureTemplate.StructureBlockInfo info
				: template.filterBlocks(BlockPos.ZERO, settings, Blocks.JIGSAW)) {
			Direction front = JigsawBlock.getFrontFacing(info.state());
			if (front.getAxis().isHorizontal()) {
				return new Doorway(info.pos(), front);
			}
		}
		return null;
	}

	private static int pathAhead(Set<Long> paths, BlockPos door, Direction face, int limit) {
		Direction side = face.getClockWise();
		for (int ahead = 1; ahead <= limit; ahead++) {
			BlockPos on = door.relative(face, ahead);
			for (int lateral = 0; lateral <= ahead; lateral++) {
				if (paths.contains(pack(on.getX() + side.getStepX() * lateral,
						on.getZ() + side.getStepZ() * lateral))
					|| paths.contains(pack(on.getX() - side.getStepX() * lateral,
						on.getZ() - side.getStepZ() * lateral))) {
					return ahead;
				}
			}
		}
		return -1;
	}

	private record Under(int low, int high) {
		int spread() {
			return this.high - this.low;
		}
	}

	private static Under groundUnder(Structure.GenerationContext context, BoundingBox box) {
		int low = Integer.MAX_VALUE;
		int high = Integer.MIN_VALUE;
		for (int x = box.minX(); x <= box.maxX(); x++) {
			for (int z = box.minZ(); z <= box.maxZ(); z++) {
				int ground = groundAt(context, new BlockPos(x, 0, z));
				low = Math.min(low, ground);
				high = Math.max(high, ground);
			}
		}
		return new Under(low, high);
	}

	private BlockPos tryNearCentre(Structure.GenerationContext context, StructurePiecesBuilder builder,
			StructurePoolElement element, BlockPos centre, int nudge, Set<Long> paths, Doorway doorway) {
		int rejectedWet = 0;
		int rejectedSlope = 0;
		int rejectedHit = 0;
		int rejectedRise = 0;
		int flattest = Integer.MAX_VALUE;

		int[][] spokes = {{0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}};
		for (int out = BED_RING_MIN; out <= BED_RING_MAX; out += 3) {
			for (int s = 0; s < spokes.length; s++) {
				int[] spoke = spokes[(s + nudge) % spokes.length];
				BlockPos spot = centre.offset(spoke[0] * out + spoke[1] * nudge * 2,
					0, spoke[1] * out - spoke[0] * nudge * 2);
				int ground = groundAt(context, spot);

				if (!isDry(context, spot)) {
					continue;
				}
				int bottom = ground - this.shoreGroundDelta;
				Direction away = Math.abs(spoke[0]) >= Math.abs(spoke[1])
					? (spoke[0] >= 0 ? Direction.EAST : Direction.WEST)
					: (spoke[1] >= 0 ? Direction.SOUTH : Direction.NORTH);

				Rotation[] order = rankRotations(context, element, spot, bottom, away, centre, paths, doorway);
				for (Rotation rotation : order) {
					BlockPos origin = anchor(context, element, rotation, spot.atY(bottom), away, bottom);
					BoundingBox box = element.getBoundingBox(context.structureTemplateManager(), origin, rotation);

					if (FACE_PATHS ? wetColumns(context, box) > 0 : !isDryFootprint(context, box)) {
						rejectedWet++;
						continue;
					}

					Under under = groundUnder(context, box);
					flattest = Math.min(flattest, under.spread());
					if (under.spread() > MAX_FOOTPRINT_SLOPE) {
						rejectedSlope++;
						continue;
					}

					if (FACE_PATHS && under.high() != ground) {
						int seat = under.high() - this.shoreGroundDelta;
						origin = origin.atY(seat);
						box = element.getBoundingBox(context.structureTemplateManager(), origin, rotation);
					}

					if (obstacleAt(builder, box.inflatedBy(1, 0, 1), null, true) != null) {
						rejectedHit++;
						continue;
					}

					if (LAND && !ringLevel(context, box, box.minY() + this.shoreGroundDelta)) {
						rejectedRise++;
						continue;
					}
					builder.addPiece(new PoolElementStructurePiece(context.structureTemplateManager(), element,
						origin, this.shoreGroundDelta, rotation, box, LiquidSettings.APPLY_WATERLOGGING));

					if (doorway == null) {
						return spot.atY(ground);
					}
					BlockPos door = doorway.cell(origin, rotation);
					Direction front = doorway.facing(rotation);
					BlockPos stepOut = door.relative(front);
					Oceania.LOGGER.info("[village] {} top-up seated at {},{} rotation {}, door {},{} facing {},"
						+ " path {} ahead", context.chunkPos(), spot.getX(), spot.getZ(), rotation,
						door.getX(), door.getZ(), front, pathAhead(paths, door, front, DOOR_REACH));
					return stepOut.atY(groundAt(context, stepOut));
				}
			}
		}
		Oceania.LOGGER.info("[village] {} found no spot near the centre: {} wet, {} too steep (flattest was {}),"
			+ " {} collided, {} walled in by the ground round them", context.chunkPos(), rejectedWet, rejectedSlope,
			flattest == Integer.MAX_VALUE ? -1 : flattest, rejectedHit, rejectedRise);
		return null;
	}

	private static Set<Long> pathColumns(Structure.GenerationContext context, StructurePiecesBuilder builder,
			boolean pathOnly) {
		Set<Long> columns = new HashSet<>();
		for (StructurePiece piece : builder.build().pieces()) {
			if (!(piece instanceof PoolElementStructurePiece pool) || (pathOnly && !isPath(pool))) {
				continue;
			}

			columnsOf(context, pool, Blocks.GRAVEL, columns);
		}
		return columns;
	}

	private static Set<Long> doorColumns(Structure.GenerationContext context, StructurePiecesBuilder builder) {
		Set<Long> columns = new HashSet<>();
		for (StructurePiece piece : builder.build().pieces()) {
			if (!(piece instanceof PoolElementStructurePiece pool) || isPath(pool)) {
				continue;
			}

			columnsOf(context, pool, Blocks.JIGSAW, columns);
		}
		return columns;
	}

	private static void columnsOf(Structure.GenerationContext context, PoolElementStructurePiece pool,
			Block block, Set<Long> into) {
		if (!(pool.getElement() instanceof SinglePoolElement single)) {
			return;
		}
		StructureTemplate template = ((SinglePoolElementAccessor) single).oceania$template()
			.map(id -> context.structureTemplateManager().getOrCreate(id), tpl -> tpl);
		StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(pool.getRotation());
		for (StructureTemplate.StructureBlockInfo info : template.filterBlocks(pool.getPosition(), settings, block)) {
			into.add(pack(info.pos().getX(), info.pos().getZ()));
		}
	}

	private static long pack(int x, int z) {
		return ((long) x << 32) | (z & 0xFFFFFFFFL);
	}

	private static int unpackX(long key) {
		return (int) (key >> 32);
	}

	private static int unpackZ(long key) {
		return (int) key;
	}

	private void stitchPaths(Structure.GenerationContext context, StructurePiecesBuilder builder) {
		Set<Long> columns = pathColumns(context, builder, true);
		if (columns.isEmpty()) {

			Oceania.LOGGER.info("[village] {} has no path of any kind: nothing to stitch or repair",
				context.chunkPos());
			return;
		}
		List<Set<Long>> runs = new ArrayList<>();
		Set<Long> todo = new HashSet<>(columns);
		while (!todo.isEmpty()) {
			long seed = todo.iterator().next();
			todo.remove(seed);
			Set<Long> run = new HashSet<>();
			List<Long> stack = new ArrayList<>();
			stack.add(seed);
			run.add(seed);
			while (!stack.isEmpty()) {
				long at = stack.remove(stack.size() - 1);
				int x = unpackX(at);
				int z = unpackZ(at);
				int here = groundAt(context, new BlockPos(x, 0, z));
				for (Direction step : Direction.Plane.HORIZONTAL) {
					int nx = x + step.getStepX();
					int nz = z + step.getStepZ();
					long next = pack(nx, nz);
					if (!todo.contains(next)) {
						continue;
					}

					if (Math.abs(groundAt(context, new BlockPos(nx, 0, nz)) - here) > 1) {
						continue;
					}
					todo.remove(next);
					run.add(next);
					stack.add(next);
				}
			}
			runs.add(run);
		}

		if (runs.size() <= 1) {
			Oceania.LOGGER.info("[village] {} path network is already one run of {} blocks",
				context.chunkPos(), columns.size());
		} else {
			runs.sort((a, b) -> Integer.compare(b.size(), a.size()));
			Set<Long> main = new HashSet<>(runs.get(0));
			int joined = 0;
			for (int n = 1; n < runs.size() && n <= MAX_STITCHES; n++) {
				Set<Long> run = runs.get(n);
				List<BoundingBox> target = new ArrayList<>();
				for (long key : main) {
					int x = unpackX(key);
					int z = unpackZ(key);
					target.add(new BoundingBox(x, context.chunkGenerator().getMinY(), z,
						x, context.chunkGenerator().getMinY(), z));
				}
				long best = run.iterator().next();
				long bestDistance = Long.MAX_VALUE;
				for (long from : run) {
					for (long to : main) {
						long dx = unpackX(from) - (long) unpackX(to);
						long dz = unpackZ(from) - (long) unpackZ(to);
						long d = dx * dx + dz * dz;
						if (d < bestDistance) {
							bestDistance = d;
							best = from;
						}
					}
				}
				BlockPos from = new BlockPos(unpackX(best), 0, unpackZ(best));
				from = from.atY(groundAt(context, from));
				linkToVillage(context, builder, from, aimFor(target, from, from), target, null);
				main.addAll(run);
				joined++;
			}
			Oceania.LOGGER.info("[village] {} path network was in {} runs ({} blocks); joined {}",
				context.chunkPos(), runs.size(), columns.size(), joined);
		}
		repair(context, builder);
	}

	private record Repairs(int runGaps, int doorGaps, int stubPieces, int stubColumns) {
		Repairs plus(Repairs other) {
			return new Repairs(this.runGaps + other.runGaps, this.doorGaps + other.doorGaps,
				this.stubPieces + other.stubPieces, this.stubColumns + other.stubColumns);
		}
	}

	private Repairs repair(Structure.GenerationContext context, StructurePiecesBuilder builder) {
		Set<Long> paths = pathColumns(context, builder, true);
		Set<Long> buildings = pathColumns(context, builder, false);
		buildings.removeAll(paths);
		Repairs repairs = fillGaps(context, builder, paths, buildings, doorColumns(context, builder));

		repairs = repairs.plus(pruneStubs(context, builder, pathColumns(context, builder, true)));
		Oceania.LOGGER.info("[village] {} path repair: {} run gap(s) filled, {} doorway gap(s) filled,"
			+ " {} stub column(s) removed in {} piece(s)", context.chunkPos(), repairs.runGaps(),
			repairs.doorGaps(), repairs.stubColumns(), repairs.stubPieces());
		return repairs;
	}

	private Repairs fillGaps(Structure.GenerationContext context, StructurePiecesBuilder builder,
			Set<Long> paths, Set<Long> buildings, Set<Long> doors) {
		StructurePoolElement segment = StructurePoolElement.single(this.pathLink.toString())
			.apply(StructureTemplatePool.Projection.TERRAIN_MATCHING);
		Set<Long> paved = new HashSet<>(paths);
		paved.addAll(buildings);
		Set<Long> bare = new HashSet<>();
		for (long key : paved) {
			for (Direction step : Direction.Plane.HORIZONTAL) {
				long next = pack(unpackX(key) + step.getStepX(), unpackZ(key) + step.getStepZ());
				if (!paved.contains(next)) {
					bare.add(next);
				}
			}
		}
		int runGaps = 0;
		int doorGaps = 0;
		for (long key : bare) {
			int x = unpackX(key);
			int z = unpackZ(key);
			BlockPos here = new BlockPos(x, 0, z);
			if (LAND && !isDry(context, here)) {
				continue;
			}
			int ground = groundAt(context, here);

			if (doors.contains(key)) {
				boolean joinsAPath = false;
				for (Direction step : Direction.Plane.HORIZONTAL) {
					BlockPos side = here.relative(step);

					if (paths.contains(pack(side.getX(), side.getZ()))
							&& Math.abs(groundAt(context, side) - ground) <= 1) {
						joinsAPath = true;
						break;
					}
				}

				if (!joinsAPath) {
					continue;
				}
				lay(context, builder, segment, here.atY(ground), ground);
				paths.add(key);
				paved.add(key);
				doorGaps++;
				continue;
			}
			boolean acrossX = paved.contains(pack(x + 1, z)) && paved.contains(pack(x - 1, z));
			boolean acrossZ = paved.contains(pack(x, z + 1)) && paved.contains(pack(x, z - 1));
			if (!acrossX && !acrossZ) {
				continue;
			}
			BlockPos a = acrossX ? new BlockPos(x + 1, 0, z) : new BlockPos(x, 0, z + 1);
			BlockPos b = acrossX ? new BlockPos(x - 1, 0, z) : new BlockPos(x, 0, z - 1);

			if (Math.abs(groundAt(context, a) - ground) > 1 || Math.abs(groundAt(context, b) - ground) > 1) {
				continue;
			}
			lay(context, builder, segment, here.atY(ground), ground);
			paths.add(key);
			paved.add(key);
			runGaps++;
		}
		return new Repairs(runGaps, doorGaps, 0, 0);
	}

	private void lay(Structure.GenerationContext context, StructurePiecesBuilder builder,
			StructurePoolElement segment, BlockPos at, int ground) {
		BlockPos origin = anchor(context, segment, Rotation.NONE, at, Direction.NORTH, ground);
		BoundingBox box = segment.getBoundingBox(context.structureTemplateManager(), origin, Rotation.NONE);
		builder.addPiece(new PoolElementStructurePiece(context.structureTemplateManager(), segment, origin, 0,
			Rotation.NONE, box, LiquidSettings.APPLY_WATERLOGGING));
	}

	private static Repairs pruneStubs(Structure.GenerationContext context, StructurePiecesBuilder builder,
			Set<Long> paths) {
		List<BoundingBox> buildings = new ArrayList<>();
		for (StructurePiece piece : builder.build().pieces()) {

			if (piece instanceof PoolElementStructurePiece pool && !isPath(pool)) {
				buildings.add(piece.getBoundingBox());
			}
		}
		Set<Long> live = new HashSet<>(paths);
		Set<Long> stubs = new HashSet<>();
		for (int round = 0; round < MAX_STUB_ROUNDS; round++) {
			Set<Long> tips = new HashSet<>();
			for (long key : live) {
				if (atBuilding(buildings, key)) {
					continue;
				}
				int neighbours = 0;
				for (Direction step : Direction.Plane.HORIZONTAL) {
					if (live.contains(pack(unpackX(key) + step.getStepX(), unpackZ(key) + step.getStepZ()))) {
						neighbours++;
					}
				}
				if (neighbours <= 1) {
					tips.add(key);
				}
			}
			if (tips.isEmpty()) {
				break;
			}

			live.removeAll(tips);
			stubs.addAll(tips);
		}
		if (stubs.isEmpty()) {
			return new Repairs(0, 0, 0, 0);
		}
		List<StructurePiece> keep = new ArrayList<>();
		int dropped = 0;
		int droppedColumns = 0;
		for (StructurePiece piece : builder.build().pieces()) {
			if (piece instanceof PoolElementStructurePiece pool && isPath(pool) && dropped < MAX_STUBS_PRUNED) {
				Set<Long> own = new HashSet<>();
				columnsOf(context, pool, Blocks.GRAVEL, own);
				if (!own.isEmpty() && stubs.containsAll(own)) {
					dropped++;
					droppedColumns += own.size();
					continue;
				}
			}
			keep.add(piece);
		}
		if (dropped > 0) {
			builder.clear();
			for (StructurePiece piece : keep) {
				builder.addPiece(piece);
			}
		}
		return new Repairs(0, 0, dropped, droppedColumns);
	}

	private static boolean atBuilding(List<BoundingBox> buildings, long key) {
		int x = unpackX(key);
		int z = unpackZ(key);
		for (BoundingBox box : buildings) {
			if (x >= box.minX() - BUILDING_REACH && x <= box.maxX() + BUILDING_REACH
					&& z >= box.minZ() - BUILDING_REACH && z <= box.maxZ() + BUILDING_REACH) {
				return true;
			}
		}
		return false;
	}

	private static List<BoundingBox> jigsawPaths(StructurePiecesBuilder builder) {
		List<BoundingBox> out = new ArrayList<>();
		for (StructurePiece piece : builder.build().pieces()) {
			if (piece instanceof PoolElementStructurePiece pool && isPath(pool)) {
				out.add(piece.getBoundingBox());
			}
		}
		return out;
	}

	private static boolean arrived(List<BoundingBox> network, BlockPos at) {
		for (BoundingBox box : network) {
			if (at.getX() >= box.minX() && at.getX() <= box.maxX()
					&& at.getZ() >= box.minZ() && at.getZ() <= box.maxZ()) {
				return true;
			}
		}
		return false;
	}

	private static boolean isPath(PoolElementStructurePiece piece) {
		return piece.getElement().toString().contains("/path_");
	}

	private static boolean isFurniture(PoolElementStructurePiece piece) {
		String id = piece.getElement().toString();
		return id.contains("/path_") || id.contains("/villager_") || id.contains("/cat_")
			|| id.contains("/torch_");
	}

	private static boolean isBuilding(StructurePiece piece) {
		return piece instanceof PoolElementStructurePiece pool && !isFurniture(pool);
	}

	private static BlockPos aimFor(List<BoundingBox> network, BlockPos from, BlockPos fallback) {
		BoundingBox best = null;
		long bestDistance = Long.MAX_VALUE;
		for (BoundingBox box : network) {
			long dx = (box.minX() + box.maxX()) / 2L - from.getX();
			long dz = (box.minZ() + box.maxZ()) / 2L - from.getZ();
			long distance = dx * dx + dz * dz;
			if (distance < bestDistance) {
				bestDistance = distance;
				best = box;
			}
		}
		if (best == null) {
			return fallback;
		}
		return new BlockPos((best.minX() + best.maxX()) / 2, fallback.getY(), (best.minZ() + best.maxZ()) / 2);
	}

	private static BoundingBox footprintClash(List<BoundingBox> claimed, BoundingBox box) {
		for (BoundingBox claim : claimed) {
			if (claim.intersects(box.minX(), box.minZ(), box.maxX(), box.maxZ())) {
				return claim;
			}
		}
		return null;
	}

	private static void separateBuildings(Structure.GenerationContext context, StructurePiecesBuilder builder) {
		if (!SEPARATE) {
			Oceania.LOGGER.info("[village] {} footprint rule off (-PoceaniaVillageSeparate=false)",
				context.chunkPos());
			return;
		}
		List<StructurePiece> pieces = builder.build().pieces();
		boolean[] drop = new boolean[pieces.size()];
		List<BoundingBox> claimed = new ArrayList<>();
		List<BoundingBox> lost = new ArrayList<>();
		for (int n = 0; n < pieces.size(); n++) {
			StructurePiece piece = pieces.get(n);
			if (!isBuilding(piece)) {
				continue;
			}
			BoundingBox box = piece.getBoundingBox();
			if (footprintClash(claimed, box) == null) {
				claimed.add(box);
			} else {
				drop[n] = true;
				lost.add(box);
			}
		}
		if (lost.isEmpty()) {
			Oceania.LOGGER.info("[village] {} no building stands on another: {} footprint(s) claimed",
				context.chunkPos(), claimed.size());
			return;
		}

		int markers = 0;
		for (int n = 0; n < pieces.size(); n++) {
			StructurePiece piece = pieces.get(n);
			if (drop[n] || isBuilding(piece) || !(piece instanceof PoolElementStructurePiece pool)
					|| isPath(pool)) {
				continue;
			}
			BoundingBox box = piece.getBoundingBox();
			for (BoundingBox gone : lost) {
				if (gone.isInside(box.minX(), box.minY(), box.minZ())) {
					drop[n] = true;
					markers++;
					break;
				}
			}
		}
		List<StructurePiece> keep = new ArrayList<>(pieces.size());
		for (int n = 0; n < pieces.size(); n++) {
			if (!drop[n]) {
				keep.add(pieces.get(n));
			}
		}
		builder.clear();
		for (StructurePiece piece : keep) {
			builder.addPiece(piece);
		}
		Oceania.LOGGER.info("[village] {} separated: dropped {} building(s) standing on ground already"
			+ " claimed and {} marker(s) inside them, {} footprint(s) kept",
			context.chunkPos(), lost.size(), markers, claimed.size());
	}

	private static StructurePiece obstacleAt(StructurePiecesBuilder builder, BoundingBox box,
			BoundingBox reserved, boolean asBuilding) {

		for (StructurePiece piece : builder.build().pieces()) {
			BoundingBox standing = piece.getBoundingBox();

			if (reserved != null && standing.equals(reserved)) {
				continue;
			}

			if (!standing.intersects(box.minX(), box.minZ(), box.maxX(), box.maxZ())) {
				continue;
			}

			if (asBuilding && SEPARATE && isBuilding(piece)) {
				return piece;
			}
			if (!standing.intersects(box)) {
				continue;
			}
			if (piece instanceof PoolElementStructurePiece pool && isPath(pool)) {
				continue;
			}
			return piece;
		}
		return null;
	}

	private static boolean isDry(Structure.GenerationContext context, BlockPos pos) {
		return columnHeight(context, pos.getX(), pos.getZ(), Heightmap.Types.WORLD_SURFACE_WG)
			== columnHeight(context, pos.getX(), pos.getZ(), Heightmap.Types.OCEAN_FLOOR_WG);
	}

	private static final ThreadLocal<Long2IntMap> SURFACE_HEIGHTS =
		ThreadLocal.withInitial(HutVillageStructure::newHeightCache);
	private static final ThreadLocal<Long2IntMap> FLOOR_HEIGHTS =
		ThreadLocal.withInitial(HutVillageStructure::newHeightCache);

	private static Long2IntMap newHeightCache() {
		Long2IntOpenHashMap map = new Long2IntOpenHashMap();
		map.defaultReturnValue(Integer.MIN_VALUE);
		return map;
	}

	private static void forgetColumns() {
		SURFACE_HEIGHTS.get().clear();
		FLOOR_HEIGHTS.get().clear();
	}

	private static int columnHeight(Structure.GenerationContext context, int x, int z, Heightmap.Types type) {
		Long2IntMap cache = type == Heightmap.Types.WORLD_SURFACE_WG
			? SURFACE_HEIGHTS.get() : FLOOR_HEIGHTS.get();
		long key = pack(x, z);
		int known = cache.get(key);
		if (known != Integer.MIN_VALUE) {
			return known;
		}

		NoiseColumn column = context.chunkGenerator().getBaseColumn(x, z, context.heightAccessor(), context.randomState());
		int top = context.heightAccessor().getMaxY();
		int bottom = context.heightAccessor().getMinY();
		int surface = bottom;
		int floor = bottom;
		var surfaceTest = Heightmap.Types.WORLD_SURFACE_WG.isOpaque();
		var floorTest = Heightmap.Types.OCEAN_FLOOR_WG.isOpaque();
		boolean surfaceFound = false;
		for (int y = top; y >= bottom; y--) {
			BlockState state = column.getBlock(y);
			if (!surfaceFound && surfaceTest.test(state)) {
				surface = y;
				surfaceFound = true;
			}
			if (floorTest.test(state)) {
				floor = y;
				break;
			}
		}
		SURFACE_HEIGHTS.get().put(key, surface);
		FLOOR_HEIGHTS.get().put(key, floor);
		return type == Heightmap.Types.WORLD_SURFACE_WG ? surface : floor;
	}

	private static int wetColumns(Structure.GenerationContext context, BoundingBox box) {
		int wet = 0;
		for (int x = box.minX(); x <= box.maxX(); x++) {
			for (int z = box.minZ(); z <= box.maxZ(); z++) {
				if (!isDry(context, new BlockPos(x, 0, z))) {
					wet++;
				}
			}
		}
		return wet;
	}

	private static boolean isDryFootprint(Structure.GenerationContext context, BoundingBox box) {
		int columns = (box.maxX() - box.minX() + 1) * (box.maxZ() - box.minZ() + 1);
		return wetColumns(context, box) * 20 <= columns;
	}

	private static int groundAt(Structure.GenerationContext context, BlockPos pos) {
		return columnHeight(context, pos.getX(), pos.getZ(), Heightmap.Types.WORLD_SURFACE_WG);
	}

	private void linkToVillage(Structure.GenerationContext context, StructurePiecesBuilder builder,
			BlockPos from, BlockPos centre, List<BoundingBox> network, BoundingBox reserved) {
		StructurePoolElement segment = StructurePoolElement.single(this.pathLink.toString())
			.apply(StructureTemplatePool.Projection.TERRAIN_MATCHING);
		BlockPos at = from;
		int laid = 0;
		int skipped = 0;
		for (int step = 0; step < MAX_LINK_SEGMENTS; step++) {
			int dx = centre.getX() - at.getX();
			int dz = centre.getZ() - at.getZ();

			boolean done = network.isEmpty()
				? Math.abs(dx) + Math.abs(dz) <= LINK_ARRIVED
				: arrived(network, at);
			if (done) {
				break;
			}

			Direction straight = Math.abs(dx) >= Math.abs(dz)
				? (dx > 0 ? Direction.EAST : Direction.WEST)
				: (dz > 0 ? Direction.SOUTH : Direction.NORTH);
			Direction sideways = Math.abs(dx) >= Math.abs(dz)
				? (dz > 0 ? Direction.SOUTH : dz < 0 ? Direction.NORTH : null)
				: (dx > 0 ? Direction.EAST : dx < 0 ? Direction.WEST : null);
			Direction towards = straight;
			if (sideways != null) {
				int here = groundAt(context, at);
				if (Math.abs(groundAt(context, at.relative(sideways, LINK_SEGMENT)) - here)
						< Math.abs(groundAt(context, at.relative(straight, LINK_SEGMENT)) - here)) {
					towards = sideways;
				}
			}

			if (LAND && !isDry(context, at)) {
				skipped++;
				at = at.relative(towards, 1);
				continue;
			}
			Rotation towardsRotation = facing(towards);
			int ground = groundAt(context, at);
			BlockPos origin = anchor(context, segment, towardsRotation, at.atY(ground), towards.getOpposite(), ground);
			BoundingBox box = segment.getBoundingBox(context.structureTemplateManager(), origin, towardsRotation);

			StructurePiece obstacle = obstacleAt(builder, box, reserved, false);
			if (obstacle == null) {
				builder.addPiece(new PoolElementStructurePiece(context.structureTemplateManager(), segment, origin, 0,
					towardsRotation, box, LiquidSettings.APPLY_WATERLOGGING));
				laid++;
				at = at.relative(towards, LINK_SEGMENT);
				continue;
			}

			skipped++;
			BoundingBox wall = obstacle.getBoundingBox();
			int clear = switch (towards) {
				case EAST -> wall.maxX() + 1 - at.getX();
				case WEST -> at.getX() - wall.minX() + 1;
				case SOUTH -> wall.maxZ() + 1 - at.getZ();
				default -> at.getZ() - wall.minZ() + 1;
			};
			at = at.relative(towards, Math.max(1, clear));
		}

		Oceania.LOGGER.info("[village] path link from {},{} to {},{}: {} laid, {} skipped ({}% of the walk laid nothing)",
			from.getX(), from.getZ(), centre.getX(), centre.getZ(), laid, skipped,
			100 * skipped / Math.max(1, laid + skipped));
	}

	private BlockPos tryShoreBuilding(Structure.GenerationContext context, StructurePiecesBuilder builder,
			StructurePoolElement element, Rotation rotation, Site site, Direction across, BlockPos centre) {
		ChunkGenerator generator = context.chunkGenerator();
		int rejectedAnchor = 0;
		int rejectedFootprint = 0;
		int rejectedCollision = 0;
		int rejectedUneven = 0;
		int driest = Integer.MAX_VALUE;
		String blockedBy = "nothing";

		for (int pass = LAND ? 0 : 1; pass < 2; pass++) {
		for (Direction side : new Direction[] {across, across.getOpposite()}) {

		for (int sideways = SHORE_SIDEWAYS_MIN; sideways <= SHORE_SIDEWAYS_MAX; sideways += 2) {
			for (int inland = SHORE_INLAND_MIN; inland <= SHORE_INLAND_MAX; inland += 2) {
				BlockPos spot = site.shore()
					.relative(side, sideways)
					.relative(site.toWater().getOpposite(), inland);
				int ground = columnHeight(context, spot.getX(), spot.getZ(), Heightmap.Types.WORLD_SURFACE_WG);

				if (!isDry(context, spot)) {
					rejectedAnchor++;
					continue;
				}

				int bottom = ground - this.shoreGroundDelta;
				BlockPos origin = anchor(context, element, rotation, spot.atY(bottom), site.toWater().getOpposite(), bottom);
				BoundingBox box = element.getBoundingBox(context.structureTemplateManager(), origin, rotation);

				int wet = wetColumns(context, box);
				driest = Math.min(driest, wet);
				if (pass == 0 ? wet > 0 : wet * 20 > (box.maxX() - box.minX() + 1) * (box.maxZ() - box.minZ() + 1)) {
					rejectedFootprint++;
					continue;
				}

				StructurePiece blocker = obstacleAt(builder, box.inflatedBy(1, 0, 1), null, true);
				if (blocker != null) {
					rejectedCollision++;
					if (blocker instanceof PoolElementStructurePiece pool) {
						String id = pool.getElement().toString();
						blockedBy = id.substring(Math.max(0, id.indexOf("hut_village/")));
					} else {
						blockedBy = blocker.getClass().getSimpleName();
					}
					continue;
				}

				Under under = groundUnder(context, box);
				int seat = under.high();
				if (LAND && (under.spread() > MAX_FOOTPRINT_SLOPE || !ringLevel(context, box, seat))) {
					rejectedUneven++;
					continue;
				}
				if (!LAND) {
					seat = ground;
				}
				if (seat != ground) {
					origin = origin.atY(seat - this.shoreGroundDelta);
					box = element.getBoundingBox(context.structureTemplateManager(), origin, rotation);
				}
				Oceania.LOGGER.info("[village] {} shore building seated, {} of {} columns wet, ground {}..{}, floor at {}",
					context.chunkPos(), wet, (box.maxX() - box.minX() + 1) * (box.maxZ() - box.minZ() + 1),
					under.low(), under.high(), seat);
				builder.addPiece(new PoolElementStructurePiece(context.structureTemplateManager(), element, origin,
					this.shoreGroundDelta, rotation, box, LiquidSettings.APPLY_WATERLOGGING));

				return spot.relative(site.toWater().getOpposite(), 1).atY(ground);
			}
		}
		}
		}
		Oceania.LOGGER.info("[village] {} no shore spot: {} wet anchor, {} wet footprint (driest {} columns),"
			+ " {} collided (last blocker: {}), {} uneven", context.chunkPos(), rejectedAnchor, rejectedFootprint,
			driest == Integer.MAX_VALUE ? -1 : driest, rejectedCollision, blockedBy, rejectedUneven);
		return null;
	}

	private void placeTorchPosts(Structure.GenerationContext context, StructurePiecesBuilder builder) {
		Set<Long> paths = pathColumns(context, builder, true);
		if (paths.isEmpty()) {
			return;
		}

		List<BoundingBox> taken = new ArrayList<>();
		for (StructurePiece piece : builder.build().pieces()) {
			taken.add(piece.getBoundingBox());
		}
		StructurePoolElement post = StructurePoolElement.single(TORCH_POST.toString())
			.apply(StructureTemplatePool.Projection.RIGID);
		List<Long> ordered = new ArrayList<>(paths);
		ordered.sort(null);
		List<Long> placed = new ArrayList<>();
		for (long key : ordered) {
			int x = unpackX(key);
			int z = unpackZ(key);
			if (nearAPost(placed, x, z)) {
				continue;
			}
			int walk = groundAt(context, new BlockPos(x, 0, z));
			for (Direction step : Direction.Plane.HORIZONTAL) {
				int px = x + step.getStepX();
				int pz = z + step.getStepZ();
				if (paths.contains(pack(px, pz)) || occupied(taken, px, pz)
						|| !isDry(context, new BlockPos(px, 0, pz))) {
					continue;
				}
				int ground = groundAt(context, new BlockPos(px, 0, pz));

				if (Math.abs(ground - walk) > 1) {
					continue;
				}
				lay(context, builder, post, new BlockPos(px, 0, pz), ground + 1);
				placed.add(pack(px, pz));
				break;
			}
		}
		Oceania.LOGGER.info("[village] {} stood {} torch post(s) along {} path column(s)",
			context.chunkPos(), placed.size(), paths.size());
	}

	private static boolean occupied(List<BoundingBox> taken, int x, int z) {
		for (BoundingBox box : taken) {
			if (x >= box.minX() && x <= box.maxX() && z >= box.minZ() && z <= box.maxZ()) {
				return true;
			}
		}
		return false;
	}

	private static boolean nearAPost(List<Long> placed, int x, int z) {
		for (long key : placed) {
			if (Math.abs(unpackX(key) - x) < TORCH_SPACING && Math.abs(unpackZ(key) - z) < TORCH_SPACING) {
				return true;
			}
		}
		return false;
	}

	private BlockPos anchor(Structure.GenerationContext context, StructurePoolElement element, Rotation rotation,
			BlockPos target, Direction away, int y) {
		BoundingBox probe = element.getBoundingBox(context.structureTemplateManager(), BlockPos.ZERO, rotation);
		int offsetX = switch (away) {
			case WEST -> probe.minX();
			case EAST -> probe.maxX();
			default -> (probe.minX() + probe.maxX()) / 2;
		};
		int offsetZ = switch (away) {
			case NORTH -> probe.minZ();
			case SOUTH -> probe.maxZ();
			default -> (probe.minZ() + probe.maxZ()) / 2;
		};
		return new BlockPos(target.getX() - offsetX, y, target.getZ() - offsetZ);
	}

	@Override
	public StructureType<?> type() {
		return OceaniaStructures.HUT_VILLAGE;
	}
}

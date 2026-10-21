package com.oceania.worldgen.structure;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.oceania.Oceania;
import com.oceania.worldgen.field.FieldFunction;
import com.oceania.worldgen.field.OceanFields;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.structures.JungleTemplePiece;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

public final class IslandTempleStructure extends Structure {
	public static final boolean ENABLED = !"false".equalsIgnoreCase(System.getProperty("oceania.temples", "true"));
	private static final List<String> TYPE_NAMES = List.of("none", "barren", "large", "cluster", "guyot");

	private static final int WIDTH = 12, DEPTH = 15;

	public static final MapCodec<IslandTempleStructure> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			settingsCodec(i),
			Codec.list(Codec.STRING).optionalFieldOf("island_types", List.of("large")).forGetter(s -> s.islandTypes),
			Codec.doubleRange(0, 4096).optionalFieldOf("min_reach", 100.0).forGetter(s -> s.minReach),
			Codec.intRange(8, 256).optionalFieldOf("search_radius", 64).forGetter(s -> s.searchRadius),
			Codec.intRange(0, 120).optionalFieldOf("max_shift", 112).forGetter(s -> s.maxShift),
			Codec.doubleRange(0, 1).optionalFieldOf("island_chance", 1.0).forGetter(s -> s.islandChance)
		).apply(i, IslandTempleStructure::new));

	private final List<String> islandTypes;
	private final double minReach;
	private final int searchRadius;
	private final int maxShift;
	private final double islandChance;
	private final boolean[] allowed = new boolean[TYPE_NAMES.size()];
	private volatile RandomSpreadStructurePlacement placement;

	private IslandTempleStructure(StructureSettings settings, List<String> islandTypes, double minReach, int searchRadius, int maxShift, double islandChance) {
		super(settings);
		this.islandTypes = islandTypes;
		this.minReach = minReach;
		this.searchRadius = searchRadius;
		this.maxShift = maxShift;
		this.islandChance = islandChance;
		for (String name : islandTypes) {
			int idx = TYPE_NAMES.indexOf(name);
			if (idx >= 0) allowed[idx] = true;
		}
	}

	@Override
	public Optional<GenerationStub> findGenerationPoint(GenerationContext ctx) {
		if (!ENABLED) return Optional.empty();
		ChunkPos chunk = ctx.chunkPos();
		OceanFields fields = FieldFunction.fields(ctx.randomState());
		double[] group = fields.groupAt(chunk.getMiddleBlockX(), chunk.getMiddleBlockZ());
		if (group == null) return Optional.empty();
		int type = (int) group[3];
		if (type < 0 || type >= allowed.length || !allowed[type]) return Optional.empty();
		double cx = group[0], cz = group[1], reach = group[2];
		if (reach < minReach) return Optional.empty();
		if (islandChance < 1.0) {
			long key = mix(ctx.seed() ^ mix(Double.doubleToLongBits(cx) * 0x9E3779B97F4A7C15L + Double.doubleToLongBits(cz)));
			if ((key >>> 11) * 0x1.0p-53 >= islandChance) return Optional.empty();
		}
		int radius = (int) Math.min(searchRadius, reach * 0.6);
		BlockPos summit = summit(ctx, fields, cx, cz, radius);
		if (summit == null) return Optional.empty();
		RandomSpreadStructurePlacement spread = placement(ctx);
		if (spread == null) return Optional.empty();
		ChunkPos owner = ownerOf(spread, ctx.seed(), summit);
		if (owner == null || !owner.equals(chunk)) return Optional.empty();

		int minX = summit.getX() - WIDTH / 2, minZ = summit.getZ() - DEPTH / 2;

		int lowest = Integer.MAX_VALUE, highest = Integer.MIN_VALUE;
		for (int[] c : new int[][]{{minX, minZ}, {minX + WIDTH - 1, minZ}, {minX, minZ + DEPTH - 1}, {minX + WIDTH - 1, minZ + DEPTH - 1}}) {
			int h = ctx.chunkGenerator().getFirstOccupiedHeight(c[0], c[1], Heightmap.Types.WORLD_SURFACE_WG, ctx.heightAccessor(), ctx.randomState());
			lowest = Math.min(lowest, h);
			highest = Math.max(highest, h);
		}
		if (lowest < ctx.chunkGenerator().getSeaLevel()) return Optional.empty();
		Oceania.LOGGER.info("[temple] island {},{} (reach {}): jungle temple at {},{},{}, {} blocks from the centre, corners y {}..{}, from chunk {},{}",
			(int) cx, (int) cz, (int) reach, summit.getX(), highest, summit.getZ(),
			(int) Math.hypot(summit.getX() - cx, summit.getZ() - cz), lowest, highest, chunk.x(), chunk.z());
		BlockPos at = new BlockPos(summit.getX(), highest, summit.getZ());
		return Optional.of(new GenerationStub(at, builder -> builder.addPiece(new JungleTemplePiece(ctx.random(), minX, minZ))));
	}

	private BlockPos summit(GenerationContext ctx, OceanFields fields, double cx, double cz, int radius) {
		BiomeResolver biomes = ctx.biomeResolver();
		Predicate<Holder<Biome>> valid = ctx.validBiome();
		int ox = Mth.floor(cx), oz = Mth.floor(cz);
		List<double[]> columns = new ArrayList<>();
		for (int dx = -radius; dx <= radius; dx += 8) {
			for (int dz = -radius; dz <= radius; dz += 8) {
				if (dx * dx + dz * dz > radius * radius) continue;
				int x = ox + dx, z = oz + dz;
				double floor = fields.sample(x, z).floorY;
				if (floor < OceanFields.SEA_LEVEL + 4) continue;
				if (!valid.test(biomes.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock((int) floor), QuartPos.fromBlock(z)))) continue;
				columns.add(new double[]{x, z, floor});
			}
		}
		if (columns.isEmpty()) return null;
		columns.sort(Comparator.comparingDouble((double[] c) -> -c[2]));
		double[] best = null;
		double bestScore = -Double.MAX_VALUE;
		for (double[] c : columns.subList(0, Math.min(8, columns.size()))) {
			int minX = (int) c[0] - WIDTH / 2, minZ = (int) c[1] - DEPTH / 2;
			double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
			for (int[] k : new int[][]{{minX, minZ}, {minX + WIDTH - 1, minZ}, {minX, minZ + DEPTH - 1}, {minX + WIDTH - 1, minZ + DEPTH - 1}}) {
				double f = fields.sample(k[0], k[1]).floorY;
				lo = Math.min(lo, f);
				hi = Math.max(hi, f);
			}

			double score = c[2] - (hi - lo);
			if (score > bestScore) {
				bestScore = score;
				best = c;
			}
		}
		return best == null ? null : new BlockPos((int) best[0], (int) best[2], (int) best[1]);
	}

	private ChunkPos ownerOf(RandomSpreadStructurePlacement spread, long seed, BlockPos summit) {
		int spacing = spread.spacing();
		int cx0 = Math.floorDiv((summit.getX() - maxShift) >> 4, spacing), cx1 = Math.floorDiv((summit.getX() + maxShift) >> 4, spacing);
		int cz0 = Math.floorDiv((summit.getZ() - maxShift) >> 4, spacing), cz1 = Math.floorDiv((summit.getZ() + maxShift) >> 4, spacing);
		ChunkPos best = null;
		double bestD = (double) maxShift * maxShift;
		for (int cx = cx0; cx <= cx1; cx++) {
			for (int cz = cz0; cz <= cz1; cz++) {
				ChunkPos c = spread.getPotentialStructureChunk(seed, cx * spacing, cz * spacing);
				double dx = c.getMiddleBlockX() - summit.getX(), dz = c.getMiddleBlockZ() - summit.getZ();
				double d = dx * dx + dz * dz;

				if (d < bestD || (d == bestD && best != null && (c.x() < best.x() || (c.x() == best.x() && c.z() < best.z())))) {
					bestD = d;
					best = c;
				}
			}
		}
		return best;
	}

	private RandomSpreadStructurePlacement placement(GenerationContext ctx) {
		RandomSpreadStructurePlacement p = placement;
		if (p != null) return p;
		Registry<StructureSet> sets = ctx.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET);
		for (StructureSet set : sets) {
			for (StructureSet.StructureSelectionEntry entry : set.structures()) {
				if (entry.structure().value() == this && set.placement() instanceof RandomSpreadStructurePlacement rs) {
					placement = rs;
					return rs;
				}
			}
		}
		return null;
	}

	private static long mix(long h) {
		h ^= h >>> 33; h *= 0xff51afd7ed558ccdL; h ^= h >>> 33; h *= 0xc4ceb9fe1a85ec53L; h ^= h >>> 33;
		return h;
	}

	@Override
	public StructureType<?> type() {
		return OceaniaWorldgenStructures.ISLAND_TEMPLE;
	}
}

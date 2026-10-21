package com.oceania.debug;

import com.mojang.datafixers.util.Pair;
import com.oceania.Oceania;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class StructureIndex {
	private StructureIndex() {
	}

	public static List<BlockPos> ordered(ServerLevel level, ResourceKey<StructureSet> setKey, Identifier structureId,
	                                     BlockPos origin, int wanted, int maxRings) {
		return ordered(level, setKey, structureId, origin, wanted, maxRings, 0L);
	}

	public static List<BlockPos> ordered(ServerLevel level, ResourceKey<StructureSet> setKey, Identifier structureId,
	                                     BlockPos origin, int wanted, int maxRings, long budgetNanos) {
		return ordered(level, setKey, List.of(structureId), origin, wanted, maxRings, budgetNanos);
	}

	public static List<BlockPos> ordered(ServerLevel level, ResourceKey<StructureSet> setKey, List<Identifier> structureIds,
	                                     BlockPos origin, int wanted, int maxRings, long budgetNanos) {
		StructureSet set = level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET).getValue(setKey);
		List<Holder<Structure>> holders = new ArrayList<>();
		for (Identifier id : structureIds) {
			level.registryAccess().lookupOrThrow(Registries.STRUCTURE)
				.get(ResourceKey.create(Registries.STRUCTURE, id)).ifPresent(holders::add);
		}
		if (set == null || holders.isEmpty() || !(set.placement() instanceof RandomSpreadStructurePlacement placement)) {
			return List.of();
		}
		Identifier structureId = structureIds.get(0);
		HolderSet<Structure> only = HolderSet.direct(holders);
		int spacing = placement.spacing();
		int cellX = Math.floorDiv(origin.getX() >> 4, spacing), cellZ = Math.floorDiv(origin.getZ() >> 4, spacing);

		Set<BlockPos> hits = new LinkedHashSet<>();
		int ringsAfterEnough = 0;

		long deadline = budgetNanos <= 0 ? Long.MAX_VALUE : System.nanoTime() + budgetNanos;
		boolean outOfTime = false;
		for (int ring = 0; ring <= maxRings && !outOfTime; ring++) {
			for (int dx = -ring; dx <= ring && !outOfTime; dx++) {
				for (int dz = -ring; dz <= ring; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
					if (System.nanoTime() > deadline) {
						Oceania.LOGGER.warn("[index] {} search gave up at ring {} of {} after {}s with {} of {}"
							+ " found; this needs slicing, not patience", structureId, ring, maxRings,
							budgetNanos / 1_000_000_000L, hits.size(), wanted);
						outOfTime = true;
						break;
					}

					int chunkX = (cellX + dx) * spacing + spacing / 2;
					int chunkZ = (cellZ + dz) * spacing + spacing / 2;
					BlockPos at = new ChunkPos(chunkX, chunkZ).getMiddleBlockPosition(level.getSeaLevel());
					Pair<BlockPos, Holder<Structure>> hit = level.getChunkSource().getGenerator()
						.findNearestMapStructure(level, only, at, 0, false);
					if (hit != null) hits.add(hit.getFirst());
				}
			}
			if (hits.size() >= wanted && ++ringsAfterEnough >= 2) break;
		}

		List<BlockPos> out = new ArrayList<>(hits);
		out.sort(Comparator.comparingDouble(p -> p.distSqr(new BlockPos(origin.getX(), p.getY(), origin.getZ()))));
		return out.size() > wanted ? out.subList(0, wanted) : out;
	}

	public static List<BlockPos> ringPositions(ServerLevel level, ResourceKey<StructureSet> setKey, BlockPos origin) {
		StructureSet set = level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET).getValue(setKey);
		if (set == null || !(set.placement() instanceof ConcentricRingsStructurePlacement rings)) return List.of();
		List<ChunkPos> chunks = level.getChunkSource().getGeneratorState().getRingPositionsFor(rings);
		if (chunks == null) return List.of();
		List<BlockPos> out = new ArrayList<>();
		for (ChunkPos c : chunks) out.add(c.getMiddleBlockPosition(0));
		out.sort(Comparator.comparingDouble(p -> p.distSqr(new BlockPos(origin.getX(), 0, origin.getZ()))));
		return out;
	}

	public static StructureStart startAt(ServerLevel level, BlockPos at, Identifier structureId) {
		ChunkPos chunkPos = ChunkPos.containing(at);
		level.getChunk(chunkPos.x(), chunkPos.z());
		Structure structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE)
			.getValue(ResourceKey.create(Registries.STRUCTURE, structureId));
		if (structure == null) return null;
		for (StructureStart start : level.structureManager().startsForStructure(chunkPos.x(), chunkPos.z(), structure)) {
			if (start.isValid()) return start;
		}
		return null;
	}

	public static BlockPos standInside(ServerLevel level, BlockPos at, Identifier structureId) {
		ChunkPos chunkPos = ChunkPos.containing(at);
		level.getChunk(chunkPos.x(), chunkPos.z());
		Structure structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE)
			.getValue(ResourceKey.create(Registries.STRUCTURE, structureId));
		BoundingBox box = null;
		if (structure != null) {
			for (StructureStart start : level.structureManager().startsForStructure(chunkPos.x(), chunkPos.z(), structure)) {
				if (start.isValid()) {
					box = start.getBoundingBox();
					break;
				}
			}
		}
		if (box == null) return new BlockPos(at.getX(), level.getSeaLevel(), at.getZ());
		int yLo = Math.max(box.minY(), level.getMinY() + 1);
		int yHi = Math.min(box.maxY(), level.getMaxY() - 2);
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;
		for (int lx = 0; lx < 16; lx++) {
			for (int lz = 0; lz < 16; lz++) {
				int x = chunkPos.getMinBlockX() + lx, z = chunkPos.getMinBlockZ() + lz;
				double dist = (x - at.getX()) * (double) (x - at.getX()) + (z - at.getZ()) * (double) (z - at.getZ());
				if (dist >= bestDist) continue;
				for (int y = yLo; y <= yHi; y++) {
					if (!level.getBlockState(p.set(x, y, z)).isAir() || !level.getBlockState(p.set(x, y + 1, z)).isAir()
						|| !level.getBlockState(p.set(x, y + 2, z)).isAir()) continue;
					var floor = level.getBlockState(p.set(x, y - 1, z));
					if (!floor.isSolid() || floor.is(Blocks.LAVA) || floor.is(Blocks.MAGMA_BLOCK)) continue;
					best = new BlockPos(x, y, z);
					bestDist = dist;
					break;
				}
			}
		}
		return best != null ? best : new BlockPos(at.getX(), (box.minY() + box.maxY()) / 2, at.getZ());
	}

	public static BlockPos topOfStructure(ServerLevel level, BlockPos at) {
		level.getChunk(at.getX() >> 4, at.getZ() >> 4);
		BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos(at.getX(), level.getSeaLevel(), at.getZ());
		for (int y = level.getSeaLevel(); y > level.getMinY(); y--) {
			probe.setY(y);
			if (!level.getBlockState(probe).isAir() && level.getFluidState(probe).isEmpty()) {
				return new BlockPos(at.getX(), y + 1, at.getZ());
			}
		}
		return new BlockPos(at.getX(), level.getSeaLevel(), at.getZ());
	}
}

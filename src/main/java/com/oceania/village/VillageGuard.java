package com.oceania.village;

import com.oceania.Oceania;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

public final class VillageGuard {
	private static final ResourceKey<Structure> HUT_VILLAGE =
		ResourceKey.create(Registries.STRUCTURE, Oceania.id("hut_village"));

	public static final int MARGIN = 50;

	public static final int SURFACE_DEPTH = 6;

	private static final int REACH = (MARGIN >> 4) + 2;

	private static final Long2ObjectOpenHashMap<BoundingBox> CACHE = new Long2ObjectOpenHashMap<>();

	private static final BoundingBox NO_VILLAGE = new BoundingBox(BlockPos.ZERO);

	private VillageGuard() {
	}

	public static void forget() {
		synchronized (CACHE) {
			CACHE.clear();
		}
	}

	public static boolean quiet(ServerLevel level, BlockPos pos) {
		return quiet(level, null, pos);
	}

	public static boolean quiet(ServerLevel level, ChunkAccess chunk, BlockPos pos) {

		if (!level.getFluidState(pos).isEmpty()) {
			return false;
		}
		BoundingBox village = nearVillage(level, pos);
		if (village == NO_VILLAGE) {
			return false;
		}

		if (pos.getX() < village.minX() - MARGIN || pos.getX() > village.maxX() + MARGIN
			|| pos.getZ() < village.minZ() - MARGIN || pos.getZ() > village.maxZ() + MARGIN) {
			return false;
		}

		int ground = heightAt(level, chunk, pos);
		return ground != Integer.MIN_VALUE && pos.getY() >= ground - SURFACE_DEPTH;
	}

	public static BoundingBox villageNear(ServerLevel level, BlockPos pos) {
		BoundingBox box = nearVillage(level, pos);
		return box == NO_VILLAGE ? null : box;
	}

	private static int heightAt(ServerLevel level, ChunkAccess chunk, BlockPos pos) {
		int chunkX = SectionPos.blockToSectionCoord(pos.getX());
		int chunkZ = SectionPos.blockToSectionCoord(pos.getZ());
		ChunkAccess at = chunk;
		if (at == null || at.getPos().x() != chunkX || at.getPos().z() != chunkZ) {
			at = level.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
		}
		if (at == null) {
			return Integer.MIN_VALUE;
		}
		return at.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ()) + 1;
	}

	private static BoundingBox nearVillage(ServerLevel level, BlockPos pos) {
		long key = ChunkPos.pack(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
		synchronized (CACHE) {
			if (CACHE.containsKey(key)) {
				return CACHE.get(key);
			}
		}
		BoundingBox found = scan(level, pos);
		if (found != UNKNOWN) {
			synchronized (CACHE) {
				CACHE.put(key, found);
			}
		}
		return found == UNKNOWN ? NO_VILLAGE : found;
	}

	private static final BoundingBox UNKNOWN = new BoundingBox(BlockPos.ZERO);

	private static BoundingBox scan(ServerLevel level, BlockPos pos) {
		Structure structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValue(HUT_VILLAGE);
		if (structure == null) {
			return NO_VILLAGE;
		}
		int chunkX = SectionPos.blockToSectionCoord(pos.getX());
		int chunkZ = SectionPos.blockToSectionCoord(pos.getZ());
		boolean complete = true;
		for (int dx = -REACH; dx <= REACH; dx++) {
			for (int dz = -REACH; dz <= REACH; dz++) {

				ChunkAccess chunk = level.getChunk(chunkX + dx, chunkZ + dz, ChunkStatus.STRUCTURE_STARTS, false);
				if (chunk == null) {
					complete = false;
					continue;
				}
				BoundingBox hit = reaches(chunk.getAllStarts().get(structure), pos);
				if (hit != null) {
					return hit;
				}
				LongSet refs = chunk.getReferencesForStructure(structure);
				for (long ref : refs) {
					ChunkPos home = ChunkPos.unpack(ref);
					ChunkAccess origin = level.getChunk(home.x(), home.z(), ChunkStatus.STRUCTURE_STARTS, false);
					if (origin == null) {
						complete = false;
						continue;
					}
					hit = reaches(origin.getAllStarts().get(structure), pos);
					if (hit != null) {
						return hit;
					}
				}
			}
		}
		return complete ? NO_VILLAGE : UNKNOWN;
	}

	private static BoundingBox reaches(StructureStart start, BlockPos pos) {
		if (start == null || !start.isValid()) {
			return null;
		}
		BoundingBox box = start.getBoundingBox();
		boolean near = pos.getX() >= box.minX() - MARGIN && pos.getX() <= box.maxX() + MARGIN
			&& pos.getZ() >= box.minZ() - MARGIN && pos.getZ() <= box.maxZ() + MARGIN;
		return near ? box : null;
	}

}

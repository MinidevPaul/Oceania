package com.oceania.village;

import com.oceania.Oceania;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import java.util.Set;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.structure.Structure;

public final class VillageClearing {
	private static final ResourceKey<Structure> HUT_VILLAGE = ResourceKey.create(Registries.STRUCTURE, Oceania.id("hut_village"));

	private VillageClearing() {
	}

	public static boolean suppressVegetation(WorldGenLevel level, BlockPos pos) {
		long reach = villageReach(level, pos);
		int covered = (int) (reach >> 32);
		int seen = (int) reach;
		if (covered == 0) {
			return false;
		}
		if (covered == seen) {
			return true;
		}
		return Math.floorMod(Mth.getSeed(pos), seen) < covered;
	}

	private static final Set<Block> MADE_GROUND = Set.of(
		Blocks.GRAVEL, Blocks.COARSE_DIRT, Blocks.TUFF, Blocks.TUFF_SLAB, Blocks.TUFF_STAIRS,
		Blocks.JUNGLE_PLANKS, Blocks.JUNGLE_SLAB, Blocks.DIRT_PATH);

	public static boolean suppressGroundCover(WorldGenLevel level, BlockPos pos) {
		if ((int) (villageReach(level, pos) >> 32) == 0) {
			return false;
		}
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (MADE_GROUND.contains(level.getBlockState(pos.offset(dx, -1, dz)).getBlock())) {
					return true;
				}
			}
		}
		return false;
	}

	private static long villageReach(WorldGenLevel level, BlockPos pos) {
		Structure structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValue(HUT_VILLAGE);
		if (structure == null) {
			return 0L;
		}
		int chunkX = SectionPos.blockToSectionCoord(pos.getX());
		int chunkZ = SectionPos.blockToSectionCoord(pos.getZ());
		int covered = 0;
		int seen = 0;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {

				if (!level.hasChunk(chunkX + dx, chunkZ + dz)) {
					continue;
				}
				seen++;
				ChunkAccess chunk = level.getChunk(chunkX + dx, chunkZ + dz);
				if (!chunk.getReferencesForStructure(structure).isEmpty() || chunk.getAllStarts().containsKey(structure)) {
					covered++;
				}
			}
		}
		return ((long) covered << 32) | seen;
	}
}

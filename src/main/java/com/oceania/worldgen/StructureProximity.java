package com.oceania.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

public final class StructureProximity {
	private StructureProximity() {
	}

	public static boolean near(ServerLevelAccessor level, BlockPos pos, ResourceKey<StructureSet> setKey, int radius) {
		StructureSet set = level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET).getValue(setKey);
		if (set == null || !(set.placement() instanceof RandomSpreadStructurePlacement placement)) return false;

		int spacing = placement.spacing();
		long seed = level.getLevel().getSeed();

		int chunkX = pos.getX() >> 4, chunkZ = pos.getZ() >> 4;
		long radiusSqr = (long) radius * radius;

		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				ChunkPos candidate = placement.getPotentialStructureChunk(
					seed, chunkX + dx * spacing, chunkZ + dz * spacing);
				int x = candidate.getMiddleBlockX(), z = candidate.getMiddleBlockZ();
				long dxB = x - pos.getX(), dzB = z - pos.getZ();
				if (dxB * dxB + dzB * dzB > radiusSqr) continue;

				BlockPos at = new BlockPos(x, pos.getY(), z);
				Holder<Biome> biome = level.getBiome(at);
				for (StructureSet.StructureSelectionEntry entry : set.structures()) {
					Structure structure = entry.structure().value();
					if (structure.biomes().contains(biome)) return true;
				}
			}
		}
		return false;
	}
}

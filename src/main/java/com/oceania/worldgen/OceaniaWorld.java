package com.oceania.worldgen;

import com.oceania.Oceania;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;

public final class OceaniaWorld {
	private OceaniaWorld() {
	}

	public static boolean isOceania(ServerLevel level) {
		return isOceania(level.getChunkSource().getGenerator().getBiomeSource());
	}

	public static boolean isOceania(BiomeSource source) {
		for (Holder<Biome> biome : source.possibleBiomes()) {
			if (biome.unwrapKey().map(key -> Oceania.MOD_ID.equals(key.identifier().getNamespace())).orElse(false)) return true;
		}
		return false;
	}
}

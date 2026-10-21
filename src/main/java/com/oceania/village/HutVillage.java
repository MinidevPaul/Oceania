package com.oceania.village;

import com.oceania.Oceania;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.npc.villager.VillagerType;
import net.minecraft.world.level.biome.Biome;

public final class HutVillage {
	public static final ResourceKey<Biome> ISLAND_JUNGLE = ResourceKey.create(Registries.BIOME, Oceania.id("island_jungle"));

	private HutVillage() {
	}

	public static void init() {
		OceaniaStructures.init();

		VillagerType.BY_BIOME.put(ISLAND_JUNGLE, VillagerType.JUNGLE);
		OceaniaTrades.init();
	}
}

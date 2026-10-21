package com.oceania.village;

import com.oceania.Oceania;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.levelgen.structure.StructureType;

public final class OceaniaStructures {
	public static final StructureType<HutVillageStructure> HUT_VILLAGE = () -> HutVillageStructure.CODEC;

	private OceaniaStructures() {
	}

	public static void init() {
		Registry.register(BuiltInRegistries.STRUCTURE_TYPE, Oceania.id("hut_village"), HUT_VILLAGE);
	}
}

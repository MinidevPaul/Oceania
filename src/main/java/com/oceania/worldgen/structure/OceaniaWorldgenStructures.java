package com.oceania.worldgen.structure;

import com.oceania.Oceania;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.levelgen.structure.StructureType;

public final class OceaniaWorldgenStructures {
	public static final StructureType<FloorJigsawStructure> FLOOR_JIGSAW = () -> FloorJigsawStructure.CODEC;
	public static final StructureType<FloorStrongholdStructure> FLOOR_STRONGHOLD = () -> FloorStrongholdStructure.CODEC;
	public static final StructureType<FloorMineshaftStructure> FLOOR_MINESHAFT = () -> FloorMineshaftStructure.CODEC;
	public static final StructureType<IslandJigsawStructure> ISLAND_JIGSAW = () -> IslandJigsawStructure.CODEC;
	public static final StructureType<IslandTempleStructure> ISLAND_TEMPLE = () -> IslandTempleStructure.CODEC;

	private OceaniaWorldgenStructures() {
	}

	public static void init() {
		Registry.register(BuiltInRegistries.STRUCTURE_TYPE, Oceania.id("floor_jigsaw"), FLOOR_JIGSAW);
		Registry.register(BuiltInRegistries.STRUCTURE_TYPE, Oceania.id("floor_stronghold"), FLOOR_STRONGHOLD);
		Registry.register(BuiltInRegistries.STRUCTURE_TYPE, Oceania.id("floor_mineshaft"), FLOOR_MINESHAFT);
		Registry.register(BuiltInRegistries.STRUCTURE_TYPE, Oceania.id("island_jigsaw"), ISLAND_JIGSAW);
		Registry.register(BuiltInRegistries.STRUCTURE_TYPE, Oceania.id("island_temple"), ISLAND_TEMPLE);
	}
}

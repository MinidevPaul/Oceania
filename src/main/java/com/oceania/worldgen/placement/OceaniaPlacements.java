package com.oceania.worldgen.placement;

import com.mojang.serialization.MapCodec;
import com.oceania.Oceania;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;

public final class OceaniaPlacements {
	private OceaniaPlacements() {
	}

	public static void init() {
		register("floor_depth", FloorDepthPlacement.CODEC);
		register("column_biome", ColumnBiomeFilter.CODEC);
	}

	private static void register(String name, MapCodec<? extends PlacementModifier> codec) {
		Registry.register(BuiltInRegistries.PLACEMENT_MODIFIER_TYPE, Oceania.id(name), codec);
	}
}

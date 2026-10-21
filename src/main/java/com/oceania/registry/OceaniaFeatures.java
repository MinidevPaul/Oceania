package com.oceania.registry;

import com.mojang.serialization.MapCodec;
import com.oceania.Oceania;
import com.oceania.worldgen.feature.AtollCactusFeature;
import com.oceania.worldgen.feature.CavePoolFeature;
import com.oceania.worldgen.feature.TallKelpFeature;
import com.oceania.worldgen.feature.UnderwaterMagmaFeature;
import com.oceania.worldgen.feature.VentFeature;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.levelgen.feature.Feature;

public final class OceaniaFeatures {
	private OceaniaFeatures() {
	}

	public static void init() {
		register("vent", VentFeature.CODEC);
		register("tall_kelp", TallKelpFeature.CODEC);
		register("atoll_cactus", AtollCactusFeature.CODEC);
		register("cave_pool", CavePoolFeature.CODEC);

		register("underwater_magma", UnderwaterMagmaFeature.CODEC);
	}

	private static void register(String name, MapCodec<? extends Feature> codec) {
		Registry.register(BuiltInRegistries.FEATURE_TYPE, Oceania.id(name), codec);
	}
}

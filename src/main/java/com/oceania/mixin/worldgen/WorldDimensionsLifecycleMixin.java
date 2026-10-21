package com.oceania.mixin.worldgen;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.oceania.Oceania;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.WorldDimensions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(WorldDimensions.class)
public abstract class WorldDimensionsLifecycleMixin {
	@ModifyReturnValue(method = "isStableOverworld", at = @At("RETURN"))
	private static boolean oceania$oceaniaOverworldIsStable(boolean original, LevelStem stem) {
		if (original) return true;
		return stem.type().is(BuiltinDimensionTypes.OVERWORLD)
			&& stem.generator() instanceof NoiseBasedChunkGenerator noise
			&& noise.stable(NoiseGeneratorSettings.OVERWORLD)
			&& noise.getBiomeSource() instanceof MultiNoiseBiomeSource biomes
			&& biomes.possibleBiomes().stream().anyMatch(biome ->
				biome.unwrapKey().map(key -> Oceania.MOD_ID.equals(key.identifier().getNamespace())).orElse(false));
	}
}

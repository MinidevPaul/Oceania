package com.oceania.mixin.debug;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.oceania.debug.GenStats;
import net.minecraft.core.Holder;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.material.rule.MaterialRule;
import org.spongepowered.asm.mixin.Mixin;

import java.util.Set;

@Mixin(NoiseBasedChunkGenerator.class)
public class NoiseBasedChunkGeneratorMixin {
	@WrapMethod(method = "doFill")
	private void oceania$timeDoFill(NoiseChunk noiseChunk, ChunkAccess chunk, Operation<Void> original) {
		long t = System.nanoTime();
		try {
			original.call(noiseChunk, chunk);
		} finally {
			GenStats.NOISE.add(System.nanoTime() - t);
		}
	}

	@WrapMethod(method = "buildSurface")
	private void oceania$timeBuildSurface(ChunkAccess chunk, NoiseChunk noiseChunk, RandomState randomState, BiomeManager biomeManager,
			Set<Holder<Biome>> possibleBiomes, MaterialRule materialRule, Operation<Void> original) {
		long t = System.nanoTime();
		try {
			original.call(chunk, noiseChunk, randomState, biomeManager, possibleBiomes, materialRule);
		} finally {
			GenStats.SURFACE.add(System.nanoTime() - t);
		}
	}

	@WrapMethod(method = "generateCarvers")
	private void oceania$timeCarvers(ChunkAccess chunk, Blender blender, NoiseChunk noiseChunk, RandomState randomState, BiomeManager biomeManager,
			WorldGenRegion carverBiomeRegion, MaterialRule materialRule, Operation<Void> original) {
		long t = System.nanoTime();
		try {
			original.call(chunk, blender, noiseChunk, randomState, biomeManager, carverBiomeRegion, materialRule);
		} finally {
			GenStats.CARVERS.add(System.nanoTime() - t);
		}
	}
}

package com.oceania.mixin.debug;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.oceania.debug.GenStats;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatusTasks;
import net.minecraft.world.level.chunk.status.ChunkStep;
import net.minecraft.world.level.chunk.status.WorldGenContext;
import org.spongepowered.asm.mixin.Mixin;

import java.util.concurrent.CompletableFuture;

@Mixin(ChunkStatusTasks.class)
public class ChunkStatusTasksMixin {
	@WrapMethod(method = "generateFeatures")
	private static CompletableFuture<ChunkAccess> oceania$timeFeatures(WorldGenContext ctx, ChunkStep step, StaticCache2D<GenerationChunkHolder> cache,
			ChunkAccess chunk, Operation<CompletableFuture<ChunkAccess>> original) {
		long t = System.nanoTime();
		try {
			return original.call(ctx, step, cache, chunk);
		} finally {
			GenStats.FEATURES.add(System.nanoTime() - t);
		}
	}
}

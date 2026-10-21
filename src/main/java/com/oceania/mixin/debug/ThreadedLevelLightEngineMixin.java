package com.oceania.mixin.debug;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.oceania.debug.GenStats;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

@Mixin(ThreadedLevelLightEngine.class)
public class ThreadedLevelLightEngineMixin {
	@WrapMethod(method = "runUpdate")
	private void oceania$timeRunUpdate(Operation<Void> original) {
		long t = System.nanoTime();
		try {
			original.call();
		} finally {
			GenStats.LIGHT.add(System.nanoTime() - t);
		}
	}

	@Inject(method = "lightChunk", at = @At("HEAD"))
	private void oceania$countLightChunk(ChunkAccess chunk, boolean isLighted, CallbackInfoReturnable<CompletableFuture<ChunkAccess>> cir) {
		GenStats.CHUNKS_LIT.increment();
	}
}

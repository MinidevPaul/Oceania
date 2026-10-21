package com.oceania.mixin.debug;

import com.oceania.debug.FeatureReadTrace;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.chunk.status.ChunkStep;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Supplier;

@Mixin(WorldGenRegion.class)
public class WorldGenRegionReadTraceMixin {
	@Shadow @Final private int centerChunkX;
	@Shadow @Final private int centerChunkZ;
	@Shadow private Supplier<String> currentlyGenerating;
	@Shadow @Final private ChunkStep generatingStep;

	@Inject(method = "warnIfReadOutsideWriteZone", at = @At("HEAD"))
	private void oceania$traceCrossChunkRead(final int chunkX, final int chunkZ, final CallbackInfo ci) {
		if (!FeatureReadTrace.ENABLED) {
			return;
		}
		if (chunkX == this.centerChunkX && chunkZ == this.centerChunkZ) {
			return;
		}
		Supplier<String> what = this.currentlyGenerating;
		FeatureReadTrace.record(this.generatingStep.targetStatus().getName(), what == null ? null : what.get(),
			chunkX - this.centerChunkX, chunkZ - this.centerChunkZ);
	}
}

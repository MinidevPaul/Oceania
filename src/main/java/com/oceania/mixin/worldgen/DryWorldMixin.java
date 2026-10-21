package com.oceania.mixin.worldgen;

import com.oceania.worldgen.OceaniaFluidPicker;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkGenerator.class)
public class DryWorldMixin {
	@Inject(method = "applyBiomeDecoration", at = @At("HEAD"), cancellable = true)
	private void oceania$noFeatures(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager, CallbackInfo ci) {
		if (OceaniaFluidPicker.DRY) ci.cancel();
	}

	@Inject(method = "createStructures", at = @At("HEAD"), cancellable = true)
	private void oceania$noStructures(CallbackInfo ci) {
		if (OceaniaFluidPicker.DRY) ci.cancel();
	}
}

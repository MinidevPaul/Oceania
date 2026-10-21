package com.oceania.mixin.worldgen;

import com.oceania.worldgen.StructureGround;
import net.minecraft.world.level.levelgen.Beardifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Beardifier.class)
public class BeardifierCaptureMixin {
	@Inject(method = "forStructuresInChunk", at = @At("RETURN"))
	private static void oceania$remember(net.minecraft.world.level.StructureManager structureManager,
			net.minecraft.world.level.ChunkPos chunkPos, CallbackInfoReturnable<Beardifier> cir) {
		StructureGround.remember(cir.getReturnValue());
	}
}

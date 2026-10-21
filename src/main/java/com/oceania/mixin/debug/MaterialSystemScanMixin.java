package com.oceania.mixin.debug;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.oceania.debug.SurfaceDepthScan;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.material.MaterialSystem;
import net.minecraft.world.level.levelgen.material.rule.RuleEvaluator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(MaterialSystem.class)
public class MaterialSystemScanMixin {
	@WrapOperation(
		method = "buildSurface",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/level/levelgen/material/rule/RuleEvaluator;tryApply(III)Lnet/minecraft/world/level/block/state/BlockState;"
		)
	)
	private BlockState oceania$scanRule(final RuleEvaluator rule, final int blockX, final int blockY, final int blockZ,
			final Operation<BlockState> original, @Local(argsOnly = true) final ChunkAccess protoChunk) {
		BlockState result = original.call(rule, blockX, blockY, blockZ);
		if (SurfaceDepthScan.ENABLED) {
			int top = protoChunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, blockX & 15, blockZ & 15);
			SurfaceDepthScan.record(top - blockY, result);
		}
		return result;
	}
}

package com.oceania.mixin.spawn;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerPlayer.class)
public class ServerPlayerSpawnMixin {
	@ModifyReturnValue(method = "adjustSpawnLocation", at = @At("RETURN"))
	private BlockPos oceania$spawnAtSurface(BlockPos result, ServerLevel level, BlockPos requested) {
		if (level.getFluidState(result).is(FluidTags.WATER) || level.getFluidState(result.below()).is(FluidTags.WATER)) {
			int sea = level.getSeaLevel();
			BlockPos surface = new BlockPos(result.getX(), sea, result.getZ());
			if (level.getFluidState(surface.below()).is(FluidTags.WATER) && level.getBlockState(surface).isAir()) {
				return surface;
			}
		}
		return result;
	}
}

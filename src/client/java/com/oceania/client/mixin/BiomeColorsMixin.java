package com.oceania.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.oceania.client.WaterColumn;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BiomeColors.class)
public class BiomeColorsMixin {
	@ModifyReturnValue(method = "getAverageWaterColor", at = @At("RETURN"))
	private static int oceania$depthTint(int color, BlockAndTintGetter level, BlockPos pos) {
		return WaterColumn.tint(color, level, pos);
	}
}

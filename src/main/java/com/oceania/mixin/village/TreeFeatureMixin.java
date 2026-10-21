package com.oceania.mixin.village;

import com.oceania.village.VillageClearing;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TreeFeature.class)
public class TreeFeatureMixin {
	@Inject(method = "place", at = @At("HEAD"), cancellable = true)
	private void oceania$clearVillage(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin, CallbackInfoReturnable<Boolean> cir) {
		if (VillageClearing.suppressVegetation(level, origin)) {
			cir.setReturnValue(false);
		}
	}
}

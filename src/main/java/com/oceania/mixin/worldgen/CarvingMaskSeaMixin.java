package com.oceania.mixin.worldgen;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.oceania.worldgen.OceaniaAquiferAccess;
import com.oceania.worldgen.OceaniaFluidPicker;
import com.oceania.worldgen.StructureGround;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(NoiseBasedChunkGenerator.class)
public class CarvingMaskSeaMixin {
	@WrapOperation(method = "lambda$applyCarvingMask$0",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/Aquifer;computeSubstance(IIID)Lnet/minecraft/world/level/block/state/BlockState;"))
	private static BlockState oceania$onlyCarveDryRock(Aquifer aquifer, int x, int y, int z, double density, Operation<BlockState> original) {
		if (StructureGround.supports(x, y, z)) return null;
		BlockState state = original.call(aquifer, x, y, z, density);
		if (state == null || state.isAir()) return state;
		if (aquifer instanceof OceaniaAquiferAccess access && access.oceania$globalPicker() instanceof OceaniaFluidPicker picker) {
			return picker.isSea(picker.computeFluid(x, y, z)) ? null : state;
		}
		return null;
	}
}

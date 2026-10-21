package com.oceania.mixin.worldgen;

import com.oceania.perf.PerfFlags;
import com.oceania.worldgen.OceaniaFluidPicker;
import com.oceania.worldgen.field.OceanFields;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Aquifer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.world.level.levelgen.Aquifer$NoiseBasedAquifer")
public abstract class AquiferDeepRockMixin {

	@Shadow @Final private Aquifer.FluidPicker globalFluidPicker;

	@Inject(method = "computeSubstance", at = @At("RETURN"), cancellable = true)
	private void oceania$deepRock(final int blockX, final int blockY, final int blockZ, final double density,
			final CallbackInfoReturnable<BlockState> cir) {
		if (!PerfFlags.DEEP_ROCK_FILL) {
			return;
		}

		BlockState result = cir.getReturnValue();
		if (result != null || !(density > 0.0)) {
			return;
		}
		if (!(globalFluidPicker instanceof OceaniaFluidPicker picker)) {
			return;
		}
		OceanFields fields = picker.oceania$fields();
		if (fields.floorAt(blockX, blockZ) - blockY >= PerfFlags.DEEP_ROCK_DEPTH) {
			PerfFlags.DEEP_ROCK_PLACED.increment();
			cir.setReturnValue(Blocks.DEEPSLATE.defaultBlockState());
		}
	}
}

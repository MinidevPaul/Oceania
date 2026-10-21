package com.oceania.mixin.worldgen;

import com.llamalad7.mixinextras.sugar.Local;
import com.oceania.worldgen.OceaniaAquiferAccess;
import com.oceania.worldgen.OceaniaFluidPicker;
import com.oceania.worldgen.field.OceanFields;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.world.level.levelgen.Aquifer$NoiseBasedAquifer")
public abstract class NoiseBasedAquiferMixin implements OceaniaAquiferAccess {
	@Shadow @Final private Aquifer.FluidPicker globalFluidPicker;
	@Shadow @Final private DensitySampler.Bound lavaNoise;
	@Shadow private boolean shouldScheduleFluidUpdate;

	@Override
	public Aquifer.FluidPicker oceania$globalPicker() {
		return globalFluidPicker;
	}

	@Inject(method = "computeSubstance",
		at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
			target = "Lnet/minecraft/world/level/levelgen/Aquifer$NoiseBasedAquifer;skipSamplingAboveY:I"),
		cancellable = true)
	private void oceania$seaIsSea(int blockX, int blockY, int blockZ, double density, CallbackInfoReturnable<BlockState> cir,
								  @Local Aquifer.FluidStatus global) {
		if (globalFluidPicker instanceof OceaniaFluidPicker picker && picker.isSea(global)) {
			shouldScheduleFluidUpdate = false;
			cir.setReturnValue(global.at(blockY));
		}
	}

	@Inject(method = "computeFluidType", at = @At("HEAD"), cancellable = true)
	private void oceania$lavaByDepth(int x, int y, int z, Aquifer.FluidStatus global, int level, CallbackInfoReturnable<BlockState> cir) {
		if (!(globalFluidPicker instanceof OceaniaFluidPicker picker)) return;
		BlockState type = global.fluidType();
		if (level == DimensionType.WAY_BELOW_MIN_Y || type.is(Blocks.LAVA) || picker.floorAt(x, z) - level < OceanFields.LAVA_POCKET_DEPTH) {
			cir.setReturnValue(type);
			return;
		}

		double d = lavaNoise.sampleValue(Math.floorDiv(x, 64), Math.floorDiv(y, 40), Math.floorDiv(z, 64));
		cir.setReturnValue(Math.abs(d) > 0.3 ? Blocks.LAVA.defaultBlockState() : type);
	}
}

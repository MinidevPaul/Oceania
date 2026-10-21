package com.oceania.mixin.worldgen;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.oceania.worldgen.OceaniaFluidPicker;
import net.minecraft.core.Holder;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Supplier;

@Mixin(NoiseBasedChunkGenerator.class)
public class NoiseBasedChunkGeneratorFluidMixin {
	@Shadow
	@Final
	private Holder<NoiseGeneratorSettings> settings;

	@WrapOperation(
		method = {"createNoiseChunk", "iterateNoiseColumn"},
		at = @At(value = "INVOKE", target = "Ljava/util/function/Supplier;get()Ljava/lang/Object;")
	)
	private Object oceania$fluidPicker(Supplier<?> supplier, Operation<Object> original, @Local(argsOnly = true) RandomState randomState) {
		Object picker = original.call(supplier);
		if (picker instanceof Aquifer.FluidPicker vanilla) {
			return OceaniaFluidPicker.create(vanilla, this.settings.value(), randomState);
		}
		return picker;
	}
}

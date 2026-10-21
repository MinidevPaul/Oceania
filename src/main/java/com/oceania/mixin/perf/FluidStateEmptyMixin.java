package com.oceania.mixin.perf;

import com.oceania.perf.PerfFlags;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;

@Mixin(FluidState.class)
public class FluidStateEmptyMixin {
	@Unique
	private byte oceania$empty;

	@Overwrite
	public boolean isEmpty() {
		FluidState self = (FluidState) (Object) this;
		if (!PerfFlags.FLUID_EMPTY_CACHE) {
			return ((FluidIsEmptyInvoker) self.getType()).oceania$isEmpty();
		}
		byte cached = oceania$empty;
		if (cached == 0) {
			cached = ((FluidIsEmptyInvoker) self.getType()).oceania$isEmpty() ? (byte) 1 : (byte) 2;
			oceania$empty = cached;
			PerfFlags.FLUID_CACHE_FILLS.increment();
		}
		return cached == 1;
	}
}

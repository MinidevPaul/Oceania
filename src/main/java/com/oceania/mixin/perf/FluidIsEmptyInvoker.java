package com.oceania.mixin.perf;

import net.minecraft.world.level.material.Fluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Fluid.class)
public interface FluidIsEmptyInvoker {
	@Invoker("isEmpty")
	boolean oceania$isEmpty();
}

package com.oceania.mixin.perf;

import com.oceania.perf.PerfFlags;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Util.class)
public class UtilWorkerThreadsMixin {
	@Inject(method = "maxAllowedExecutorThreads", at = @At("RETURN"), cancellable = true)
	private static void oceania$capWorkerThreads(final CallbackInfoReturnable<Integer> cir) {
		int cap = PerfFlags.WORKER_THREADS;
		if (cap > 0 && cir.getReturnValueI() > cap) {
			cir.setReturnValue(cap);
		}
	}
}

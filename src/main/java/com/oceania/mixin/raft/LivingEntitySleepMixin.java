package com.oceania.mixin.raft;

import com.oceania.raft.RaftBeds;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntitySleepMixin {
	@Inject(method = "checkBedExists", at = @At("HEAD"), cancellable = true)
	private void oceania$raftBedExists(CallbackInfoReturnable<Boolean> cir) {
		if ((Object) this instanceof ServerPlayer player && RaftBeds.isSleepingOnRaft(player)) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "stopSleeping", at = @At("TAIL"))
	private void oceania$leaveRaftBed(CallbackInfo ci) {
		if ((Object) this instanceof ServerPlayer player) {
			RaftBeds.onStopSleeping(player);
		}
	}
}

package com.oceania.client.mixin;

import com.oceania.client.raft.RaftTargeting;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftRaftInputMixin {
	@Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
	private void oceania$useRaftBlock(CallbackInfo ci) {
		if (RaftTargeting.handleUse((Minecraft) (Object) this)) ci.cancel();
	}

	@Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
	private void oceania$attackRaftBlock(CallbackInfoReturnable<Boolean> cir) {
		if (RaftTargeting.handleAttackStart((Minecraft) (Object) this)) cir.setReturnValue(false);
	}

	@Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
	private void oceania$keepBreakingRaftBlock(boolean leftClick, CallbackInfo ci) {
		if (RaftTargeting.handleContinueAttack((Minecraft) (Object) this, leftClick)) ci.cancel();
	}
}

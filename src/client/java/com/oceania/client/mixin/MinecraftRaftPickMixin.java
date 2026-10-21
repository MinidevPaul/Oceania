package com.oceania.client.mixin;

import com.oceania.client.raft.RaftTargeting;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftRaftPickMixin {
	@Inject(method = "pick(F)V", at = @At("TAIL"))
	private void oceania$pickRaftBlocks(float partialTick, CallbackInfo ci) {
		RaftTargeting.pick((Minecraft) (Object) this, partialTick);
	}
}

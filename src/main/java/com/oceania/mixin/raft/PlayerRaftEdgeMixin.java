package com.oceania.mixin.raft;

import com.oceania.raft.RaftCollider;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public class PlayerRaftEdgeMixin {
	@Inject(method = "maybeBackOffFromEdge", at = @At("HEAD"), cancellable = true)
	private void oceania$keepStepOnDeck(Vec3 motion, MoverType type, CallbackInfoReturnable<Vec3> cir) {
		Player self = (Player) (Object) this;
		if (RaftCollider.standsOnDeck(self)) cir.setReturnValue(RaftCollider.backOffFromDeckEdge(self, motion, type));
	}
}

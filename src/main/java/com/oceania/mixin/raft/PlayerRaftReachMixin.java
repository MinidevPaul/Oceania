package com.oceania.mixin.raft;

import com.oceania.raft.RaftEntity;
import com.oceania.raft.RaftRider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerRaftReachMixin {
	@Inject(method = "isWithinBlockInteractionRange", at = @At("HEAD"), cancellable = true)
	private void oceania$raftMenuReach(BlockPos pos, double padding, CallbackInfoReturnable<Boolean> cir) {
		if (!((Object) this instanceof RaftRider rider)) return;
		RaftEntity raft = rider.oceania$menuRaft();
		if (raft == null) return;
		Player self = (Player) (Object) this;
		if (raft.isRemoved() || self.containerMenu == self.inventoryMenu) {
			rider.oceania$setMenuRaft(null);
			return;
		}
		if (raft.raftLevel().hasBlock(pos)) {
			cir.setReturnValue(raft.withinReach(self, pos, padding));
		}
	}
}

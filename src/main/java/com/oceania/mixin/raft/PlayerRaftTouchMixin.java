package com.oceania.mixin.raft;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.oceania.raft.RaftEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Player.class)
public class PlayerRaftTouchMixin {
	@WrapOperation(method = "aiStep", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/phys/AABB;minmax(Lnet/minecraft/world/phys/AABB;)Lnet/minecraft/world/phys/AABB;"))
	private AABB oceania$riderTouchesOnlyWhatItTouches(AABB playerBox, AABB vehicleBox, Operation<AABB> original) {
		Player self = (Player) (Object) this;
		if (self.getVehicle() instanceof RaftEntity) {
			return playerBox;
		}
		return original.call(playerBox, vehicleBox);
	}
}

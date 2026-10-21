package com.oceania.mixin.item;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.oceania.item.TurtleArmor;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Player.class)
public abstract class PlayerDestroySpeedMixin {
	@ModifyExpressionValue(method = "getDestroySpeed",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;isEyeInFluid(Lnet/minecraft/tags/TagKey;)Z"))
	private boolean oceania$fullTurtleSetMinesAsIfDry(boolean submerged) {
		return submerged && !TurtleArmor.fullSet((Player) (Object) this);
	}

	@ModifyExpressionValue(method = "getDestroySpeed",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;onGround()Z"))
	private boolean oceania$fullTurtleSetFloatsAsIfGrounded(boolean onGround) {
		Player self = (Player) (Object) this;
		return onGround || (self.isEyeInFluid(FluidTags.WATER) && TurtleArmor.fullSet(self));
	}
}

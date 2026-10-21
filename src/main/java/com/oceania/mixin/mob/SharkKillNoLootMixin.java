package com.oceania.mixin.mob;

import com.oceania.mob.SharkEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public class SharkKillNoLootMixin {

	@Inject(method = "dropAllDeathLoot", at = @At("HEAD"), cancellable = true)
	private void oceania$sharkKillsDropNothing(ServerLevel level, DamageSource source, CallbackInfo ci) {
		if (source.getEntity() instanceof SharkEntity && !((Object)this instanceof Player)) {
			ci.cancel();
		}
	}
}

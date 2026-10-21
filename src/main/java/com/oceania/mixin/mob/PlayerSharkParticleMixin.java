package com.oceania.mixin.mob;

import com.llamalad7.mixinextras.injector.WrapWithCondition;
import com.oceania.mob.SharkEntity;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Player.class)
public class PlayerSharkParticleMixin {

	@WrapWithCondition(
		method = "damageStatsAndHearts",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/server/level/ServerLevel;sendParticles"
				+ "(Lnet/minecraft/core/particles/ParticleOptions;DDDIDDDD)I"))
	private boolean oceania$noDamageHeartsOnSharks(ServerLevel level, ParticleOptions particle,
	                                               double x, double y, double z, int count,
	                                               double xDist, double yDist, double zDist,
	                                               double speed, Entity target) {

		return particle != ParticleTypes.DAMAGE_INDICATOR || !(target instanceof SharkEntity);
	}
}

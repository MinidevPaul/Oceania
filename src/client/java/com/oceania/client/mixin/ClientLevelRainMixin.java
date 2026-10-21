package com.oceania.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.oceania.client.weather.HeavyRain;
import com.oceania.client.weather.HeavyRainTuning;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ClientLevel.class)
public abstract class ClientLevelRainMixin {
	@WrapOperation(
		method = "tickWeatherEffects",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getRainLevel(F)F"))
	private float oceania$moreSplashes(ClientLevel level, float partialTick, Operation<Float> original) {
		return original.call(level, partialTick) * HeavyRain.splashMultiplier(level);
	}

	@WrapOperation(
		method = "tickWeatherEffects",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;playLocalSound(Lnet/minecraft/core/BlockPos;Lnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FFZ)V"))
	private void oceania$heavierRainSound(ClientLevel level, BlockPos pos, SoundEvent sound, SoundSource source,
	                                      float volume, float pitch, boolean distanceDelay, Operation<Void> original) {
		float s = HeavyRain.strength(level);
		float v = volume * (1.0F + s * (float) (HeavyRainTuning.SOUND_VOLUME_BOOST - 1.0));
		float p = Math.max(0.5F, pitch - s * (float) HeavyRainTuning.SOUND_PITCH_DROP);
		original.call(level, pos, sound, source, v, p, distanceDelay);
	}
}

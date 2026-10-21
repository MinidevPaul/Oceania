package com.oceania.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.oceania.client.weather.HeavyRain;
import com.oceania.client.weather.HeavyRainTuning;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(WeatherEffectRenderer.class)
public abstract class WeatherEffectRendererMixin {
	@WrapOperation(
		method = "extractRenderState",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getRainLevel(F)F"))
	private float oceania$heavierStreaks(ClientLevel level, float partialTick, Operation<Float> original) {
		return original.call(level, partialTick) * HeavyRain.streakMultiplier(level);
	}

	@WrapOperation(
		method = "prepare",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/texture/TextureManager;getTexture(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/client/renderer/texture/AbstractTexture;"))
	private AbstractTexture oceania$denserRainTexture(TextureManager manager, Identifier texture, Operation<AbstractTexture> original) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level != null && HeavyRain.VANILLA_RAIN.equals(texture) && HeavyRain.strength(level) >= 0.5F) {
			return original.call(manager, HeavyRain.HEAVY_RAIN);
		}
		return original.call(manager, texture);
	}

	@WrapOperation(
		method = "createRainColumnInstance",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/util/RandomSource;nextFloat()F"))
	private float oceania$fasterFall(RandomSource random, Operation<Float> original) {
		ClientLevel level = Minecraft.getInstance().level;
		float extra = level == null ? 0.0F : HeavyRain.strength(level) * (float) HeavyRainTuning.FALL_SPEED_ADD;
		return original.call(random) + extra;
	}
}

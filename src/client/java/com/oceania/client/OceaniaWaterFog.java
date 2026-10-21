package com.oceania.client;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.environment.FogEnvironment;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FogType;
import org.joml.Vector3f;
import org.joml.Vector3fc;

public final class OceaniaWaterFog extends FogEnvironment {
	private float smoothR = -1, smoothG, smoothB;
	private long lastMillis;

	@Override
	public boolean isApplicable(FogType fogType, Entity entity) {
		return fogType == FogType.WATER;
	}

	@Override
	public void setupFog(FogData fog, Camera camera, ClientLevel level, float renderDistance, DeltaTracker deltaTracker) {
		float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
		float nightVision = camera.entity() instanceof LivingEntity living ? nightVisionScale(living, partialTick) : 0.0F;
		double depth = UnderwaterView.depth(camera.position(), level);
		float end = (float) UnderwaterView.fogEnd(depth, renderDistance);
		end = Math.max(end, renderDistance * 0.5f * nightVision);
		fog.environmentalStart = end * 0.15f;
		fog.environmentalEnd = end;

		fog.skyEnd = end;
		fog.cloudEnd = end;
	}

	@Override
	public boolean providesColor() {
		return true;
	}

	@Override
	public Vector3fc getBaseColor(ClientLevel level, Camera camera, int renderDistance, float partialTick) {
		Vector3fc biome = camera.attributeProbe().getValue(EnvironmentAttributes.WATER_FOG_COLOR, partialTick);
		float r = biome.x(), g = biome.y(), b = biome.z();
		float max = Math.max(r, Math.max(g, b));
		if (max > 0) {
			r /= max;
			g /= max;
			b /= max;
		}
		double depth = UnderwaterView.depth(camera.position(), level);

		float skyLight = camera.attributeProbe().getValue(EnvironmentAttributes.SKY_LIGHT_FACTOR, partialTick);
		float bright = UnderwaterView.brightness(depth) * skyLight;
		if (camera.entity() instanceof LivingEntity living) {
			bright = Math.max(bright, 0.6f * nightVisionScale(living, partialTick));
		}
		r *= bright;
		g *= bright;
		b *= bright;
		long now = Util.getMillis();
		if (smoothR < 0 || now - lastMillis > 2000) {
			smoothR = r;
			smoothG = g;
			smoothB = b;
		} else {
			float k = 1 - (float) Math.exp(-(now - lastMillis) / 1500.0);
			smoothR += (r - smoothR) * k;
			smoothG += (g - smoothG) * k;
			smoothB += (b - smoothB) * k;
		}
		lastMillis = now;
		return new Vector3f(smoothR, smoothG, smoothB);
	}

	static float nightVisionScale(LivingEntity living, float partialTick) {
		MobEffectInstance effect = living.getEffect(MobEffects.NIGHT_VISION);
		if (effect == null) return 0.0F;
		if (effect.isInfiniteDuration()) return 1.0F;
		int left = effect.getDuration();
		return left > 200 ? 1.0F : 0.7F + Mth.sin((left - partialTick) * (float) Math.PI * 0.2F) * 0.3F;
	}
}

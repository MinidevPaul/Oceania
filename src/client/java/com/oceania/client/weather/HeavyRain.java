package com.oceania.client.weather;

import com.oceania.Oceania;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

@Environment(EnvType.CLIENT)
public final class HeavyRain {

	public static final Identifier VANILLA_RAIN =
		Identifier.withDefaultNamespace("textures/environment/rain.png");

	public static final Identifier HEAVY_RAIN = Oceania.id("textures/environment/rain_heavy.png");

	private HeavyRain() {
	}

	private static double raw(long dayTime) {
		double t = dayTime;
		return Math.sin(t / HeavyRainTuning.PERIOD_1 * (Math.PI * 2))
			+ HeavyRainTuning.WEIGHT_2 * Math.sin(t / HeavyRainTuning.PERIOD_2 * (Math.PI * 2) + HeavyRainTuning.PHASE_2)
			+ HeavyRainTuning.WEIGHT_3 * Math.sin(t / HeavyRainTuning.PERIOD_3 * (Math.PI * 2) + HeavyRainTuning.PHASE_3);
	}

	public static float strength(ClientLevel level) {
		return strengthAt(dayTime(level));
	}

	public static long dayTime(ClientLevel level) {
		return level.getOverworldClockTime();
	}

	public static float strengthAt(long dayTime) {
		double r = raw(dayTime);
		double lo = HeavyRainTuning.THRESHOLD - HeavyRainTuning.SOFTNESS;
		double hi = HeavyRainTuning.THRESHOLD + HeavyRainTuning.SOFTNESS;
		double t = Mth.clamp((r - lo) / (hi - lo), 0.0, 1.0);
		return (float) (t * t * (3.0 - 2.0 * t));
	}

	public static double rawFor(ClientLevel level) {
		return raw(dayTime(level));
	}

	public static float streakMultiplier(ClientLevel level) {
		return 1.0F + strength(level) * (float) (HeavyRainTuning.STREAK_BOOST - 1.0);
	}

	public static float splashMultiplier(ClientLevel level) {
		return 1.0F + strength(level) * (float) (HeavyRainTuning.SPLASH_BOOST - 1.0);
	}
}

package com.oceania.client.biolum;

import com.oceania.Oceania;
import com.oceania.mob.SharkEntity;
import com.oceania.mob.WhaleEntity;
import com.oceania.mob.WhaleHitboxPart;
import com.oceania.raft.RaftEntity;
import com.oceania.registry.OceaniaParticles;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
public final class SurfaceGlow {
	private static final boolean ENABLED = !"false".equalsIgnoreCase(System.getProperty("oceania.biolum", "true"));

	private static final double RATE = num("oceania.biolum.rate", 1.0, 0.0, 8.0);

	private static final double REACH = num("oceania.biolum.reach", 72.0, 8.0, 256.0);

	private static final double SANE_SPEED = 4.0;
	private static boolean loggedStatus;

	private SurfaceGlow() {
	}

	public static void init() {
		if (!ENABLED) {
			Oceania.LOGGER.info("[biolum] oceania.biolum=false, surface bioluminescence is off");
			return;
		}
		ClientTickEvents.END_CLIENT_TICK.register(SurfaceGlow::tick);
	}

	private static void tick(Minecraft client) {
		ClientLevel level = client.level;
		if (level == null || client.isPaused() || client.player == null) return;

		level.updateSkyBrightness();
		float darkness = darkStrengthFor(level.getSkyDarken());
		if (darkness <= 0.0F) return;
		logStatusOnce(client);
		double thinning = thinningFor(client);
		if (thinning <= 0.0) return;
		Vec3 eye = client.player.position();
		RandomSource random = level.getRandom();
		for (Entity entity : level.entitiesForRendering()) {
			if (!triggersFor(entity)) continue;
			if (!entity.isInWater()) continue;
			if (entity.distanceToSqr(eye) > REACH * REACH) continue;
			double dx = entity.getX() - entity.xOld;
			double dy = entity.getY() - entity.yOld;
			double dz = entity.getZ() - entity.zOld;
			double speed = Math.sqrt(dx * dx + dy * dy + dz * dz);
			if (speed < SurfaceGlowTuning.MIN_SPEED || speed > SANE_SPEED) continue;

			double surface = waterSurfaceY(level, entity);
			if (Double.isNaN(surface)) continue;
			double depth = surface - entity.getY();
			float near = depthStrengthFor(depth);
			if (near <= 0.0F) continue;
			emit(level, client.particleEngine, entity, dx, dz, speed, darkness * near * thinning, random, surface);
		}
	}

	static boolean triggersFor(Entity entity) {
		if (entity.isPassenger()) return false;
		if (entity instanceof WhaleHitboxPart) return false;
		return entity instanceof Player
			|| entity instanceof RaftEntity
			|| entity instanceof AbstractBoat
			|| entity instanceof SharkEntity
			|| entity instanceof WhaleEntity;
	}

	static int skyDarkenTranscribed(ClientLevel level) {
		return (int) (15.0F - level.environmentAttributes().getDimensionValue(net.minecraft.world.attribute.EnvironmentAttributes.SKY_LIGHT_LEVEL));
	}

	static float darkStrengthFor(int skyDarken) {
		if (skyDarken <= SurfaceGlowTuning.SKY_DARKEN_MIN) return 0.0F;
		if (skyDarken >= SurfaceGlowTuning.SKY_DARKEN_FULL) return 1.0F;
		return (float) (skyDarken - SurfaceGlowTuning.SKY_DARKEN_MIN) / (SurfaceGlowTuning.SKY_DARKEN_FULL - SurfaceGlowTuning.SKY_DARKEN_MIN);
	}

	static double waterSurfaceY(ClientLevel level, Entity entity) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(
			Mth.floor(entity.getX()),
			Mth.floor(entity.getY() + entity.getBbHeight() * 0.5),
			Mth.floor(entity.getZ()));
		int dropped = 0;
		while (!level.getFluidState(pos).is(FluidTags.WATER) && dropped < 4) {
			pos.move(Direction.DOWN);
			dropped++;
		}
		if (!level.getFluidState(pos).is(FluidTags.WATER)) return Double.NaN;
		int limit = (int) Math.ceil(SurfaceGlowTuning.DEPTH_FADE) + 2;
		for (int i = 0; i < limit; i++) {
			if (!level.getFluidState(pos).is(FluidTags.WATER)) {
				pos.move(Direction.DOWN);
				return pos.getY() + level.getFluidState(pos).getHeight(level, pos);
			}
			pos.move(Direction.UP);
		}
		return Double.NaN;
	}

	static float depthStrengthFor(double depth) {
		if (depth <= -SurfaceGlowTuning.ABOVE_SURFACE) return 0.0F;
		if (depth <= SurfaceGlowTuning.DEPTH_FULL) return 1.0F;
		if (depth >= SurfaceGlowTuning.DEPTH_FADE) return 0.0F;
		return (float) (1.0 - (depth - SurfaceGlowTuning.DEPTH_FULL) / (SurfaceGlowTuning.DEPTH_FADE - SurfaceGlowTuning.DEPTH_FULL));
	}

	static double thinningFor(Minecraft client) {
		double k = switch (client.options.particles().get()) {
			case ALL -> 1.0;
			case DECREASED -> 0.5;
			case MINIMAL -> 0.25;
		};
		return k * RATE;
	}

	private static void logStatusOnce(Minecraft client) {
		if (loggedStatus) return;
		loggedStatus = true;
		Oceania.LOGGER.info("[biolum] on, particles option {} -> rate x{} (including x{} from -Doceania.biolum.rate)",
			client.options.particles().get(), thinningFor(client), RATE);
	}

	private static void emit(ClientLevel level, ParticleEngine engine, Entity entity, double dx, double dz,
	                         double speed, double strength, RandomSource random, double surface) {
		double width = Math.max(0.6, entity.getBbWidth());
		double k = Mth.clamp((speed - SurfaceGlowTuning.MIN_SPEED) / (SurfaceGlowTuning.FULL_SPEED - SurfaceGlowTuning.MIN_SPEED), 0.0, 1.0);
		double rate = SurfaceGlowTuning.RATE_MIN + (SurfaceGlowTuning.RATE_MAX - SurfaceGlowTuning.RATE_MIN) * k;
		rate *= 1.0 + (SurfaceGlowTuning.SIZE_BONUS - 1.0) * Math.min(1.0, (width - 1.0) / 3.0);
		rate *= strength;
		int count = (int) rate;
		if (random.nextDouble() < rate - count) count++;
		if (count <= 0) return;

		double inv = 1.0 / Math.max(1.0E-4, Math.sqrt(dx * dx + dz * dz));
		double fx = dx * inv, fz = dz * inv;
		double nx = -fz, nz = fx;

		double y = surface - 0.05;
		BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
		for (int i = 0; i < count; i++) {
			double across = (random.nextDouble() - 0.5) * width * SurfaceGlowTuning.SPREAD;
			double px = entity.getX() - fx * SurfaceGlowTuning.BEHIND + nx * across;
			double pz = entity.getZ() - fz * SurfaceGlowTuning.BEHIND + nz * across;
			double out = SurfaceGlowTuning.DRIFT * Math.signum(across == 0 ? 1 : across) * (0.5 + random.nextDouble());

			if (!level.getFluidState(probe.set(Mth.floor(px), Mth.floor(y), Mth.floor(pz))).is(FluidTags.WATER)) {
				continue;
			}
			engine.createParticle(
				OceaniaParticles.BIOLUM,
				px, y + (random.nextDouble() - 0.5) * 0.12, pz,
				nx * out - dx * 0.05 + (random.nextDouble() - 0.5) * SurfaceGlowTuning.DRIFT,
				(random.nextDouble() - 0.5) * SurfaceGlowTuning.DRIFT * 0.4,
				nz * out - dz * 0.05 + (random.nextDouble() - 0.5) * SurfaceGlowTuning.DRIFT);
		}
	}

	private static double num(String key, double fallback, double min, double max) {
		String v = System.getProperty(key);
		if (v == null) return fallback;
		try {
			return Mth.clamp(Double.parseDouble(v), min, max);
		} catch (NumberFormatException e) {
			return fallback;
		}
	}
}

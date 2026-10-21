package com.oceania.client.biolum;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

@Environment(EnvType.CLIENT)
public class BiolumParticle extends SingleQuadParticle {
	private final float baseSize;

	BiolumParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites) {
		super(level, x, y, z, 0, 0, 0, sprites.first());
		this.xd = vx;
		this.yd = vy;
		this.zd = vz;

		this.gravity = 0.0F;
		this.hasPhysics = false;
		this.friction = (float) SurfaceGlowTuning.FRICTION;
		this.lifetime = SurfaceGlowTuning.LIFE_MIN + this.random.nextInt(SurfaceGlowTuning.LIFE_MAX - SurfaceGlowTuning.LIFE_MIN + 1);
		this.baseSize = (float) (SurfaceGlowTuning.SIZE_MIN + this.random.nextDouble() * (SurfaceGlowTuning.SIZE_MAX - SurfaceGlowTuning.SIZE_MIN));
		this.quadSize = this.baseSize;
		this.setSpriteFromAge(sprites);
		applyAge(0.0F);
	}

	@Override
	protected Layer getLayer() {
		return Layer.TRANSLUCENT;
	}

	@Override
	protected int getLightCoords(float partialTick) {
		return 240;
	}

	@Override
	public void tick() {
		super.tick();
		if (!this.removed) applyAge((float) this.age / this.lifetime);
	}

	private void applyAge(float f) {
		f = Mth.clamp(f, 0.0F, 1.0F);
		int from;
		int to;
		float k;
		if (f < 0.25F) {
			from = SurfaceGlowTuning.COLOR_CORE;
			to = SurfaceGlowTuning.COLOR_BODY;
			k = f / 0.25F;
		} else {
			from = SurfaceGlowTuning.COLOR_BODY;
			to = SurfaceGlowTuning.COLOR_TAIL;
			k = (f - 0.25F) / 0.75F;
		}
		this.setColor(lerpChannel(from, to, k, 16), lerpChannel(from, to, k, 8), lerpChannel(from, to, k, 0));

		float alpha;
		if (f < SurfaceGlowTuning.FADE_IN) {
			alpha = (float) (f / SurfaceGlowTuning.FADE_IN * SurfaceGlowTuning.ALPHA_PEAK);
		} else {
			float g = (float) ((f - SurfaceGlowTuning.FADE_IN) / (1.0 - SurfaceGlowTuning.FADE_IN));
			alpha = (float) (SurfaceGlowTuning.ALPHA_DISCARD + (SurfaceGlowTuning.ALPHA_PEAK - SurfaceGlowTuning.ALPHA_DISCARD) * Math.pow(1.0F - g, 1.6));
		}
		this.setAlpha(alpha);

		this.quadSize = this.baseSize * (0.75F + 0.45F * Mth.sin(f * Mth.PI));
	}

	private static float lerpChannel(int from, int to, float k, int shift) {
		float a = (from >> shift & 0xFF) / 255.0F;
		float b = (to >> shift & 0xFF) / 255.0F;
		return a + (b - a) * k;
	}

	@Environment(EnvType.CLIENT)
	public record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
		@Override
		public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
		                               double vx, double vy, double vz, RandomSource random) {
			return new BiolumParticle(level, x, y, z, vx, vy, vz, this.sprites);
		}
	}
}

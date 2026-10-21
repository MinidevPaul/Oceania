package com.oceania.mob;

import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

public class WhaleSurfaceGoal extends Goal {

	private static final int SURFACE_AT = 1200;

	private static final double ASCENT_RATE = 0.09;
	private static final double DESCENT_RATE = 0.09;

	private static final double TRIM_RATE = 0.02;

	private static final float CLIMB_PUSH = 0.010F;

	private static final float HOLD_PUSH = 0.006F;
	private static final float DIVE_PUSH = 0.010F;

	private static final float STEER_RATE = 2.0F;
	private static final float MAX_PITCH = 35.0F;

	private static final int ASCEND_MAX = 900;

	private static final int BLOWS_MIN = 6;
	private static final int BLOWS_MAX = 12;
	private static final int BLOW_INTERVAL_MIN = 70;
	private static final int BLOW_INTERVAL_MAX = 110;

	private static final double DIVE_DEPTH = 40.0;
	private static final int DIVE_MAX = 900;

	private static final double BLOW_STATION = 0.185;
	private static final double BLOW_FORWARD = (0.5 - BLOW_STATION) * WhaleHitboxPart.BODY_LENGTH;
	private static final double BLOW_UP = 3.44;

	private static final double SPOUT_HEIGHT = 9.0;
	private static final int SPOUT_PUFFS = 14;

	private static final double SPOUT_RADIUS = 0.35;
	private static final double SPOUT_RISE = 0.07;

	private static final int SURFACE_SCAN = 96;

	private enum Phase { ASCEND, BLOW, DIVE, DONE }

	private final WhaleEntity whale;
	private final BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();

	private Phase phase = Phase.DONE;
	private int phaseTicks;
	private int blowsLeft;
	private int nextBlowIn;

	public WhaleSurfaceGoal(WhaleEntity whale) {
		this.whale = whale;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		return this.whale.isInWater() && this.whale.getAirSupply() <= SURFACE_AT;
	}

	@Override
	public boolean isInterruptable() {
		return false;
	}

	@Override
	public boolean canContinueToUse() {
		return this.phase != Phase.DONE && this.whale.isInWater();
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void start() {

		this.whale.getNavigation().stop();
		enter(Phase.ASCEND);
	}

	@Override
	public void stop() {
		this.phase = Phase.DONE;
		this.whale.setSurfacePhase(WhaleEntity.PHASE_DOWN);
	}

	private void enter(Phase next) {
		this.phase = next;
		this.phaseTicks = 0;
		this.whale.setSurfacePhase(switch (next) {
			case ASCEND -> WhaleEntity.PHASE_ASCEND;
			case BLOW -> WhaleEntity.PHASE_BLOW;

			case DIVE -> WhaleEntity.PHASE_SOUND;
			case DONE -> WhaleEntity.PHASE_DOWN;
		});
		if (next == Phase.BLOW) {
			this.blowsLeft = BLOWS_MIN + this.whale.getRandom().nextInt(BLOWS_MAX - BLOWS_MIN + 1);
			this.nextBlowIn = 0;
		}
	}

	@Override
	public void tick() {
		this.phaseTicks++;
		switch (this.phase) {
			case ASCEND -> tickAscend();
			case BLOW -> tickBlow();
			case DIVE -> tickDive();
			case DONE -> {
			}
		}
	}

	private void tickAscend() {
		if (!this.whale.isEyeInFluid(FluidTags.WATER)) {
			enter(Phase.BLOW);
			return;
		}
		if (this.phaseTicks > ASCEND_MAX) {
			enter(Phase.DIVE);
			return;
		}
		drive(ASCENT_RATE, CLIMB_PUSH);
	}

	private void tickBlow() {
		drive(this.whale.isEyeInFluid(FluidTags.WATER) ? TRIM_RATE : -TRIM_RATE, HOLD_PUSH);

		if (--this.nextBlowIn > 0) {
			return;
		}
		if (this.whale.level() instanceof ServerLevel server) {
			blow(server);
		}
		this.nextBlowIn = BLOW_INTERVAL_MIN
			+ this.whale.getRandom().nextInt(BLOW_INTERVAL_MAX - BLOW_INTERVAL_MIN + 1);
		if (--this.blowsLeft <= 0) {
			enter(Phase.DIVE);
		}
	}

	private void tickDive() {
		double depth = this.whale.level().getSeaLevel() - this.whale.getY();
		if (depth >= DIVE_DEPTH || this.phaseTicks > DIVE_MAX) {
			enter(Phase.DONE);
			return;
		}
		drive(-DESCENT_RATE, DIVE_PUSH);
	}

	private void drive(double climb, float push) {
		Vec3 heading = this.whale.getLookAngle();

		Vec3 flat = new Vec3(heading.x, 0.0, heading.z);
		if (flat.lengthSqr() < 1.0E-6) {
			flat = new Vec3(0.0, 0.0, 1.0);
		}

		this.whale.steerToward(flat.normalize().add(0.0, climb * 8.0, 0.0),
			STEER_RATE, MAX_PITCH);

		this.whale.moveRelative(push, new Vec3(0.0, 0.0, 1.0));
		Vec3 delta = this.whale.getDeltaMovement();
		this.whale.setDeltaMovement(delta.x, climb, delta.z);
	}

	private void blow(ServerLevel level) {
		Vec3 origin = blowhole();
		double surface = findSurface();

		level.playSound(null, origin.x, surface, origin.z,
			com.oceania.registry.OceaniaSounds.WHALE_BLOW,
			net.minecraft.sounds.SoundSource.NEUTRAL,
			2.6F, 0.92F + this.whale.getRandom().nextFloat() * 0.16F);

		for (int i = 0; i < SPOUT_PUFFS; i++) {
			double up = (i + 0.5) / SPOUT_PUFFS * SPOUT_HEIGHT;

			double spread = SPOUT_RADIUS * (0.4 + up / SPOUT_HEIGHT);
			double x = origin.x + (this.whale.getRandom().nextDouble() * 2.0 - 1.0) * spread;
			double z = origin.z + (this.whale.getRandom().nextDouble() * 2.0 - 1.0) * spread;
			sendFar(level, ParticleTypes.CAMPFIRE_COSY_SMOKE, x, origin.y + up, z,
				0.0, SPOUT_RISE, 0.0);
			if (i < SPOUT_PUFFS / 3) {

				sendFar(level, ParticleTypes.CLOUD, x, origin.y + up * 0.6, z,
					0.0, SPOUT_RISE * 1.6, 0.0);
			}
		}

		for (int i = 0; i < 6; i++) {
			double x = origin.x + (this.whale.getRandom().nextDouble() * 2.0 - 1.0) * 1.2;
			double z = origin.z + (this.whale.getRandom().nextDouble() * 2.0 - 1.0) * 1.2;
			sendFar(level, ParticleTypes.SPLASH, x, surface + 0.1, z, 0.0, 0.3, 0.0);
		}
	}

	private Vec3 blowhole() {
		float yaw = this.whale.getYRot() * Mth.DEG_TO_RAD;
		float pitch = this.whale.getXRot() * Mth.DEG_TO_RAD;
		double horizontal = BLOW_FORWARD * Mth.cos(pitch);
		return new Vec3(
			this.whale.getX() - Mth.sin(yaw) * horizontal,
			this.whale.getY() + BLOW_UP - BLOW_FORWARD * Mth.sin(pitch),
			this.whale.getZ() + Mth.cos(yaw) * horizontal);
	}

	private double findSurface() {
		this.probe.set(Mth.floor(this.whale.getX()), Mth.floor(this.whale.getEyeY()),
			Mth.floor(this.whale.getZ()));
		for (int i = 0; i < SURFACE_SCAN; i++) {
			if (!this.whale.level().getFluidState(this.probe).is(FluidTags.WATER)) {
				BlockPos top = this.probe.below();
				FluidState fluid = this.whale.level().getFluidState(top);
				return fluid.isEmpty()
					? top.getY()
					: top.getY() + fluid.getHeight(this.whale.level(), top);
			}
			this.probe.move(Direction.UP);
		}
		return this.whale.getEyeY();
	}

	private static <T extends ParticleOptions> void sendFar(ServerLevel level, T particle,
	                                                        double x, double y, double z,
	                                                        double vx, double vy, double vz) {
		for (ServerPlayer player : level.players()) {

			level.sendParticles(player, particle, true, true, x, y, z, 0, vx, vy, vz, 1.0);
		}
	}
}

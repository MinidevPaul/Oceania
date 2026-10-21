package com.oceania.mob;

import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

public class WhaleCruiseGoal extends Goal {

	private static final double LEG_MIN = 24.0;
	private static final double LEG_MAX = 64.0;

	private static final double DEPTH_MIN = 30.0;
	private static final double DEPTH_MAX = 70.0;

	private static final double DEPTH_CORRECTION = 0.4;

	private static final double MAX_LEG_CLIMB = 24.0;

	private static final float TURN_SPREAD = 1.0F;

	private static final int LEG_TIMEOUT = 900;

	private final WhaleEntity whale;

	private Vec3 leg = Vec3.ZERO;
	private int ticks;
	private int repickCooldown;

	private static final int REPICK_INTERVAL = 20;

	public WhaleCruiseGoal(WhaleEntity whale) {
		this.whale = whale;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE));
	}

	private static final double CRUISE_SPEED = 0.30;

	private static final float TURN_RATE = 3.0F;

	private static final float MAX_PITCH = 30.0F;

	private static final double ARRIVE_RADIUS = 10.0;

	private static final double LOOKAHEAD = 14.0;

	@Override
	public boolean canUse() {
		return this.whale.isInWater() && pickLeg();
	}

	@Override
	public boolean canContinueToUse() {
		return this.whale.isInWater()
			&& this.ticks < LEG_TIMEOUT
			&& this.whale.position().distanceToSqr(this.leg) > ARRIVE_RADIUS * ARRIVE_RADIUS;
	}

	@Override
	public void start() {
		this.ticks = 0;
		this.whale.getNavigation().stop();
	}

	@Override
	public void tick() {
		this.ticks++;

		if (this.repickCooldown > 0) {
			this.repickCooldown--;
		} else if (!isSwimmable(this.whale.position().add(heading().scale(LOOKAHEAD)))) {

			pickLeg();
			this.repickCooldown = REPICK_INTERVAL;
		}

		Vec3 toLeg = this.leg.subtract(this.whale.position());
		if (toLeg.lengthSqr() > 1.0E-4) {
			this.whale.steerToward(toLeg, TURN_RATE, MAX_PITCH);
		}

		this.whale.setDeltaMovement(heading().scale(CRUISE_SPEED));
	}

	private Vec3 heading() {
		float yaw = this.whale.getYRot() * Mth.DEG_TO_RAD;
		float pitch = this.whale.getXRot() * Mth.DEG_TO_RAD;
		float horiz = Mth.cos(pitch);
		return new Vec3(-Mth.sin(yaw) * horiz, -Mth.sin(pitch), Mth.cos(yaw) * horiz);
	}

	private static final int LEG_TRIES = 6;

	private static final int CLEARANCE_SAMPLES = 5;

	private boolean pickLeg() {
		Vec3 best = null;
		for (int attempt = 0; attempt < LEG_TRIES; attempt++) {
			Vec3 candidate = candidateLeg();
			best = candidate;
			if (isSwimmable(candidate)) {
				break;
			}
		}
		this.leg = best;
		return true;
	}

	private Vec3 candidateLeg() {
		float yaw = this.whale.getYRot() * Mth.DEG_TO_RAD
			+ (this.whale.getRandom().nextFloat() - 0.5F) * 2.0F * TURN_SPREAD;
		double dist = LEG_MIN + this.whale.getRandom().nextDouble() * (LEG_MAX - LEG_MIN);
		double x = this.whale.getX() - Mth.sin(yaw) * dist;
		double z = this.whale.getZ() + Mth.cos(yaw) * dist;

		double wantDepth = DEPTH_MIN + this.whale.getRandom().nextDouble() * (DEPTH_MAX - DEPTH_MIN);
		double wantY = this.whale.level().getSeaLevel() - wantDepth;
		double climb = Mth.clamp((wantY - this.whale.getY()) * DEPTH_CORRECTION,
			-MAX_LEG_CLIMB, MAX_LEG_CLIMB);
		return new Vec3(x, this.whale.getY() + climb, z);
	}

	private boolean isSwimmable(Vec3 target) {
		Vec3 from = this.whale.position();
		for (int i = 1; i <= CLEARANCE_SAMPLES; i++) {
			Vec3 at = from.lerp(target, (double) i / CLEARANCE_SAMPLES);
			if (!this.whale.level().getFluidState(BlockPos.containing(at))
				.is(FluidTags.WATER)) {
				return false;
			}
		}
		return true;
	}
}

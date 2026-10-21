package com.oceania.mob;

import java.util.EnumSet;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

public class WhaleLungeGoal extends Goal {

	private static final int INTERVAL_MIN = 3600;
	private static final int INTERVAL_MAX = 6000;

	private static final int OPEN_TICKS = 40;
	private static final int HOLD_TICKS = 20;
	private static final int CLOSE_TICKS = 120;
	private static final int RECOVER_TICKS = 60;

	private static final float CRUISE_PUSH = 0.012F;
	private static final float LUNGE_PUSH = 0.015F;

	private static final float STEER_RATE = 1.5F;
	private static final float MAX_PITCH = 20.0F;

	private static final int MIN_AIR = 2400;

	private enum Phase { OPEN, HOLD, CLOSE, RECOVER }

	private final WhaleEntity whale;

	private Phase phase = Phase.OPEN;
	private int phaseTicks;

	private boolean finished;

	private int nextLungeAt = -1;

	public WhaleLungeGoal(WhaleEntity whale) {
		this.whale = whale;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		if (!this.whale.isInWater() || this.whale.getAirSupply() < MIN_AIR) {
			return false;
		}
		if (this.nextLungeAt < 0) {

			schedule();
			return false;
		}
		return this.whale.tickCount >= this.nextLungeAt;
	}

	@Override
	public boolean canContinueToUse() {
		return !this.finished && this.whale.isInWater();
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void start() {

		this.whale.getNavigation().stop();
		this.phase = Phase.OPEN;
		this.phaseTicks = 0;
		this.finished = false;
	}

	@Override
	public void stop() {
		this.whale.setGape(0.0F);
		schedule();
	}

	private void schedule() {
		this.nextLungeAt = this.whale.tickCount + INTERVAL_MIN
			+ this.whale.getRandom().nextInt(INTERVAL_MAX - INTERVAL_MIN + 1);
	}

	@Override
	public void tick() {
		if (this.finished) {

			return;
		}
		this.phaseTicks++;
		int length = switch (this.phase) {
			case OPEN -> OPEN_TICKS;
			case HOLD -> HOLD_TICKS;
			case CLOSE -> CLOSE_TICKS;
			case RECOVER -> RECOVER_TICKS;
		};

		float t = Mth.clamp((float) this.phaseTicks / length, 0.0F, 1.0F);

		float gape = switch (this.phase) {
			case OPEN -> t;
			case HOLD -> 1.0F;
			case CLOSE -> 1.0F - t;
			case RECOVER -> 0.0F;
		};

		this.whale.setGape(gape);

		float push = switch (this.phase) {
			case OPEN -> Mth.lerp(t, CRUISE_PUSH, LUNGE_PUSH);
			case HOLD -> LUNGE_PUSH;
			case CLOSE -> Mth.lerp(t, LUNGE_PUSH, CRUISE_PUSH);
			case RECOVER -> CRUISE_PUSH;
		};
		drive(push);

		if (this.phaseTicks >= length) {
			this.phaseTicks = 0;
			switch (this.phase) {
				case OPEN -> this.phase = Phase.HOLD;
				case HOLD -> this.phase = Phase.CLOSE;
				case CLOSE -> this.phase = Phase.RECOVER;

				case RECOVER -> this.finished = true;
			}
		}
	}

	private void drive(float push) {
		Vec3 heading = this.whale.getLookAngle();
		Vec3 flat = new Vec3(heading.x, 0.0, heading.z);
		if (flat.lengthSqr() < 1.0E-6) {
			flat = new Vec3(0.0, 0.0, 1.0);
		}
		this.whale.steerToward(flat.normalize(), STEER_RATE, MAX_PITCH);
		this.whale.moveRelative(push, new Vec3(0.0, 0.0, 1.0));
	}
}

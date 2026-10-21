package com.oceania.mob;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class GiantSquidCruiseGoal extends Goal {

	private static final int HOLD_MIN = 90;
	private static final int HOLD_MAX = 260;

	private static final float DEFLECT = 0.9F;

	private static final int COMFORTABLE_DEPTH = 70;

	private final GiantSquidEntity squid;
	private int hold;

	public GiantSquidCruiseGoal(GiantSquidEntity squid) {
		this.squid = squid;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		return this.squid.getTarget() == null;
	}

	@Override
	public boolean canContinueToUse() {
		return this.squid.getTarget() == null;
	}

	@Override
	public boolean requiresUpdateEveryTick() {

		return true;
	}

	@Override
	public void start() {
		this.hold = 0;
	}

	@Override
	public void tick() {
		if (--this.hold > 0) {
			return;
		}
		this.hold = HOLD_MIN + this.squid.getRandom().nextInt(HOLD_MAX - HOLD_MIN);

		float yaw = this.squid.headYaw
			+ (this.squid.getRandom().nextFloat() - 0.5F) * 2.0F * DEFLECT;
		float pitch = this.squid.headPitch
			+ (this.squid.getRandom().nextFloat() - 0.5F) * DEFLECT;

		double below = this.squid.level().getSeaLevel() - this.squid.getY();
		if (below < COMFORTABLE_DEPTH) {
			pitch += 0.35F * (float) ((COMFORTABLE_DEPTH - below) / COMFORTABLE_DEPTH);
		}

		this.squid.setWantedHeading(yaw, Mth.clamp(pitch, -0.9F, 0.9F));

		if (this.squid.getRandom().nextInt(5) == 0) {
			this.squid.beginJet();
		}
		this.squid.relaxStrike();
	}

	Vec3 heading() {
		return this.squid.headingVector();
	}
}

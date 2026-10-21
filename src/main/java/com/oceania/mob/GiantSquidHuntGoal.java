package com.oceania.mob;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class GiantSquidHuntGoal extends Goal {

	private static final double HOLD_RANGE = 6.0;

	private static final int WIND_UP = 16;

	private static final int WITHDRAW = 34;

	private enum Phase { CLOSE, WIND_UP, WITHDRAW }

	private final GiantSquidEntity squid;
	private Phase phase = Phase.CLOSE;
	private int timer;

	public GiantSquidHuntGoal(GiantSquidEntity squid) {
		this.squid = squid;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		LivingEntity target = this.squid.getTarget();
		return target != null && target.isAlive();
	}

	@Override
	public boolean canContinueToUse() {
		return this.canUse();
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void start() {
		this.phase = Phase.CLOSE;
		this.timer = 0;
	}

	@Override
	public void stop() {
		this.squid.relaxStrike();
	}

	@Override
	public void tick() {
		LivingEntity target = this.squid.getTarget();
		if (target == null) {
			return;
		}
		double dist = this.squid.distanceTo(target);

		switch (this.phase) {
			case CLOSE -> {

				Vec3 lead = target.position().add(target.getDeltaMovement().scale(6.0));
				this.squid.aimAt(lead);
				this.squid.relaxStrike();
				if (dist > 30.0 && this.squid.getRandom().nextInt(40) == 0) {

					this.squid.beginJet();
				}

				if (dist <= HOLD_RANGE
					&& this.squid.isFacing(target, GiantSquidEntity.STRIKE_ARC)) {
					this.phase = Phase.WIND_UP;
					this.timer = WIND_UP;
				}
			}
			case WIND_UP -> {
				this.squid.aimAt(target.position());
				if (--this.timer <= 0) {

					if (this.squid.canStrike() && dist <= GiantSquidEntity.STRIKE_REACH
						&& this.squid.isFacing(target, GiantSquidEntity.STRIKE_ARC)) {
						this.squid.strike(target);
					}
					this.phase = Phase.WITHDRAW;
					this.timer = WITHDRAW;
					this.squid.beginJet();
				}
			}
			case WITHDRAW -> {

				this.squid.aimAt(target.position());
				this.squid.relaxStrike();
				if (--this.timer <= 0) {
					this.phase = Phase.CLOSE;
				}
			}
		}
	}
}

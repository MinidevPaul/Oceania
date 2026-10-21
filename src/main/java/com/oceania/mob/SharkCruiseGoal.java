package com.oceania.mob;

import java.util.EnumSet;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

public class SharkCruiseGoal extends Goal {

	private final SharkEntity shark;
	private final double speed;
	private final int legLength;

	private Vec3 leg = Vec3.ZERO;
	private int ticks;

	public SharkCruiseGoal(SharkEntity shark, double speed, int legLength) {
		this.shark = shark;
		this.speed = speed;
		this.legLength = legLength;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE));
	}

	@Override
	public boolean canUse() {
		return this.shark.isInWater() && this.shark.getTarget() == null && pickLeg();
	}

	@Override
	public boolean canContinueToUse() {
		return this.shark.isInWater()
			&& this.shark.getTarget() == null
			&& !this.shark.getNavigation().isDone()
			&& this.ticks < 400;
	}

	@Override
	public void start() {
		this.ticks = 0;
		this.shark.getNavigation().moveTo(this.leg.x, this.leg.y, this.leg.z, this.speed);
	}

	@Override
	public void tick() {
		this.ticks++;
	}

	private boolean pickLeg() {

		float yaw = this.shark.getYRot() * Mth.DEG_TO_RAD
			+ (this.shark.getRandom().nextFloat() - 0.5F) * 1.6F;
		double dist = 20.0 + this.shark.getRandom().nextDouble() * (this.legLength - 20.0);
		double x = this.shark.getX() - Mth.sin(yaw) * dist;
		double z = this.shark.getZ() + Mth.cos(yaw) * dist;
		double y = this.shark.getY() + (this.shark.getRandom().nextDouble() - 0.5) * 12.0;
		this.leg = new Vec3(x, y, z);
		return true;
	}
}

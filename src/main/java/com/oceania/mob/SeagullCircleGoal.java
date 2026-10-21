package com.oceania.mob;

import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

public class SeagullCircleGoal extends Goal {

	private static final float RADIUS_MIN = 28.0F;
	private static final float RADIUS_MAX = 64.0F;

	private static final float RADIUS_STEP = 4.0F;

	private static final float WAYPOINT_CHORD = 5.0F;

	private static final float HEIGHT_MIN = -6.0F;
	private static final float HEIGHT_MAX = 6.0F;

	private static final float ESCAPE_STEP = 3.0F;
	private static final float ESCAPE_CEILING = 48.0F;

	private static final double ARRIVAL_DIST_SQR = 4.0;

	private static final int HEIGHT_REROLL = 350;
	private static final int RADIUS_REROLL = 250;
	private static final int ANGLE_REROLL = 450;

	private final SeagullEntity gull;

	private float angle;
	private float radius;
	private float height;

	private float clockwise;

	public SeagullCircleGoal(SeagullEntity gull) {
		this.gull = gull;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE));
	}

	@Override
	public boolean canUse() {
		return true;
	}

	@Override
	public void start() {
		this.radius = RADIUS_MIN + this.gull.getRandom().nextFloat() * (RADIUS_MAX - RADIUS_MIN);
		this.height = HEIGHT_MIN + this.gull.getRandom().nextFloat() * (HEIGHT_MAX - HEIGHT_MIN);
		this.clockwise = this.gull.getRandom().nextBoolean() ? 1.0F : -1.0F;

		this.angle = this.gull.getRandom().nextFloat() * Mth.TWO_PI;
		this.selectNext();
	}

	@Override
	public void tick() {
		if (this.gull.getRandom().nextInt(this.adjustedTickDelay(HEIGHT_REROLL)) == 0) {
			this.height = HEIGHT_MIN + this.gull.getRandom().nextFloat() * (HEIGHT_MAX - HEIGHT_MIN);
		}

		if (this.gull.getRandom().nextInt(this.adjustedTickDelay(RADIUS_REROLL)) == 0) {

			this.radius += RADIUS_STEP;
			if (this.radius > RADIUS_MAX) {
				this.radius = RADIUS_MIN;
				this.clockwise = -this.clockwise;
			}
		}

		if (this.gull.getRandom().nextInt(this.adjustedTickDelay(ANGLE_REROLL)) == 0) {
			this.angle = this.gull.getRandom().nextFloat() * Mth.TWO_PI;
			this.selectNext();
		}

		if (this.touchingTarget()) {
			this.selectNext();
		}

		if (this.gull.moveTargetPoint.y < this.gull.getY()
			&& !this.gull.level().isEmptyBlock(this.gull.blockPosition().below(1))) {
			this.height = Math.min(Math.max(1.0F, this.height + ESCAPE_STEP), ESCAPE_CEILING);
			this.selectNext();
		}

		if (this.gull.moveTargetPoint.y > this.gull.getY()
			&& !this.gull.level().isEmptyBlock(this.gull.blockPosition().above(1))) {
			this.height = Math.min(-1.0F, this.height);
			this.selectNext();
		}
	}

	private boolean touchingTarget() {
		return this.gull.moveTargetPoint.distanceToSqr(this.gull.getX(), this.gull.getY(), this.gull.getZ())
			< ARRIVAL_DIST_SQR;
	}

	private void selectNext() {
		if (BlockPos.ZERO.equals(this.gull.getAnchorPoint())) {

			this.gull.setAnchorPoint(this.gull.blockPosition());
		}

		this.angle += this.clockwise * (WAYPOINT_CHORD / this.radius);
		this.gull.moveTargetPoint = Vec3.atLowerCornerOf(this.gull.getAnchorPoint())
			.add(this.radius * Mth.cos(this.angle), this.height, this.radius * Mth.sin(this.angle));
	}
}

package com.oceania.mob;

import net.minecraft.server.level.ServerLevel;
import java.util.EnumSet;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

public class SharkHuntGoal extends Goal {

	private static final int WINDUP_TICKS = 12;
	private static final int CHARGE_MAX = 40;
	private static final int WITHDRAW_TICKS = 30;
	private static final int STALK_BITTEN = 180;
	private static final int STALK_PARRIED = 60;
	private static final int STALK_FIRST = 70;
	private static final int LOITER_TICKS = 400;

	private static final double CIRCLE_RADIUS = 9.0;
	private static final double CIRCLE_RATE = 0.055;
	private static final double CHARGE_SPEED = 0.40;
	private static final double WINDUP_SPEED = 0.06;
	private static final double BITE_REACH = 0.35;

	private enum Phase { APPROACH, CIRCLE, WINDUP, CHARGE, WITHDRAW, LOITER }

	private final SharkEntity shark;

	private LivingEntity quarry;
	private Phase phase = Phase.APPROACH;
	private int timer;
	private int phaseStart;
	private double angle;
	private int orbitDir = 1;
	private Vec3 withdrawTo = Vec3.ZERO;
	private int repathIn;

	public SharkHuntGoal(SharkEntity shark) {
		this.shark = shark;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		LivingEntity target = this.shark.getTarget();
		return target instanceof net.minecraft.world.entity.player.Player
			&& target.isAlive() && this.shark.isInWater();
	}

	@Override
	public boolean canContinueToUse() {
		if (this.quarry == null || !this.quarry.isAlive() || !this.shark.isInWater()) {
			return false;
		}

		if (!SharkEntity.worthLoitering(this.quarry, this.shark.level().getGameTime())) {
			return false;
		}

		double follow = this.shark.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE);
		if (this.shark.distanceToSqr(this.quarry) > follow * follow * 1.4) {
			return false;
		}

		return this.phase == Phase.LOITER || this.shark.getTarget() != null;
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void start() {
		this.quarry = this.shark.getTarget();
		this.orbitDir = this.shark.getRandom().nextBoolean() ? 1 : -1;
		this.angle = this.shark.getRandom().nextDouble() * Math.PI * 2.0;
		this.repathIn = 0;
		enter(Phase.APPROACH, 0);
	}

	@Override
	public void stop() {
		this.quarry = null;
		this.shark.setPhase(SharkEntity.PHASE_CRUISE);
		this.shark.setAggressive(false);
		this.shark.getNavigation().stop();
	}

	private void enter(Phase next, int ticks) {

		if (next == Phase.WINDUP && !this.shark.level().isClientSide()) {
			this.shark.level().playSound(null, this.shark.getX(), this.shark.getY(), this.shark.getZ(),
				net.minecraft.sounds.SoundEvents.DOLPHIN_SWIM,
				net.minecraft.sounds.SoundSource.HOSTILE, 1.4F, 0.55F);
		}
		this.phase = next;
		this.timer = ticks;
		this.phaseStart = this.shark.tickCount;
		this.repathIn = 0;
		this.shark.setPhase(switch (next) {
			case APPROACH, CIRCLE, LOITER -> SharkEntity.PHASE_CIRCLE;
			case WINDUP -> SharkEntity.PHASE_WINDUP;
			case CHARGE -> SharkEntity.PHASE_CHARGE;
			case WITHDRAW -> SharkEntity.PHASE_WITHDRAW;
		});
		this.shark.setAggressive(next == Phase.WINDUP || next == Phase.CHARGE);
	}

	private boolean parried() {
		return this.shark.wasParriedSince(this.shark.level().getGameTime()
			- (this.shark.tickCount - this.phaseStart));
	}

	@Override
	public void tick() {
		LivingEntity q = this.quarry;
		if (q == null) {
			return;
		}

		boolean huntable = SharkEntity.isHuntable(q, this.shark.level().getGameTime());
		if (!huntable && this.phase != Phase.LOITER) {
			enter(Phase.LOITER, LOITER_TICKS);
		} else if (huntable && this.phase == Phase.LOITER) {
			enter(Phase.CIRCLE, STALK_FIRST);
		}

		this.shark.getLookControl().setLookAt(q, 30.0F, 30.0F);
		if (this.timer > 0) {
			this.timer--;
		}

		switch (this.phase) {
			case APPROACH -> tickApproach(q);
			case CIRCLE, LOITER -> tickCircle(q);
			case WINDUP -> tickWindup(q);
			case CHARGE -> tickCharge(q);
			case WITHDRAW -> tickWithdraw();
		}
	}

	private void tickApproach(LivingEntity q) {
		if (this.shark.distanceToSqr(q) < (CIRCLE_RADIUS + 4.0) * (CIRCLE_RADIUS + 4.0)) {
			enter(Phase.CIRCLE, STALK_FIRST);
			return;
		}
		if (--this.repathIn <= 0) {
			this.repathIn = 10;
			this.shark.getNavigation().moveTo(q, 1.0);
		}
	}

	private void tickCircle(LivingEntity q) {
		this.angle += CIRCLE_RATE * this.orbitDir;

		if (--this.repathIn <= 0) {
			this.repathIn = 8;

			double lead = this.angle + 0.5 * this.orbitDir;
			double x = q.getX() + Math.cos(lead) * CIRCLE_RADIUS;
			double z = q.getZ() + Math.sin(lead) * CIRCLE_RADIUS;
			double y = q.getY() + (this.phase == Phase.LOITER ? -3.5 : -1.0);
			this.shark.getNavigation().moveTo(x, y, z, 1.0);
		}

		if (this.phase == Phase.CIRCLE && this.timer <= 0) {
			enter(Phase.WINDUP, WINDUP_TICKS);
		} else if (this.phase == Phase.LOITER && this.timer <= 0) {
			this.quarry = null;
		}
	}

	private void tickWindup(LivingEntity q) {
		this.shark.getNavigation().stop();
		if (parried()) {
			enter(Phase.CIRCLE, STALK_PARRIED);
			return;
		}
		Vec3 dir = q.position().subtract(this.shark.position()).normalize();
		faceAlong(dir);
		this.shark.setDeltaMovement(dir.scale(WINDUP_SPEED));
		if (this.timer <= 0) {
			enter(Phase.CHARGE, CHARGE_MAX);
		}
	}

	private void tickCharge(LivingEntity q) {
		this.shark.getNavigation().stop();
		if (parried()) {
			enter(Phase.CIRCLE, STALK_PARRIED);
			return;
		}

		Vec3 to = q.position().add(0.0, q.getBbHeight() * 0.5, 0.0)
			.subtract(this.shark.position().add(0.0, this.shark.getBbHeight() * 0.5, 0.0));
		Vec3 dir = to.lengthSqr() < 1.0E-6 ? this.shark.getLookAngle() : to.normalize();
		faceAlong(dir);
		this.shark.setDeltaMovement(dir.scale(CHARGE_SPEED));

		if (this.shark.getBoundingBox().inflate(BITE_REACH).intersects(q.getBoundingBox())) {
			if (this.shark.level() instanceof ServerLevel serverLevel) this.shark.doHurtTarget(serverLevel, q);
			this.withdrawTo = this.shark.position().add(dir.scale(14.0));
			enter(Phase.WITHDRAW, WITHDRAW_TICKS);
			return;
		}

		if (this.timer <= 0) {

			enter(Phase.CIRCLE, STALK_FIRST);
		}
	}

	private void tickWithdraw() {
		if (--this.repathIn <= 0) {
			this.repathIn = 10;
			this.shark.getNavigation().moveTo(this.withdrawTo.x, this.withdrawTo.y, this.withdrawTo.z, 1.2);
		}
		if (this.timer <= 0) {
			enter(Phase.CIRCLE, STALK_BITTEN);
		}
	}

	private void faceAlong(Vec3 dir) {
		float yaw = (float) (Mth.atan2(dir.z, dir.x) * (180.0 / Math.PI)) - 90.0F;
		float pitch = (float) (-(Mth.atan2(dir.y, Math.sqrt(dir.x * dir.x + dir.z * dir.z)) * (180.0 / Math.PI)));
		this.shark.setYRot(yaw);
		this.shark.yBodyRot = yaw;
		this.shark.yHeadRot = yaw;
		this.shark.setXRot(Mth.clamp(pitch, -60.0F, 60.0F));
	}
}

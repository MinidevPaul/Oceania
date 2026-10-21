package com.oceania.mob;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.dolphin.Dolphin;
import net.minecraft.world.entity.animal.squid.Squid;
import net.minecraft.world.entity.animal.turtle.Turtle;
import net.minecraft.world.entity.animal.fish.AbstractFish;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

public final class SharkTargetGoals {

	private SharkTargetGoals() {
	}

	private static AABB cube(LivingEntity mob, double d) {
		return mob.getBoundingBox().inflate(d, d, d);
	}

	public static class Swimmers extends NearestAttackableTargetGoal<Player> {

		private static final int DISENGAGE = Integer.getInteger("oceania.shark.disengage", 120);

		private final SharkEntity shark;
		private long resumeAt;

		public Swimmers(SharkEntity shark) {
			super(shark, Player.class, 10, true, false,
				(target, level) -> SharkEntity.isHuntable(target, shark.level().getGameTime()));
			this.shark = shark;
		}

		@Override
		protected AABB getTargetSearchArea(double d) {
			return cube(this.shark, d);
		}

		@Override
		public boolean canUse() {
			return this.shark.level().getGameTime() >= this.resumeAt && super.canUse();
		}

		@Override
		public boolean canContinueToUse() {
			LivingEntity target = this.shark.getTarget();
			if (target != null
				&& !SharkEntity.isHuntable(target, this.shark.level().getGameTime())) {
				this.resumeAt = this.shark.level().getGameTime() + DISENGAGE;
				return false;
			}
			return super.canContinueToUse();
		}
	}

	public static class Prey extends NearestAttackableTargetGoal<LivingEntity> {
		private static final double PREY_RANGE = 12.0;
		private final SharkEntity shark;

		public Prey(SharkEntity shark) {
			super(shark, LivingEntity.class, 20, true, false, (target, level) -> SharkTargetGoals.isPrey(target));
			this.shark = shark;
		}

		@Override
		protected double getFollowDistance() {
			return PREY_RANGE;
		}

		@Override
		protected AABB getTargetSearchArea(double d) {
			return cube(this.shark, d);
		}
	}

	private static boolean isPrey(LivingEntity e) {
		if (e instanceof Turtle turtle) {
			return turtle.isBaby();
		}

		if (e instanceof Dolphin dolphin) {
			return dolphin.isBaby();
		}
		return e instanceof Squid || e instanceof AbstractFish;
	}
}

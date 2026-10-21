package com.oceania.mob;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.navigation.AmphibiousPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;

public abstract class BottomWalkingCrab extends AbstractCrab {

	private final double strollSpeed;

	protected BottomWalkingCrab(EntityType<? extends BottomWalkingCrab> type, Level level,
	                            float fleeRadius, double fleeFast, double fleeSlow,
	                            double strollSpeed) {
		super(type, level, fleeRadius, fleeFast, fleeSlow);
		this.strollSpeed = strollSpeed;
	}

	@Override
	protected PathNavigation createNavigation(Level level) {
		AmphibiousPathNavigation nav = new AmphibiousPathNavigation(this, level);

		nav.setCanFloat(false);
		return nav;
	}

	@Override
	protected Goal strollGoal() {
		return new RandomStrollGoal(this, this.strollSpeed);
	}

	@Override
	public boolean causeFallDamage(double distance, float multiplier, DamageSource source) {
		return false;
	}
}

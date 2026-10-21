package com.oceania.mob;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathType;

public abstract class AbstractCrab extends PathfinderMob {

	private final float fleeRadius;

	private final double fleeFast;
	private final double fleeSlow;

	protected AbstractCrab(EntityType<? extends AbstractCrab> type, Level level,
	                       float fleeRadius, double fleeFast, double fleeSlow) {
		super(type, level);
		this.fleeRadius = fleeRadius;
		this.fleeFast = fleeFast;
		this.fleeSlow = fleeSlow;

		this.setPathfindingMalus(PathType.WATER, 0.0F);
	}

	@Override
	protected void registerGoals() {

		this.goalSelector.addGoal(1, new AvoidEntityGoal<>(
			this, Player.class, this.fleeRadius, this.fleeFast, this.fleeSlow));
		this.goalSelector.addGoal(2, this.strollGoal());

		this.goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 8.0F));
		this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));
	}

	protected abstract net.minecraft.world.entity.ai.goal.Goal strollGoal();

	@Override
	public boolean isPushedByFluid() {
		return false;
	}

	@Override
	protected net.minecraft.sounds.SoundEvent getAmbientSound() {
		return null;
	}
}

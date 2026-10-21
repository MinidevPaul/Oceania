package com.oceania.mob;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

public class KingCrabEntity extends BottomWalkingCrab {

	private static final float FLEE_RADIUS = 13.0F;
	private static final double FLEE_FAST = 1.60;
	private static final double FLEE_SLOW = 1.10;
	private static final double STROLL_SPEED = 0.8;

	public KingCrabEntity(EntityType<? extends KingCrabEntity> type, Level level) {
		super(type, level, FLEE_RADIUS, FLEE_FAST, FLEE_SLOW, STROLL_SPEED);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()

			.add(Attributes.MAX_HEALTH, 4.0)

			.add(Attributes.MOVEMENT_SPEED, 0.20)
			.add(Attributes.FOLLOW_RANGE, 16.0)

			.add(Attributes.STEP_HEIGHT, 1.0)

			.add(Attributes.WATER_MOVEMENT_EFFICIENCY, 1.0);
	}
}

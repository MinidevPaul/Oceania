package com.oceania.mob;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

public class MoonJellyEntity extends AbstractJellyfish {

	public static final int PULSE_TICKS = 44;

	public MoonJellyEntity(EntityType<? extends MoonJellyEntity> type, Level level) {
		super(type, level);
	}

	@Override
	protected int basePulseTicks() {
		return PULSE_TICKS;
	}
}

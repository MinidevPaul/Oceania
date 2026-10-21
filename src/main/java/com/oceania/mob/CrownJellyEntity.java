package com.oceania.mob;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

public class CrownJellyEntity extends AbstractJellyfish {

	public static final int PULSE_TICKS = 70;

	public CrownJellyEntity(EntityType<? extends CrownJellyEntity> type, Level level) {
		super(type, level);
	}

	@Override
	protected int basePulseTicks() {
		return PULSE_TICKS;
	}
}

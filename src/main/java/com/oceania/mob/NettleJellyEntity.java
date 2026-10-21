package com.oceania.mob;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

public class NettleJellyEntity extends AbstractJellyfish {

	public static final int PULSE_TICKS = 58;

	public NettleJellyEntity(EntityType<? extends NettleJellyEntity> type, Level level) {
		super(type, level);
	}

	@Override
	protected int basePulseTicks() {
		return PULSE_TICKS;
	}
}

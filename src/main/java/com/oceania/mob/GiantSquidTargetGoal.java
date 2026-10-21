package com.oceania.mob;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;

public class GiantSquidTargetGoal extends NearestAttackableTargetGoal<LivingEntity> {

	public GiantSquidTargetGoal(GiantSquidEntity squid) {
		super(squid, LivingEntity.class, 10, true, false,
			(target, level) -> squid.isHuntable(target));
	}
}

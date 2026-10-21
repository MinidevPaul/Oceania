package com.oceania.mob;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.goal.Goal;

public class JellyfishDriftGoal extends Goal {

	private static final int RE_ROLL_TICKS = 240;

	private static final int IDLE_LIMIT = 100;

	private final AbstractJellyfish jelly;

	public JellyfishDriftGoal(AbstractJellyfish jelly) {
		this.jelly = jelly;

		this.setFlags(AbstractJellyfish.moveOnly());
	}

	@Override
	public boolean canUse() {
		return true;
	}

	@Override
	public void tick() {
		if (this.jelly.getNoActionTime() > IDLE_LIMIT) {
			this.jelly.setDriftVector(0.0F, 0.0F, 0.0F);
			return;
		}
		boolean stranded = !this.jelly.isInWater();
		if (stranded || !this.jelly.hasDriftVector()
			|| this.jelly.getRandom().nextInt(reducedTickDelay(RE_ROLL_TICKS)) == 0) {
			float heading = this.jelly.getRandom().nextFloat() * Mth.TWO_PI;
			float horizontal = AbstractJellyfish.driftHorizontal();
			float vertical = AbstractJellyfish.driftVertical();

			this.jelly.setDriftVector(
				Mth.cos(heading) * horizontal,
				(this.jelly.getRandom().nextFloat() * 2.0F - 1.0F) * vertical,
				Mth.sin(heading) * horizontal);
		}
	}
}

package com.oceania.client.render;

import com.oceania.mob.SharkVariant;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

public class SharkRenderState extends LivingEntityRenderState {
	public SharkVariant variant = SharkVariant.REEF;

	public float bankAmount;

	public float jawOpen;

	public boolean charging;
}

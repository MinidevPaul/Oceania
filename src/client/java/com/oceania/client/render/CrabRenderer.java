package com.oceania.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.oceania.mob.AbstractCrab;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;

public abstract class CrabRenderer<T extends AbstractCrab> extends MobRenderer<T, CrabRenderState, CrabModel> {
	private static final boolean SIDEWAYS = !"false".equalsIgnoreCase(System.getProperty("oceania.crab.sideways", "true"));

	private static final float SIDEWAYS_YAW = 90.0F;

	protected CrabRenderer(EntityRendererProvider.Context context, CrabModel model, float shadowRadius) {
		super(context, model, shadowRadius);
	}

	@Override
	public CrabRenderState createRenderState() {
		return new CrabRenderState();
	}

	@Override
	protected void setupRotations(CrabRenderState state, PoseStack poseStack, float bodyRot, float scale) {
		super.setupRotations(state, poseStack, bodyRot, scale);
		if (SIDEWAYS) {
			poseStack.rotateDegrees(Axis.YP, SIDEWAYS_YAW);
		}
	}
}

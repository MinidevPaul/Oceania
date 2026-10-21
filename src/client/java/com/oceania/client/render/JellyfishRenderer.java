package com.oceania.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.oceania.mob.AbstractJellyfish;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

public abstract class JellyfishRenderer<T extends AbstractJellyfish> extends MobRenderer<T, JellyfishRenderState, JellyfishModel> {

	private final Identifier texture;
	private final int[] tints;

	protected JellyfishRenderer(EntityRendererProvider.Context context, JellyfishModel model, Identifier texture, int[] tints) {
		super(context, model, 0.0F);
		this.texture = texture;
		this.tints = tints;
	}

	@Override
	public JellyfishRenderState createRenderState() {
		return new JellyfishRenderState();
	}

	@Override
	public void extractRenderState(T jelly, JellyfishRenderState state, float partialTick) {
		super.extractRenderState(jelly, state, partialTick);
		state.bellTurns = jelly.bellTurns(partialTick);
		state.tint = jelly.getTint();
		state.leanX = Mth.lerp(partialTick, jelly.leanXO, jelly.leanX);
		state.leanZ = Mth.lerp(partialTick, jelly.leanZO, jelly.leanZ);
	}

	@Override
	public Identifier getTextureLocation(JellyfishRenderState state) {
		return this.texture;
	}

	@Override
	protected int getModelTint(JellyfishRenderState state) {
		return JellyfishModel.tintFor(state, this.tints);
	}

	@Override
	protected void setupRotations(JellyfishRenderState state, PoseStack poseStack, float bodyRot, float scale) {
		super.setupRotations(state, poseStack, bodyRot, scale);
		poseStack.rotateDegrees(Axis.XP, state.leanX * Mth.RAD_TO_DEG);
		poseStack.rotateDegrees(Axis.ZP, state.leanZ * Mth.RAD_TO_DEG);
	}

	protected static Identifier sheet(String species) {
		return Identifier.fromNamespaceAndPath("oceania", "textures/entity/jellyfish/" + species + ".png");
	}
}

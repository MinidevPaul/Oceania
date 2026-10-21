package com.oceania.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.oceania.mob.SeagullEntity;
import com.oceania.registry.OceaniaEntities;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;

public class SeagullRenderer extends MobRenderer<SeagullEntity, SeagullRenderState, SeagullModel> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Identifier.fromNamespaceAndPath("oceania", "seagull"), "main");
	private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("oceania", "textures/entity/seagull/seagull.png");

	public static void register() {
		ModelLayerRegistry.registerModelLayer(LAYER, SeagullModel::createBodyLayer);
		EntityRendererRegistry.register(OceaniaEntities.SEAGULL, SeagullRenderer::new);
	}

	public SeagullRenderer(EntityRendererProvider.Context context) {
		super(context, new SeagullModel(context.bakeLayer(LAYER)), 0.0F);
	}

	@Override
	public SeagullRenderState createRenderState() {
		return new SeagullRenderState();
	}

	@Override
	public void extractRenderState(SeagullEntity gull, SeagullRenderState state, float partialTick) {
		super.extractRenderState(gull, state, partialTick);

		state.wingPhase = gull.getWingPhase(state.ageInTicks);
		state.flapAmplitude = gull.getFlapAmplitude();
	}

	@Override
	public Identifier getTextureLocation(SeagullRenderState state) {
		return TEXTURE;
	}

	@Override
	protected void setupRotations(SeagullRenderState state, PoseStack poseStack, float bodyRot, float scale) {
		super.setupRotations(state, poseStack, bodyRot, scale);
		poseStack.rotateDegrees(Axis.XP, state.xRot);
	}
}

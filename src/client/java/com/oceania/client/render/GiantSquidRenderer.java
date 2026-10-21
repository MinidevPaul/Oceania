package com.oceania.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.oceania.mob.GiantSquidEntity;
import com.oceania.registry.OceaniaEntities;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

public class GiantSquidRenderer extends MobRenderer<GiantSquidEntity, GiantSquidRenderState, GiantSquidModel> {

	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Identifier.fromNamespaceAndPath("oceania", "giant_squid"), "main");
	private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("oceania", "textures/entity/squid/giant_squid.png");

	public static void register() {
		ModelLayerRegistry.registerModelLayer(LAYER, GiantSquidModel::createBodyLayer);
		EntityRendererRegistry.register(OceaniaEntities.GIANT_SQUID, GiantSquidRenderer::new);
	}

	public GiantSquidRenderer(EntityRendererProvider.Context context) {
		super(context, new GiantSquidModel(context.bakeLayer(LAYER)), 0.0F);
	}

	@Override
	public GiantSquidRenderState createRenderState() {
		return new GiantSquidRenderState();
	}

	@Override
	public void extractRenderState(GiantSquidEntity squid, GiantSquidRenderState state, float partialTick) {
		super.extractRenderState(squid, state, partialTick);
		state.wavePhase = Mth.lerp(partialTick, squid.wavePhaseO, squid.wavePhase);
		state.turnLag = Mth.lerp(partialTick, squid.turnLagO, squid.turnLag);
		state.jet = squid.getJet();
		state.strike = squid.getStrike();
		state.roll = Mth.lerp(partialTick, squid.rollO, squid.roll);
	}

	@Override
	public Identifier getTextureLocation(GiantSquidRenderState state) {
		return TEXTURE;
	}

	@Override
	protected boolean affectedByCulling(GiantSquidEntity squid) {
		return false;
	}

	@Override
	protected void setupRotations(GiantSquidRenderState state, PoseStack poseStack, float bodyRot, float scale) {
		super.setupRotations(state, poseStack, bodyRot, scale);
		poseStack.rotateDegrees(Axis.XP, state.xRot);
		poseStack.rotateDegrees(Axis.ZP, state.roll * Mth.RAD_TO_DEG);
	}
}

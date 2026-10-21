package com.oceania.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.oceania.mob.SharkEntity;
import com.oceania.mob.SharkVariant;
import com.oceania.registry.OceaniaEntities;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import java.util.EnumMap;
import java.util.Map;

public class SharkRenderer extends MobRenderer<SharkEntity, SharkRenderState, SharkModel> {

	public static ModelLayerLocation layer(SharkVariant variant) {
		return new ModelLayerLocation(Identifier.fromNamespaceAndPath("oceania", "shark/" + variant.getSerializedName()), "main");
	}

	public static void register() {
		for (SharkVariant variant : SharkVariant.values()) {
			ModelLayerRegistry.registerModelLayer(layer(variant), () -> SharkModel.createBodyLayer(variant));
		}
		EntityRendererRegistry.register(OceaniaEntities.SHARK, SharkRenderer::new);
	}

	private final Map<SharkVariant, SharkModel> models = new EnumMap<>(SharkVariant.class);

	public SharkRenderer(EntityRendererProvider.Context context) {
		super(context, new SharkModel(context.bakeLayer(layer(SharkVariant.REEF))), 0.8F);
		for (SharkVariant variant : SharkVariant.values()) {
			this.models.put(variant, new SharkModel(context.bakeLayer(layer(variant))));
		}
	}

	@Override
	public SharkRenderState createRenderState() {
		return new SharkRenderState();
	}

	@Override
	public void extractRenderState(SharkEntity shark, SharkRenderState state, float partialTick) {
		super.extractRenderState(shark, state, partialTick);
		state.variant = shark.getVariant();
		state.bankAmount = Mth.lerp(partialTick, shark.bankAmountO, shark.bankAmount);
		state.jawOpen = shark.jawOpen;
		state.charging = shark.getPhase() == SharkEntity.PHASE_CHARGE;
	}

	@Override
	public Identifier getTextureLocation(SharkRenderState state) {
		return state.variant.texture();
	}

	@Override
	public void submit(SharkRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		this.model = this.models.get(state.variant);
		super.submit(state, poseStack, collector, camera);
	}

	@Override
	protected float getShadowRadius(SharkRenderState state) {
		return 0.8F * state.variant.scale();
	}
}

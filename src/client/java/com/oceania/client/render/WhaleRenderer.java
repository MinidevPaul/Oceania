package com.oceania.client.render;

import com.oceania.mob.WhaleEntity;
import com.oceania.registry.OceaniaEntities;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.resources.Identifier;

public class WhaleRenderer extends MobRenderer<WhaleEntity, WhaleRenderState, WhaleModel> {

	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Identifier.fromNamespaceAndPath("oceania", "whale"), "main");

	private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("oceania", "textures/entity/whale/whale.png");

	public static void register() {
		ModelLayerRegistry.registerModelLayer(LAYER, WhaleModel::createBodyLayer);
		EntityRendererRegistry.register(OceaniaEntities.WHALE, WhaleRenderer::new);

		EntityRendererRegistry.register(OceaniaEntities.WHALE_PART, NoopRenderer::new);
	}

	public WhaleRenderer(EntityRendererProvider.Context context) {
		super(context, new WhaleModel(context.bakeLayer(LAYER)), 4.0F);
	}

	@Override
	public WhaleRenderState createRenderState() {
		return new WhaleRenderState();
	}

	@Override
	public void extractRenderState(WhaleEntity whale, WhaleRenderState state, float partialTick) {
		super.extractRenderState(whale, state, partialTick);
		state.gape = whale.getGape(partialTick);
	}

	@Override
	public Identifier getTextureLocation(WhaleRenderState state) {
		return TEXTURE;
	}

	@Override
	protected boolean affectedByCulling(WhaleEntity whale) {
		return false;
	}

	@Override
	protected float getShadowRadius(WhaleRenderState state) {
		return 6.0F;
	}
}

package com.oceania.client.render;

import com.oceania.mob.ReefCrabEntity;
import com.oceania.registry.OceaniaEntities;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;

public class ReefCrabRenderer extends CrabRenderer<ReefCrabEntity> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Identifier.fromNamespaceAndPath("oceania", "reef_crab"), "main");
	private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("oceania", "textures/entity/crab/reef_crab.png");

	public static void register() {
		ModelLayerRegistry.registerModelLayer(LAYER, ReefCrabModel::createBodyLayer);
		EntityRendererRegistry.register(OceaniaEntities.REEF_CRAB, ReefCrabRenderer::new);
	}

	public ReefCrabRenderer(EntityRendererProvider.Context context) {
		super(context, new ReefCrabModel(context.bakeLayer(LAYER)), 0.3F);
	}

	@Override
	public Identifier getTextureLocation(CrabRenderState state) {
		return TEXTURE;
	}
}

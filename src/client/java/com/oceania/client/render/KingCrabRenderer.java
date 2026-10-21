package com.oceania.client.render;

import com.oceania.mob.KingCrabEntity;
import com.oceania.registry.OceaniaEntities;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;

public class KingCrabRenderer extends CrabRenderer<KingCrabEntity> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Identifier.fromNamespaceAndPath("oceania", "king_crab"), "main");
	private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("oceania", "textures/entity/crab/king_crab.png");

	public static void register() {
		ModelLayerRegistry.registerModelLayer(LAYER, KingCrabModel::createBodyLayer);
		EntityRendererRegistry.register(OceaniaEntities.KING_CRAB, KingCrabRenderer::new);
	}

	public KingCrabRenderer(EntityRendererProvider.Context context) {
		super(context, new KingCrabModel(context.bakeLayer(LAYER)), 0.9F);
	}

	@Override
	public Identifier getTextureLocation(CrabRenderState state) {
		return TEXTURE;
	}
}

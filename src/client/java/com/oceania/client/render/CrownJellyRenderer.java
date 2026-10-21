package com.oceania.client.render;

import com.oceania.mob.CrownJellyEntity;
import com.oceania.registry.OceaniaEntities;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;

public class CrownJellyRenderer extends JellyfishRenderer<CrownJellyEntity> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Identifier.fromNamespaceAndPath("oceania", "crown_jelly"), "main");

	public static void register() {
		ModelLayerRegistry.registerModelLayer(LAYER, CrownJellyModel::createBodyLayer);
		EntityRendererRegistry.register(OceaniaEntities.CROWN_JELLY, CrownJellyRenderer::new);
	}

	public CrownJellyRenderer(EntityRendererProvider.Context context) {
		super(context, new CrownJellyModel(context.bakeLayer(LAYER)), sheet("crown"), CrownJellyGeometry.TINTS);
	}
}

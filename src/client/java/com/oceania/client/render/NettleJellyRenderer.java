package com.oceania.client.render;

import com.oceania.mob.NettleJellyEntity;
import com.oceania.registry.OceaniaEntities;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;

public class NettleJellyRenderer extends JellyfishRenderer<NettleJellyEntity> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Identifier.fromNamespaceAndPath("oceania", "nettle_jelly"), "main");

	public static void register() {
		ModelLayerRegistry.registerModelLayer(LAYER, NettleJellyModel::createBodyLayer);
		EntityRendererRegistry.register(OceaniaEntities.NETTLE_JELLY, NettleJellyRenderer::new);
	}

	public NettleJellyRenderer(EntityRendererProvider.Context context) {
		super(context, new NettleJellyModel(context.bakeLayer(LAYER)), sheet("nettle"), NettleJellyGeometry.TINTS);
	}
}

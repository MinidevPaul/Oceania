package com.oceania.client.render;

import com.oceania.mob.MoonJellyEntity;
import com.oceania.registry.OceaniaEntities;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;

public class MoonJellyRenderer extends JellyfishRenderer<MoonJellyEntity> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Identifier.fromNamespaceAndPath("oceania", "moon_jelly"), "main");

	public static void register() {
		ModelLayerRegistry.registerModelLayer(LAYER, MoonJellyModel::createBodyLayer);
		EntityRendererRegistry.register(OceaniaEntities.MOON_JELLY, MoonJellyRenderer::new);
	}

	public MoonJellyRenderer(EntityRendererProvider.Context context) {
		super(context, new MoonJellyModel(context.bakeLayer(LAYER)), sheet("moon"), MoonJellyGeometry.TINTS);
	}
}

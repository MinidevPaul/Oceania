package com.oceania.client.render;

import com.oceania.mob.CoconutCrabEntity;
import com.oceania.registry.OceaniaEntities;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;

public class CoconutCrabRenderer extends CrabRenderer<CoconutCrabEntity> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Identifier.fromNamespaceAndPath("oceania", "coconut_crab"), "main");
	private static final Identifier BLUE = Identifier.fromNamespaceAndPath("oceania", "textures/entity/crab/coconut_blue.png");
	private static final Identifier ORANGE = Identifier.fromNamespaceAndPath("oceania", "textures/entity/crab/coconut_orange.png");

	public static void register() {
		ModelLayerRegistry.registerModelLayer(LAYER, CoconutCrabModel::createBodyLayer);
		EntityRendererRegistry.register(OceaniaEntities.COCONUT_CRAB, CoconutCrabRenderer::new);
	}

	public CoconutCrabRenderer(EntityRendererProvider.Context context) {
		super(context, new CoconutCrabModel(context.bakeLayer(LAYER)), 0.45F);
	}

	@Override
	public void extractRenderState(CoconutCrabEntity crab, CrabRenderState state, float partialTick) {
		super.extractRenderState(crab, state, partialTick);
		state.orangeMorph = crab.getMorph() == CoconutCrabEntity.CrabMorph.ORANGE;
	}

	@Override
	public Identifier getTextureLocation(CrabRenderState state) {
		return state.orangeMorph ? ORANGE : BLUE;
	}
}

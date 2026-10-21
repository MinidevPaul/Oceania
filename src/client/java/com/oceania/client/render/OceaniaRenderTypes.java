package com.oceania.client.render;

import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

public final class OceaniaRenderTypes {
	private OceaniaRenderTypes() {
	}

	public static RenderType jellyfish(Identifier texture) {
		return RenderTypes.entityTranslucentEmissive(texture);
	}
}

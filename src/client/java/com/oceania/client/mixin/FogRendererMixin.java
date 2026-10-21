package com.oceania.client.mixin;

import com.oceania.Oceania;
import com.oceania.client.OceaniaWaterFog;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.fog.environment.FogEnvironment;
import net.minecraft.client.renderer.fog.environment.WaterFogEnvironment;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {
	@Shadow
	@Final
	private static List<FogEnvironment> FOG_ENVIRONMENTS;

	@Inject(method = "<clinit>", at = @At("TAIL"))
	private static void oceania$depthFog(CallbackInfo ci) {
		int at = FOG_ENVIRONMENTS.size();
		for (int i = 0; i < FOG_ENVIRONMENTS.size(); i++) {
			if (FOG_ENVIRONMENTS.get(i) instanceof WaterFogEnvironment) {
				at = i;
				break;
			}
		}
		FOG_ENVIRONMENTS.add(at, new OceaniaWaterFog());
		Oceania.LOGGER.info("[fog] depth water fog installed ahead of vanilla's at index {} of {}", at, FOG_ENVIRONMENTS.size());
	}
}

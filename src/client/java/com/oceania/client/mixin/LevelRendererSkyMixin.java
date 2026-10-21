package com.oceania.client.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.oceania.client.UnderwaterView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererSkyMixin {
	@ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true, ordinal = 1)
	private boolean oceania$hideSkyWhenDeep(boolean shouldRenderSky, @Local(argsOnly = true) CameraRenderState camera) {
		if (!shouldRenderSky || camera.fogType != FogType.WATER) return shouldRenderSky;
		ClientLevel level = Minecraft.getInstance().level;
		return level == null || UnderwaterView.depth(camera.pos, level) <= UnderwaterView.SKY_HIDE_DEPTH;
	}
}

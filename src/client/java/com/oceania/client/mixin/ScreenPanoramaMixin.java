package com.oceania.client.mixin;

import com.oceania.client.MenuBackground;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class ScreenPanoramaMixin {
	@Inject(method = "extractPanorama", at = @At("HEAD"), cancellable = true)
	private void oceania$drawOceanBackground(GuiGraphicsExtractor graphics, float fade, CallbackInfo ci) {
		MenuBackground.draw(graphics, fade);
		ci.cancel();
	}
}

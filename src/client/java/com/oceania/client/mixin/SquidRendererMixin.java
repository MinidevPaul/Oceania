package com.oceania.client.mixin;

import com.oceania.client.render.SquidVariants;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.renderer.entity.SquidRenderer;
import net.minecraft.client.renderer.entity.state.SquidRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.squid.Squid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SquidRenderer.class)
public class SquidRendererMixin {
	private static final RenderStateDataKey<Identifier> OCEANIA_VARIANT = RenderStateDataKey.create(() -> "oceania:squid_variant");

	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/animal/squid/Squid;Lnet/minecraft/client/renderer/entity/state/SquidRenderState;F)V", at = @At("TAIL"))
	private void oceania$rememberVariant(Squid squid, SquidRenderState state, float partialTick, CallbackInfo ci) {
		((FabricRenderState) state).setData(OCEANIA_VARIANT, squid.getType() == EntityTypes.SQUID ? SquidVariants.textureFor(squid) : null);
	}

	@Inject(method = "getTextureLocation(Lnet/minecraft/client/renderer/entity/state/SquidRenderState;)Lnet/minecraft/resources/Identifier;", at = @At("HEAD"), cancellable = true)
	private void oceania$squidColourVariant(SquidRenderState state, CallbackInfoReturnable<Identifier> cir) {
		Identifier texture = ((FabricRenderState) state).getData(OCEANIA_VARIANT);
		if (texture != null) cir.setReturnValue(texture);
	}
}

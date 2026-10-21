package com.oceania.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.oceania.client.raft.RaftEntityLight;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EntityRenderer.class)
public class EntityRendererLightMixin {
	@ModifyReturnValue(method = "getPackedLightCoords", at = @At("RETURN"))
	private int oceania$raftLight(int packed, Entity entity, float partialTick) {
		return RaftEntityLight.blend(entity, partialTick, packed);
	}
}

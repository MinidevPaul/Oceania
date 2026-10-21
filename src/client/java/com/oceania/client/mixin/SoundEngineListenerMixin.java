package com.oceania.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundEngine.class)
public class SoundEngineListenerMixin {
	private static final Identifier WHALE_SONG = Identifier.fromNamespaceAndPath("oceania", "entity.whale.song");
	private static final Identifier THUNDER = Identifier.withDefaultNamespace("entity.lightning_bolt.thunder");
	private static final Identifier LIGHTNING_IMPACT = Identifier.withDefaultNamespace("entity.lightning_bolt.impact");

	private static final int THUNDER_DEPTH = Integer.getInteger("oceania.sound.thunder.depth", 20);

	@Inject(method = "play(Lnet/minecraft/client/resources/sounds/SoundInstance;)Lnet/minecraft/client/sounds/SoundEngine$PlayResult;", at = @At("HEAD"), cancellable = true)
	private void oceania$gateByListener(SoundInstance sound, CallbackInfoReturnable<SoundEngine.PlayResult> cir) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}
		Identifier id = sound.getIdentifier();
		if (WHALE_SONG.equals(id)) {

			if (!player.isUnderWater()) {
				cir.setReturnValue(SoundEngine.PlayResult.NOT_STARTED);
			}
			return;
		}
		if ((THUNDER.equals(id) || LIGHTNING_IMPACT.equals(id)) && player.isUnderWater() && oceania$deeperThan(player, THUNDER_DEPTH)) {
			cir.setReturnValue(SoundEngine.PlayResult.NOT_STARTED);
		}
	}

	private static boolean oceania$deeperThan(LocalPlayer player, int depth) {
		Level level = player.level();
		int x = Mth.floor(player.getX());
		int z = Mth.floor(player.getZ());
		int eye = Mth.floor(player.getEyeY());
		BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
		for (int i = 1; i <= depth; i++) {
			probe.set(x, eye + i, z);
			if (!level.getFluidState(probe).is(FluidTags.WATER)) {
				return false;
			}
		}
		return true;
	}
}

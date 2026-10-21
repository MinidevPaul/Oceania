package com.oceania.client.mixin;

import java.util.List;

import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;

@Mixin(WorldCreationUiState.class)
public abstract class WorldTypeLockMixin {

	@Inject(method = "getNormalPresetList", at = @At("HEAD"), cancellable = true)
	private void oceania$onlyOurs(CallbackInfoReturnable<List<WorldCreationUiState.WorldTypeEntry>> cir) {
		cir.setReturnValue(List.of(((WorldCreationUiState) (Object) this).getWorldType()));
	}

	@Inject(method = "getAltPresetList", at = @At("HEAD"), cancellable = true)
	private void oceania$onlyOursAlt(CallbackInfoReturnable<List<WorldCreationUiState.WorldTypeEntry>> cir) {
		cir.setReturnValue(List.of(((WorldCreationUiState) (Object) this).getWorldType()));
	}
}

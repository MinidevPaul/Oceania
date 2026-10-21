package com.oceania.client.mixin.modmenu;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.terraformersmc.modmenu.gui.widget.DescriptionListWidget;
import com.terraformersmc.modmenu.util.mod.Mod;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(DescriptionListWidget.class)
public abstract class DescriptionListWidgetMixin {
	@Shadow
	@Final
	private static Component CREDITS_TEXT;

	@Shadow
	@Final
	private static Component LICENSE_TEXT;

	@Shadow
	private Mod selectedMod;

	@Unique
	private boolean oceania$ours() {
		return selectedMod != null && "oceania".equals(selectedMod.getId());
	}

	@WrapOperation(
		method = "rebuildUI",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Font;split(Lnet/minecraft/network/chat/FormattedText;I)Ljava/util/List;"),
		require = 0
	)
	private List<FormattedCharSequence> oceania$dropCreditsHeading(Font font, FormattedText text, int width, Operation<List<FormattedCharSequence>> original) {
		if (text == CREDITS_TEXT && oceania$ours()) {
			return List.of();
		}
		return original.call(font, text, width);
	}

	@Unique
	private static String oceania$plain(FormattedCharSequence text) {
		StringBuilder plain = new StringBuilder();
		text.accept((index, style, codePoint) -> {
			plain.appendCodePoint(codePoint);
			return true;
		});
		return plain.toString();
	}

	@Unique
	private static boolean oceania$coloured(FormattedCharSequence text) {
		boolean[] coloured = {false};
		text.accept((index, style, codePoint) -> {
			coloured[0] = style.getColor() != null;
			return false;
		});
		return coloured[0];
	}

	@Unique
	private static boolean oceania$isSectionTitle(String plain) {
		String trimmed = plain.trim();
		if (trimmed.isEmpty() || trimmed.length() > 40) return false;
		boolean letters = false;
		for (int i = 0; i < trimmed.length(); i++) {
			char c = trimmed.charAt(i);
			if (Character.isLetter(c)) {
				if (!Character.isUpperCase(c)) return false;
				letters = true;
			}
		}
		return letters;
	}

	@Inject(method = "rebuildUI", at = @At("TAIL"), require = 0)
	private void oceania$styleOurPanel(CallbackInfo ci) {
		if (!oceania$ours()) return;
		for (Object child : ((AbstractSelectionList<?>) (Object) this).children()) {
			if (!(child instanceof DescriptionEntryAccessor entry)) continue;
			int indent = entry.oceania$indent();
			String plain = oceania$plain(entry.oceania$text());
			boolean header = indent == 0 && (plain.equals(LICENSE_TEXT.getString()) || oceania$isSectionTitle(plain))
				|| indent == 8 && plain.endsWith(":");
			if (header) {
				if (!oceania$coloured(entry.oceania$text())) {
					entry.oceania$setText(Component.literal(plain).withStyle(ChatFormatting.GREEN).getVisualOrderText());
				}
				entry.oceania$setIndent(0);
			} else if (indent == 16) {
				entry.oceania$setIndent(8);
			}
		}
	}
}

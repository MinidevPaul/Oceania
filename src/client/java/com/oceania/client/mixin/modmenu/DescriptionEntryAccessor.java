package com.oceania.client.mixin.modmenu;

import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "com.terraformersmc.modmenu.gui.widget.DescriptionListWidget$DescriptionEntry", remap = false)
public interface DescriptionEntryAccessor {
	@Accessor("text")
	FormattedCharSequence oceania$text();

	@Accessor("text")
	void oceania$setText(FormattedCharSequence text);

	@Accessor("indent")
	int oceania$indent();

	@Accessor("indent")
	void oceania$setIndent(int indent);
}

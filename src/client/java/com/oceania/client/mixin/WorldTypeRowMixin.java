package com.oceania.client.mixin;

import com.oceania.Oceania;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "net/minecraft/client/gui/screens/worldselection/CreateWorldScreen$WorldTab")
public abstract class WorldTypeRowMixin {

	@Unique
	private static final int OCEANIA_ROW_WIDTH = 310;

	@Unique
	private static boolean oceania$reported;

	@Unique
	private int oceania$rowChild;

	@Redirect(
		method = "<init>",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/gui/layouts/GridLayout$RowHelper;"
				+ "addChild(Lnet/minecraft/client/gui/layouts/LayoutElement;)Lnet/minecraft/client/gui/layouts/LayoutElement;"),
		require = 0,
		expect = 2)
	private LayoutElement oceania$oneInertRow(GridLayout.RowHelper helper, LayoutElement child) {
		if (this.oceania$rowChild++ == 0) {
			Button plaque = Button.builder(
					CommonComponents.optionNameValue(
						Component.translatable("selectWorld.mapType"),
						Component.translatable("generator.minecraft.normal")),
					button -> {
					})
				.width(OCEANIA_ROW_WIDTH)
				.build();

			plaque.active = false;

			helper.addChild(plaque, 2);
			if (!oceania$reported) {
				oceania$reported = true;
				Oceania.LOGGER.info("[ui] world type row: one inert Oceania plaque spanning both columns");
			}
		}

		return child;
	}
}

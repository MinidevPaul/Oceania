package com.oceania.client.config;

import com.oceania.config.OceaniaConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.TextAlignment;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

public class OceaniaConfigScreen extends Screen {
	private static final int ROW_HEIGHT = 24;
	private static final int BUTTON_WIDTH = 210;

	private static final int GREY = 0xFFA0A0A0;
	private final Screen parent;
	private List<FormattedCharSequence> note = List.of();

	public OceaniaConfigScreen(Screen parent) {
		super(Component.translatable("oceania.config.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		OceaniaConfig config = OceaniaConfig.get();
		int x = this.width / 2 - BUTTON_WIDTH / 2;
		int y = 48;
		y = this.toggle(x, y, "sharks", config.spawnSharks, v -> config.spawnSharks = v);
		y = this.toggle(x, y, "whales", config.spawnWhales, v -> config.spawnWhales = v);
		y = this.toggle(x, y, "giant_squid", config.spawnGiantSquid, v -> config.spawnGiantSquid = v);
		y = this.toggle(x, y, "jellyfish", config.spawnJellyfish, v -> config.spawnJellyfish = v);
		this.toggle(x, y, "crabs", config.spawnCrabs, v -> config.spawnCrabs = v);

		this.note = this.font.split(Component.translatable("oceania.config.note"), this.width - 40);
		this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> this.onClose())
			.bounds(this.width / 2 - 100, this.height - 28, 200, 20)
			.build());
	}

	private interface Setter {
		void set(boolean value);
	}

	private int toggle(int x, int y, String key, boolean initial, Setter setter) {
		Component tooltip = Component.translatable("oceania.config.spawn." + key + ".tooltip");
		this.addRenderableWidget(CycleButton.onOffBuilder(initial)
			.withTooltip(value -> Tooltip.create(tooltip))
			.create(x, y, BUTTON_WIDTH, 20, Component.translatable("oceania.config.spawn." + key), (button, value) -> {
				setter.set(value);
				OceaniaConfig.save();
			}));
		return y + ROW_HEIGHT;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		graphics.textRenderer().accept(TextAlignment.CENTER, this.width / 2, 12, this.title);
		centred(graphics, Component.translatable("oceania.config.spawning"), 30);
		int noteHeight = this.note.size() * this.font.lineHeight;
		int y = this.height - 36 - noteHeight;
		for (FormattedCharSequence line : this.note) {
			graphics.text(this.font, line, this.width / 2 - this.font.width(line) / 2, y, GREY);
			y += this.font.lineHeight;
		}
	}

	private void centred(GuiGraphicsExtractor graphics, Component text, int y) {
		graphics.text(this.font, text, this.width / 2 - this.font.width(text) / 2, y, GREY);
	}

	@Override
	public void onClose() {
		OceaniaConfig.save();
		this.minecraft.setScreenAndShow(this.parent);
	}
}

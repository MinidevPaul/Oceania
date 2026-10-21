package com.oceania.client;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.joml.Matrix3x2fStack;

public final class MenuBackground {
	public static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("oceania", "textures/gui/title/ocean_menu.png");
	public static final int WIDTH = 480;
	public static final int HEIGHT = 270;
	public static final int FRAMES = 8;

	private static final long FRAME_MS = 130L;

	private MenuBackground() {
	}

	public static void draw(GuiGraphicsExtractor graphics, float partialTick) {
		Window window = Minecraft.getInstance().getWindow();
		double guiScale = window.getGuiScale();
		if (guiScale <= 0.0) {
			guiScale = 1.0;
		}
		int pixelW = window.getWidth();
		int pixelH = window.getHeight();
		if (pixelW <= 0 || pixelH <= 0) {
			return;
		}

		int scale = Math.max(ceilDiv(pixelW, WIDTH), ceilDiv(pixelH, HEIGHT));
		float scaleInGuiUnits = (float) (scale / guiScale);

		float offsetX = (float) ((pixelW - WIDTH * scale) / guiScale);
		float offsetY = (float) ((pixelH - HEIGHT * scale) / guiScale);
		int frame = (int) ((Util.getMillis() / FRAME_MS) % FRAMES);
		int color = ARGB.white(1.0F);
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(offsetX, offsetY);
		pose.scale(scaleInGuiUnits, scaleInGuiUnits);
		graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, 0, 0, 0.0F, (float) (frame * HEIGHT), WIDTH, HEIGHT, WIDTH, HEIGHT * FRAMES, color);
		pose.popMatrix();
	}

	private static int ceilDiv(int value, int divisor) {
		return Math.max(1, (value + divisor - 1) / divisor);
	}
}

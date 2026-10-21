package com.oceania.client;

import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

public final class UnderwaterView {

	public static final double FOG_SCALE = 100;
	public static final double FOG_MIN = 10;

	public static final double COLOR_SCALE = 70;

	public static final double SKY_HIDE_DEPTH = 24;

	private UnderwaterView() {
	}

	public static double depth(Camera camera, LevelReader level) {
		return depth(camera.position(), level);
	}

	public static double depth(Vec3 eye, LevelReader level) {
		return Math.max(0, surfaceY(level, eye.x, eye.z) - eye.y);
	}

	public static double surfaceY(LevelReader level, double x, double z) {
		return level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
	}

	public static double fogEnd(double depth, float renderDistance) {
		return Math.max(FOG_MIN, renderDistance * Math.exp(-depth / FOG_SCALE));
	}

	public static float brightness(double depth) {
		return (float) Math.exp(-depth / COLOR_SCALE);
	}
}

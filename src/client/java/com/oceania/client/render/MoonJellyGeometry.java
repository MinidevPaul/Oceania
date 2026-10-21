package com.oceania.client.render;

public final class MoonJellyGeometry {

	public static final int TEX_W = 64, TEX_H = 32;

	public static final float SPAN = 11.72F, HEIGHT = 10.65F;

	public static final float RIM_W = 10F, RIM_H = 1F, RIM_D = 10F;
	public static final float BELL_W = 8F, BELL_H = 2F, BELL_D = 8F;
	public static final float DOME_W = 5F, DOME_H = 2F, DOME_D = 5F;
	public static final float ORAL_W = 2F, ORAL_H = 6F, ORAL_D = 2F;
	public static final float TENTACLE_W = 1F, TENTACLE_H = 4F, TENTACLE_D = 1F;

	public static final int RIM_U = 0, RIM_V = 0;
	public static final int BELL_U = 0, BELL_V = 11;
	public static final int DOME_U = 32, DOME_V = 11;
	public static final int ORAL_U = 40, ORAL_V = 0;
	public static final int TENTACLE_U = 48, TENTACLE_V = 0;

	public static final float BELL_Y = 15.0000F;
	public static final float ORAL_R = 1.6000F;
	public static final float ORAL_SPLAY = 0.1400F;
	public static final int ORALS = 4;
	public static final float TENTACLE_R = 4.2000F;
	public static final float TENTACLE_SPLAY = 0.2600F;
	public static final int TENTACLES = 8;

	public static final float ORAL_INFLATE = -0.40F;
	public static final float TENTACLE_INFLATE = -0.15F;

	public static final int PULSE_TICKS = 44;
	public static final float PULSE_SQUASH = 0.1700F;
	public static final float TENTACLE_LAG = 0.5500F;
	public static final float TENTACLE_KICK = 0.3000F;

	public static final int[] TINTS = {0x47F2FF, 0x9C6BFF, 0x5BFFC4};
	public static final String[] TINT_NAMES = {"cyan", "violet", "seafoam"};

	private MoonJellyGeometry() {
	}
}

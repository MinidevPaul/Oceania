package com.oceania.client.render;

public final class NettleJellyGeometry {

	public static final int TEX_W = 64, TEX_H = 32;

	public static final float SPAN = 21.17F, HEIGHT = 22.84F;

	public static final float RIM_W = 9F, RIM_H = 1F, RIM_D = 9F;
	public static final float BELL_W = 7F, BELL_H = 3F, BELL_D = 7F;
	public static final float DOME_W = 4F, DOME_H = 3F, DOME_D = 4F;
	public static final float ORAL_W = 2F, ORAL_H = 12F, ORAL_D = 2F;
	public static final float TENTACLE_W = 1F, TENTACLE_H = 16F, TENTACLE_D = 1F;

	public static final int RIM_U = 12, RIM_V = 0;
	public static final int BELL_U = 0, BELL_V = 17;
	public static final int DOME_U = 48, DOME_V = 0;
	public static final int ORAL_U = 4, ORAL_V = 0;
	public static final int TENTACLE_U = 0, TENTACLE_V = 0;

	public static final float BELL_Y = 12.0000F;
	public static final float ORAL_R = 2.0000F;
	public static final float ORAL_SPLAY = 0.1100F;
	public static final int ORALS = 4;
	public static final float TENTACLE_R = 3.8000F;
	public static final float TENTACLE_SPLAY = 0.1400F;
	public static final int TENTACLES = 6;

	public static final float ORAL_INFLATE = -0.35F;
	public static final float TENTACLE_INFLATE = -0.15F;

	public static final int PULSE_TICKS = 58;
	public static final float PULSE_SQUASH = 0.2200F;
	public static final float TENTACLE_LAG = 0.5500F;
	public static final float TENTACLE_KICK = 0.4200F;

	public static final int[] TINTS = {0xFF46C8, 0xFFA33D, 0xC65BFF};
	public static final String[] TINT_NAMES = {"magenta", "amber", "orchid"};

	private NettleJellyGeometry() {
	}
}

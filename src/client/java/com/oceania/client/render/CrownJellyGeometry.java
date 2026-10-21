package com.oceania.client.render;

public final class CrownJellyGeometry {

	public static final int TEX_W = 64, TEX_H = 32;

	public static final float SPAN = 11.45F, HEIGHT = 23.90F;

	public static final float RIM_W = 9F, RIM_H = 1F, RIM_D = 9F;
	public static final float BELL_W = 7F, BELL_H = 2F, BELL_D = 7F;
	public static final float DOME_W = 4F, DOME_H = 1F, DOME_D = 4F;
	public static final float TENTACLE_W = 1F, TENTACLE_H = 4F, TENTACLE_D = 1F;
	public static final float TRAILER_W = 1F, TRAILER_H = 20F, TRAILER_D = 1F;

	public static final int RIM_U = 4, RIM_V = 0;
	public static final int BELL_U = 0, BELL_V = 21;
	public static final int DOME_U = 40, DOME_V = 0;
	public static final int TENTACLE_U = 56, TENTACLE_V = 0;
	public static final int TRAILER_U = 0, TRAILER_V = 0;

	public static final float BELL_Y = 14.0000F;
	public static final float ORAL_R = 0.0000F;
	public static final float ORAL_SPLAY = 0.0000F;
	public static final int ORALS = 0;
	public static final float TENTACLE_R = 4.2000F;
	public static final float TENTACLE_SPLAY = 0.2400F;
	public static final int TENTACLES = 10;

	public static final float TENTACLE_INFLATE = -0.15F;
	public static final float TRAILER_INFLATE = -0.10F;

	public static final int PULSE_TICKS = 70;
	public static final float PULSE_SQUASH = 0.1100F;
	public static final float TENTACLE_LAG = 0.5500F;
	public static final float TENTACLE_KICK = 0.2000F;

	public static final int[] TINTS = {0xFF3B52, 0x3D7BFF, 0x8CFF3D};
	public static final String[] TINT_NAMES = {"ember", "electric", "acid"};

	private CrownJellyGeometry() {
	}
}

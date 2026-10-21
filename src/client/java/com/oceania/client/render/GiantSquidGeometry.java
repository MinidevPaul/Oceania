package com.oceania.client.render;

public final class GiantSquidGeometry {

	public static final int TEX_W = 128, TEX_H = 128;

	public static final float LENGTH = 136.07F, GIRTH = 32.00F;
	public static final int ARMS = 8;
	public static final int TENT_SEGMENTS = 3;
	public static final String[] ARM_SEGMENTS = {"arm_up", "arm_mid", "arm_lo"};

	public static final float MANTLE_W = 17F, MANTLE_H = 17F, MANTLE_D = 42F;
	public static final float TIP_W = 9F, TIP_H = 9F, TIP_D = 12F;
	public static final float FIN_W = 16F, FIN_H = 3F, FIN_D = 16F;
	public static final float HEAD_W = 14F, HEAD_H = 13F, HEAD_D = 10F;
	public static final float EYE_W = 9F, EYE_H = 9F, EYE_D = 4F;
	public static final float ARM_UP_W = 4F, ARM_UP_H = 14F, ARM_UP_D = 4F;
	public static final float ARM_MID_W = 3F, ARM_MID_H = 12F, ARM_MID_D = 3F;
	public static final float ARM_LO_W = 2F, ARM_LO_H = 10F, ARM_LO_D = 2F;
	public static final float TENTACLE_W = 3F, TENTACLE_H = 20F, TENTACLE_D = 3F;
	public static final float CLUB_W = 5F, CLUB_H = 13F, CLUB_D = 5F;

	public static final int MANTLE_U = 0, MANTLE_V = 0;
	public static final int TIP_U = 60, TIP_V = 59;
	public static final int FIN_U = 0, FIN_V = 82;
	public static final int HEAD_U = 0, HEAD_V = 59;
	public static final int EYE_U = 96, EYE_V = 82;
	public static final int ARM_UP_U = 102, ARM_UP_V = 59;
	public static final int ARM_MID_U = 84, ARM_MID_V = 82;
	public static final int ARM_LO_U = 118, ARM_LO_V = 0;
	public static final int TENTACLE_U = 48, TENTACLE_V = 59;
	public static final int CLUB_U = 64, CLUB_V = 82;

	public static final float ARM_BEND = 0.4600F;
	public static final float ARM_R = 5.0000F;
	public static final float ARM_SWEEP = 0.4200F;
	public static final float AXIS_Y = 16.0000F;
	public static final float EYE_X = 7.2000F;
	public static final float EYE_Y = -1.0000F;
	public static final float EYE_Z = -3.5000F;
	public static final float FIN_X = 8.0000F;
	public static final float FIN_Z = 15.0000F;
	public static final float TENT_R = 3.0000F;
	public static final float TENT_SWEEP = 0.1600F;

	public static final float[] ARM_WAVE = {0.0600F, 0.1100F, 0.1900F};
	public static final float[] TENT_WAVE = {0.0500F, 0.1000F, 0.1700F, 0.2600F};
	public static final float WAVE_LAG = 0.1700F;

	private GiantSquidGeometry() {
	}
}

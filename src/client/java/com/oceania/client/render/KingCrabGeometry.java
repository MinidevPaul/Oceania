package com.oceania.client.render;

public final class KingCrabGeometry {

	public static final int TEX_W = 64, TEX_H = 48;
	public static final float GROUND_Y = 24.0F;

	public static final float SPAN = 44.15F, HEIGHT = 13.20F;
	public static final int LEGS_PER_SIDE = 3;

	public static final float CARAPACE_W = 9F, CARAPACE_H = 4F, CARAPACE_D = 10F;
	public static final float ROSTRUM_W = 2F, ROSTRUM_H = 1F, ROSTRUM_D = 4F;
	public static final float SPINE_W = 1F, SPINE_H = 1F, SPINE_D = 1F;
	public static final float EYE_W = 1F, EYE_H = 2F, EYE_D = 1F;
	public static final float CLAW_ARM_W = 10F, CLAW_ARM_H = 2F, CLAW_ARM_D = 2F;
	public static final float CLAW_W = 5F, CLAW_H = 3F, CLAW_D = 2F;
	public static final float LEG_UP_W = 11F, LEG_UP_H = 2F, LEG_UP_D = 2F;
	public static final float LEG_LO_W = 14F, LEG_LO_H = 1F, LEG_LO_D = 1F;

	public static final int CARAPACE_U = 0, CARAPACE_V = 0;
	public static final int ROSTRUM_U = 38, ROSTRUM_V = 0;
	public static final int SPINE_U = 54, SPINE_V = 14;
	public static final int EYE_U = 50, EYE_V = 14;
	public static final int CLAW_ARM_U = 0, CLAW_ARM_V = 14;
	public static final int CLAW_U = 50, CLAW_V = 0;
	public static final int LEG_UP_U = 24, LEG_UP_V = 14;
	public static final int LEG_LO_U = 0, LEG_LO_V = 18;

	public static final float BODY_Y = 15.0000F;
	public static final float CLAW_ARM_PITCH = -0.3000F;
	public static final float CLAW_ARM_YAW = 0.7800F;
	public static final float CLAW_PITCH = 0.4500F;
	public static final float CLAW_SMALL_SCALE = 0.7000F;
	public static final float CLAW_X = 3.2000F;
	public static final float CLAW_Y = 0.8000F;
	public static final float CLAW_YAW = -0.2500F;
	public static final float CLAW_Z = -5.0000F;
	public static final float EYE_X = 1.5000F;
	public static final float EYE_Y = -2.2000F;
	public static final float EYE_Z = -4.4000F;
	public static final float HIP_X = 4.0000F;
	public static final float HIP_Y = 0.5000F;
	public static final float[] LEG_FAN = {0.4200F, 0.0000F, -0.4200F};
	public static final float LEG_LO_DROP = 1.3000F;
	public static final float LEG_UP_RISE = -0.3000F;
	public static final float[] LEG_Z = {-2.5000F, 0.5000F, 3.5000F};
	public static final float ROSTRUM_Y = -1.6000F;
	public static final float ROSTRUM_Z = -4.8000F;
	public static final float[] SPINE_X = {3.0000F, 3.8000F, 2.6000F};
	public static final float[] SPINE_Z = {-3.2000F, 0.5000F, 3.8000F};

	public static final int GAIT_TICKS = 16;
	public static final float SWING = 0.2200F, LIFT = 0.0900F;

	private KingCrabGeometry() {
	}
}

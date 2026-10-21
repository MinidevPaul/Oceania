package com.oceania.client.render;

public final class ReefCrabGeometry {

	public static final int TEX_W = 64, TEX_H = 32;
	public static final float GROUND_Y = 24.0F;

	public static final float SPAN = 14.19F, HEIGHT = 6.75F;
	public static final int LEGS_PER_SIDE = 4;

	public static final float CARAPACE_W = 6F, CARAPACE_H = 3F, CARAPACE_D = 6F;
	public static final float DOME_W = 4F, DOME_H = 1F, DOME_D = 4F;
	public static final float ABDOMEN_W = 5F, ABDOMEN_H = 2F, ABDOMEN_D = 4F;
	public static final float EYE_W = 1F, EYE_H = 2F, EYE_D = 1F;
	public static final float ANTENNA_W = 1F, ANTENNA_H = 1F, ANTENNA_D = 2F;
	public static final float CLAW_ARM_W = 2F, CLAW_ARM_H = 2F, CLAW_ARM_D = 2F;
	public static final float CLAW_W = 3F, CLAW_H = 2F, CLAW_D = 3F;
	public static final float LEG_UP_W = 3F, LEG_UP_H = 2F, LEG_UP_D = 2F;
	public static final float LEG_LO_W = 4F, LEG_LO_H = 1F, LEG_LO_D = 1F;

	public static final int CARAPACE_U = 0, CARAPACE_V = 0;
	public static final int DOME_U = 42, DOME_V = 0;
	public static final int ABDOMEN_U = 24, ABDOMEN_V = 0;
	public static final int EYE_U = 58, EYE_V = 0;
	public static final int ANTENNA_U = 30, ANTENNA_V = 9;
	public static final int CLAW_ARM_U = 12, CLAW_ARM_V = 9;
	public static final int CLAW_U = 0, CLAW_V = 9;
	public static final int LEG_UP_U = 20, LEG_UP_V = 9;
	public static final int LEG_LO_U = 36, LEG_LO_V = 9;

	public static final float ABDOMEN_Y = 0.4000F;
	public static final float ABDOMEN_Z = 3.8000F;
	public static final float ANTENNA_PITCH = -0.1000F;
	public static final float ANTENNA_X = 1.6000F;
	public static final float ANTENNA_Y = -1.0000F;
	public static final float ANTENNA_YAW = 0.9000F;
	public static final float ANTENNA_Z = -2.8000F;
	public static final float BODY_Y = 21.0500F;
	public static final float CLAW_ARM_PITCH = 0.0800F;
	public static final float CLAW_ARM_YAW = 0.3000F;
	public static final float CLAW_PITCH = 0.0000F;
	public static final float CLAW_SMALL_SCALE = 0.8800F;
	public static final float CLAW_X = 2.2000F;
	public static final float CLAW_Y = 0.8000F;
	public static final float CLAW_YAW = -0.2600F;
	public static final float CLAW_Z = -2.6000F;
	public static final float EYE_X = 1.2000F;
	public static final float EYE_Y = -1.8000F;
	public static final float EYE_Z = -2.6000F;
	public static final float HIP_X = 2.2000F;
	public static final float HIP_Y = 0.2000F;
	public static final float[] LEG_FAN = {0.3600F, 0.1200F, -0.1200F, -0.3600F};
	public static final float LEG_LO_DROP = 1.3000F;
	public static final float LEG_UP_RISE = -0.2500F;
	public static final float[] LEG_Z = {-2.0000F, -0.6000F, 0.8000F, 2.2000F};

	public static final int GAIT_TICKS = 8;
	public static final float SWING = 0.3400F, LIFT = 0.1200F;

	private ReefCrabGeometry() {
	}
}

package com.oceania.client.render;

public final class CoconutCrabGeometry {

	public static final int TEX_W = 64, TEX_H = 32;
	public static final float GROUND_Y = 24.0F;

	public static final float SPAN = 19.46F, HEIGHT = 8.60F;
	public static final int LEGS_PER_SIDE = 3;

	public static final float CARAPACE_W = 7F, CARAPACE_H = 4F, CARAPACE_D = 7F;
	public static final float DOME_W = 5F, DOME_H = 2F, DOME_D = 5F;
	public static final float ABDOMEN_W = 6F, ABDOMEN_H = 4F, ABDOMEN_D = 5F;
	public static final float EYE_W = 1F, EYE_H = 2F, EYE_D = 1F;
	public static final float ANTENNA_W = 1F, ANTENNA_H = 1F, ANTENNA_D = 3F;
	public static final float CLAW_ARM_W = 3F, CLAW_ARM_H = 2F, CLAW_ARM_D = 2F;
	public static final float CLAW_W = 4F, CLAW_H = 3F, CLAW_D = 4F;
	public static final float LEG_UP_W = 4F, LEG_UP_H = 2F, LEG_UP_D = 2F;
	public static final float LEG_LO_W = 6F, LEG_LO_H = 1F, LEG_LO_D = 1F;

	public static final int CARAPACE_U = 0, CARAPACE_V = 0;
	public static final int DOME_U = 0, DOME_V = 11;
	public static final int ABDOMEN_U = 28, ABDOMEN_V = 0;
	public static final int EYE_U = 58, EYE_V = 0;
	public static final int ANTENNA_U = 50, ANTENNA_V = 0;
	public static final int CLAW_ARM_U = 36, CLAW_ARM_V = 11;
	public static final int CLAW_U = 20, CLAW_V = 11;
	public static final int LEG_UP_U = 46, LEG_UP_V = 11;
	public static final int LEG_LO_U = 0, LEG_LO_V = 18;

	public static final float ABDOMEN_Y = 0.6000F;
	public static final float ABDOMEN_Z = 4.6000F;
	public static final float ANTENNA_PITCH = -0.0800F;
	public static final float ANTENNA_X = 2.2000F;
	public static final float ANTENNA_Y = -1.6000F;
	public static final float ANTENNA_YAW = 1.0000F;
	public static final float ANTENNA_Z = -3.4000F;
	public static final float BODY_Y = 19.6000F;
	public static final float CLAW_ARM_PITCH = 0.0500F;
	public static final float CLAW_ARM_YAW = 0.2800F;
	public static final float CLAW_PITCH = 0.0000F;
	public static final float CLAW_SMALL_SCALE = 0.6200F;
	public static final float CLAW_X = 3.0000F;
	public static final float CLAW_Y = 1.4000F;
	public static final float CLAW_YAW = -0.2200F;
	public static final float CLAW_Z = -2.8000F;
	public static final float EYE_X = 1.5000F;
	public static final float EYE_Y = -2.2000F;
	public static final float EYE_Z = -3.0000F;
	public static final float HIP_X = 3.0000F;
	public static final float HIP_Y = 0.0000F;
	public static final float[] LEG_FAN = {0.3400F, 0.0600F, -0.3000F};
	public static final float LEG_LO_DROP = 1.3000F;
	public static final float LEG_UP_RISE = -0.2200F;
	public static final float[] LEG_Z = {-1.5000F, 1.0000F, 3.5000F};

	public static final int GAIT_TICKS = 14;
	public static final float SWING = 0.2600F, LIFT = 0.1000F;

	private CoconutCrabGeometry() {
	}
}

package com.oceania.client.render;

import com.oceania.mob.WhaleEntity;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

public final class WhaleGeometry {

	public static final float TL = 384.0F;
	public static final int TEX_W = 512, TEX_H = 512;

	public static final float Z_NOSE = -192.0F;
	public static final float Z_THROAT_START = -154.0F;
	public static final float Z_ROSTRUM_MID = -164.0F;
	public static final float Z_ROSTRUM_END = -125.0F;
	public static final float Z_HEAD_END = -92.0F;
	public static final float Z_THROAT_MID = -88.0F;
	public static final float Z_BODY1_END = -23.0F;
	public static final float Z_THROAT_END = 31.0F;
	public static final float Z_BODY2_END = 38.0F;
	public static final float Z_BODY3_END = 96.0F;
	public static final float Z_PED1_END = 136.0F;
	public static final float Z_PED_END = 173.0F;
	public static final float Z_TAIL = 192.0F;
	public static final float Z_JAW_HINGE = -115.0F;
	public static final float Y_AXIS = -8.0F;

	public static final float JAW_FULL = 1.3600F;

	public static final int GULLET_SLICES = 22;
	public static final float GULLET_SLICE_LEN = 2.0F;

	public static final float[][] POUCH_STAGES = {
		{0.00F, 1.00F, 1.00F},
		{0.18F, 1.22F, 1.75F},
		{0.52F, 1.42F, 2.55F},
		{0.84F, 1.60F, 3.20F},
	};

	public static final float RTIP_W = 27F, RTIP_H = 15F, RTIP_D = 29F;
	public static final float ROSTRUM_W = 35F, ROSTRUM_H = 20F, ROSTRUM_D = 40F;
	public static final float HEAD_W = 46F, HEAD_H = 36F, HEAD_D = 34F;
	public static final float RIDGE_W = 3F, RIDGE_H = 3F, RIDGE_D = 65F;
	public static final float BLOW_W = 18F, BLOW_H = 6F, BLOW_D = 15F;
	public static final float JAW_W = 36F, JAW_H = 12F, JAW_D = 78F;
	public static final float GULLET_SLICE_W = 42F, GULLET_SLICE_H = 17F, GULLET_SLICE_D = 3F;
	public static final float GULLET_STEP_W = 42F, GULLET_STEP_H = 17F, GULLET_STEP_D = 47F;
	public static final float THROAT2_W = 40F, THROAT2_H = 16F, THROAT2_D = 93F;
	public static final float THROAT3_W = 31F, THROAT3_H = 12F, THROAT3_D = 55F;
	public static final float BODY1_W = 58F, BODY1_H = 50F, BODY1_D = 70F;
	public static final float BODY2_W = 52F, BODY2_H = 46F, BODY2_D = 62F;
	public static final float BODY3_W = 40F, BODY3_H = 38F, BODY3_D = 59F;
	public static final float PED1_W = 22F, PED1_H = 28F, PED1_D = 41F;
	public static final float PED2_W = 15F, PED2_H = 18F, PED2_D = 38F;
	public static final float FLUKE_W = 32F, FLUKE_H = 3F, FLUKE_D = 21F;
	public static final float FLUKE_TIP_W = 26F, FLUKE_TIP_H = 2F, FLUKE_TIP_D = 14F;
	public static final float DORSAL_W = 2F, DORSAL_H = 5F, DORSAL_D = 12F;
	public static final float FLIPPER_W = 28F, FLIPPER_H = 2F, FLIPPER_D = 14F;
	public static final float FLIPPER_TIP_W = 23F, FLIPPER_TIP_H = 2F, FLIPPER_TIP_D = 10F;

	public static final int RTIP_U = 328, RTIP_V = 388;
	public static final int ROSTRUM_U = 178, ROSTRUM_V = 388;
	public static final int HEAD_U = 228, HEAD_V = 229;
	public static final int RIDGE_U = 126, RIDGE_V = 319;
	public static final int BLOW_U = 434, BLOW_V = 319;
	public static final int JAW_U = 0, JAW_V = 229;
	public static final int GULLET_SLICE_U = 106, GULLET_SLICE_V = 452;
	public static final int GULLET_STEP_U = 0, GULLET_STEP_V = 388;
	public static final int THROAT2_U = 0, THROAT2_V = 120;
	public static final int THROAT3_U = 262, THROAT3_V = 319;
	public static final int BODY1_U = 0, BODY1_V = 0;
	public static final int BODY2_U = 256, BODY2_V = 0;
	public static final int BODY3_U = 266, BODY3_V = 120;
	public static final int PED1_U = 0, PED1_V = 319;
	public static final int PED2_U = 388, PED2_V = 229;
	public static final int FLUKE_U = 0, FLUKE_V = 452;
	public static final int FLUKE_TIP_U = 196, FLUKE_TIP_V = 452;
	public static final int DORSAL_U = 484, DORSAL_V = 0;
	public static final int FLIPPER_U = 276, FLIPPER_V = 452;
	public static final int FLIPPER_TIP_U = 440, FLIPPER_TIP_V = 388;

	private WhaleGeometry() {
	}
}

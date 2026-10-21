package com.oceania.client.render;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

import static com.oceania.client.render.CoconutCrabGeometry.*;

public class CoconutCrabModel extends CrabModel {

	public CoconutCrabModel(ModelPart root) {

		super(root, LEGS_PER_SIDE, LEG_FAN, LEG_UP_RISE, SWING, LIFT, GAIT_TICKS,
			CLAW_SMALL_SCALE, true);
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();

		PartDefinition body = root.addOrReplaceChild("body",
			uv(CARAPACE_U, CARAPACE_V, false).addBox(
				-CARAPACE_W / 2.0F, -CARAPACE_H / 2.0F, -CARAPACE_D / 2.0F,
				CARAPACE_W, CARAPACE_H, CARAPACE_D),
			PartPose.offset(0.0F, BODY_Y, 0.0F));

		body.addOrReplaceChild("dome",
			uv(DOME_U, DOME_V, false).addBox(-DOME_W / 2.0F, -DOME_H, -DOME_D / 2.0F,
				DOME_W, DOME_H, DOME_D),
			PartPose.offset(0.0F, -CARAPACE_H / 2.0F, -0.5F));

		body.addOrReplaceChild("abdomen",
			uv(ABDOMEN_U, ABDOMEN_V, false).addBox(
				-ABDOMEN_W / 2.0F, -ABDOMEN_H / 2.0F, -ABDOMEN_D / 2.0F,
				ABDOMEN_W, ABDOMEN_H, ABDOMEN_D),
			PartPose.offset(0.0F, ABDOMEN_Y, ABDOMEN_Z));

		addLegs(body, LEGS_PER_SIDE,
			LEG_UP_U, LEG_UP_V, LEG_UP_W, LEG_UP_H, LEG_UP_D,
			LEG_LO_U, LEG_LO_V, LEG_LO_W, LEG_LO_H, LEG_LO_D,
			HIP_X, HIP_Y, LEG_Z, LEG_FAN, LEG_UP_RISE, LEG_LO_DROP);

		addClaws(body,
			CLAW_ARM_U, CLAW_ARM_V, CLAW_ARM_W, CLAW_ARM_H, CLAW_ARM_D,
			CLAW_U, CLAW_V, CLAW_W, CLAW_H, CLAW_D,
			CLAW_X, CLAW_Y, CLAW_Z, CLAW_ARM_YAW, CLAW_ARM_PITCH, CLAW_YAW, CLAW_PITCH);

		addEyes(body, EYE_U, EYE_V, EYE_W, EYE_H, EYE_D, EYE_X, EYE_Y, EYE_Z);

		for (int side = 0; side < 2; side++) {
			float sign = side == 0 ? 1.0F : -1.0F;
			body.addOrReplaceChild("antenna_" + (side == 0 ? "r" : "l"),
				uv(ANTENNA_U, ANTENNA_V, false).addBox(
					-ANTENNA_W / 2.0F, -ANTENNA_H / 2.0F, -ANTENNA_D,
					ANTENNA_W, ANTENNA_H, ANTENNA_D),
				PartPose.offsetAndRotation(sign * ANTENNA_X, ANTENNA_Y, ANTENNA_Z,
					ANTENNA_PITCH, sign * ANTENNA_YAW, 0.0F));
		}

		return LayerDefinition.create(mesh, TEX_W, TEX_H);
	}
}

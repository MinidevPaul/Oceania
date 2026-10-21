package com.oceania.client.render;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

public abstract class CrabModel extends EntityModel<CrabRenderState> {

	private static final float STRIDE_FREQ = 1.15F;

	private static final float IDLE_SHARE = 0.16F;

	protected final ModelPart body;

	private final ModelPart[] legUp;

	private final int legsPerSide;
	private final float[] legFan;
	private final float legUpRise;
	private final float swing;
	private final float lift;
	private final float idleRate;

	protected CrabModel(ModelPart root, int legsPerSide, float[] legFan, float legUpRise,
	                    float swing, float lift, int gaitTicks,
	                    float smallClawScale, boolean smallClawIsRight) {
		super(root);
		this.body = root.getChild("body");
		this.legsPerSide = legsPerSide;
		this.legFan = legFan;
		this.legUpRise = legUpRise;
		this.swing = swing;
		this.lift = lift;
		this.idleRate = Mth.TWO_PI / gaitTicks;

		ModelPart smaller = this.body.getChild(smallClawIsRight ? "claw_arm_r" : "claw_arm_l");
		smaller.xScale = smallClawScale;
		smaller.yScale = smallClawScale;
		smaller.zScale = smallClawScale;

		this.legUp = new ModelPart[legsPerSide * 2];
		for (int side = 0; side < 2; side++) {
			String s = side == 0 ? "r" : "l";
			for (int i = 0; i < legsPerSide; i++) {
				this.legUp[side * legsPerSide + i] = this.body.getChild("leg_up_" + s + i);
			}
		}
	}

	protected static CubeListBuilder uv(int u, int v, boolean mirror) {
		CubeListBuilder b = CubeListBuilder.create().texOffs(u, v);
		return mirror ? b.mirror() : b;
	}

	protected static void addLegs(PartDefinition body, int legsPerSide,
	                              int upU, int upV, float upW, float upH, float upD,
	                              int loU, int loV, float loW, float loH, float loD,
	                              float hipX, float hipY, float[] legZ, float[] legFan,
	                              float upRise, float loDrop) {
		for (int side = 0; side < 2; side++) {
			float sign = side == 0 ? 1.0F : -1.0F;
			boolean mirror = side == 1;
			String s = side == 0 ? "r" : "l";
			for (int i = 0; i < legsPerSide; i++) {
				PartDefinition up = body.addOrReplaceChild("leg_up_" + s + i,
					uv(upU, upV, mirror).addBox(mirror ? -upW : 0.0F, -upH / 2.0F, -upD / 2.0F,
						upW, upH, upD),
					PartPose.offsetAndRotation(sign * hipX, hipY, legZ[i],
						0.0F, sign * legFan[i], sign * upRise));
				up.addOrReplaceChild("leg_lo_" + s + i,
					uv(loU, loV, mirror).addBox(mirror ? -loW : 0.0F, -loH / 2.0F, -loD / 2.0F,
						loW, loH, loD),
					PartPose.offsetAndRotation(sign * upW, 0.0F, 0.0F, 0.0F, 0.0F, sign * loDrop));
			}
		}
	}

	protected static void addClaws(PartDefinition body,
	                               int armU, int armV, float armW, float armH, float armD,
	                               int clawU, int clawV, float clawW, float clawH, float clawD,
	                               float x, float y, float z, float armYaw, float armPitch,
	                               float clawYaw, float clawPitch) {
		for (int side = 0; side < 2; side++) {
			float sign = side == 0 ? 1.0F : -1.0F;
			boolean mirror = side == 1;
			String s = side == 0 ? "r" : "l";
			PartDefinition arm = body.addOrReplaceChild("claw_arm_" + s,
				uv(armU, armV, mirror).addBox(mirror ? -armW : 0.0F, -armH / 2.0F, -armD / 2.0F,
					armW, armH, armD),
				PartPose.offsetAndRotation(sign * x, y, z, 0.0F, sign * armYaw, sign * armPitch));
			arm.addOrReplaceChild("claw_" + s,
				uv(clawU, clawV, mirror).addBox(mirror ? -clawW : 0.0F, -clawH / 2.0F, -clawD,
					clawW, clawH, clawD),
				PartPose.offsetAndRotation(sign * armW, 0.0F, 0.0F,
					0.0F, sign * clawYaw, sign * clawPitch));
		}
	}

	protected static void addEyes(PartDefinition body, int u, int v, float w, float h, float d,
	                              float x, float y, float z) {
		for (int side = 0; side < 2; side++) {
			float sign = side == 0 ? 1.0F : -1.0F;
			body.addOrReplaceChild("eye_" + (side == 0 ? "r" : "l"),
				uv(u, v, false).addBox(-w / 2.0F, -h, -d / 2.0F, w, h, d),
				PartPose.offset(sign * x, y, z));
		}
	}

	@Override
	public void setupAnim(CrabRenderState state) {
		super.setupAnim(state);
		float amount = Math.min(state.walkAnimationSpeed, 1.0F);
		float walkPhase = state.walkAnimationPos * STRIDE_FREQ;
		float idlePhase = state.ageInTicks * this.idleRate;

		this.body.yRot = state.yRot * Mth.DEG_TO_RAD * 0.25F;
		this.body.xRot = state.xRot * Mth.DEG_TO_RAD * 0.25F
			+ 0.02F * amount * Mth.sin(walkPhase * 2.0F);

		for (int side = 0; side < 2; side++) {
			float sign = side == 0 ? 1.0F : -1.0F;
			for (int i = 0; i < this.legsPerSide; i++) {
				ModelPart up = this.legUp[side * this.legsPerSide + i];

				boolean leading = (i % 2 == 0) == (side == 0);
				float phase = walkPhase + (leading ? 0.0F : Mth.PI);

				float fore = this.swing * amount * Mth.sin(phase)
					+ this.swing * IDLE_SHARE * Mth.sin(idlePhase + i * 1.7F);

				float up2 = this.lift * amount * Math.max(0.0F, Mth.cos(phase));

				up.yRot = sign * (this.legFan[i] + fore);
				up.zRot = sign * (this.legUpRise - up2);
			}
		}
	}
}

package com.oceania.client.render;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

import static com.oceania.client.render.GiantSquidGeometry.*;

public class GiantSquidModel extends EntityModel<GiantSquidRenderState> {

	private final ModelPart body;
	private final ModelPart mantle;
	private final ModelPart[] armBase;
	private final ModelPart[][] armJoint;
	private final ModelPart[] tentBase;
	private final ModelPart[][] tentJoint;

	public GiantSquidModel(ModelPart root) {
		super(root);
		this.body = root.getChild("body");
		this.mantle = this.body.getChild("mantle");

		this.armBase = new ModelPart[ARMS];
		this.armJoint = new ModelPart[ARMS][ARM_SEGMENTS.length - 1];
		for (int i = 0; i < ARMS; i++) {
			ModelPart node = this.body.getChild("arm_" + ARM_SEGMENTS[0] + i);
			this.armBase[i] = node;
			for (int s = 1; s < ARM_SEGMENTS.length; s++) {
				node = node.getChild("arm_" + ARM_SEGMENTS[s] + i);
				this.armJoint[i][s - 1] = node;
			}
		}

		this.tentBase = new ModelPart[2];
		this.tentJoint = new ModelPart[2][TENT_SEGMENTS];
		for (int side = 0; side < 2; side++) {
			String s = side == 0 ? "r" : "l";
			ModelPart node = this.body.getChild("tent_" + s);
			this.tentBase[side] = node;
			for (int seg = 1; seg < TENT_SEGMENTS; seg++) {
				node = node.getChild("tent_" + s + seg);
				this.tentJoint[side][seg - 1] = node;
			}
			this.tentJoint[side][TENT_SEGMENTS - 1] = node.getChild("club_" + s);
		}
	}

	private static CubeListBuilder uv(int u, int v, boolean mirror) {
		CubeListBuilder b = CubeListBuilder.create().texOffs(u, v);
		return mirror ? b.mirror() : b;
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();

		PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create(),
			PartPose.offset(0.0F, AXIS_Y, 0.0F));

		body.addOrReplaceChild("mantle",
			uv(MANTLE_U, MANTLE_V, false).addBox(-MANTLE_W / 2.0F, -MANTLE_H / 2.0F,
				-MANTLE_D / 2.0F, MANTLE_W, MANTLE_H, MANTLE_D),
			PartPose.ZERO);

		body.addOrReplaceChild("tip",
			uv(TIP_U, TIP_V, false).addBox(-TIP_W / 2.0F, -TIP_H / 2.0F, 0.0F,
				TIP_W, TIP_H, TIP_D),
			PartPose.offset(0.0F, 0.0F, MANTLE_D / 2.0F));

		for (int side = 0; side < 2; side++) {
			float sign = side == 0 ? 1.0F : -1.0F;
			boolean mirror = side == 1;
			body.addOrReplaceChild("fin_" + (side == 0 ? "r" : "l"),
				uv(FIN_U, FIN_V, mirror).addBox(mirror ? -FIN_W : 0.0F, -FIN_H / 2.0F,
					-FIN_D / 2.0F, FIN_W, FIN_H, FIN_D),
				PartPose.offsetAndRotation(sign * FIN_X, 0.0F, FIN_Z, 0.0F, 0.0F, sign * 0.18F));
		}

		PartDefinition head = body.addOrReplaceChild("head",
			uv(HEAD_U, HEAD_V, false).addBox(-HEAD_W / 2.0F, -HEAD_H / 2.0F, -HEAD_D,
				HEAD_W, HEAD_H, HEAD_D),
			PartPose.offset(0.0F, 0.0F, -MANTLE_D / 2.0F));

		for (int side = 0; side < 2; side++) {
			float sign = side == 0 ? 1.0F : -1.0F;
			head.addOrReplaceChild("eye_" + (side == 0 ? "r" : "l"),
				uv(EYE_U, EYE_V, false).addBox(-EYE_W / 2.0F, -EYE_H / 2.0F, -EYE_D / 2.0F,
					EYE_W, EYE_H, EYE_D),
				PartPose.offset(sign * EYE_X, EYE_Y, EYE_Z));
		}

		float[][] seg = {{ARM_UP_W, ARM_UP_H, ARM_UP_D},
			{ARM_MID_W, ARM_MID_H, ARM_MID_D},
			{ARM_LO_W, ARM_LO_H, ARM_LO_D}};
		int[][] segUv = {{ARM_UP_U, ARM_UP_V}, {ARM_MID_U, ARM_MID_V}, {ARM_LO_U, ARM_LO_V}};

		if (seg.length != ARM_SEGMENTS.length) {
			throw new IllegalStateException("GiantSquidModel has " + seg.length
				+ " arm segments but GiantSquidGeometry declares " + ARM_SEGMENTS.length
				+ "; rerun tools/giant_squid_textures.py --install and update this model");
		}

		for (int i = 0; i < ARMS; i++) {
			float a = (float) (2.0 * Math.PI * i / ARMS);
			PartDefinition node = body.addOrReplaceChild("arm_" + ARM_SEGMENTS[0] + i,
				uv(segUv[0][0], segUv[0][1], false).addBox(-seg[0][0] / 2.0F, 0.0F,
					-seg[0][2] / 2.0F, seg[0][0], seg[0][1], seg[0][2]),
				PartPose.offsetAndRotation(-Mth.sin(a) * ARM_R, Mth.cos(a) * ARM_R, -HEAD_D,
					-Mth.HALF_PI + ARM_SWEEP, 0.0F, a));
			for (int s = 1; s < ARM_SEGMENTS.length; s++) {
				node = node.addOrReplaceChild("arm_" + ARM_SEGMENTS[s] + i,
					uv(segUv[s][0], segUv[s][1], false).addBox(-seg[s][0] / 2.0F, 0.0F,
						-seg[s][2] / 2.0F, seg[s][0], seg[s][1], seg[s][2]),
					PartPose.offsetAndRotation(0.0F, seg[s - 1][1], 0.0F,
						ARM_BEND / (ARM_SEGMENTS.length - 1), 0.0F, 0.0F));
			}
		}

		for (int side = 0; side < 2; side++) {
			float sign = side == 0 ? 1.0F : -1.0F;
			String s = side == 0 ? "r" : "l";
			float a = sign * Mth.HALF_PI;
			PartDefinition node = body.addOrReplaceChild("tent_" + s,
				uv(TENTACLE_U, TENTACLE_V, false).addBox(-TENTACLE_W / 2.0F, 0.0F,
					-TENTACLE_D / 2.0F, TENTACLE_W, TENTACLE_H, TENTACLE_D),
				PartPose.offsetAndRotation(-Mth.sin(a) * TENT_R, Mth.cos(a) * TENT_R + 2.0F,
					-HEAD_D, -Mth.HALF_PI + TENT_SWEEP, 0.0F, a));
			for (int segIdx = 1; segIdx < TENT_SEGMENTS; segIdx++) {
				node = node.addOrReplaceChild("tent_" + s + segIdx,
					uv(TENTACLE_U, TENTACLE_V, false).addBox(-TENTACLE_W / 2.0F, 0.0F,
						-TENTACLE_D / 2.0F, TENTACLE_W, TENTACLE_H, TENTACLE_D),
					PartPose.offsetAndRotation(0.0F, TENTACLE_H, 0.0F, 0.06F, 0.0F, 0.0F));
			}
			node.addOrReplaceChild("club_" + s,
				uv(CLUB_U, CLUB_V, false).addBox(-CLUB_W / 2.0F, 0.0F, -CLUB_D / 2.0F,
					CLUB_W, CLUB_H, CLUB_D),
				PartPose.offset(0.0F, TENTACLE_H, 0.0F));
		}

		return LayerDefinition.create(mesh, TEX_W, TEX_H);
	}

	@Override
	public void setupAnim(GiantSquidRenderState state) {
		super.setupAnim(state);
		float phase = state.wavePhase;
		float lag = state.turnLag;
		float jet = state.jet;
		float strike = state.strike;

		float squeeze = 1.0F - 0.16F * jet;
		this.mantle.xScale = squeeze;
		this.mantle.yScale = squeeze;
		this.mantle.zScale = 1.0F + 0.05F * jet;

		float opening = strike * 0.9F;

		for (int i = 0; i < ARMS; i++) {
			float a = (float) (2.0 * Math.PI * i / ARMS);

			float armPhase = phase + a / Mth.TWO_PI;
			float wave = ARM_WAVE[0] * Mth.sin(armPhase);

			this.armBase[i].xRot = -Mth.HALF_PI + ARM_SWEEP + opening * 0.7F + wave
				+ lag * Mth.cos(a) * 0.5F;
			this.armBase[i].zRot = a + lag * Mth.sin(a) * 0.3F;
			for (int s = 0; s < this.armJoint[i].length; s++) {
				float amp = ARM_WAVE[Math.min(s + 1, ARM_WAVE.length - 1)];
				this.armJoint[i][s].xRot =
					ARM_BEND / (ARM_SEGMENTS.length - 1) - opening * 0.5F
						+ amp * Mth.sin(armPhase - (s + 1) * WAVE_LAG * Mth.TWO_PI);
			}
		}

		for (int side = 0; side < 2; side++) {
			float sign = side == 0 ? 1.0F : -1.0F;
			float a = sign * Mth.HALF_PI;
			this.tentBase[side].xRot = -Mth.HALF_PI + TENT_SWEEP
				+ TENT_WAVE[0] * Mth.sin(phase) + lag * Mth.cos(a) * 0.6F;
			for (int seg = 0; seg < this.tentJoint[side].length; seg++) {
				float amp = TENT_WAVE[Math.min(seg + 1, TENT_WAVE.length - 1)];

				float damp = 1.0F - strike * 0.8F;
				this.tentJoint[side][seg].xRot = 0.06F * damp
					+ amp * damp * Mth.sin(phase - (seg + 1) * WAVE_LAG * Mth.TWO_PI);
			}
		}
	}
}

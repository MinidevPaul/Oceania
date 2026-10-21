package com.oceania.client.render;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

import static com.oceania.client.render.WhaleGeometry.*;

public class WhaleModel extends EntityModel<WhaleRenderState> {

	private static final float WAVE_LAG = 0.62F;

	private static final float FLUKE_FEATHER = 0.9F;

	private static final float[] WAVE_AMP = {0.048F, 0.050F, 0.092F, 0.143F, 0.186F, 0.219F};

	private static final float WAVE_RATE = 0.19F;

	private static final float OVERLAP = 1.0F;
	private static final CubeDeformation SEAM = new CubeDeformation(0.02F);

	private static final float MOUTH_Y = -HEAD_H / 2.0F + HEAD_H * 0.06F + ROSTRUM_H;

	private final ModelPart body1;
	private final ModelPart body2;
	private final ModelPart body3;
	private final ModelPart ped1;
	private final ModelPart ped2;
	private final ModelPart fluke;
	private final ModelPart head;
	private final ModelPart jaw;
	private final ModelPart throat2;
	private final ModelPart throat3;
	private final ModelPart gulletStep;
	private final ModelPart[] gullet = new ModelPart[GULLET_SLICES];

	public WhaleModel(ModelPart root) {
		super(root);
		this.body1 = root.getChild("body1");
		this.head = this.body1.getChild("head");
		this.jaw = this.head.getChild("jaw");
		this.body2 = this.body1.getChild("body2");
		this.body3 = this.body2.getChild("body3");
		this.ped1 = this.body3.getChild("ped1");
		this.ped2 = this.ped1.getChild("ped2");
		this.fluke = this.ped2.getChild("fluke");
		this.throat2 = this.body1.getChild("throat2");
		this.throat3 = this.body2.getChild("throat3");
		this.gulletStep = this.body1.getChild("gullet_step");
		for (int i = 0; i < GULLET_SLICES; i++) {
			this.gullet[i] = this.body1.getChild("gullet_" + i);
		}
	}

	private static CubeListBuilder uv(int u, int v) {
		return CubeListBuilder.create().texOffs(u, v);
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();

		PartDefinition body1 = root.addOrReplaceChild("body1",
			uv(BODY1_U, BODY1_V).addBox(-BODY1_W / 2.0F, -BODY1_H / 2.0F, 0.0F,
				BODY1_W, BODY1_H, BODY1_D, SEAM),
			PartPose.offset(0.0F, Y_AXIS, Z_HEAD_END));

		float headLen = Z_HEAD_END - Z_ROSTRUM_END;
		float noseLen = Z_ROSTRUM_END - Z_NOSE;
		float rosTop = -HEAD_H / 2.0F + HEAD_H * 0.06F;
		float tipTop = MOUTH_Y - RTIP_H;

		PartDefinition head = body1.addOrReplaceChild("head",
			uv(HEAD_U, HEAD_V)
				.addBox(-HEAD_W / 2.0F, -HEAD_H / 2.0F, Z_ROSTRUM_END - Z_HEAD_END - 1.0F,
					HEAD_W, HEAD_H, HEAD_D, SEAM)

				.texOffs(ROSTRUM_U, ROSTRUM_V)
				.addBox(-ROSTRUM_W / 2.0F, rosTop, Z_ROSTRUM_MID - Z_HEAD_END - 1.0F,
					ROSTRUM_W, ROSTRUM_H, ROSTRUM_D)
				.texOffs(RTIP_U, RTIP_V)
				.addBox(-RTIP_W / 2.0F, tipTop, Z_NOSE - Z_HEAD_END, RTIP_W, RTIP_H, RTIP_D)

				.texOffs(RIDGE_U, RIDGE_V)
				.addBox(-RIDGE_W / 2.0F, tipTop - 2.5F, Z_NOSE - Z_HEAD_END,
					RIDGE_W, RIDGE_H, RIDGE_D)

				.texOffs(BLOW_U, BLOW_V)
				.addBox(-BLOW_W / 2.0F, -HEAD_H / 2.0F - BLOW_H + 1.0F,
					Z_NOSE + 0.165F * TL - Z_HEAD_END, BLOW_W, BLOW_H, BLOW_D),
			PartPose.ZERO);

		head.addOrReplaceChild("jaw",
			uv(JAW_U, JAW_V).addBox(-JAW_W / 2.0F, 0.0F, -JAW_D + OVERLAP,
				JAW_W, JAW_H, JAW_D),
			PartPose.offset(0.0F, MOUTH_Y, Z_JAW_HINGE - Z_HEAD_END));

		for (int i = 0; i < GULLET_SLICES; i++) {
			body1.addOrReplaceChild("gullet_" + i,
				uv(GULLET_SLICE_U, GULLET_SLICE_V)
					.addBox(-GULLET_SLICE_W / 2.0F, 0.0F, -GULLET_SLICE_D + OVERLAP,
						GULLET_SLICE_W, GULLET_SLICE_H, GULLET_SLICE_D),
				PartPose.offset(0.0F, 0.0F,
					Z_JAW_HINGE - Z_HEAD_END - i * GULLET_SLICE_LEN));
		}

		body1.addOrReplaceChild("gullet_step",
			uv(GULLET_STEP_U, GULLET_STEP_V)
				.addBox(-GULLET_STEP_W / 2.0F, 0.0F, -GULLET_STEP_D + OVERLAP,
					GULLET_STEP_W, GULLET_STEP_H, GULLET_STEP_D),
			PartPose.offset(0.0F, 0.0F, Z_JAW_HINGE - Z_HEAD_END + GULLET_STEP_D - OVERLAP));

		body1.addOrReplaceChild("throat2",
			uv(THROAT2_U, THROAT2_V).addBox(-THROAT2_W / 2.0F, 0.0F, -THROAT2_D + OVERLAP,
				THROAT2_W, THROAT2_H, THROAT2_D),
			PartPose.offset(0.0F, 0.0F, Z_BODY1_END - Z_HEAD_END));

		float flipZ = Z_NOSE + 0.310F * TL - Z_HEAD_END;
		body1.addOrReplaceChild("flipper_left",
			uv(FLIPPER_U, FLIPPER_V).mirror()
				.addBox(0.0F, -1.0F, 0.0F, FLIPPER_W, FLIPPER_H, FLIPPER_D)
				.texOffs(FLIPPER_TIP_U, FLIPPER_TIP_V).mirror()
				.addBox(FLIPPER_W - 0.5F, -1.0F, FLIPPER_D * 0.26F,
					FLIPPER_TIP_W, FLIPPER_TIP_H, FLIPPER_TIP_D),
			PartPose.offsetAndRotation(BODY1_W / 2.0F - 1.0F, BODY1_H * 0.22F, flipZ,
				0.0F, -0.52F, 0.34F));
		body1.addOrReplaceChild("flipper_right",
			uv(FLIPPER_U, FLIPPER_V)
				.addBox(-FLIPPER_W, -1.0F, 0.0F, FLIPPER_W, FLIPPER_H, FLIPPER_D)
				.texOffs(FLIPPER_TIP_U, FLIPPER_TIP_V)
				.addBox(-FLIPPER_W - FLIPPER_TIP_W + 0.5F, -1.0F, FLIPPER_D * 0.26F,
					FLIPPER_TIP_W, FLIPPER_TIP_H, FLIPPER_TIP_D),
			PartPose.offsetAndRotation(-BODY1_W / 2.0F + 1.0F, BODY1_H * 0.22F, flipZ,
				0.0F, 0.52F, -0.34F));

		PartDefinition body2 = body1.addOrReplaceChild("body2",
			uv(BODY2_U, BODY2_V).addBox(-BODY2_W / 2.0F, -BODY2_H / 2.0F, -OVERLAP,
				BODY2_W, BODY2_H, BODY2_D, SEAM),
			PartPose.offset(0.0F, 0.0F, Z_BODY1_END - Z_HEAD_END));

		body2.addOrReplaceChild("throat3",
			uv(THROAT3_U, THROAT3_V).addBox(-THROAT3_W / 2.0F, 0.0F, -THROAT3_D + OVERLAP,
				THROAT3_W, THROAT3_H, THROAT3_D),
			PartPose.offset(0.0F, 0.0F, Z_THROAT_END - Z_BODY1_END));

		PartDefinition body3 = body2.addOrReplaceChild("body3",
			uv(BODY3_U, BODY3_V).addBox(-BODY3_W / 2.0F, -BODY3_H / 2.0F, -OVERLAP,
				BODY3_W, BODY3_H, BODY3_D, SEAM),
			PartPose.offset(0.0F, 0.0F, Z_BODY2_END - Z_BODY1_END));

		body3.addOrReplaceChild("dorsal",
			uv(DORSAL_U, DORSAL_V).addBox(-DORSAL_W / 2.0F, -DORSAL_H, -1.0F,
				DORSAL_W, DORSAL_H, DORSAL_D),
			PartPose.offsetAndRotation(0.0F, -BODY3_H / 2.0F + 2.0F,
				Z_NOSE + 0.750F * TL - Z_BODY2_END, 0.14F, 0.0F, 0.0F));

		PartDefinition ped1 = body3.addOrReplaceChild("ped1",
			uv(PED1_U, PED1_V).addBox(-PED1_W / 2.0F, -PED1_H / 2.0F, -OVERLAP,
				PED1_W, PED1_H, PED1_D, SEAM),
			PartPose.offset(0.0F, 0.0F, Z_BODY3_END - Z_BODY2_END));

		PartDefinition ped2 = ped1.addOrReplaceChild("ped2",
			uv(PED2_U, PED2_V).addBox(-PED2_W / 2.0F, -PED2_H / 2.0F, -OVERLAP,
				PED2_W, PED2_H, PED2_D, SEAM),
			PartPose.offset(0.0F, 0.0F, Z_PED1_END - Z_BODY3_END));

		ped2.addOrReplaceChild("fluke",
			uv(FLUKE_U, FLUKE_V)
				.addBox(-FLUKE_W / 2.0F, -FLUKE_H / 2.0F, -OVERLAP,
					FLUKE_W, FLUKE_H, FLUKE_D)
				.texOffs(FLUKE_TIP_U, FLUKE_TIP_V).mirror()
				.addBox(FLUKE_W / 2.0F - 0.5F, -FLUKE_TIP_H / 2.0F, FLUKE_D * 0.45F,
					FLUKE_TIP_W, FLUKE_TIP_H, FLUKE_TIP_D)
				.texOffs(FLUKE_TIP_U, FLUKE_TIP_V)
				.addBox(-FLUKE_W / 2.0F - FLUKE_TIP_W + 0.5F, -FLUKE_TIP_H / 2.0F,
					FLUKE_D * 0.45F, FLUKE_TIP_W, FLUKE_TIP_H, FLUKE_TIP_D),
			PartPose.offset(0.0F, 0.0F, Z_PED_END - Z_PED1_END));

		return LayerDefinition.create(mesh, TEX_W, TEX_H);
	}

	private static void pouchStage(float gape, float[] out) {
		out[0] = 1.0F;
		out[1] = 1.0F;
		for (float[] stage : POUCH_STAGES) {
			if (gape >= stage[0]) {
				out[0] = stage[1];
				out[1] = stage[2];
			}
		}
	}

	private final float[] stage = new float[2];

	@Override
	public void setupAnim(WhaleRenderState state) {
		super.setupAnim(state);

		this.body1.xRot = state.xRot * Mth.DEG_TO_RAD;
		this.body1.yRot = state.yRot * Mth.DEG_TO_RAD;

		float phase = state.walkAnimationPos * WAVE_RATE;

		float amp = 0.25F + 0.75F * Math.min(state.walkAnimationSpeed, 1.0F);

		this.body1.xRot += WAVE_AMP[0] * amp * Mth.sin(phase);
		this.body2.xRot = WAVE_AMP[1] * amp * Mth.sin(phase - WAVE_LAG);
		this.body3.xRot = WAVE_AMP[2] * amp * Mth.sin(phase - WAVE_LAG * 2.0F);
		this.ped1.xRot = WAVE_AMP[3] * amp * Mth.sin(phase - WAVE_LAG * 3.0F);
		this.ped2.xRot = WAVE_AMP[4] * amp * Mth.sin(phase - WAVE_LAG * 4.0F);
		this.fluke.xRot = WAVE_AMP[5] * amp
			* Mth.sin(phase - WAVE_LAG * 5.0F - FLUKE_FEATHER);

		float gape = state.gape;
		float theta = gape * JAW_FULL;
		this.jaw.xRot = theta;

		pouchStage(gape, this.stage);
		float sw = this.stage[0];
		float sh = this.stage[1];

		float gTop = BODY1_H / 2.0F - GULLET_SLICE_H + 2.0F;

		float jawTipY = MOUTH_Y + JAW_D * Mth.sin(theta);
		float gBot = Math.max(gTop + GULLET_SLICE_H * sh, jawTipY);

		float tanJ = (float) Math.tan(Math.min(theta, 1.50F));

		for (int i = 0; i < GULLET_SLICES; i++) {
			ModelPart slice = this.gullet[i];
			float d = (i + 0.5F) * GULLET_SLICE_LEN;
			float visTop = Math.max(gTop, MOUTH_Y + d * tanJ);
			if (visTop >= gBot - 0.5F) {

				slice.visible = false;
				continue;
			}
			slice.visible = true;
			slice.y = visTop;
			slice.xScale = sw;
			slice.yScale = (gBot - visTop) / GULLET_SLICE_H;
		}

		float t2Top = BODY1_H / 2.0F - THROAT2_H + 2.0F;
		float s2h = 1.0F + (sh - 1.0F) * 0.78F;
		this.throat2.y = t2Top;
		this.throat2.xScale = 1.0F + (sw - 1.0F) * 0.78F;
		this.throat2.yScale = s2h;

		float t3Top = BODY2_H / 2.0F - THROAT3_H + 2.0F;
		this.throat3.y = t3Top;
		this.throat3.xScale = 1.0F + (sw - 1.0F) * 0.42F;
		this.throat3.yScale = 1.0F + (sh - 1.0F) * 0.42F;

		float stepTop = BODY1_H / 2.0F - GULLET_STEP_H + 2.0F;
		float stepBot = 0.5F * (gBot + (t2Top + THROAT2_H * s2h));
		if (stepBot > stepTop + GULLET_STEP_H * 0.2F) {
			this.gulletStep.visible = true;
			this.gulletStep.y = stepTop;
			this.gulletStep.xScale = sw;
			this.gulletStep.yScale = (stepBot - stepTop) / GULLET_STEP_H;
		} else {
			this.gulletStep.visible = false;
		}
	}
}

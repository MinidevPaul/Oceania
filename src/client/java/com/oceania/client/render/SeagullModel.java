package com.oceania.client.render;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

import static com.oceania.client.render.SeagullGeometry.*;

public class SeagullModel extends EntityModel<SeagullRenderState> {

	private final ModelPart body;
	private final ModelPart head;
	private final ModelPart tail;
	private final ModelPart wingLeft;
	private final ModelPart wingRight;
	private final ModelPart wingTipLeft;
	private final ModelPart wingTipRight;

	public SeagullModel(ModelPart root) {
		super(root);
		this.body = root.getChild("body");
		this.head = this.body.getChild("head");
		this.tail = this.body.getChild("tail");
		this.wingLeft = this.body.getChild("wing_left");
		this.wingRight = this.body.getChild("wing_right");
		this.wingTipLeft = this.wingLeft.getChild("wing_tip_left");
		this.wingTipRight = this.wingRight.getChild("wing_tip_right");
	}

	private static CubeListBuilder uv(int u, int v) {
		return CubeListBuilder.create().texOffs(u, v);
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();

		PartDefinition body = root.addOrReplaceChild("body",
			uv(BODY_U, BODY_V).addBox(-BODY_W / 2.0F, -BODY_H / 2.0F, -BODY_D / 2.0F,
				BODY_W, BODY_H, BODY_D),
			PartPose.offset(0.0F, Y_AXIS, 0.0F));

		PartDefinition head = body.addOrReplaceChild("head",
			uv(HEAD_U, HEAD_V).addBox(-HEAD_W / 2.0F, -HEAD_H / 2.0F, -HEAD_D + 0.5F,
				HEAD_W, HEAD_H, HEAD_D),
			PartPose.offsetAndRotation(0.0F, -0.8F, -BODY_D / 2.0F + 0.5F, -0.10F, 0.0F, 0.0F));
		head.addOrReplaceChild("bill",
			uv(BILL_U, BILL_V).addBox(-BILL_W / 2.0F, -BILL_H / 2.0F, -BILL_D,
				BILL_W, BILL_H, BILL_D),
			PartPose.offset(0.0F, 0.6F, -HEAD_D + 0.5F));

		body.addOrReplaceChild("tail",
			uv(TAIL_U, TAIL_V).addBox(-TAIL_W / 2.0F, -0.5F, 0.0F, TAIL_W, TAIL_H, TAIL_D),
			PartPose.offsetAndRotation(0.0F, 0.0F, BODY_D / 2.0F - 0.5F, 0.12F, 0.0F, 0.0F));

		PartDefinition wingL = body.addOrReplaceChild("wing_left",
			uv(WING_U, WING_V).mirror()
				.addBox(0.0F, -0.5F, -WING_D / 2.0F, WING_W, WING_H, WING_D),
			PartPose.offsetAndRotation(BODY_W / 2.0F - 0.5F, -BODY_H * 0.18F, -0.5F,
				0.0F, SWEEP, -DIHEDRAL));
		wingL.addOrReplaceChild("wing_tip_left",
			uv(WING_TIP_U, WING_TIP_V).mirror()
				.addBox(0.0F, -0.5F, -WING_TIP_D / 2.0F, WING_TIP_W, WING_TIP_H, WING_TIP_D),
			PartPose.offsetAndRotation(WING_W, 0.0F, 0.6F, 0.0F, SWEEP, -CARPAL));

		PartDefinition wingR = body.addOrReplaceChild("wing_right",
			uv(WING_U, WING_V)
				.addBox(-WING_W, -0.5F, -WING_D / 2.0F, WING_W, WING_H, WING_D),
			PartPose.offsetAndRotation(-BODY_W / 2.0F + 0.5F, -BODY_H * 0.18F, -0.5F,
				0.0F, -SWEEP, DIHEDRAL));
		wingR.addOrReplaceChild("wing_tip_right",
			uv(WING_TIP_U, WING_TIP_V)
				.addBox(-WING_TIP_W, -0.5F, -WING_TIP_D / 2.0F,
					WING_TIP_W, WING_TIP_H, WING_TIP_D),
			PartPose.offsetAndRotation(-WING_W, 0.0F, 0.6F, 0.0F, -SWEEP, CARPAL));

		return LayerDefinition.create(mesh, TEX_W, TEX_H);
	}

	@Override
	public void setupAnim(SeagullRenderState state) {
		super.setupAnim(state);

		float phase = state.wingPhase;
		float flap = state.flapAmplitude;

		float beat = Mth.sin(phase);
		float beatTip = Mth.sin(phase - FLAP_LAG);

		this.body.xRot = state.xRot * Mth.DEG_TO_RAD + 0.05F * flap * Mth.sin(phase * 2.0F);
		this.body.yRot = state.yRot * Mth.DEG_TO_RAD;
		this.head.xRot = -0.10F - state.xRot * Mth.DEG_TO_RAD * 0.5F;
		this.tail.xRot = 0.12F + 0.14F * flap * Mth.sin(phase * 2.0F);

		this.wingLeft.zRot = -(DIHEDRAL + FLAP_BASE * flap * beat);
		this.wingTipLeft.zRot = -(CARPAL + FLAP_TIP * flap * beatTip);
		this.wingRight.zRot = DIHEDRAL + FLAP_BASE * flap * beat;
		this.wingTipRight.zRot = CARPAL + FLAP_TIP * flap * beatTip;
	}
}

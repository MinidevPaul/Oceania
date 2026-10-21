package com.oceania.client.render;

import com.oceania.mob.SharkVariant;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

public class SharkModel extends EntityModel<SharkRenderState> {

	private static final float WAVE_LAG = 0.55F;

	private static final float WAVE_RATE = 0.33F;

	private static final float OVERLAP = 1.0F;

	private static final float JAW_REACH = 0.45F;
	private static final CubeDeformation SEAM = new CubeDeformation(0.02F);

	private static final float R_HEAD = 0.86F;
	private static final float R_TRUNK1 = 1.00F;
	private static final float R_TRUNK2 = 0.84F;
	private static final float R_PEDUNCLE = 0.48F;

	private static final float L_TRUNK1 = 9.0F;
	private static final float L_TRUNK2 = 8.0F;
	private static final float L_PEDUNCLE = 7.0F;

	private final ModelPart body1;
	private final ModelPart body2;
	private final ModelPart peduncle;
	private final ModelPart caudal;
	private final ModelPart jaw;

	public SharkModel(ModelPart root) {
		super(root);
		this.body1 = root.getChild("body1");
		this.body2 = this.body1.getChild("body2");
		this.peduncle = this.body2.getChild("peduncle");
		this.caudal = this.peduncle.getChild("caudal");
		this.jaw = this.body1.getChild("head").getChild("jaw");
	}

	private static int ri(float v) {
		return Math.max(1, Math.round(v));
	}

	public static LayerDefinition createBodyLayer(SharkVariant variant) {
		SharkVariant.Shape s = variant.shape();
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();

		float g = s.girth();
		float wr = s.widthRatio();

		int h1 = ri(g * 2.0F * R_TRUNK1), w1 = ri(g * wr * R_TRUNK1), d1 = ri(L_TRUNK1);
		int h2 = ri(g * 2.0F * R_TRUNK2), w2 = ri(g * wr * R_TRUNK2), d2 = ri(L_TRUNK2 + OVERLAP);
		int h3 = ri(g * 2.0F * R_PEDUNCLE), w3 = ri(g * wr * R_PEDUNCLE), d3 = ri(L_PEDUNCLE + OVERLAP);

		PartDefinition body1 = root.addOrReplaceChild("body1",
			uv(variant, SharkVariant.Box.BODY1)
				.addBox(-w1 / 2.0F, -h1 / 2.0F, 0.0F, w1, h1, d1, SEAM),
			PartPose.offset(0.0F, 20.0F, -4.0F));

		PartDefinition body2 = body1.addOrReplaceChild("body2",
			uv(variant, SharkVariant.Box.BODY2)
				.addBox(-w2 / 2.0F, -h2 / 2.0F, -OVERLAP, w2, h2, d2, SEAM),
			PartPose.offset(0.0F, 0.0F, L_TRUNK1));

		PartDefinition peduncle = body2.addOrReplaceChild("peduncle",
			uv(variant, SharkVariant.Box.PEDUNCLE)
				.addBox(-w3 / 2.0F, -h3 / 2.0F, -OVERLAP, w3, h3, d3, SEAM),
			PartPose.offset(0.0F, 0.0F, L_TRUNK2));

		int cu = ri(s.caudalUp()), cl = ri(s.caudalLo());
		int cd = ri(s.caudalLen() + OVERLAP), cdl = ri(s.caudalLen() * 0.62F + OVERLAP);
		int cuBase = ri(s.caudalUp() * 0.58F), cuTip = ri(s.caudalUp() * 0.48F);
		int cdTip = ri(s.caudalLen() * 0.70F);
		PartDefinition caudal = peduncle.addOrReplaceChild("caudal",
			uv(variant, SharkVariant.Box.CAUDAL)
				.addBox(-0.5F, -cuBase, -OVERLAP, 1, cuBase, cd)
				.texOffs(variant.texU(SharkVariant.Box.CAUDAL_TIP), variant.texV(SharkVariant.Box.CAUDAL_TIP))
				.addBox(-0.5F, -cu, s.caudalLen() * 0.30F, 1, cuTip, cdTip),
			PartPose.offsetAndRotation(0.0F, 0.0F, L_PEDUNCLE, -0.28F, 0.0F, 0.0F));
		caudal.addOrReplaceChild("caudal_lower",
			uv(variant, SharkVariant.Box.CAUDAL_LOWER).addBox(-0.5F, 0.0F, -OVERLAP, 1, cl, cdl),
			PartPose.rotation(0.26F, 0.0F, 0.0F));

		int hh = ri(g * 2.0F * R_HEAD), wh = ri(g * wr * R_HEAD), dh = ri(s.headLen() + OVERLAP);
		PartDefinition head = body1.addOrReplaceChild("head",
			uv(variant, SharkVariant.Box.HEAD)
				.addBox(-wh / 2.0F, -hh / 2.0F, -s.headLen(), wh, hh, dh, SEAM),
			PartPose.ZERO);

		float snoutBase = s.snoutLen() * 0.62F;
		int ws = ri(wh * 0.60F), hs = ri(hh * 0.30F), ds = ri(snoutBase + OVERLAP);
		int wt = ri(wh * 0.36F), ht = ri(hh * 0.18F), dt = ri(s.snoutLen() * 0.38F + OVERLAP);
		head.addOrReplaceChild("snout",
			uv(variant, SharkVariant.Box.SNOUT)
				.addBox(-ws / 2.0F, -hh * 0.20F - hs / 2.0F, -s.headLen() - snoutBase, ws, hs, ds)
				.texOffs(variant.texU(SharkVariant.Box.SNOUT_TIP), variant.texV(SharkVariant.Box.SNOUT_TIP))
				.addBox(-wt / 2.0F, -hh * 0.10F - ht / 2.0F, -s.headLen() - s.snoutLen(), wt, ht, dt),
			PartPose.ZERO);

		int wj = ri(wh * 0.76F), dj = ri(s.headLen() + s.snoutLen() * JAW_REACH);
		head.addOrReplaceChild("jaw",
			uv(variant, SharkVariant.Box.JAW)
				.addBox(-wj / 2.0F, 0.0F, -s.headLen() - s.snoutLen() * JAW_REACH, wj, 2, dj),
			PartPose.offset(0.0F, hh * 0.30F, 0.0F));

		int dH = ri(s.dorsalH()), dL = ri(s.dorsalLen());
		int dBase = ri(s.dorsalH() * 0.55F), dTip = ri(s.dorsalH() * 0.52F);
		int dLTip = ri(s.dorsalLen() * 0.65F);
		body1.addOrReplaceChild("dorsal",
			uv(variant, SharkVariant.Box.DORSAL)
				.addBox(-0.5F, -dBase, 0.0F, 1, dBase, dL)
				.texOffs(variant.texU(SharkVariant.Box.DORSAL_TIP), variant.texV(SharkVariant.Box.DORSAL_TIP))
				.addBox(-0.5F, -dH, s.dorsalLen() * 0.30F, 1, dTip, dLTip),
			PartPose.offsetAndRotation(0.0F, -h1 / 2.0F + 0.5F, L_TRUNK1 * 0.28F, 0.34F, 0.0F, 0.0F));

		int d2H = ri(s.dorsalH() * 0.34F);
		body2.addOrReplaceChild("dorsal2",
			uv(variant, SharkVariant.Box.DORSAL2).addBox(-0.5F, -d2H, 0.0F, 1, d2H, 3),
			PartPose.offsetAndRotation(0.0F, -h2 / 2.0F + 0.5F, L_TRUNK2 * 0.62F, 0.30F, 0.0F, 0.0F));
		body2.addOrReplaceChild("pelvic",
			uv(variant, SharkVariant.Box.PELVIC).addBox(-w2 / 2.0F, 0.0F, 0.0F, w2, 1, 3),
			PartPose.offset(0.0F, h2 / 2.0F - 0.5F, L_TRUNK2 * 0.48F));

		int plRoot = ri(s.pectLen() * 0.55F), pwRoot = ri(Math.max(3.0F, s.pectLen() * 0.55F));
		int plTip = ri(s.pectLen() * 0.50F), pwTip = ri(Math.max(2.0F, s.pectLen() * 0.34F));
		float pz = s.pectLen() * 0.22F;
		body1.addOrReplaceChild("pectoral_left",
			uv(variant, SharkVariant.Box.PECTORAL).mirror()
				.addBox(0.0F, -0.5F, 0.0F, plRoot, 1, pwRoot)
				.texOffs(variant.texU(SharkVariant.Box.PECTORAL_TIP), variant.texV(SharkVariant.Box.PECTORAL_TIP))
				.mirror().addBox(plRoot - 0.5F, -0.5F, pz, plTip, 1, pwTip),
			PartPose.offsetAndRotation(w1 / 2.0F - 0.5F, h1 * 0.18F, L_TRUNK1 * 0.18F,
				0.0F, -0.42F, 0.52F));
		body1.addOrReplaceChild("pectoral_right",
			uv(variant, SharkVariant.Box.PECTORAL)
				.addBox(-plRoot, -0.5F, 0.0F, plRoot, 1, pwRoot)
				.texOffs(variant.texU(SharkVariant.Box.PECTORAL_TIP), variant.texV(SharkVariant.Box.PECTORAL_TIP))
				.addBox(-plRoot - plTip + 0.5F, -0.5F, pz, plTip, 1, pwTip),
			PartPose.offsetAndRotation(-w1 / 2.0F + 0.5F, h1 * 0.18F, L_TRUNK1 * 0.18F,
				0.0F, 0.42F, -0.52F));

		return LayerDefinition.create(mesh, SharkVariant.TEX_W, SharkVariant.TEX_H);
	}

	private static CubeListBuilder uv(SharkVariant variant, SharkVariant.Box box) {
		return CubeListBuilder.create().texOffs(variant.texU(box), variant.texV(box));
	}

	@Override
	public void setupAnim(SharkRenderState state) {
		super.setupAnim(state);

		this.body1.xRot = state.xRot * Mth.DEG_TO_RAD;
		this.body1.yRot = state.yRot * Mth.DEG_TO_RAD;
		this.body1.zRot = state.bankAmount * 0.55F;

		float phase = state.walkAnimationPos * WAVE_RATE;
		float amp = Math.min(state.walkAnimationSpeed, 1.0F);

		if (state.charging) {
			amp = Math.min(amp * 1.7F, 1.6F);
		}

		float a = 0.12F + 0.88F * amp;

		this.body1.yRot += -0.06F * a * Mth.sin(phase);
		this.body2.yRot = 0.15F * a * Mth.sin(phase - WAVE_LAG);
		this.peduncle.yRot = 0.26F * a * Mth.sin(phase - WAVE_LAG * 2.0F);
		this.caudal.yRot = 0.32F * a * Mth.sin(phase - WAVE_LAG * 3.0F);

		this.jaw.xRot = state.jawOpen * 0.6F;
	}
}

package com.oceania.client.render;

import com.oceania.mob.AbstractJellyfish;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

public abstract class JellyfishModel extends EntityModel<JellyfishRenderState> {

	private static final float BELL_HEIGHT_SHARE = 0.7F;
	private static final float ORAL_KICK_SHARE = 0.4F;
	private static final float TRAILER_SWAY = 0.06F;

	private static final float GLOW_MIN = 0.74F;

	protected final ModelPart body;

	private final ModelPart[] tentacles;
	private final float[] tentacleAngles;
	private final ModelPart[] orals;
	private final float[] oralAngles;
	@Nullable
	private final ModelPart trailer;

	private final float pulseSquash;
	private final float tentacleLag;
	private final float tentacleKick;
	private final float tentacleSplay;
	private final float oralSplay;
	private final int[] tints;

	protected JellyfishModel(ModelPart root, float pulseSquash, float tentacleLag,
	                         float tentacleKick, int tentacleCount, float tentacleSplay,
	                         int oralCount, float oralSplay, int[] tints) {
		super(root, OceaniaRenderTypes::jellyfish);
		this.body = root.getChild("body");
		this.pulseSquash = pulseSquash;
		this.tentacleLag = tentacleLag;
		this.tentacleKick = tentacleKick;
		this.tentacleSplay = tentacleSplay;
		this.oralSplay = oralSplay;
		this.tints = tints;

		this.tentacleAngles = ringAngles(tentacleCount, false);
		this.tentacles = new ModelPart[tentacleCount];
		for (int i = 0; i < tentacleCount; i++) {
			this.tentacles[i] = this.body.getChild("tentacle" + i);
		}
		this.oralAngles = ringAngles(oralCount, true);
		this.orals = new ModelPart[oralCount];
		for (int i = 0; i < oralCount; i++) {
			this.orals[i] = this.body.getChild("oral" + i);
		}
		this.trailer = this.body.hasChild("trailer") ? this.body.getChild("trailer") : null;
	}

	protected static float[] ringAngles(int count, boolean halfStep) {
		float[] angles = new float[count];
		for (int i = 0; i < count; i++) {
			angles[i] = Mth.TWO_PI * (i + (halfStep ? 0.5F : 0.0F)) / count;
		}
		return angles;
	}

	protected static PartDefinition addBody(PartDefinition root, float bellY) {
		return root.addOrReplaceChild("body", CubeListBuilder.create(),
			PartPose.offset(0.0F, bellY, 0.0F));
	}

	protected static void addBell(PartDefinition body,
	                             int rimU, int rimV, float rimW, float rimH, float rimD,
	                             int bellU, int bellV, float bellW, float bellH, float bellD,
	                             int domeU, int domeV, float domeW, float domeH, float domeD) {
		float y = 0.0F;
		y = stack(body, "rim", rimU, rimV, rimW, rimH, rimD, y);
		y = stack(body, "bell", bellU, bellV, bellW, bellH, bellD, y);
		stack(body, "dome", domeU, domeV, domeW, domeH, domeD, y);
	}

	private static float stack(PartDefinition body, String name, int u, int v,
	                           float w, float h, float d, float y) {
		body.addOrReplaceChild(name,
			CubeListBuilder.create().texOffs(u, v)
				.addBox(-w / 2.0F, -h, -d / 2.0F, w, h, d),
			PartPose.offset(0.0F, y, 0.0F));
		return y - h;
	}

	protected static void addStalks(PartDefinition body, String prefix, float[] angles,
	                                float radius, float splay,
	                                int u, int v, float w, float h, float d, float inflate) {
		for (int i = 0; i < angles.length; i++) {
			float a = angles[i];
			body.addOrReplaceChild(prefix + i,
				CubeListBuilder.create().texOffs(u, v)
					.addBox(-w / 2.0F, 0.0F, -d / 2.0F, w, h, d, new CubeDeformation(inflate)),
				PartPose.offsetAndRotation(Mth.cos(a) * radius, 0.0F, Mth.sin(a) * radius,
					Mth.sin(a) * splay, 0.0F, -Mth.cos(a) * splay));
		}
	}

	protected static void addTrailer(PartDefinition body, int u, int v,
	                                float w, float h, float d, float inflate) {
		body.addOrReplaceChild("trailer",
			CubeListBuilder.create().texOffs(u, v)
				.addBox(-w / 2.0F, 0.0F, -d / 2.0F, w, h, d, new CubeDeformation(inflate)),
			PartPose.ZERO);
	}

	protected static void requireGeneratedMatch(String species, int generatedPulse,
	                                            int declaredPulse, int[] tints) {
		if (generatedPulse != declaredPulse) {
			throw new IllegalStateException(species + ": generated PULSE_TICKS is " + generatedPulse
				+ " but the entity declares " + declaredPulse
				+ ". Update the entity constant, or the bell will not be in phase with the thrust.");
		}
		if (tints.length != AbstractJellyfish.TINT_COUNT) {
			throw new IllegalStateException(species + ": the generated sheet has " + tints.length
				+ " tints but AbstractJellyfish.TINT_COUNT is " + AbstractJellyfish.TINT_COUNT
				+ ", so some of them could never be rolled.");
		}
	}

	@Override
	public void setupAnim(JellyfishRenderState state) {
		super.setupAnim(state);

		float turns = state.bellTurns;
		float phase = turns * Mth.TWO_PI;

		float contraction = 0.5F * (1.0F - Mth.cos(phase));
		float squeeze = this.pulseSquash * contraction;
		this.body.xScale = 1.0F - squeeze;
		this.body.zScale = 1.0F - squeeze;
		this.body.yScale = 1.0F - squeeze * BELL_HEIGHT_SHARE;

		float kick = this.tentacleKick * Mth.sin(phase - this.tentacleLag);
		splay(this.tentacles, this.tentacleAngles, this.tentacleSplay + kick);
		splay(this.orals, this.oralAngles, this.oralSplay + kick * ORAL_KICK_SHARE);
		if (this.trailer != null) {
			this.trailer.xRot = TRAILER_SWAY * Mth.sin(phase);
		}

	}

	private static void splay(ModelPart[] parts, float[] angles, float splay) {
		for (int i = 0; i < parts.length; i++) {
			parts[i].xRot = Mth.sin(angles[i]) * splay;
			parts[i].zRot = -Mth.cos(angles[i]) * splay;
		}
	}

	public static int tintFor(JellyfishRenderState state, int[] tints) {
		float contraction = 0.5F * (1.0F - Mth.cos(state.bellTurns * Mth.TWO_PI));
		int alpha = Math.round(255.0F * Mth.lerp(contraction, GLOW_MIN, 1.0F));
		return (alpha << 24) | (tints[state.tint] & 0xFFFFFF);
	}
}

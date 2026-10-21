package com.oceania.client.render;

import com.oceania.mob.CrownJellyEntity;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

import static com.oceania.client.render.CrownJellyGeometry.*;

public class CrownJellyModel extends JellyfishModel {

	public CrownJellyModel(ModelPart root) {
		super(root, PULSE_SQUASH, TENTACLE_LAG, TENTACLE_KICK,
			TENTACLES, TENTACLE_SPLAY, ORALS, ORAL_SPLAY, TINTS);
	}

	public static LayerDefinition createBodyLayer() {
		requireGeneratedMatch("crown jelly", PULSE_TICKS, CrownJellyEntity.PULSE_TICKS, TINTS);

		MeshDefinition mesh = new MeshDefinition();
		PartDefinition body = addBody(mesh.getRoot(), BELL_Y);

		addBell(body,
			RIM_U, RIM_V, RIM_W, RIM_H, RIM_D,
			BELL_U, BELL_V, BELL_W, BELL_H, BELL_D,
			DOME_U, DOME_V, DOME_W, DOME_H, DOME_D);

		addStalks(body, "tentacle", ringAngles(TENTACLES, false), TENTACLE_R, TENTACLE_SPLAY,
			TENTACLE_U, TENTACLE_V, TENTACLE_W, TENTACLE_H, TENTACLE_D, TENTACLE_INFLATE);

		addTrailer(body, TRAILER_U, TRAILER_V, TRAILER_W, TRAILER_H, TRAILER_D, TRAILER_INFLATE);

		return LayerDefinition.create(mesh, TEX_W, TEX_H);
	}
}

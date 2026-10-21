package com.oceania.client.raft;

import com.oceania.raft.RaftEntity;
import com.oceania.raft.RaftLevel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class RaftEntityLight {

	private static final List<RaftEntity> RAFTS = new ArrayList<>();
	private static long builtAtTick = Long.MIN_VALUE;
	private static Level builtFor;

	private RaftEntityLight() {
	}

	public static void forget() {
		RAFTS.clear();
		builtFor = null;
		builtAtTick = Long.MIN_VALUE;
		reported = false;
	}

	public static int blend(Entity entity, float partialTick, int worldPacked) {
		List<RaftEntity> rafts = rafts(entity.level());
		if (rafts.isEmpty()) {
			return worldPacked;
		}

		AABB box = entity.getBoundingBox();
		Vec3 probe = null;
		int packed = worldPacked;
		for (RaftEntity raft : rafts) {

			if (raft == entity || raft.blocks().isEmpty()) {
				continue;
			}

			if (!raft.getBoundingBox().intersects(box.minX - 1.0, box.minY - 1.0, box.minZ - 1.0,
					box.maxX + 1.0, box.maxY + 1.0, box.maxZ + 1.0)) {
				continue;
			}
			if (probe == null) {
				probe = entity.getLightProbePosition(partialTick);
			}
			Vec3 local = raft.toLocal(probe, partialTick);
			AABB frame = raft.blocks().localBounds();
			if (local.x < frame.minX - 1.0 || local.x > frame.maxX + 1.0
				|| local.y < frame.minY - 1.0 || local.y > frame.maxY + 1.0
				|| local.z < frame.minZ - 1.0 || local.z > frame.maxZ + 1.0) {
				continue;
			}
			RaftLevel level = raft.raftLevel();
			BlockPos at = BlockPos.containing(local);
			packed = LightCoordsUtil.max(packed, LightCoordsUtil.pack(
				level.getBrightness(LightLayer.BLOCK, at), level.getBrightness(LightLayer.SKY, at)));
		}
		if (packed != worldPacked && !reported) {
			reported = true;

			com.oceania.Oceania.LOGGER.info(
				"[raft] entity lighting live: {} at {} took block {} sky {} from the raft, world had block {} sky {}",
				entity.getType().getDescriptionId(), entity.blockPosition().toShortString(),
				LightCoordsUtil.block(packed), LightCoordsUtil.sky(packed),
				LightCoordsUtil.block(worldPacked), LightCoordsUtil.sky(worldPacked));
		}
		return packed;
	}

	private static boolean reported;

	private static List<RaftEntity> rafts(Level level) {
		if (!(level instanceof ClientLevel client)) {
			return List.of();
		}
		long tick = level.getGameTime();
		if (level == builtFor && tick == builtAtTick) {
			return RAFTS;
		}
		builtFor = level;
		builtAtTick = tick;
		RAFTS.clear();
		for (Entity e : client.entitiesForRendering()) {
			if (e instanceof RaftEntity raft && !raft.blocks().isEmpty()) {
				RAFTS.add(raft);
			}
		}
		return RAFTS;
	}

	public static AABB reach(RaftEntity raft) {
		return raft.getBoundingBox().inflate(1.0);
	}
}

package com.oceania.client.raft;

import com.oceania.raft.RaftCollider;
import com.oceania.raft.RaftEntity;
import com.oceania.raft.RaftMoveTrace;
import com.oceania.raft.RaftNetworking;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public final class RaftClient {
	private RaftClient() {
	}

	private record Rider(int raftId, Vec3 local, int tick) {
	}

	private static final Map<Integer, Rider> RIDERS = new HashMap<>();

	private static final int RIDER_FRESH_TICKS = 3;
	private static int tick;
	private static boolean wasWalking;

	private static final Map<Integer, Pending> PENDING = new HashMap<>();
	private static final int PENDING_TIMEOUT_TICKS = 100;

	private record Pending(CompoundTag blocks, int tick) {
	}

	private static final double BOAT_STAND_TOLERANCE = 0.1;

	private static void carryOnBoat(Minecraft mc) {
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null || player.isPassenger() || player.isSpectator()) return;
		AABB box = player.getBoundingBox();
		for (AbstractBoat boat : mc.level.getEntitiesOfClass(AbstractBoat.class, box.inflate(0.5))) {
			AABB top = boat.getBoundingBox();
			boolean standing = box.minY >= top.maxY - BOAT_STAND_TOLERANCE && box.minY <= top.maxY + BOAT_STAND_TOLERANCE
				&& box.maxX > top.minX && box.minX < top.maxX && box.maxZ > top.minZ && box.minZ < top.maxZ;
			if (!standing) continue;
			double dx = boat.getX() - boat.xo;
			double dz = boat.getZ() - boat.zo;
			if (dx * dx + dz * dz < 1.0E-10) return;
			player.setPos(player.getX() + dx, player.getY(), player.getZ() + dz);
			return;
		}
	}

	private static int traceWalkTicks;
	private static double traceWalkInput;
	private static @Nullable Vec3 traceWalkStart;

	private static void moveTrace(Minecraft mc, @Nullable RaftCollider.Contact contact) {
		if (!RaftMoveTrace.ENABLED || mc.player == null || mc.level == null) {
			RaftMoveTrace.armed = false;
			return;
		}
		RaftMoveTrace.tick();
		boolean near = contact != null || mc.player.getVehicle() instanceof AbstractBoat
			|| !mc.level.getEntitiesOfClass(RaftEntity.class, mc.player.getBoundingBox().inflate(4.0)).isEmpty()
			|| !mc.level.getEntitiesOfClass(AbstractBoat.class, mc.player.getBoundingBox().inflate(4.0)).isEmpty();
		RaftMoveTrace.armed = near;
		if (!near) {
			traceWalkTicks = 0;
			traceWalkStart = null;
			traceWalkInput = 0;
			return;
		}

		Vec2 move = mc.player.input.getMoveVector();
		double input = Math.abs(move.y) + Math.abs(move.x);
		RaftMoveTrace.record(String.format("tick end: pos %s vel %s onGround=%s riding=%s pose=%s sneak=%s input(f=%.1f l=%.1f jump=%s) onDeck=%s",
			RaftMoveTrace.fmt(mc.player.position()), RaftMoveTrace.fmt(mc.player.getDeltaMovement()), mc.player.onGround(), mc.player.isPassenger(),
			mc.player.getPose(), mc.player.isShiftKeyDown(), move.y, move.x, mc.player.input.keyPresses.jump(), contact != null));
		if (input == 0 || (mc.player.isPassenger() && !(mc.player.getVehicle() instanceof AbstractBoat))) {
			traceWalkTicks = 0;
			traceWalkStart = null;
			traceWalkInput = 0;
			return;
		}
		if (traceWalkStart == null) traceWalkStart = mc.player.position();
		traceWalkInput += input;
		if (++traceWalkTicks < 20) return;
		double moved = mc.player.position().subtract(traceWalkStart).horizontalDistance();
		if (moved < 0.25 && traceWalkInput >= 10) {
			RaftMoveTrace.dump(String.format("stuck: a held movement key moved the player %.2f blocks in 20 ticks (input %.1f)", moved, traceWalkInput));
		}
		traceWalkTicks = 0;
		traceWalkStart = null;
		traceWalkInput = 0;
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(RaftNetworking.BlocksPayload.TYPE, (payload, context) -> {
			RaftEntity raft = raft(context.client(), payload.raftId());
			if (raft != null) {
				raft.applyBlocks(payload.blocks());
			} else {

				PENDING.put(payload.raftId(), new Pending(payload.blocks(), tick));
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(RaftNetworking.BlockPayload.TYPE, (payload, context) -> {
			RaftEntity raft = raft(context.client(), payload.raftId());
			if (raft != null) raft.applyBlock(payload.pos(), payload.state(), payload.nbt());
		});
		ClientPlayNetworking.registerGlobalReceiver(RaftNetworking.BlockEventPayload.TYPE, (payload, context) -> {
			RaftEntity raft = raft(context.client(), payload.raftId());
			if (raft != null) raft.raftLevel().blockEvent(payload.pos(), raft.raftLevel().getBlockState(payload.pos()).getBlock(), payload.eventType(), payload.data());
		});
		ClientPlayNetworking.registerGlobalReceiver(RaftNetworking.BreakProgressPayload.TYPE, (payload, context) -> {
			RaftEntity raft = raft(context.client(), payload.raftId());
			if (raft != null) raft.setBreakStage(payload.pos(), payload.stage());
		});
		ClientPlayNetworking.registerGlobalReceiver(RaftNetworking.RidersPayload.TYPE, (payload, context) -> {
			int[] ids = payload.playerIds();
			double[] locals = payload.locals();
			for (int i = 0; i < ids.length; i++) {
				RIDERS.put(ids[i], new Rider(payload.raftId(), new Vec3(locals[i * 3], locals[i * 3 + 1], locals[i * 3 + 2]), tick));
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(RaftClient::tick);
	}

	private static @Nullable RaftEntity raft(Minecraft mc, int id) {
		ClientLevel level = mc.level;
		if (level == null) return null;
		return level.getEntity(id) instanceof RaftEntity raft ? raft : null;
	}

	private static void tick(Minecraft mc) {
		if (mc.level == null || mc.player == null) {
			RIDERS.clear();
			PENDING.clear();
			wasWalking = false;
			return;
		}
		if (mc.isPaused()) return;
		tick++;
		if (!PENDING.isEmpty()) {
			for (Iterator<Map.Entry<Integer, Pending>> it = PENDING.entrySet().iterator(); it.hasNext(); ) {
				Map.Entry<Integer, Pending> entry = it.next();
				RaftEntity raft = raft(mc, entry.getKey());
				if (raft != null) {
					raft.applyBlocks(entry.getValue().blocks());
					it.remove();
				} else if (tick - entry.getValue().tick() > PENDING_TIMEOUT_TICKS) {
					it.remove();
				}
			}
		}
		RaftCollider.Contact contact = RaftCollider.clientContact;
		if (contact == null) carryOnBoat(mc);
		moveTrace(mc, contact);
		if (contact != null) {
			ClientPlayNetworking.send(new RaftNetworking.WalkerPayload(contact.raft().getId(), contact.local()));
			wasWalking = true;
		} else if (wasWalking) {
			ClientPlayNetworking.send(new RaftNetworking.WalkerPayload(-1, Vec3.ZERO));
			wasWalking = false;
		}
		if (RIDERS.isEmpty()) return;
		for (Iterator<Map.Entry<Integer, Rider>> it = RIDERS.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<Integer, Rider> entry = it.next();
			Rider rider = entry.getValue();
			if (tick - rider.tick > RIDER_FRESH_TICKS) {
				it.remove();
				continue;
			}
			Entity entity = mc.level.getEntity(entry.getKey());
			if (entity == null || entity == mc.player || entity.isPassenger()) continue;
			if (!(mc.level.getEntity(rider.raftId) instanceof RaftEntity raft)) continue;
			Vec3 world = raft.toWorld(rider.local);
			entity.setPos(world.x, world.y, world.z);
		}
	}
}

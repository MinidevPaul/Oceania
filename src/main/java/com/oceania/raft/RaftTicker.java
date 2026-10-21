package com.oceania.raft;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

public final class RaftTicker {
	private RaftTicker() {
	}

	static final long WALKER_FRESH_TICKS = 2;

	public static void initServer() {
		ServerTickEvents.END_LEVEL_TICK.register(level -> {
			List<RaftEntity> rafts = new ArrayList<>();
			for (Entity e : level.getAllEntities()) {
				if (e instanceof RaftEntity raft && !raft.isRemoved()) rafts.add(raft);
			}
			RaftCollider.beginServerTick(level);
			for (RaftEntity raft : rafts) {
				RaftCollider.collideEntities(raft);
				relayRiders(level, raft);
			}
		});
	}

	public static void collideAll(Level level, Iterable<Entity> entities) {
		RaftCollider.beginClientTick(level);
		List<RaftEntity> rafts = new ArrayList<>();
		for (Entity e : entities) {
			if (e instanceof RaftEntity raft && !raft.isRemoved()) rafts.add(raft);
		}
		for (RaftEntity raft : rafts) {
			RaftCollider.collideEntities(raft);
		}
	}

	private static void relayRiders(ServerLevel level, RaftEntity raft) {
		long now = level.getGameTime();
		List<ServerPlayer> walkers = null;
		for (ServerPlayer player : level.players()) {
			RaftRider rider = (RaftRider) player;
			if (rider.oceania$walkerRaft() == raft.getId() && now - rider.oceania$walkerTime() <= WALKER_FRESH_TICKS) {
				if (walkers == null) walkers = new ArrayList<>();
				walkers.add(player);
			}
		}
		if (walkers == null) return;
		int[] ids = new int[walkers.size()];
		double[] locals = new double[walkers.size() * 3];
		for (int i = 0; i < walkers.size(); i++) {
			ids[i] = walkers.get(i).getId();
			var local = ((RaftRider) walkers.get(i)).oceania$walkerLocal();
			locals[i * 3] = local.x;
			locals[i * 3 + 1] = local.y;
			locals[i * 3 + 2] = local.z;
		}
		RaftNetworking.sendToTracking(raft, new RaftNetworking.RidersPayload(raft.getId(), ids, locals));
	}
}

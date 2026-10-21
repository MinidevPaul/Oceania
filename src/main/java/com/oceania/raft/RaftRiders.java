package com.oceania.raft;

import net.minecraft.world.entity.Entity;

public final class RaftRiders {
	private RaftRiders() {
	}

	public static boolean isOnRaft(Entity entity, long gameTime) {
		return entity instanceof RaftRider rider && rider.oceania$isOnRaft(gameTime);
	}
}

package com.oceania.raft;

import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public interface RaftRider {
	void oceania$markOnRaft(long gameTime);

	boolean oceania$isOnRaft(long gameTime);

	void oceania$setWalker(int raftId, Vec3 local, long gameTime);

	int oceania$walkerRaft();

	Vec3 oceania$walkerLocal();

	long oceania$walkerTime();

	void oceania$setMenuRaft(@Nullable RaftEntity raft);

	@Nullable RaftEntity oceania$menuRaft();

	@Nullable RaftBeds.RaftBed oceania$bed();

	void oceania$setBed(@Nullable RaftBeds.RaftBed bed);

	@Nullable RaftBeds.RaftBed oceania$sleepingIn();

	void oceania$setSleepingIn(@Nullable RaftBeds.RaftBed bed);

	@Nullable RaftInteraction.Breaking oceania$breaking();

	void oceania$setBreaking(@Nullable RaftInteraction.Breaking breaking);

	long oceania$breakCooldownUntil();

	void oceania$setBreakCooldownUntil(long gameTime);
}

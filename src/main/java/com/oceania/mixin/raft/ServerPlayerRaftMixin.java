package com.oceania.mixin.raft;

import com.oceania.raft.RaftBeds;
import com.oceania.raft.RaftEntity;
import com.oceania.raft.RaftInteraction;
import com.oceania.raft.RaftRider;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerRaftMixin implements RaftRider {

	@Unique
	private static final long OCEANIA_GRACE_TICKS = 20;

	@Unique
	private long oceania$lastOnRaft = Long.MIN_VALUE / 2;
	@Unique
	private int oceania$walkerRaft = -1;
	@Unique
	private Vec3 oceania$walkerLocal = Vec3.ZERO;
	@Unique
	private long oceania$walkerTime = Long.MIN_VALUE / 2;
	@Unique
	private @Nullable RaftEntity oceania$menuRaft;
	@Unique
	private @Nullable RaftBeds.RaftBed oceania$bed;
	@Unique
	private @Nullable RaftBeds.RaftBed oceania$sleepingIn;
	@Unique
	private @Nullable RaftInteraction.Breaking oceania$breaking;
	@Unique
	private long oceania$breakCooldownUntil;

	@Override
	public void oceania$markOnRaft(long gameTime) {
		this.oceania$lastOnRaft = gameTime;
	}

	@Override
	public boolean oceania$isOnRaft(long gameTime) {
		return gameTime - this.oceania$lastOnRaft <= OCEANIA_GRACE_TICKS;
	}

	@Override
	public void oceania$setWalker(int raftId, Vec3 local, long gameTime) {
		this.oceania$walkerRaft = raftId;
		this.oceania$walkerLocal = local;
		this.oceania$walkerTime = gameTime;
	}

	@Override
	public int oceania$walkerRaft() {
		return oceania$walkerRaft;
	}

	@Override
	public Vec3 oceania$walkerLocal() {
		return oceania$walkerLocal;
	}

	@Override
	public long oceania$walkerTime() {
		return oceania$walkerTime;
	}

	@Override
	public void oceania$setMenuRaft(@Nullable RaftEntity raft) {
		this.oceania$menuRaft = raft;
	}

	@Override
	public @Nullable RaftEntity oceania$menuRaft() {
		return oceania$menuRaft;
	}

	@Override
	public @Nullable RaftBeds.RaftBed oceania$bed() {
		return oceania$bed;
	}

	@Override
	public void oceania$setBed(@Nullable RaftBeds.RaftBed bed) {
		this.oceania$bed = bed;
	}

	@Override
	public @Nullable RaftBeds.RaftBed oceania$sleepingIn() {
		return oceania$sleepingIn;
	}

	@Override
	public void oceania$setSleepingIn(@Nullable RaftBeds.RaftBed bed) {
		this.oceania$sleepingIn = bed;
	}

	@Override
	public @Nullable RaftInteraction.Breaking oceania$breaking() {
		return oceania$breaking;
	}

	@Override
	public void oceania$setBreaking(@Nullable RaftInteraction.Breaking breaking) {
		this.oceania$breaking = breaking;
	}

	@Override
	public long oceania$breakCooldownUntil() {
		return oceania$breakCooldownUntil;
	}

	@Override
	public void oceania$setBreakCooldownUntil(long gameTime) {
		this.oceania$breakCooldownUntil = gameTime;
	}

	@Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
	private void oceania$saveRaftBed(ValueOutput output, CallbackInfo ci) {
		if (oceania$bed != null) output.store("OceaniaRaftBed", RaftBeds.RaftBed.CODEC, oceania$bed);
		if (oceania$sleepingIn != null) output.store("OceaniaRaftSleeping", RaftBeds.RaftBed.CODEC, oceania$sleepingIn);
	}

	@Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
	private void oceania$loadRaftBed(ValueInput input, CallbackInfo ci) {
		oceania$bed = input.read("OceaniaRaftBed", RaftBeds.RaftBed.CODEC).orElse(null);
		oceania$sleepingIn = input.read("OceaniaRaftSleeping", RaftBeds.RaftBed.CODEC).orElse(null);
	}

	@Inject(method = "restoreFrom", at = @At("TAIL"))
	private void oceania$restoreRaftBed(ServerPlayer old, boolean alive, CallbackInfo ci) {
		oceania$bed = ((RaftRider) old).oceania$bed();
	}

	@Inject(method = "setRespawnPosition", at = @At("TAIL"))
	private void oceania$landBedWins(@Nullable ServerPlayer.RespawnConfig config, boolean showMessage, CallbackInfo ci) {
		if (config != null) oceania$bed = null;
	}
}

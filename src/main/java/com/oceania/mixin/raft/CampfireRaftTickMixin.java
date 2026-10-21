package com.oceania.mixin.raft;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.oceania.raft.RaftLevel;
import com.oceania.raft.RaftTickers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(CampfireBlockEntity.class)
public abstract class CampfireRaftTickMixin {

	@WrapOperation(
		method = "cookTick",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/Containers;dropItemStack(Lnet/minecraft/world/level/Level;DDDLnet/minecraft/world/item/ItemStack;)V"))
	private static void oceania$dropOnRaft(Level level, double x, double y, double z, ItemStack stack, Operation<Void> original) {
		RaftLevel raft = RaftTickers.active();
		if (raft == null) {
			original.call(level, x, y, z, stack);
			return;
		}
		BlockPos world = raft.worldBlockPos(new BlockPos((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z)));
		original.call(level, (double) world.getX(), (double) world.getY(), (double) world.getZ(), stack);
	}

	@WrapOperation(
		method = "cookTick",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;sendBlockUpdated(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;I)V"))
	private static void oceania$updateOnRaft(ServerLevel level, BlockPos pos, BlockState oldState, BlockState newState, int flags, Operation<Void> original) {
		RaftLevel raft = RaftTickers.active();
		if (raft != null) raft.sendBlockUpdated(pos, oldState, newState, flags);
		else original.call(level, pos, oldState, newState, flags);
	}

	@WrapOperation(
		method = "cookTick",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;gameEvent(Lnet/minecraft/core/Holder;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/gameevent/GameEvent$Context;)V"))
	private static void oceania$eventOnRaft(ServerLevel level, Holder<GameEvent> event, BlockPos pos, GameEvent.Context context, Operation<Void> original) {
		RaftLevel raft = RaftTickers.active();
		if (raft != null) raft.gameEvent(event, pos, context);
		else original.call(level, event, pos, context);
	}

	@WrapOperation(
		method = "cookTick",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/entity/CampfireBlockEntity;setChanged(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V"))
	private static void oceania$changedOnRaft(Level level, BlockPos pos, BlockState state, Operation<Void> original) {
		RaftLevel raft = RaftTickers.active();
		if (raft == null) {
			original.call(level, pos, state);
			return;
		}
		raft.blockEntityChanged(pos);
		if (!state.isAir()) raft.updateNeighbourForOutputSignal(pos, state.getBlock());
	}
}

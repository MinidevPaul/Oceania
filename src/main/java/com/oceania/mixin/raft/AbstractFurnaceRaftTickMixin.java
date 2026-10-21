package com.oceania.mixin.raft;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.oceania.raft.RaftLevel;
import com.oceania.raft.RaftTickers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class AbstractFurnaceRaftTickMixin {

	@WrapOperation(
		method = "serverTick",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
	private static boolean oceania$litOnRaft(ServerLevel level, BlockPos pos, BlockState state, Operation<Boolean> original) {
		RaftLevel raft = RaftTickers.active();
		return raft != null ? raft.setBlock(pos, state, 3) : original.call(level, pos, state);
	}

	@WrapOperation(
		method = "serverTick",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity;setChanged(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V"))
	private static void oceania$changedOnRaft(Level level, BlockPos pos, BlockState state, Operation<Void> original) {
		RaftLevel raft = RaftTickers.active();
		if (raft == null) {
			original.call(level, pos, state);
			return;
		}
		raft.blockEntityChanged(pos);
		if (!state.isAir()) raft.updateNeighbourForOutputSignal(pos, state.getBlock());
	}

	@WrapOperation(
		method = "serverTick",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity;consumeFuel(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/NonNullList;Lnet/minecraft/world/item/ItemStack;)V"))
	private static void oceania$remainderOnRaft(ServerLevel level, BlockPos pos, NonNullList<ItemStack> items, ItemStack fuel, Operation<Void> original) {
		RaftLevel raft = RaftTickers.active();
		original.call(level, raft != null ? raft.worldBlockPos(pos) : pos, items, fuel);
	}
}

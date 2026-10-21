package com.oceania.mixin.raft;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.oceania.raft.RaftLevel;
import com.oceania.raft.RaftTickers;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BrewingStandBlockEntity.class)
public abstract class BrewingStandRaftTickMixin {

	@WrapOperation(
		method = "serverTick",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
	private static boolean oceania$bottlesOnRaft(ServerLevel level, BlockPos pos, BlockState state, int flags, Operation<Boolean> original) {
		RaftLevel raft = RaftTickers.active();
		return raft != null ? raft.setBlock(pos, state, flags) : original.call(level, pos, state, flags);
	}

	@WrapOperation(
		method = "serverTick",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/entity/BrewingStandBlockEntity;setChanged(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V"))
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
		method = {"serverTick", "doBrew"},
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
		method = "doBrew",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;levelEvent(ILnet/minecraft/core/BlockPos;I)V"))
	private static void oceania$soundOnRaft(ServerLevel level, int type, BlockPos pos, int data, Operation<Void> original) {
		RaftLevel raft = RaftTickers.active();
		if (raft != null) raft.levelEvent(null, type, pos, data);
		else original.call(level, type, pos, data);
	}
}
